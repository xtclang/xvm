import ecstasy.mgmt.ModuleRepository;

import ecstasy.reflect.ModuleTemplate;

/**
 * Code generator for jsondb connection.
 */
class JsondbCodeGenerator
    {
    @Inject Console console;

    /**
     * Generate all the necessary classes to use imdb.
     */
    ModuleTemplate generateStubs(ModuleRepository repository, String dbModuleName, Directory buildDir)
        {
        ModuleTemplate dbModule = repository.getResolvedModule(dbModuleName);

        String appName = dbModuleName;

        Directory moduleDir = buildDir.dirFor(appName + "_jsondb");
        if (moduleDir.exists)
            {
            moduleDir.deleteRecursively();
            }
        moduleDir.create();

        // temporary; replace with the compilation of generated source
        return repository.getModule(dbModuleName + "_jsondb");
        }
    }