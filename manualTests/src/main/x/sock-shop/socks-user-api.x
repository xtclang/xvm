/**
 * The Ecstasy Sock Shop User microservice.
 */
module SockShopUserApi
    {
    package db   import SockShopUser;
    package json import json.xtclang.org;
    package web  import web.xtclang.org;

    import ecstasy.io.ByteArrayInputStream;
    import ecstasy.io.UTF8Reader;

    import db.Connection;
    import db.Address;
    import db.AddressId;
    import db.Card;
    import db.CardId;
    import db.User;

    import json.Schema;

    import web.Body;
    import web.Consumes;
    import web.Delete;
    import web.Get;
    import web.HeaderParam;
    import web.HttpStatus;
    import web.Patch;
    import web.PathParam;
    import web.Post;
    import web.Produces;
    import web.QueryParam;
    import web.WebServer;

    /**
     * The Users web-service API.
     *
     * This is a very naive and insecure implementation of user
     * authentication. It should not be used as an example/blueprint to
     * follow when implementing authentication in custom services. The code
     * below exists purely to provide compatibility with the original front
     * end written for SockShop
     */
    const UserApi()
        {
        /**
         * The users database.
         */
        @Inject Connection dbc;

        @Get("login")
        @Produces("application/json")
        HttpStatus login(@HeaderParam("Authorization") String auth)
            {
            return HttpStatus.OK;
            }

        @Post("register")
        @Consumes("application/json")
        @Produces("application/json")
        (Status<String>, HttpStatus) register(@Body User user)
            {
            if (dbc.users.register(user))
                {
                return new Status("id", user.username), HttpStatus.OK;
                }
            return new Status("message", "username already exists"), HttpStatus.Conflict;
            }
        }

    /**
     * The Address web-service API.
     */
    const AddressApi
        {
        /**
         * The users database.
         */
        @Inject Connection dbc;

        @Get
        @Produces("application/json")
        Embedded<Address> getAll()
            {
            return new Embedded("address");
            }

        @Post
        @Consumes("application/json")
        @Produces("application/json")
        conditional Status<AddressId> register(@Body AddAddressRequest req)
            {
            if (AddressId id := dbc.users.addAddress(req.userID, req.getAddress()))
                {
                return True, new Status("id", id);
                }
            return False;
            }

        @Get("{id}")
        @Produces("application/json")
        conditional Address get(@PathParam AddressId id)
            {
            return dbc.users.getAddress(id);
            }

        @Delete("{id}")
        @Produces("application/json")
        Status<Boolean> delete(@PathParam AddressId id)
            {
            Boolean updated = dbc.users.removeAddress(id);
            return new Status("status", updated);
            }
        }

    /**
     * The payment card web-service API.
     */
    const CardApi
        {
        /**
         * The users database.
         */
        @Inject Connection dbc;

        @Get
        @Produces("application/json")
        Embedded<Card> getAll()
            {
            return new Embedded("card");
            }

        @Post
        @Consumes("application/json")
        @Produces("application/json")
        conditional Status<CardId> register(@Body AddCardRequest req)
            {
            if (CardId id := dbc.users.addCard(req.userID, req.getCard()))
                {
                return True, new Status("id", id);
                }
            return False;
            }

        @Get("{id}")
        @Produces("application/json")
        conditional Card get(@PathParam CardId id)
            {
            return dbc.users.getCard(id);
            }

        @Delete("{id}")
        @Produces("application/json")
        Status<Boolean> delete(@PathParam CardId id)
            {
            Boolean removed = dbc.users.removeCard(id);
            return new Status("status", removed);
            }
        }

    /**
     * A const representing the http request body to add an address.
     */
    const AddAddressRequest(String number, String street, String city, String postcode, String country, String userID)
        {
        Address getAddress()
            {
            return new Address("", number, street, city, postcode, country);
            }
        }

    /**
     * A const representing the http request body to add an payment cars.
     */
    const AddCardRequest(String longNum, String expires, String ccv, String userID)
        {
        Card getCard()
            {
            return new Card("", longNum, expires, ccv);
            }
        }

    /**
     * A status returned by a http request.
     */
    const Status<Value>(String name, Value value)
        {
        }

    /**
     * A holder for an embedded map of values.
     */
    const Embedded<Value>(Map<String, Value[]> _embedded)
        {
        construct(String id)
            {
            Map<String, Value[]> map = new ListMap();
            map.put(id, []);
            _embedded = map;
            }

        construct(String id, Value value)
            {
            Map<String, Value[]> map = new ListMap();
            map.put(id, [value]);
            _embedded = map;
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
                console.println($"Loaded Sock Shop User configuration from {config}\n{app}");
                }
            else
                {
                app = new Application();
                console.println($"Using Sock Shop User configuration\n{app}");
                }
            return app;
            }
        }

    void run()
        {
        @Inject Console   console;
        @Inject Directory curDir;

        console.println("Starting Sock Shop User");

        Directory rootDir  =  curDir.dirFor("src")
                                    .dirFor("main")
                                    .dirFor("x")
                                    .dirFor("sock-shop")
                                    .dirFor("users");

        // Load the application configuration
        Application appConfig = Application.load(rootDir);
        WebServer   server    = new WebServer(appConfig.port)
                                    .addRoutes(new UserApi(), "/")
                                    .addRoutes(new AddressApi(), "/addresses")
                                    .addRoutes(new CardApi(), "/cards")
                                    .start();

        console.println($"Serving User API at http://localhost:{server.port}");

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
