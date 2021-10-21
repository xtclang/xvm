import ecstasy.io.Log;

import jsondb.Catalog;
import jsondb.CatalogMetadata;

import oodb.Connection;
import oodb.DBUser;

/**
 * Host for jsondb-based DB module.
 */
class JsondbHost(String dbModuleName)
        extends DbHost(dbModuleName)
    {
    // ---- run-time support -----------------------------------------------------------------------

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

        // +++ TODO temporary for testing
        dataDir = curDir.dirFor($"build/{dbModuleName}_data").ensure();

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

    @Override
    String customInvocationTemplate = $./templates/CustomInvocation.txt;
    }