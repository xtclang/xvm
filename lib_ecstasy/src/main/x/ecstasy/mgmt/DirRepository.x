import io.IOException;

import reflect.FileTemplate;
import reflect.ModuleTemplate;

/**
 * A directory-based [ModuleRepository] implementation.
 */
service DirRepository
        implements ModuleRepository {
    /**
     * Construct a read-only directory-based [ModuleRepository].
     *
     * @param dir  the directory that contains the repository contents
     */
    construct(Directory dir) {
        this.dir           = dir;
        this.onReadFailure = reportReadFailure;
    }

    /**
     * Construct a repository with an observer for unsuccessful candidate reads.
     *
     * The observer replaces the default console report and receives the file and exception for
     * each failed read attempt. Scanning continues so another candidate can satisfy the lookup.
     * A report does not identify the requested module or establish whether the file was damaged,
     * written unsuccessfully, or produced by an incompatible compiler. Cached results do not
     * generate reports; a later retry can report another failure for the same file.
     *
     * @param dir            the directory that contains the repository contents
     * @param onReadFailure  the observer; it must not call back into this repository
     */
    construct(Directory dir, function void(File, Exception) onReadFailure) {
        this.dir           = dir;
        this.onReadFailure = onReadFailure;
    }

    // ----- properties ----------------------------------------------------------------------------

    @Inject Clock clock;

    /**
     * The underlying directory.
     */
    public/private Directory dir;

    /**
     * Internal state.
     */
    private Map<String, ModuleInfo> modulesByName = new HashMap();
    private Map<String, ModuleInfo> modulesByFile = new HashMap();
    private Time                    lastScan      = Time.EPOCH;
    private Boolean                 failedScan    = False;

    private function void(File, Exception) onReadFailure;

    // ----- ModuleRepository API ------------------------------------------------------------------

    @Override
    immutable Set<String> moduleNames.get() {
        ensureCache();
        return new HashSet(modulesByName.keys).freeze(inPlace=True);
    }

    @Override
    conditional ModuleTemplate getModule(String name, Version? version = Null) {
        Boolean fresh = ensureCache();
        if (ModuleInfo     info     := modulesByName.get(name),
            ModuleTemplate template := info.template.extractVersion(version)) {
            return True, template;
        }

        if (!fresh) {
            // refresh the cache before blowing up
            lastScan = Time.EPOCH;
            ensureCache();
            if (ModuleInfo     info     := modulesByName.get(name),
                ModuleTemplate template := info.template.extractVersion(version)) {
                return True, template;
            }
        }
        return False;
    }

    @Override
    void storeModule(ModuleTemplate template) {
        throw new Unsupported();
    }

    @Override
    String toString() = $"DirRepository({dir})";

    // ----- internal ------------------------------------------------------------------------------

    /**
     * Make sure that the cache is up to date.
     *
     * @return True if the directory has just been scanned; False if the cache is still considered
     *         valid.
     */
    private Boolean ensureCache() {
        if (isCacheValid()) {
            return False;
        }

        Map<String, ModuleInfo> oldModules = modulesByFile;
        Map<String, ModuleInfo> newByFile  = new HashMap();
        Map<String, ModuleInfo> newByName  = new HashMap();
        failedScan = False;

        for (File file : dir.files().filter(MODULES_ONLY)) {
            ModuleInfo info;
            ModuleInfo? previous = oldModules.getOrNull(file.name);
            if (previous != Null && previous.timestamp == file.modified && previous.size == file.size) {
                info = previous;
            } else {
                try {
                    info = new ModuleInfo(file);
                } catch (Exception e) {
                    failedScan = True;
                    onReadFailure(file, e);
                    continue;
                }
            }
            newByFile.put(file.name, info);
            newByName.put(info.template.mainModule.qualifiedName, info);
        }

        modulesByFile = newByFile;
        modulesByName = newByName;
        lastScan      = clock.now;
        return True;
    }

    /**
     * Quick scan to make sure that the cache is still valid.
     *
     * @return true if the cache is still good, or false if it needs to be rebuilt
     */
    private Boolean isCacheValid() {
        // only scan once a second (at the most)
        if (clock.now < lastScan + Duration.Second) {
            return True;
        }

        if (failedScan) {
            return False;
        }

        Int count = 0;
        for (File file : dir.files().filter(MODULES_ONLY)) {
            ++count;
            if (ModuleInfo info := modulesByFile.get(file.name),
                    info.timestamp == file.modified && info.size == file.size) {
                continue;
            }
            return False;
        }

        return count == modulesByFile.size;
    }

    private static void reportReadFailure(File file, Exception cause) {
        @Inject Console console;
        console.print($|DirRepository: Failed to load a module from "{file}": {cause.message}
                     );
    }

    // ----- inner class: ModuleInfo ---------------------------------------------------------------

    static const ModuleInfo {
        File         file;
        Time         timestamp;
        Int          size;
        FileTemplate template;

        construct(File file) {
            this.template  = tryLoad(file);
            this.file      = file;
            this.timestamp = file.modified;
            this.size      = file.size;
        }

        static FileTemplate tryLoad(File xtcFile) {
            try {
                @Inject Container.Linker linker;

                return linker.loadFileTemplate(xtcFile);
            } catch (Exception e) {
                throw new IOException($"Failed to read module file: {xtcFile}", e);
            }
        }
    }

    // ----- constants -----------------------------------------------------------------------------

    static function Boolean(File) MODULES_ONLY =
            file -> file.name.size > 4 && file.name.endsWith(".xtc") && file.readable;
}
