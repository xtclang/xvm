/**
 * This mixin is used to mark a module as being a web-application module.
 */
mixin WebModule
        into Module
    {
    /**
     * Collect all roots declared by this Module.
     *
     * @return the Catalog handler for this WebModule
     */
    WebServer.Handler createCatalog_(HttpServer httpServer)
        {
        Router router = new Router();

        for (Class child : this.as(Module).classes)
            {
            if (child.implements(WebService), Struct structure := child.allocate())
                {
                WebService webService = child.instantiate(structure).as(WebService);

                router.addRoutes(webService, webService.path);
                }
            }

        // TODO: this should be a handler factory instead
        return new Catalog_(httpServer, router.freeze(True));
        }

    static service Catalog_
            extends WebServer.RoutingHandler
        {
        construct(HttpServer httpServer, Router router)
            {
            construct WebServer.RoutingHandler(httpServer, router);
            }
        }
    }