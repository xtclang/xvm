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
     * Obtain a connection.
     */
    oodb.Connection ensureConnection();

    /**
     * The Container that hosts the DB module.
     */
    @Unassigned
    Container dbContainer;
    }