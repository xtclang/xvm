/**
 * The Ecstasy Sock Shop Cart microservice.
 */
module SockShopCartApi
    {
    package db   import SockShopCart;
    package json import json.xtclang.org;
    package web  import web.xtclang.org;

    import ecstasy.io.ByteArrayInputStream;
    import ecstasy.io.UTF8Reader;

    import db.Connection;
    import db.Cart;
    import db.Item;

    import json.Schema;

    import web.Body;
    import web.Consumes;
    import web.Delete;
    import web.Get;
    import web.HttpStatus;
    import web.Patch;
    import web.PathParam;
    import web.Post;
    import web.Produces;
    import web.QueryParam;
    import web.WebServer;

    /**
     * The Carts web-service API.
     */
    const CartsApi()
        {
        /**
         * The carts database.
         */
        @Inject Connection dbc;

        /**
         * Get a specific Cart by id.
         */
        @Get("/{id}")
        @Produces("application/json")
        conditional Cart getCart(@PathParam("id") String id)
            {
            return dbc.carts.get(id);
            }

        /**
         * Delete a specific Cart by id.
         */
        @Delete("/{id}")
        HttpStatus deleteCart(@PathParam("id") String id)
            {
            if (dbc.carts.contains(id))
                {
                dbc.carts.get(id);
                return HttpStatus.OK;
                }
            return HttpStatus.NotFound;
            }

        /**
         * Merge one shopping cart into another.
         *
         * Customer can add products to a shopping cart anonymously, but when
         * they log in the anonymous shopping cart needs to be merged into
         * the customer's own shopping cart
         */
        @Get("/{id}/merge")
        HttpStatus merge(@PathParam String id, @QueryParam("sessionId") String sessionId)
            {
            return dbc.carts.merge(id, sessionId) ? HttpStatus.Accepted : HttpStatus.NotFound;
            }

        /**
         * Get the items for a specific Cart identifier.
         */
        @Get("{id}/items")
        @Produces("application/json")
        conditional Item[] getItems(@PathParam String id)
            {
            if (Cart cart := dbc.carts.get(id))
                {
                return True, cart.items;
                }
            return False;
            }

        /**
         * Add item to the shopping cart.
         *
         * This operation will add item to the shopping cart if it doesn't already exist,
         * or increment quantity by the specified number of items if it does.
         */
        @Post("/{id}/items")
        @Consumes("application/json")
        @Produces("application/json")
        (Item, HttpStatus) addItem(@PathParam String id, @Body Item item)
            {
            Item added = dbc.carts.addItem(id, item);
            return (added, HttpStatus.Accepted);
            }

        /**
         * Return the specified item from the shopping cart.
         */
        @Get("/{id}/items/{itemId}")
        @Produces("application/json")
        conditional Item getItem(@PathParam String id, @PathParam String itemId)
            {
            if (Cart cart := dbc.carts.get(id))
                {
                return cart.getItem(itemId);
                }
            return False;
            }

        /**
         * Remove specified item from the shopping cart, if it exists.
         */
        @Delete("/{id}/items/{itemId}")
        HttpStatus deleteItem(@PathParam String id, @PathParam String itemId)
            {
            dbc.carts.removeItem(id, itemId);
            return HttpStatus.Accepted;
            }

        /**
         * Update item in a shopping cart.
         *
         * This operation will add item to the shopping cart if it doesn't
         * already exist, or replace it with the specified item if it does.
         */
        @Patch("/{id}/items")
        @Consumes("application/json")
        HttpStatus updateItem(@PathParam String id, @Body Item item)
            {
            dbc.carts.updateItem(id, item);
            return HttpStatus.Accepted;
            }
        }

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
                console.println($"Loaded Sock Shop Cart configuration from {config}\n{app}");
                }
            else
                {
                app = new Application();
                console.println($"Using Sock Shop Cart configuration\n{app}");
                }
            return app;
            }
        }

    void run()
        {
        @Inject Console   console;
        @Inject Directory curDir;

        console.println("Starting Sock Shop Cart");

        Directory rootDir  =  curDir.dirFor("src")
                                    .dirFor("main")
                                    .dirFor("x")
                                    .dirFor("sock-shop")
                                    .dirFor("carts");

        // Load the application configuration
        Application appConfig = Application.load(rootDir);
        WebServer   server    = new WebServer(appConfig.port)
                                    .addRoutes(new CartsApi(), "/carts")
                                    .start();

        console.println($"Serving Cart API at http://localhost:{server.port}");

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
