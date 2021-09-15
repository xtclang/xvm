import ecstasy.io.Log;

import imdb.CatalogMetadata;

import oodb.Connection;
import oodb.DBUser;


/**
 * Host for imdb-based DB module.
 */
class ImdbHost(String dbModuleName)
        extends DbHost(dbModuleName)
    {
    // ---- run-time support -----------------------------------------------------------------------

    @Override
    function Connection(DBUser)
            ensureDatabase(Map<String, String>? configOverrides = Null)
        {
        CatalogMetadata meta = dbContainer.innerTypeSystem.primaryModule.as(CatalogMetadata);
        return meta.ensureConnectionFactory();
        }

    @Override
    void closeDatabase()
        {
        }


    // ---- load-time support ----------------------------------------------------------------------

    @Override
    String hostName = "imdb";

    @Override
    String moduleSourceTemplate = $./templates/imdb/_module.txt;

    @Override
    String propertyGetterTemplate = $./templates/imdb/PropertyGetter.txt;

    @Override
    String propertyInfoTemplate = $./templates/imdb/PropertyInfo.txt;

    @Override
    String customInstantiationTemplate = $./templates/imdb/CustomInstantiation.txt;

    @Override
    String customDeclarationTemplate = $./templates/imdb/CustomDeclaration.txt;

    @Override
    String customMethodTemplate = $./templates/imdb/CustomMethod.txt;

    @Override
    String customInvocationTemplate = $./templates/common/CustomInvocation.txt;
    }