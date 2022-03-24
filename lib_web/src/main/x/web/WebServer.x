/**
 * A web server.
 */
@Concurrent
service WebServer(Int port)
        implements Handler
    {
    /**
     * The HTTP server.
     */
    @Unassigned
    private HttpServer httpServer;

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

    // ToDo: Methods to add handlers for HTTP response statuses and exceptions

    /**
     * Start this web server.
     */
    void start()
        {
        this.router = router.freeze();

        @Inject(opts=port) HttpServer server;

        server.attachHandler(this);

        this.httpServer = server;
        }

    /**
     * The Handler implementation to handle all the requests to this web server.
     */
    @Override
    void handle(Object context, String uri, String method,
                String[] headerNames, String[][] headerValues, Byte[] body)
        {
        Handler handler = new RequestHandler(httpServer, router);
        handler.handle^(context, uri, method, headerNames, headerValues, body);
        }

    /**
     * A handler for HTTP requests.
     */
    @Concurrent
    private static service RequestHandler(HttpServer httpServer, Router router)
            implements Handler
        {
        @Override
        void handle(Object context, String uri, String methodName,
                    String[] headerNames, String[][] headerValues, Byte[] body)
            {
            @Future Tuple<Int, String[], String[][], Byte[]> result =
                router.handle(uri, methodName, headerNames, headerValues, body);

            &result.whenComplete((t, e) ->
                {
                if (t == Null)
                    {
                    httpServer.send^(context, 500, [], [], []);
                    }
                else
                    {
                    httpServer.send^(context, t[0], t[1], t[2], t[3]);
                    }
                });
            }
        }

    static interface Handler
        {
        /**
         * Handle an HTTP request.
         */
        void handle(Object context, String uri, String method,
                    String[] headerNames, String[][] headerValues, Byte[] body);
        }
    }