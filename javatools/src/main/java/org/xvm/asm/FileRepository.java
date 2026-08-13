package org.xvm.asm;


import java.io.File;
import java.io.IOException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import java.util.stream.Stream;


/**
 * A simple ModuleRepository for a single file. The file is most commonly a single-module .xtc, but
 * an .xtc file is a module container and may hold multiple modules (a "bundle"), in which case
 * every real (non-fingerprint) module in the container is exposed by this repository.
 */
public class FileRepository
        implements ModuleRepository {
    // ----- constructors  -------------------------------------------------------------------------

    /**
     * Construct a single-file ModuleRepository.
     *
     * @param file       the file that contains the module(s)
     * @param fReadOnly  true to make the repository "read-only"
     */
    public FileRepository(File file, boolean fReadOnly) {
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
        this.fRO = fReadOnly;
    }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the module file (which may or may not exist)
     */
    public File getFile() {
        return file;
    }

    /**
     * @return true iff read-only
     */
    public boolean isReadOnly() {
        return fRO;
    }


    // ----- ModuleRepository API ------------------------------------------------------------------

    @Override
    public Set<String> getModuleNames() {
        checkCache();
        return cache.names();
    }

    @Override
    public VersionTree<Boolean> getAvailableVersions(String sModule) {
        checkCache();
        return err ? null : cache.versionsByName().get(sModule);
    }

    @Override
    public ModuleStructure loadModule(String sModule) {
        checkCache();
        if (!cache.names().contains(sModule) || !ensureModulesLoaded()) {
            return null;
        }

        var module = cache.modulesByName().get(sModule);
        if (module != null && !module.isMainModule() && module.getFileStructure() == cache.struct()) {
            // a non-main module of a multi-module container ("bundle") is served as a detached
            // copy (memoized), so that every consumer - the linker, the runtime compiler's
            // fingerprint hoisting, reflection - sees the single-module-file shape it expects
            module = module.detachedCopy();
            cache.modulesByName().put(sModule, module);
        }
        return module;
    }

    @Override
    public ModuleStructure loadModule(String sModule, Version version, boolean fExact) {
        // the default implementation narrows a module to a version via ModuleStructure
        // .extractVersion(), which asserts that the module is the main module of its file
        // structure; that does not hold for the non-main modules of a multi-module container,
        // so version matching is done in place here instead
        if (version == null) {
            return loadModule(sModule);
        }

        var module = loadModule(sModule);
        if (module == null) {
            return null;
        }

        var ver = module.getVersion();
        if (ver == null) {
            return null;
        }

        return (fExact ? ver.equals(version) : ver.isSubstitutableFor(version)) ? module : null;
    }

    @Override
    public void storeModule(ModuleStructure module)
            throws IOException {
        if (fRO) {
            throw new IOException("repository is read-only: " + this);
        }

        if (file.exists() && !file.delete()) {
            err = true;
            throw new IOException("unable to delete " + file);
        }

        try {
            module.getFileStructure().writeTo(file);
            err = false;
        } catch (IOException e) {
            err = true;
            file.delete(); // don't leave a corrupted file; it may prevent the next compilation
            throw new IOException("Error writing module to file: " + file, e);
        }

        cacheFrom(module.getFileStructure());
        this.timestamp = file.lastModified();
        this.size      = file.length();
        this.lastScan  = System.currentTimeMillis();
    }


    // ----- Object methods ------------------------------------------------------------------------

    @Override
    public int hashCode() {
        return file.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this || !(obj instanceof FileRepository that)) {
            return obj == this;
        }

        return this.file.equals(that.file) &&
               this.fRO      == that.fRO;
    }

    @Override
    public String toString() {
        return "FileRepository(Path=" + file.toString() + ", RO=" + fRO + ")";
    }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Make sure that the cache is up to date.
     */
    void checkCache() {
        if (isCacheValid()) {
            return;
        }

        this.timestamp = file.lastModified();
        this.size      = file.length();
        this.err       = false;

        var structLoaded = tryLoad();
        if (structLoaded == null) {
            this.cache = Cache.EMPTY;
            this.err   = true;
        } else {
            cacheFrom(structLoaded);
        }

        this.lastScan = System.currentTimeMillis();
    }

    /**
     * Populate the cache from the passed file structure.
     *
     * @param struct  the FileStructure to cache the contained module information from
     */
    private void cacheFrom(FileStructure struct) {
        // the primary module comes first; fingerprints represent external dependencies of the
        // contained modules and are not modules that this repository can provide
        var mapModules = new LinkedHashMap<String, ModuleStructure>();
        Stream.concat(Stream.of(struct.getModule()), struct.children().stream())
                .filter(module -> !module.isFingerprint())
                .forEach(module -> mapModules.putIfAbsent(module.getIdentityConstant().getName(), module));

        var mapVersions = new LinkedHashMap<String, VersionTree<Boolean>>();
        mapModules.forEach((sName, module) -> mapVersions.put(sName, module.getVersions()));

        // deterministic (primary module first) AND immutable: the JDK's Set.of/Set.copyOf are
        // hash-ordered (randomized per JVM run), so an unmodifiable view over a LinkedHashSet is
        // the only stdlib form that keeps insertion order; wrapped once here, handed out for
        // free by getModuleNames()
        this.cache = new Cache(
                Collections.unmodifiableSet(new LinkedHashSet<>(mapModules.keySet())),
                mapVersions, mapModules, struct);
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

        if (!file.exists()) {
            cache = Cache.EMPTY;
            return true;
        }

        if (cache.struct() == null || timestamp != file.lastModified() || size != file.length()) {
            return false;
        }

        return true;
    }

    /**
     * Make sure the fully loaded module cache is present and current.
     *
     * @return true iff the modules are loaded and servable
     */
    private boolean ensureModulesLoaded() {
        if (err) {
            return false;
        }

        // detached copies handed out by loadModule() are freshly cloned (and thus "modified"), so
        // staleness is judged by the container's own (attached) main module only
        var struct = cache.struct();
        if (struct == null || struct.getModule().isModified()) {
            var structLoaded = tryLoad();
            if (structLoaded == null) {
                err = true;
                return false;
            }
            cacheFrom(structLoaded);
        }

        return true;
    }

    private FileStructure tryLoad() {
        try {
            return new FileStructure(file);
        } catch (Exception e) {
            System.out.println("Error loading module(s) from file: " + file + "; " + e.getMessage());
        }

        err = true;
        return null;
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * An atomically swapped snapshot of the cached container state: the servable module names
     * (insertion-ordered, primary module first, immutable and safe to hand out), the per-name
     * version trees, the loaded modules by name (non-main members are memoized detached copies),
     * and the loaded container itself — null when the file is absent, unreadable, or not yet
     * scanned, which is also what marks the snapshot as needing a rebuild.
     */
    private record Cache(Set<String>                       names,
                         Map<String, VersionTree<Boolean>> versionsByName,
                         Map<String, ModuleStructure>      modulesByName,
                         FileStructure                     struct) {
        private static final Cache EMPTY = new Cache(Set.of(), Map.of(), Map.of(), null);
    }

    private final File    file;
    private final boolean fRO;

    private Cache   cache = Cache.EMPTY;
    private long    timestamp;
    private long    size;
    private long    lastScan;
    private boolean err;
}
