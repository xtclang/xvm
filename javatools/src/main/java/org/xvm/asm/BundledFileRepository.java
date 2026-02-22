package org.xvm.asm;


import java.io.File;
import java.io.IOException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;


/**
 * A read-only ModuleRepository that exposes all non-fingerprint modules from a single .xtc file.
 * Unlike {@link FileRepository} which only exposes the primary module, this repository exposes
 * every real (non-fingerprint) module contained in the file, enabling support for bundled .xtc
 * files that contain multiple modules.
 * <p>
 * For single-module files, this behaves identically to a read-only {@link FileRepository}.
 */
public class BundledFileRepository
        implements ModuleRepository {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a BundledFileRepository for the specified .xtc file.
     *
     * @param file  the .xtc file that may contain one or more modules
     */
    public BundledFileRepository(File file) {
        assert file != null && !file.isDirectory();

        String sName = file.getName();
        if (!sName.endsWith(".xtc")) {
            if (sName.endsWith(".x")) {
                file = new File(file.getParentFile(), sName.substring(0, sName.lastIndexOf('.')) + ".xtc");
            } else {
                file = new File(file.getParentFile(), sName + ".xtc");
            }
        }

        this.file = file;
    }


    // ----- ModuleRepository API ------------------------------------------------------------------

    @Override
    public Set<String> getModuleNames() {
        ensureCache();
        return modulesByName == null ? Collections.emptySet() : Collections.unmodifiableSet(modulesByName.keySet());
    }

    @Override
    public VersionTree<Boolean> getAvailableVersions(String sModule) {
        ensureCache();
        if (modulesByName == null) {
            return null;
        }
        ModuleStructure module = modulesByName.get(sModule);
        return module == null ? null : module.getVersions();
    }

    @Override
    public ModuleStructure loadModule(String sModule) {
        ensureCache();
        return modulesByName == null ? null : modulesByName.get(sModule);
    }

    @Override
    public void storeModule(ModuleStructure module)
            throws IOException {
        throw new IOException("repository is read-only: " + this);
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode() {
        return file.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this || !(obj instanceof BundledFileRepository that)) {
            return obj == this;
        }
        return this.file.equals(that.file);
    }

    @Override
    public String toString() {
        return "BundledFileRepository(Path=" + file + ")";
    }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Ensure the cache is populated and up to date.
     */
    private void ensureCache() {
        if (isCacheValid()) {
            return;
        }

        timestamp = file.lastModified();
        size      = file.length();
        lastScan  = System.currentTimeMillis();

        if (!file.exists()) {
            modulesByName  = null;
            fileStructure  = null;
            return;
        }

        try {
            var struct = new FileStructure(file);
            var modules = new LinkedHashMap<String, ModuleStructure>();

            for (ModuleStructure module : struct.children()) {
                if (!module.isFingerprint()) {
                    modules.put(module.getIdentityConstant().getName(), module);
                }
            }

            fileStructure  = struct;
            modulesByName  = modules;
        } catch (Exception e) {
            System.out.println("Error loading modules from file: " + file + "; " + e.getMessage());
            modulesByName  = null;
            fileStructure  = null;
        }
    }

    /**
     * Quick check to see if the cache is still valid.
     *
     * @return true if the cache is still good, or false if it needs to be rebuilt
     */
    private boolean isCacheValid() {
        if (System.currentTimeMillis() < lastScan + 1000) {
            return true;
        }

        if (!file.exists()) {
            modulesByName = null;
            fileStructure = null;
            return true;
        }

        return modulesByName != null && timestamp == file.lastModified() && size == file.length();
    }


    // ----- fields --------------------------------------------------------------------------------

    private final File file;

    private Map<String, ModuleStructure> modulesByName;
    private FileStructure                fileStructure;
    private long                         timestamp;
    private long                         size;
    private long                         lastScan;
}
