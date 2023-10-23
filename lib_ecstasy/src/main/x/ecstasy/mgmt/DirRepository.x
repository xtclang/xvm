import io.IOException;

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
        this.dir = dir;
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
    private Time                    lastScan      = Time.EPOCH;


    // ----- ModuleRepository API ------------------------------------------------------------------

    @Override
    immutable Set<String> moduleNames.get() {
        ensureCache();
        return new HashSet(modulesByName.keys).freeze(inPlace=True);
    }

    @Override
    conditional ModuleTemplate getModule(String name) {
        Boolean fresh = ensureCache();
        if (ModuleInfo info := modulesByName.get(name)) {
            return True, info.template;
        }

        if (!fresh) {
            // refresh the cache before blowing up
            lastScan = Time.EPOCH;
            ensureCache();
            if (ModuleInfo info := modulesByName.get(name)) {
                return True, info.template;
            }
        }
        return False;
    }

    @Override
    void storeModule(ModuleTemplate template) {
        throw new UnsupportedOperation();
    }

    @Override
    String toString() {
        return $"DirRepository({dir})";
    }


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

        Map<String, ModuleInfo> oldModules = modulesByName;
        Map<String, ModuleInfo> newModules = new HashMap();

        for (File file : dir.files().filter(MODULES_ONLY)) {
            ModuleInfo info;
            if (info := oldModules.get(file.name),
                    info.timestamp == file.modified && info.size == file.size) {
                continue;
            }

            // build a new one to cache
            try {
                info = new ModuleInfo(file);
            } catch (Exception e) {
                @Inject Console console;
                console.print($|DirRepository: Failed to load a module from "{file}": {e.message}
                             );
                continue;
            }
            newModules.put(info.template.qualifiedName, info);
        }

        modulesByName = newModules;
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

        for (File file : dir.files().filter(MODULES_ONLY)) {
            if (ModuleInfo info := modulesByName.get(file.name),
                    info.timestamp == file.modified && info.size == file.size) {
                continue;
            }
            return False;
        }

        return True;
    }


    // ----- inner class: ModuleInfo ---------------------------------------------------------------

    static const ModuleInfo {
        File           file;
        Time           timestamp;
        Int            size;
        ModuleTemplate template;

        construct(File file) {
            this.template  = tryLoad(file);
            this.file      = file;
            this.timestamp = file.modified;
            this.size      = file.size;
        }

        static ModuleTemplate tryLoad(File xtcFile) {
            try {
                @Inject Container.Linker linker;

                return linker.loadFileTemplate(xtcFile).mainModule;
            } catch (Exception e) {
                throw new IOException($"Error: Failed to resolve the module: {xtcFile} ({e.text})");
            }
        }
    }


    // ----- constants -----------------------------------------------------------------------------

    static function Boolean(File) MODULES_ONLY =
            file -> file.name.size > 4 && file.name.endsWith(".xtc") && file.readable;
}