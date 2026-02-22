package org.xvm.asm;


import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.UncheckedIOException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
        assert dir != null && dir.isDirectory();

        m_dir = dir;
        m_fRO = fReadOnly;
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
        ensureCache();
        var info = modulesByName.get(sModule);
        if (info == null) {
            return null;
        }
        try {
            return info.ensureModule(sModule);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load module \"" + sModule + "\" from " + info.file, e);
        }
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
               this.m_fRO     == that.m_fRO;
    }

    @Override
    public String toString() {
        return "DirRepository(Path=" + m_dir.toString() + ", RO=" + m_fRO + ")";
    }


    // ----- internal ------------------------------------------------------------------------------

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
            var cached = oldModulesByFile.get(file);
            var current = (cached == null || cached.timestamp != file.lastModified() || cached.size != file.length())
                    ? new ModuleInfo(file)
                    : cached;

            newModulesByFile.put(file, current);
            if (!current.err) {
                current.moduleNames.forEach(moduleName -> modulesByName.put(moduleName, current));
            }
        }

        lastScan = System.currentTimeMillis();
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
            this.file      = file;
            this.timestamp = file.lastModified();
            this.size      = file.length();

            FileStructure struct;
            try {
                struct = new FileStructure(file);
            } catch (IOException e) {
                System.err.println("Error loading module from file: " + file + "; " + e.getMessage());
                this.name        = null;
                this.moduleNames = List.of();
                this.versions    = null;
                this.err         = true;
                return;
            }

            var names = struct.children().stream()
                    .filter(m -> !m.isFingerprint())
                    .map(m -> m.getIdentityConstant().getName())
                    .toList();

            this.moduleNames = names;
            this.name        = names.isEmpty() ? null : names.getFirst();
            this.versions    = struct.getModule() == null ? null : struct.getModule().getVersions();
            this.err         = names.isEmpty();
        }

        /**
         * Load a specific module by name from this file.
         *
         * @param moduleName  the fully qualified module name to load
         * @return the module structure
         * @throws IOException if the file cannot be read
         */
        ModuleStructure ensureModule(String moduleName) throws IOException {
            if (err) {
                throw new IOException("Cannot load module \"" + moduleName + "\" from erroneous file: " + file);
            }

            // Fast path: cached module matches the requested name
            if (module != null && !module.isModified()
                    && module.getIdentityConstant().getName().equals(moduleName)) {
                return module;
            }

            var struct = new FileStructure(file);
            var found = struct.children().stream()
                    .filter(m -> !m.isFingerprint()
                            && m.getIdentityConstant().getName().equals(moduleName))
                    .findFirst()
                    .orElseThrow(() -> new IOException(
                            "Module \"" + moduleName + "\" not found in file: " + file));

            module = found;
            return found;
        }

        public final String               name;
        public final List<String>          moduleNames;
        public final File                 file;
        public final VersionTree<Boolean> versions;
        public final long                 timestamp;
        public final long                 size;
        public final boolean              err;

        /**
         * Cached instance of the most recently loaded module. If the caller changes it, we will
         * detect it and reload it as necessary.
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

    private       Map<File  , ModuleInfo> modulesByFile = new HashMap<>();
    private final Map<String, ModuleInfo> modulesByName = new TreeMap<>();
    private       long lastScan;
}