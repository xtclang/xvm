/**
 * This mixin is used to mark a module as being a web-application module.
 */
mixin WebModule
        into Module
    {
    /**
     * Collect all roots declared by this Module.
     */
   void createCatalog_(HttpServer httpServer)
        {
        // TODO: this should be a handler factory instead
        Catalog_ catalog = new Catalog_(httpServer);

        for (Class child : this.as(Module).classes)
            {
            if (child.annotatedBy(WebService))
                {
                WebService webService;

                if (function Object() constructor := child.PublicType.defaultConstructor())
                    {
                    webService = constructor().as(WebService);
                    }
                else if (Struct structure := child.allocate())
                    {
                    webService = child.instantiate(structure).as(WebService);
                    }
                else
                    {
                    // how to report a non-instantiatable WebService
                    continue;
                    }
                catalog.addWebService(webService);
                }
            }

        catalog.start();
        }

    static service Catalog_(HttpServer httpServer)
            extends WebServer(httpServer)
        {
        }
    }