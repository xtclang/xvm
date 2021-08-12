import ecstasy.io.Log;

import ecstasy.mgmt.ModuleRepository;

import ecstasy.reflect.ModuleTemplate;

import jsondb.Catalog;
import jsondb.CatalogMetadata;
import jsondb.Client;

import oodb.Connection;
import oodb.DBUser;

/**
 * Host for jsondb-based db module.
 */
class JsondbHost
        extends DbHost
    {
    @Inject Console console;

    @Override
    conditional ModuleTemplate generateDBModule(
            ModuleRepository repository, String dbModuleName, Directory buildDir, Log errors)
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
        return True, repository.getModule(dbModuleName + "_jsondb");
        }

    /**
     * Cached CatalogMetadata instance.
     */
    @Lazy CatalogMetadata meta.calc()
        {
        return dbContainer.innerTypeSystem.primaryModule.as(CatalogMetadata);
        }

    /**
     * Cached Catalog instance.
     */
    @Lazy Catalog catalog.calc()
        {
        @Inject Directory curDir;
        Directory dataDir = curDir;
        if (val subDir := dataDir.find("data"), subDir.is(Directory))
            {
            dataDir = subDir;
            }

        // +++ TODO temporary for testing; always start afresh -- REMOVE!!
        dataDir = curDir.dirFor("build/data");
        if (dataDir.exists)
            {
            dataDir.deleteRecursively();
            }
        dataDir.create();
        // ---

        Catalog catalog = meta.createCatalog(dataDir, False);
        try
            {
            catalog.open();
            }
        catch (IllegalState e)
            {
            try
                {
                catalog.create("name_goes_here");
                catalog.open();
                }
            catch (IllegalState e2)
                {
                catalog.recover();
                }
            }
        return catalog;
        }

    @Override
    function oodb.Connection(DBUser) ensureDatabase(Map<String, String>? configOverrides = Null)
        {
        return meta.ensureConnectionFactory(catalog);
        }
    }