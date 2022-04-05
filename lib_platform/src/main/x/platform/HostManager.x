import ecstasy.reflect.FileTemplate;

interface HostManager
    {
    /**
     * Retrieve an AppHost for the specified name.
     *
     * @return True iff there is an AppHost for the specified name
     * @return (optional) the AppHost
     */
    conditional AppHost getAppHost(String appName);

    /**
     * Load a FileTemplate for the app at the specified path.
     *
     * @param path            the location of ".xtc" file  // TODO: replace with ModuleRepository
     * @param errors          the error log
     *
     * @return True iff there is a file template for the specified module path
     * @return (optional) the FileTemplate for the module
     * @return (optional) the "home" directory to use
     */
    conditional (FileTemplate, Directory) loadTemplate(String path, Log errors);

    /**
     * Create an AppHost for the specified application module.
     *
     * @param template        the FileTemplate for the module
     * @param workDir         the application "home" directory for the module
     * @param errors          the error log
     * @param realm           TODO
     * @param privateDbNames  the names of Db modules that should not be shared
     *
     * @return True iff there is a file template for the specified module path
     * @return (optional) the AppHost for the newly loaded Container
     */
    conditional AppHost createAppHost(FileTemplate fileTemplate, Directory appHomeDir, Log errors,
                                      String realm = "", String[] privateDbNames=[]);

    /**
     * Remove the specified AppHost.
     */
    void removeAppHost(AppHost appHost);
    }