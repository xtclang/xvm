/**
 * The Ecstasy Sock Shop catalog microservice.
 */
module SockShopCatalogApi
    {
    package db   import SockShopCatalog;
    package json import json.xtclang.org;
    package web  import web.xtclang.org;

    import ecstasy.collections.HashMap;
    import ecstasy.io.ByteArrayInputStream;
    import ecstasy.io.InputStream;
    import ecstasy.io.UTF8Reader;

    import db.Connection;
    import db.Sock;
    import db.SockOrder;

    import json.Schema;

    import web.Get;
    import web.MediaType;
    import web.PathParam;
    import web.Produces;
    import web.QueryParam;
    import web.StaticContent;
    import web.WebServer;

    /**
     * The Catalog web-service API.
     */
    const CatalogApi()
        {
        /**
         * The catalog database.
         */
        @Inject Connection dbc;

        /**
         * Get all Socks, ordered by name or price and optionally matching
         * one or more tags.
         */
        @Get("/")
        @Produces("application/json")
        Collection<Sock> getSocks(@QueryParam("tags")  String? tags    = Null,
                                  @QueryParam("order") String order    = "price",
                                  @QueryParam("page")  Int    pageNum  = 1,
                                  @QueryParam("size")  Int    pageSize = 10)
            {
            SockOrder sockOrder = order == "price" ? Price : Name;
            String[]  tagNames;

            if (tags.is(String) && tags != "")
                {
                tagNames = tags.split(',').freeze();
                }
            else
                {
                tagNames = [];
                }

            return dbc.products.findSocks(tagNames, sockOrder, pageNum, pageSize);
            }

        /**
         * Get a specific Sock by id.
         */
        @Get("/{id}")
        @Produces("application/json")
        conditional Sock getSock(@PathParam("id") String id)
            {
            return dbc.products.get(id);
            }

        /**
         * Get the total number of Socks, optionally filtered
         * by matching tags.
         */
        @Get("/size")
        @Produces("application/json")
        Count getSockCount(@QueryParam("tags") String tags = "")
            {
            return new Count(tags == ""
                ? dbc.products.size
                : dbc.products.count(tags.split(',')));
            }
        }

    /**
     * The Catalog tags API.
     */
    const TagApi()
        {
        /**
         * The catalog database.
         */
        @Inject Connection dbc;

        /**
         * Get all of the tags in the catalog.
         */
        @Get("/")
        Tags getTags()
            {
            return new Tags(dbc.products.tags());
            }
        }

    /**
     * A holder for counts.
     */
    const Count(Int size);

    /**
     * A holder for tags values.
     */
    const Tags(String[] tags);

    /**
     * The application configuration.
     */
    const Application(Int port = 8080)
        {
        /**
         * Create an Application configuration, possibly loading it from a json file.
         */
        static Application load(Directory dir)
            {
            @Inject Console console;
            Application     app;

            if (File config := dir.findFile("application.json"))
                {
                ByteArrayInputStream in = new ByteArrayInputStream(config.contents);
                app = Schema.DEFAULT.createObjectInput(new UTF8Reader(in)).read<Application>();
                console.println($"Loaded Sock Shop Catalog configuration from {config}\n{app}");
                }
            else
                {
                app = new Application();
                console.println($"Using Sock Shop Catalog configuration\n{app}");
                }
            return app;
            }
        }

    void run()
        {
        @Inject Console    console;
        @Inject Directory  curDir;
        @Inject Connection dbc;

        console.println("Starting Sock Shop Catalog");

        // Get the locations of the data files and application configuration file
        Directory        rootDir  =  curDir.dirFor("src")
                                           .dirFor("main")
                                           .dirFor("x")
                                           .dirFor("sock-shop")
                                           .dirFor("catalog");

        assert Directory imageDir := rootDir.findDir("images");
        assert File      data     := rootDir.findFile("data.json");

        // Load the DB from a json file
        console.println("Loading Sock Shop DB");
        if (dbc.products.size == 0)
            {
            dbc.loadSocks(data);
            }

        // Load the application configuration
        Application appConfig = Application.load(rootDir);

        CatalogApi    api    = new CatalogApi();
        TagApi        tags   = new TagApi();
        StaticContent images = new StaticContent(imageDir, MediaType.IMAGE_JPEG_TYPE);
        WebServer     server = new WebServer(appConfig.port)
                                    .addRoutes(api, "/catalogue")
                                    .addRoutes(images, "/catalogue/images")
                                    .addRoutes(tags, "/tags")
                                    .start();

        console.println($"Serving Catalog API at http://localhost:{server.port}");

        wait(server, Duration.ofSeconds(600));
        }

    void wait(WebServer server, Duration duration)
        {
        @Inject Timer timer;

        @Future Tuple<> result;

        // schedule a "forced shutdown"
        timer.schedule(duration, () ->
            {
            if (!&result.assigned)
                {
                @Inject Console console;
                console.println("Shutting down");
                result=Tuple:();
                }
            });

        private void checkRunning(WebServer server, Timer timer, FutureVar<Tuple> result)
            {
            if (server.isRunning())
                {
                timer.schedule(Duration.ofSeconds(10), &checkRunning(server, timer, result));
                return;
                }

            if (!result.assigned)
                {
                @Inject Console console;
                console.println("Webserver has stopped");
                result.set(Tuple:());
                }
            }

        // schedule a periodic check
        timer.schedule(Duration.ofSeconds(10), &checkRunning(server, timer, &result));
        return result;
        }
    }
