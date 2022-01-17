import ecstasy.http.Response;
import ecstasy.http.Server;

/**
 * A web server.
 */
@Concurrent service WebServer
        implements Server.Handler
    {
    construct (Int port = 8080)
        {
        this.port = port;
        }

    public/private Int port;

    /**
     * The http server.
     */
    private Server? server = Null;

    /**
     * The Router that routes requests to endpoints.
     */
    private Router router = new Router();

    /**
     * Add all of the annotated endpoints in the specified web-service.
     *
     * @param webService  the web-service with annotated endpoints
     * @param path        the root path prefix for all endpoints
     */
    <T> WebServer addRoutes(T webService, String path = "")
        {
        router.addRoutes(webService, path);
        return this;
        }

    // ToDo: Methods to add handlers for http response statuses and exceptions

    /**
     * Start this web server.
     */
    WebServer start()
        {
        this.router = router.freeze();
        Server server = new Server(port, this);
        server.start();
        this.server = server;
        return this;
        }

    /**
     * Stop this web server.
     */
    void stop()
        {
        Server? s = this.server;
        if (s.is(Server))
            {
            s.stop();
            }
        }

    /**
     * Determine whether this web service is running.
     *
     * @return True iff this web server is running
     */
    Boolean isRunning()
        {
        return this.server?.isRunning() : False;
        }

    /**
     * The Server.Handler implementation to handle all the requests
     * to the web server.
     */
    @Override
    void handle(String uri, String method, String[] headerNames, String[][] headerValues,
                Byte[] body, Response response)
        {
        Handler handler = new Handler(router);
        handler.handle^(uri, method, headerNames, headerValues, body, response);
        }

    /**
     * A handler for http requests.
     */
    @Concurrent
    private static service Handler(Router router)
            implements Server.Handler
        {
        @Override
        void handle(String uri, String methodName, String[] headerNames, String[][] headerValues,
                    Byte[] body, Response response)
            {
            @Future Tuple<Int, String[], String[][], Byte[]> result = router.handle(uri, methodName, headerNames, headerValues, body);
            &result.whenComplete((t, e) ->
                {
                if (t.is(Tuple<Int, String[], String[][], Byte[]>))
                    {
                    response.send^(t[0], t[1], t[2], t[3]);
                    }
                else
                    {
                    response.send^(500, [], [], []);
                    }
                });
            }
        }
    }