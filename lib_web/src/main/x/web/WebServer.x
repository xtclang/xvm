/**
 * A web server.
 */
@Concurrent
service WebServer(HttpServer httpServer)
        implements Handler
    {
    /**
     * The map of handlers for root paths.
     */
    private Map<String, Handler> handlers = new HashMap();

    /**
     * Add a handler.
     */
    void addHandler(String path, Handler handler)
        {
        assert:arg !handlers.contains(path);
        handlers.put(path, handler);
        }

    /**
     * Remove a handler.
     */
    void removeHandler(String path)
        {
        handlers.remove(path);
        }

    /**
     * Add all annotated endpoints from the specified web-service.
     *
     * @param webService  the web-service with annotated endpoints
     * @param path        the root path prefix for all endpoints
     */
    void addWebService(WebService webService, String? path = Null, Router? router = Null)
        {
        if (path == Null)
            {
            path = webService.path;
            }
        if (router == Null)
            {
            router = new Router();
            }
        router.addRoutes(webService, path);
        router.freeze(True);
        addHandler(path, new RoutingHandler(httpServer, router));
        }

    /**
     * Start this web server.
     */
    void start()
        {
        httpServer.attachHandler(this);
        }

    /**
     * The Handler implementation to handle all the requests to this web server.
     */
    @Override
    void handle(Object context, String uri, String method,
                String[] headerNames, String[][] headerValues, Byte[] body)
        {
        for ((String path, Handler handler) : handlers)
            {
            if (uri.startsWith(path))
                {
                handler.handle^(context, uri, method, headerNames, headerValues, body);
                return;
                }
            }
        httpServer.send(context, HttpStatus.NotFound.code, [], [], []);
        }

    /**
     * A handler for HTTP requests.
     */
    @Concurrent
    static service RoutingHandler(HttpServer httpServer, Router router)
            implements Handler
        {
        @Override
        void handle(Object context, String uri, String methodName,
                    String[] headerNames, String[][] headerValues, Byte[] body)
            {
            @Future HttpResponse result =
                router.handle(uri, methodName, headerNames, headerValues, body);

            &result.whenComplete((response, e) ->
                {
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

    static interface Handler
        {
        /**
         * Handle an HTTP request.
         */
        void handle(Object context, String uri, String method,
                    String[] headerNames, String[][] headerValues, Byte[] body);
        }
    }