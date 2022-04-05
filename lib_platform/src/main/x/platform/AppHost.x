import ecstasy.mgmt.Container;


/**
 * A host for an application module.
 */
class AppHost(String moduleName, Directory homeDir, String appRealm="")
        implements Closeable
    {
    /**
     * The Container that hosts the module.
     */
    @Unassigned
    Container container;

    /**
     * The hosted module name.
     */
    public/protected String moduleName;

    /**
     * The home directory.
     */
    public/protected Directory homeDir;

    /**
     * The application realm (used only for Web applications).
     */
    public/protected String appRealm;

    /**
     * True iff the application module is a WebModule.
     */
    Boolean isWeb.get()
        {
        return False;
        }


    // ----- Closeable -----------------------------------------------------------------------------

    @Override
    void close(Exception? e = Null)
        {
        }
    }