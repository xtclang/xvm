import ecstasy.mgmt.Container;
import ecstasy.mgmt.ModuleRepository;

import ecstasy.reflect.ModuleTemplate;

/**
 * An abstract host for a db module.
 */
@Abstract
class DbHost
    {
    /**
     * Generate all the necessary classes to use imdb.
     */
    ModuleTemplate generateStubs(ModuleRepository repository, String dbModuleName, Directory buildDir);

    /**
     * Check an existence of the DB (e.g. on disk); create or recover if necessary.
     *
     * @return a connection factory
     */
    function oodb.Connection(oodb.DBUser)
        ensureDatabase(Map<String, String>? configOverrides = Null);

    /**
     * The Container that hosts the DB module.
     */
    @Unassigned
    Container dbContainer;
    }