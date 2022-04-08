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
   void createCatalog_(HttpServer httpServer)
        {
        // TODO: this should be a handler factory instead
        Catalog_ catalog = new Catalog_(httpServer);

        for (Class child : this.as(Module).classes)
            {
            if (child.implements(WebService), Struct structure := child.allocate())
                {
                WebService webService = child.instantiate(structure).as(WebService);

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