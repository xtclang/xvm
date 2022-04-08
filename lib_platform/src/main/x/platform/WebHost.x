import ecstasy.mgmt.Container;

import web.HttpServer;

/**
 * AppHost for a Web module.
 */
const WebHost
        extends AppHost
    {
    construct (Container container, String moduleName, Directory homeDir, String domain,
               HttpServer httpServer, AppHost[] dependents)
        {
        construct AppHost(moduleName, homeDir);

        this.container  = container;
        this.httpServer = httpServer;
        this.domain     = domain;
        this.dependents = dependents;
        }

    /**
     * The application domain.
     */
    String domain;

    /**
     * The HttpServer used by this Web module.
     */
    HttpServer httpServer;

    /**
     * The AppHosts for the containers this module depends on.
     */
    AppHost[] dependents;


    // ----- Closeable -----------------------------------------------------------------------------

    @Override
    void close(Exception? e = Null)
        {
        for (AppHost dependent : dependents)
            {
            dependent.close(e);
            }
        httpServer.close(e);
        }
    }