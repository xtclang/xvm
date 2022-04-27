import ecstasy.mgmt.ModuleRepository;

import ecstasy.io.IOException;

import ecstasy.reflect.ModuleTemplate;


/**
 * Directory-based [ModuleRepository] implementation.
 */
class DirRepository
        implements ModuleRepository
    {
    /**
     * Construct a read-only directory-based [ModuleRepository].
     *
     * @param dir  the directory that contains the repository contents
     */
    construct(Directory dir)
        {
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
    private DateTime                lastScan      = DateTime.EPOCH;


    // ----- ModuleRepository API ------------------------------------------------------------------

    @Override
    immutable Set<String> moduleNames.get()
        {
        ensureCache();
        return new HashSet(modulesByName.keys).freeze(inPlace=True);
        }

    @Override
    ModuleTemplate getModule(String name)
        {
        ensureCache();
        if (ModuleInfo info := modulesByName.get(name))
            {
            return info.template;
            }
        throw new IllegalArgument($"Module ${name} is missing or cannot be loaded");
        }

    @Override
    void storeModule(ModuleTemplate template)
        {
        throw new UnsupportedOperation();
        }

    @Override
    String toString()
        {
        return $"DirRepository({dir})";
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Make sure that the cache is up to date.
     */
    private void ensureCache()
        {
        if (isCacheValid())
            {
            return;
            }

        Map<String, ModuleInfo> oldModules = modulesByName;
        Map<String, ModuleInfo> newModules = new HashMap();

        for (File file : dir.files().filter(MODULES_ONLY))
            {
            ModuleInfo info;
            if (info := oldModules.get(file.name),
                    info.timestamp == file.modified && info.size == file.size)
                {
                continue;
                }

            // build a new one to cache
            try
                {
                info = new ModuleInfo(file);
                }
            catch (Exception e)
                {
                // $"Error loading module from file: {file}; {e.text}"
                continue;
                }
            newModules.put(info.template.qualifiedName, info);
            }

        modulesByName = newModules;
        lastScan      = clock.now;
        }

    /**
     * Quick scan to make sure that the cache is still valid.
     *
     * @return true if the cache is still good, or false if it needs to be rebuilt
     */
    private Boolean isCacheValid()
        {
        // only scan once a second (at the most)
        if (clock.now < lastScan + Duration.SECOND)
            {
            return True;
            }

        for (File file : dir.files().filter(MODULES_ONLY))
            {
            if (ModuleInfo info := modulesByName.get(file.name),
                    info.timestamp == file.modified && info.size == file.size)
                {
                continue;
                }
            return False;
            }

        return True;
        }


    // ----- inner class: ModuleInfo ---------------------------------------------------------------

    static const ModuleInfo
        {
        File           file;
        DateTime       timestamp;
        Int            size;
        ModuleTemplate template;

        construct(File file)
            {
            this.template  = tryLoad(file);
            this.file      = file;
            this.timestamp = file.modified;
            this.size      = file.size;
            }

        static ModuleTemplate tryLoad(File fileXtc)
            {
            Byte[] bytes;
            try
                {
                bytes = fileXtc.contents;
                }
            catch (IOException e)
                {
                throw new IOException($"Error: Failed to read the module: {fileXtc}");
                }

            FileTemplate fileTemplate;
            try
                {
                @Inject Container.Linker linker;

                return linker.loadFileTemplate(bytes).mainModule;
                }
            catch (Exception e)
                {
                throw new IOException($"Error: Failed to resolve the module: {fileXtc} ({e.text})");
                }
            }
        }


    // ----- constants -----------------------------------------------------------------------------

    static function Boolean(File) MODULES_ONLY =
            file -> file.name.size > 4 && file.name.endsWith(".xtc") && file.readable;
    }