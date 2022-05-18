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
     * Create an WebHost for the specified application module.
     *
     * @param userDir  the user directory
     * @param appName  the application module name
     * @param domain   a sub-domain to use for the application (only for web applications)
     * @param errors   the error log
     *
     * @return True iff the WebHost was successfully created
     * @return (optional) the WebHost for the newly loaded Container
     */
    conditional WebHost createWebHost(Directory userDir, String appName, String domain, Log errors);

    /**
     * Remove the specified WebHost.
     */
    void removeWebHost(WebHost webHost);
    }