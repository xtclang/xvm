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

        return meta.createCatalog(curDir, False);
        }

    @Override
    Connection ensureConnection()
        {
        DBUser user = new oodb.model.DBUser(1, "test"); // TODO CP

        return meta.createConnection(catalog, user);
        }
    }