/**
 * The module for basic web-based hosting functionality.
 */
module hostWeb.xtclang.org
    {
    package host import host.xtclang.org;
    package web  import web.xtclang.org;

    import web.WebServer;

    void run()
        {
        server.addWebService(new HostApi(), "/host");
        server.start();

        @Inject Console console;
        console.println("Started Ecstasy hosting at http://localhost:8080");
        }

    @Lazy WebServer server.calc()
        {
        @Inject(opts=8080) web.HttpServer server;
        return new WebServer(server);
        }
    }