package org.xvm.asm;


import java.io.File;
import java.io.IOException;

import java.nio.file.Files;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import java.util.stream.Stream;

import static org.xvm.util.Handy.BINARY_EXTENSION;
import static org.xvm.util.Handy.hasBinaryExtension;
import static org.xvm.util.Handy.removeSourceExtension;


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
     * @param readOnly  true to make the repository "read-only"
     */
    public FileRepository(File file, boolean readOnly) {
        assert file != null && !file.isDirectory();

        this.file     = moduleFile(file);
        this.readOnly = readOnly;
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
        return readOnly;
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
        if (!cache.hasModule(sModule) || !ensureModulesLoaded()) {
            return null;
        }

        return cache.moduleFor(sModule);
    }

    @Override
    public ModuleStructure loadModule(String sModule, Version version, boolean fExact) {
        return ModuleRepository.super.loadModule(sModule, version, fExact);
    }

    @Override
    public void storeModule(ModuleStructure module)
            throws IOException {
        if (readOnly) {
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
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException deleteFailure) {
                file.deleteOnExit();
                e.addSuppressed(deleteFailure);
            }
            throw new IOException("Error writing module to file: " + file, e);
        }

        this.cache     = cacheFrom(module.getFileStructure());
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
               this.readOnly == that.readOnly;
    }

    @Override
    public String toString() {
        return "FileRepository(Path=" + file.toString() + ", RO=" + readOnly + ")";
    }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Convert a source or extensionless module file name to its compiled module file name.
     *
     * @param file  the module file
     *
     * @return the same file if it already names an .xtc file, otherwise the .xtc sibling
     */
    private static File moduleFile(File file) {
        return Optional.of(file.getName())
                .filter(sName -> !hasBinaryExtension(sName))
                .map(sName -> file.toPath()
                        .resolveSibling(removeSourceExtension(sName) + BINARY_EXTENSION)
                        .toFile())
                .orElse(file);
    }

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

        this.cache     = readCache();
        this.err       = cache == Cache.EMPTY;
        this.lastScan  = System.currentTimeMillis();
    }

    /**
     * Build a cache snapshot from the file.
     *
     * @return the cache snapshot, or {@link Cache#EMPTY} on load failure
     */
    private Cache readCache() {
        return Optional.ofNullable(tryReadMetadata())
                .map(FileRepository::cacheFrom)
                .or(() -> Optional.ofNullable(tryLoad()).map(FileRepository::cacheFrom))
                .orElse(Cache.EMPTY);
    }

    /**
     * Build a cache snapshot from the passed file structure.
     *
     * @param struct  the FileStructure to cache the contained module information from
     *
     * @return the cache snapshot
     */
    private static Cache cacheFrom(FileStructure struct) {
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
        return new Cache(
                Collections.unmodifiableSet(new LinkedHashSet<>(mapModules.keySet())),
                mapVersions, mapModules, struct, struct.getFileMetadata());
    }

    /**
     * Build a cache snapshot from constant-pool-free file metadata.
     *
     * @param metadata  the metadata to cache
     *
     * @return the cache snapshot
     */
    private static Cache cacheFrom(FileStructure.FileMetadata metadata) {
        var mapVersions = new LinkedHashMap<String, VersionTree<Boolean>>();
        var versionsByModule = metadata.versionsByModule();
        metadata.moduleNames().forEach(sName ->
                mapVersions.put(sName, versionTree(versionsByModule.getOrDefault(sName, List.of()))));

        return new Cache(
                Collections.unmodifiableSet(new LinkedHashSet<>(metadata.moduleNames())),
                mapVersions, new LinkedHashMap<>(), null, metadata);
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

        if (!cache.hasFileSnapshot()) {
            return false;
        }

        return timestamp == file.lastModified() && size == file.length();
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

        if (cache.needsModuleLoad()) {
            var structLoaded = tryLoad();
            if (structLoaded == null) {
                err = true;
                return false;
            }
            this.cache = cacheFrom(structLoaded);
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

    private FileStructure.FileMetadata tryReadMetadata() {
        try {
            return FileStructure.readMetadata(file);
        } catch (Exception _) {
            return null;
        }
    }

    private static VersionTree<Boolean> versionTree(List<String> versions) {
        return versions.stream()
                .map(Version::new)
                .collect(VersionTree::new,
                        (vtree, version) -> vtree.put(version, Boolean.TRUE),
                        VersionTree::putAll);
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * An atomically swapped snapshot of the cached container state: the servable module names
     * (insertion-ordered, primary module first, immutable and safe to hand out), the per-name
     * version trees, the loaded modules by name (non-main members are memoized detached copies),
     * the loaded container itself, and the constant-pool-free metadata when available. Both the
     * container and metadata are null when the file is absent, unreadable, or not yet scanned.
     */
    private record Cache(Set<String>                       names,
                         Map<String, VersionTree<Boolean>> versionsByName,
                         Map<String, ModuleStructure>      modulesByName,
                         FileStructure                     struct,
                         FileStructure.FileMetadata        metadata) {
        private static final Cache EMPTY = new Cache(Set.of(), Map.of(), Map.of(), null, null);

        boolean hasModule(String sModule) {
            return names.contains(sModule);
        }

        boolean hasFileSnapshot() {
            return struct != null || metadata != null;
        }

        boolean needsModuleLoad() {
            // detached copies handed out by loadModule() are freshly cloned (and thus "modified"),
            // so staleness is judged by the container's own (attached) main module only
            return struct == null || struct.getModule().isModified();
        }

        ModuleStructure moduleFor(String sModule) {
            var module = modulesByName.get(sModule);
            if (module != null && !module.isMainModule() && module.getFileStructure() == struct) {
                // a non-main module of a multi-module container ("bundle") is served as a detached
                // copy (memoized), so that every consumer - the linker, the runtime compiler's
                // fingerprint hoisting, reflection - sees the single-module-file shape it expects
                module = module.detachedCopy();
                modulesByName.put(sModule, module);
            }
            return module;
        }
    }

    private final File    file;
    private final boolean readOnly;

    private Cache   cache = Cache.EMPTY;
    private long    timestamp;
    private long    size;
    private long    lastScan;
    private boolean err;
}
