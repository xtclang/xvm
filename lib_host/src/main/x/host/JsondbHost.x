import ecstasy.io.Log;

import jsondb.Catalog;
import jsondb.CatalogMetadata;

import oodb.Connection;
import oodb.DBUser;

/**
 * Host for jsondb-based DB module.
 */
class JsondbHost(String dbModuleName, Directory homeDir)
        extends DbHost(dbModuleName, homeDir)
    {
    // ---- run-time support -----------------------------------------------------------------------

    /**
     * Cached CatalogMetadata instance.
     */
    @Lazy CatalogMetadata meta.calc()
        {
        return container.innerTypeSystem.primaryModule.as(CatalogMetadata);
        }

    /**
     * Cached Catalog instance.
     */
    @Lazy Catalog catalog.calc()
        {
        Directory dataDir = homeDir.dirFor("data").ensure();
        Catalog   catalog = meta.createCatalog(dataDir, False);
        Boolean   success = False;
        try
            {
            success = catalog.open();
            }
        catch (IllegalState e)
            {
            catalog.log($"Failed to open the catalog for \"{dbModuleName}\"; reason={e.text}");
            }

        if (!success)
            {
            // failed to open; try to recover
            try
                {
                catalog.recover();
                success = True;
                }
            catch (IllegalState e)
                {
                catalog.log($"Failed to recover the catalog for \"{dbModuleName}\"; reason={e.text}");
                }
            }

        if (!success)
            {
            // failed to recover; try to create
            catalog.create(dbModuleName);
            assert catalog.open() as $"Failed to create the catalog for \"{dbModuleName}\"";
            }
        return catalog;
        }

    @Override
    Type schemaType.get()
        {
        return meta.Schema;
        }

    @Override
    function oodb.Connection(DBUser) ensureDatabase(Map<String, String>? configOverrides = Null)
        {
        return meta.ensureConnectionFactory(catalog);
        }

    @Override
    void closeDatabase()
        {
        catalog.close();
        }


    // ----- load-time support ---------------------------------------------------------------------

    @Override
    String hostName = "jsondb";

    @Override
    String moduleSourceTemplate = $./templates/_module.txt;

    @Override
    String propertyGetterTemplate = $./templates/PropertyGetter.txt;

    @Override
    String propertyInfoTemplate = $./templates/PropertyInfo.txt;

    @Override
    String customInstantiationTemplate = $./templates/CustomInstantiation.txt;

    @Override
    String customDeclarationTemplate = $./templates/CustomDeclaration.txt;

    @Override
    String customMethodTemplate = $./templates/CustomMethod.txt;
    }