import ecstasy.io.Log;

import jsondb.Catalog;
import jsondb.CatalogMetadata;
import jsondb.Client;

import oodb.Connection;
import oodb.DBUser;

/**
 * Host for jsondb-based DB module.
 */
class JsondbHost
        extends DbHost
    {
    @Inject Console console;

    @Override
    String hostName = "jsondb";

    @Override
    String moduleSourceTemplate = $./templates/jsondb/_module.txt;

    @Override
    String propertyGetterTemplate = $./templates/jsondb/PropertyGetter.txt;

    @Override
    String propertyInfoTemplate = $./templates/jsondb/PropertyInfo.txt;

    @Override
    String customInstantiationTemplate = $./templates/jsondb/CustomInstantiation.txt;

    @Override
    String customDeclarationTemplate = $./templates/jsondb/CustomDeclaration.txt;

    @Override
    String customMethodTemplate = $./templates/jsondb/CustomMethod.txt;

    @Override
    String customInvocationTemplate = $./templates/common/CustomInvocation.txt;

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