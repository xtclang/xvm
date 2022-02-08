import ecstasy.mgmt.Container;


/**
 * A host for an application module.
 */
class AppHost(String moduleName, Directory homeDir)
    {
    /**
     * The hosted db module name.
     */
    public/protected String moduleName;

    /**
     * The home directory.
     */
    public/protected Directory homeDir;

    /**
     * The Container that hosts the module.
     */
    @Unassigned
    Container container;
    }