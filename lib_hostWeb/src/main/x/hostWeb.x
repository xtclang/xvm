/**
 * The module for basic web-based hosting functionality.
 */
@web.WebModule
module hostWeb.xtclang.org // TODO: rename to hostController
    {
    package platform import platform.xtclang.org;
    package web      import web.xtclang.org;

    import platform.HostManager;

    import web.HttpServer;
    import web.WebServer;

    /**
     * Configure the host controller.
     */
    void configure(HostManager mgr, HttpServer httpController, HttpServer httpPublic)
        {
        WebServer controllerServer = new WebServer(httpController);
        controllerServer.addWebService(new Controller(mgr, httpPublic), "/host"); // TODO: Controller factory?
        controllerServer.start();

        @Inject Console console;
        console.println("Started Ecstasy hosting at http://localhost:8080");
        }
    }