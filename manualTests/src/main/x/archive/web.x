/**
 * This test will run a web server on localhost port 8080.
 *
 * After the server has started it can be tested using curl.
 *
 * 1. Get a non-existent User
 *
 * curl -i -w '\n'  -X GET http://localhost:8080/users/joe
 *
 * This will call the UsersApi.getUser method but no user exists so a 404 will be returned.
 *
 * 2. Add the user
 *
 * curl -i -w '\n' --header 'Content-Type: application/json' -X POST http://localhost:8080/users/joe -d '{"$type":"User", "name":"joe","email":"joe@ecstasy.com"}'
 *
 * This will call the UsersApi.putUser method and add the user to the Map.
 *
 * 3. Repeat the get call again
 *
 * curl -i -w '\n'  -X GET http://localhost:8080/users/joe
 *
 * It should now return a 200 response and a json representation of the User
 *
 * 4. Delete the user
 *
 * curl -i -w '\n' -X DELETE http://localhost:8080/users/joe
 *
 * This will call the UsersApi.deleteUser and remove the user from the Map
 *
 * 5. Repeat the get call again
 *
 * curl -i -w '\n'  -X GET http://localhost:8080/users/joe
 *
 * It should now return a 404 again
 *
 */
@HttpModule
@HttpsRequired
@LoginRequired
module TestWebApp
    {
    package web import web.xtclang.org;

    import web.Body;
    import web.Consumes;
    import web.Delete;
    import web.Get;
    import web.HttpModule;
    import web.HttpStatus;
    import web.HttpsRequired;
    import web.LoginRequired;
    import web.NotFound;
    import web.PathParam;
    import web.Post;
    import web.WebServer;

    /**
     * A simple CRUD web service.
     */
    @web.WebService("/users")
    service UsersApi
        {
        private Map<String, User> users = new HashMap();

        /**
         * Add or update a user.
         *
         * @param userId   the user name extracted from the URI path
         * @param user  the User to add or update, decoded from the json request body
         */
        @Post("/{userId}")
        @Consumes("application/json")
        void putUser(@PathParam String userId, @Body User user)
            {
            // ToDo: There could probably be validation here which could show handling of exceptions or status codes etc.
            // For example, validate the userId parameter matches the name in the User.
            // This could be a simple assertion that would result in a 400 response
            // Having said that, exceptions are a poor way to indicate a response code and instead
            // just return multiple values, e.g. the response body and a HttpStatus
            users.put(userId, user);
            }

        /**
         * Get a user.
         * If this method returns False, the web framework converts it to a 404 response.
         *
         * @param userId   the name of the User to get
         *
         * @return a True iff a User associated with the specified name exists
         * @return the User value associated with the specified name (conditional)
         */
        @Get("/{userId}")
        conditional User getUser(String userId)
            {
            if (User user := users.get(userId))
                {
                return True, user;
                }
            return False;
            }

        /**
         * Delete a user.
         *
         * @param userId  the name of the User to delete
         *
         * @return HttpStatus.OK if the User was deleted or HttpStatus.NotFound
         *         if no User exists for the specified name
         */
        @Delete("/{userId}")
        HttpStatus deleteUser(String userId)
            {
            if (!users.contains(userId))
                {
                return HttpStatus.NotFound;
                }
            users.remove(userId);
            return HttpStatus.OK;
            }
        }

    /**
     * A user with a name and email address.
     */
    const User(String name, String email)
        {
        }

    void run()
        {
        @Inject Console console;
        console.print("Testing Web App");

        // Create the web server, add the endpoints, and start.
        @Inject("server", opts=8080) web.HttpServer httpServer;
        WebServer server = new WebServer(httpServer);
        server.addWebService(new UsersApi());
        server.start();

        console.print("Started WebServer http://localhost:8080");
        }
    }