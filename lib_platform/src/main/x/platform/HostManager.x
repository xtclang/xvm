import ecstasy.reflect.FileTemplate;

interface HostManager
    {
    /**
     * Retrieve an WebHost for the specified domain.
     *
     * @return True iff there is an WebHost for the specified domain
     * @return (optional) the WebHost
     */
    conditional WebHost getWebHost(String domain);

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
     * Create an WebHost for the specified application module.
     *
     * @param template        the FileTemplate for the module
     * @param workDir         the application "home" directory for the module
     * @param domain          a sub-domain to use for the application (only for web applications)
     * @param platform        True iff this application is the intrinsic part of the platform
     * @param errors          the error log
     *
     * @return True iff there is a file template for the specified module path
     * @return (optional) the WebHost for the newly loaded Container
     */
    conditional WebHost createWebHost(FileTemplate fileTemplate, Directory appHomeDir,
                                      String domain, Boolean platform, Log errors);

    /**
     * Remove the specified WebHost.
     */
    void removeWebHost(WebHost webHost);
    }