/**
 * This mixin is used to mark a module as being a web-application module.
 */
mixin WebModule
        into Module
    {
    import WebServer.Handler;

    /**
     * Collect all roots declared by this Module and instantiate corresponding WebServices.
     */
    immutable Map<String, Handler> collectRoots_(HttpServer httpServer)
        {
        import ecstasy.reflect.Annotation;

        Map<String, Handler> roots = new HashMap();

        Module webModule = this;
        for (Class child : webModule.classes)
            {
            if (child.implements(WebService), Struct structure := child.allocate())
                {
                WebService webService = child.instantiate(structure).as(WebService);

                String path = webService.path;
                if (roots.contains(path))
                    {
                    // TODO: how to report a duplicate path?
                    }
                else
                    {
                    Bridge bridge = new Bridge(httpServer, webService);
                    roots.put(path, &bridge.maskAs(Handler));
                    }
                }
            }

        return roots.makeImmutable();
        }

    static service Bridge
            extends WebServer.RoutingHandler
        {
        construct(HttpServer httpServer, WebService webService)
            {
            Router router = new Router();
            router.addRoutes(webService, webService.path);

            this.webService = webService;
            construct WebServer.RoutingHandler(httpServer, router);
            }

        WebService webService;
        }
    }