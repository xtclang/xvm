package org.xvm.asm;


import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;


/**
 * A simple ModuleRepository that manages its contents in a directory.
 */
public class DirRepository
        implements ModuleRepository {
    // ----- constructors  -------------------------------------------------------------------------

    /**
     * Construct a File System ModuleRepository.
     *
     * @param dir        the directory that contains the repository contents
     * @param fReadOnly  true to make the repository "read-only"
     */
    public DirRepository(File dir, boolean fReadOnly) {
        this(dir, fReadOnly, true);
    }

    /**
     * Construct a File System ModuleRepository.
     *
     * @param dir            the directory that contains the repository contents
     * @param fReadOnly      true to make the repository "read-only"
     * @param fScanFallback  true to scan the directory when a module does not use its conventional
     *                       file name
     */
    public DirRepository(File dir, boolean fReadOnly, boolean fScanFallback) {
        assert dir != null && dir.isDirectory();

        m_dir           = dir;
        m_fRO           = fReadOnly;
        m_fScanFallback = fScanFallback;
    }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the directory containing the module files
     */
    public File getDir() {
        return m_dir;
    }

    /**
     * @return true iff read-only
     */
    public boolean isReadOnly() {
        return m_fRO;
    }


    // ----- ModuleRepository API ------------------------------------------------------------------

    @Override
    public Set<String> getModuleNames() {
        ensureCache();
        return Collections.unmodifiableSet(modulesByName.keySet());
    }

    @Override
    public ModuleStructure loadModule(String sModule) {
        ModuleInfo info = findModuleDirect(sModule);
        if (info != null) {
            return info.ensureModule();
        }

        if (m_fScanFallback || cacheComplete) {
            ensureCache();
            info = modulesByName.get(sModule);
            if (info != null) {
                return info.ensureModule();
            }
        }
        return null;
    }

    @Override
    public void storeModule(ModuleStructure module)
            throws IOException {
        if (m_fRO) {
            throw new IOException("repository is read-only: " + this);
        }

        String name = module.getIdentityConstant().getName();
        ModuleInfo info = modulesByName.get(name);
        File file = (info == null)
                ? new File(m_dir, module.getIdentityConstant().getUnqualifiedName() + ".xtc")
                : info.file;

        if (file.exists() && !file.delete()) {
            throw new IOException("unable to delete " + file);
        }

        module.getFileStructure().writeTo(file);

        if (file.exists()) {
            info = new ModuleInfo(file);
            modulesByName.put(name, info);
            modulesByFile.put(file, info);
        } else {
            modulesByName.remove(name);
            modulesByFile.remove(file);
        }
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode() {
        return m_dir.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this || !(obj instanceof DirRepository that)) {
            return obj == this;
        }

        return this.m_dir.equals(that.m_dir) &&
               this.m_fRO           == that.m_fRO &&
               this.m_fScanFallback == that.m_fScanFallback;
    }

    @Override
    public String toString() {
        return "DirRepository(Path=" + m_dir.toString() + ", RO=" + m_fRO + ")";
    }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Look for a module using the conventional file name before scanning and deserializing every
     * module in the repository.
     *
     * @param sModule  the qualified module name
     *
     * @return the matching module information, or null
     */
    private ModuleInfo findModuleDirect(String sModule) {
        int    ofDot = sModule.indexOf('.');
        String name  = ofDot < 0 ? sModule : sModule.substring(0, ofDot);
        File   file  = new File(m_dir, name + ".xtc");

        if (!ModulesOnly.accept(file)) {
            return null;
        }

        ModuleInfo info = modulesByFile.get(file);
        if (info == null || info.timestamp != file.lastModified() || info.size != file.length()) {
            if (info != null && !info.err) {
                modulesByName.remove(info.name, info);
            }
            info = new ModuleInfo(file, true);
            modulesByFile.put(file, info);
            if (!info.err) {
                modulesByName.put(info.name, info);
            }
        }
        return !info.err && info.name.equals(sModule) ? info : null;
    }

    /**
     * Make sure that the cache is up to date.
     */
    protected void ensureCache() {
        if (isCacheValid()) {
            return;
        }

        Map<File, ModuleInfo> oldModulesByFile = modulesByFile;
        Map<File, ModuleInfo> newModulesByFile = new HashMap<>();

        modulesByFile = newModulesByFile;
        modulesByName.clear();

        File[] files = m_dir.listFiles(ModulesOnly);
        for (File file : files) {
            ModuleInfo info = oldModulesByFile.get(file);
            if (info == null || info.timestamp != file.lastModified() || info.size != file.length()) {
                // build a new one to cache
                info = new ModuleInfo(file);
            }

            newModulesByFile.put(file, info);
            if (!info.err) {
                modulesByName.put(info.name, info);
            }
        }

        lastScan      = System.currentTimeMillis();
        cacheComplete = true;
    }

    /**
     * Quick scan to make sure that the cache is still valid.
     *
     * @return true if the cache is still good, or false if it needs to be rebuilt
     */
    private boolean isCacheValid() {
        // only scan once a second (at the most)
        if (System.currentTimeMillis() < lastScan + 1000) {
            return true;
        }

        File[] files = m_dir.listFiles(ModulesOnly);
        if (files == null || files.length != modulesByFile.size()) {
            return false;
        }

        for (File file : files) {
            ModuleInfo info = modulesByFile.get(file);
            if (info == null || info.timestamp != file.lastModified() || info.size != file.length()) {
                return false;
            }
        }

        return true;
    }

    // ----- inner class: ModuleInfo ---------------------------------------------------------------

    protected static class ModuleInfo {
        public ModuleInfo(File file) {
            this(file, false);
        }

        private ModuleInfo(File file, boolean fCacheModule) {
            this.file      = file;
            this.timestamp = file.lastModified();
            this.size      = file.length();

            ModuleStructure module = tryLoad();
            if (fCacheModule) {
                this.module = module;
            }
            if (module == null) {
                this.name     = null;
                this.versions = null;
                this.err      = true;
            } else {
                this.name     = module.getIdentityConstant().getName();
                this.versions = module.getVersions();
                this.err      = false;
            }
        }

        ModuleStructure tryLoad() {
            try {
                FileStructure struct = new FileStructure(file);
                return struct.getModule();
            } catch (Exception e) {
                System.out.println("Error loading module from file: " + file + "; " + e.getMessage());
            }

            return null;
        }

        ModuleStructure ensureModule() {
            if (err) {
                return null;
            }

            if (module == null || module.isModified()) {
                module = tryLoad();
            }

            return module;
        }

        public final String               name;
        public final File                 file;
        public final VersionTree<Boolean> versions;
        public final long                 timestamp;
        public final long                 size;
        public final boolean              err;

        /**
         * Cached instance of the module struct. If the caller changes it, we will detect it and
         * reload it as necessary.
         */
        private transient ModuleStructure module;
    }


    // ----- constants -----------------------------------------------------------------------------

    public static final FileFilter ModulesOnly = file ->
            file.getName().length() > 4 && file.getName().endsWith(".xtc") &&
            file.exists() && file.isFile() && file.canRead() && file.length() > 0;


    // ----- fields --------------------------------------------------------------------------------

    private final File    m_dir;
    private final boolean m_fRO;
    private final boolean m_fScanFallback;

    private       Map<File  , ModuleInfo> modulesByFile = new HashMap<>();
    private final Map<String, ModuleInfo> modulesByName = new TreeMap<>();
    private       boolean                 cacheComplete;
    private       long                    lastScan;
}