/**
 * A web server.
 */
@Concurrent
service WebServer(HttpServer httpServer)
        implements Handler
    {
    /**
     * The "down stream" handler.
     */
    @Unassigned
    private RoutingHandler handler;

    /**
     * The router.
     */
    private Router router = new Router();

    /**
     * Add all annotated endpoints from the specified web-service.
     *
     * @param webService  the web-service with annotated endpoints
     * @param router      an optional router
     */
    void addWebService(WebService webService)
        {
        router.addRoutes(webService, webService.path);
        }

    /**
     * Start this web server.
     */
    @Synchronized
    void start()
        {
        assert !&handler.assigned;

        this.handler = new RoutingHandler(httpServer, router.freeze(True));
        httpServer.attachHandler(this);
        }

    /**
     * Shutdown this web server.
     */
    @Synchronized
    void shutdown()
        {
        if (handler.pendingRequests == 0)
            {
            httpServer.close();
            }
        else
            {
            // wait a second
            @Inject Timer timer;
            timer.schedule(SECOND, () -> httpServer.close());
            }
        }

    /**
     * The Handler implementation to handle all the requests to this web server.
     */
    @Override
    void handle(Object context, String uri, String method,
                String[] headerNames, String[][] headerValues, Byte[] body)
        {
        handler.handle^(context, uri, method, headerNames, headerValues, body);
        }

    /**
     * A handler for HTTP requests.
     */
    @Concurrent
    static service RoutingHandler(HttpServer httpServer, Router router)
            implements Handler
        {
        /**
         * Pending request counter.
         */
        Int pendingRequests;

        @Override
        void handle(Object context, String uri, String methodName,
                    String[] headerNames, String[][] headerValues, Byte[] body)
            {
            HttpResponse result =
                router.handle^(uri, methodName, headerNames, headerValues, body);
            pendingRequests++;

            &result.whenComplete((response, e) ->
                {
                pendingRequests--;
                if (response == Null)
                    {
                    httpServer.send(context, HttpStatus.InternalServerError.code, [], [], []);
                    }
                else
                    {
                    response.send(httpServer, context);
                    }
                });
            }
        }

    @Override
    String toString()
        {
        return httpServer.toString();
        }

    /**
     * The handler interface used via duck-typing by the native HTTP server.
     */
    static interface Handler
        {
        /**
         * Handle an HTTP request.
         */
        void handle(Object context, String uri, String method,
                    String[] headerNames, String[][] headerValues, Byte[] body);
        }
    }