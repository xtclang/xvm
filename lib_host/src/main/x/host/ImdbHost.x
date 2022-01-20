import ecstasy.io.Log;

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
        TODO
        }

    @Override
    void closeDatabase()
        {
        }


    // ---- load-time support ----------------------------------------------------------------------

    @Override
    String hostName = "imdb";
    }