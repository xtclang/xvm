/**
 * A annotation that represents a set of endpoints for a specific URI path.
 *
 * Example:
 *
 *     @web.WebModule
 *     module HelloWorld {
 *         package web import web.xtclang.org;
 *
 *         @web.WebService("/")
 *         service Hello {
 *             @web.Get("hello")
 *             @web.Produces(Text)
 *             String sayHello() {
 *                 return "Hello World";
 *             }
 *         }
 *     }
 *
 * To version an HTTP API, tag only the API changes on the endpoints. For example, assume an
 * existing versionless API:
 *
 *     @WebService("/api")
 *     service api {
 *         @Get("/items{/id}") Item|HttpStatus getItems(Int? id = Null) {...}
 *         @Put("/items/{id}") HttpStatus putItem(Int? id, @BodyParam Item item) {...}
 *         @Delete("/items/{id}") HttpStatus deleteItem(Int? id) {...}
 *     }
 *
 * At some point, the popularity of the API leads to a new version that completely changes the
 * implementation of the HTTP `PUT` method for items, and removes the HTTP `DELETE` method, and
 * the decision is made that existing clients should automatically use the latest API version:
 *
 *     @WebService("/api", currentVer=v:2, defaultVer=v:2)
 *     service api {
 *         @Get("/items{/id}") Item|HttpStatus getItems(Int? id = Null) {...}
 *         @Put("/items/{id}") HttpStatus putItem(Int? id, @BodyParam Item item) {...}
 *         @Put("/items/{id}", api=v:2) HttpStatus putItem2(Int? id, @BodyParam Item item) {...}
 *         @Delete("/items/{id}", api=v:1..v:1) HttpStatus deleteItem(Int? id) {...}
 *     }
 *
 * Given the version 2 of the above example, a `PUT` to `/api/v1/items/14` would be routed to the
 * `putItem()` method, while a `PUT` to either `/api/items/14` or `/api/v2/items/14` would be routed
 * to the `putItem2()` method.
 *
 * @param path        the path string for the web service, for example "/" or "/api"
 * @param currentVer  (optional) the current (i.e. latest) API version, if the API is versioned
 * @param defaultVer  (optional) iff the API is versioned, then this is the API version that will be
 *                    automatically routed to if no version number is present in the URL path; to
 *                    use the latest API version by default, use the same value as `currentVer`
 */
annotation WebService(String path, Version? currentVer = Null, Version? defaultVer = Null)
        into service {

    /**
     * The function that represents a WebService constructor.
     */
    typedef function WebService() as Constructor;

    /**
     * The request for the currently executing handler within this service.
     */
    RequestIn? request;

    /**
     * The session related to the current request.
     */
    Session? session.get() = request?.session : Null;

    /**
     * The [WebApp] containing this `WebService`. If no `WebApp` is explicitly configured, then the
     * module containing the `WebService` is used.
     */
    @Unassigned
    WebApp webApp {
        @Override
        WebApp get() {
            if (!assigned) {
                assert val moduleObject := this:class.baseTemplate.containingModule.ensureClass().isSingleton()
                        as $"Unable to obtain containing module for {this}";
                assert WebApp app := moduleObject.is(WebApp) as $"Unable to obtain the WebApp for {this}";
                set(app);
            }

            return super();
        }

        @Override
        void set(WebApp app) {
            assert !assigned as $"The WebApp containing this WebService cannot be modified";
            super(app);
        }
    }

    /**
     * Process a received [RequestIn]. This is invoked by the server in order to transfer control to
     * an "EndPoint Handler".
     *
     * This method is called from the previous step in the routing chain, in order to transfer
     * control into this web service. For the duration of the processing inside this web service,
     * the request will be available as a property on this WebService.
     *
     * @param request  the current [RequestIn] that is going to be processed by the `WebService`
     * @param handler  the handler to delegate the processing to
     * @param onError  (optional) the error handler to delegate the error processing to
     *
     * @return the [ResponseOut] to send back to the caller
     */
    ResponseOut route(RequestIn request, Handler handle, ErrorHandler? onError) {
        assert this.request == Null;
        this.request = request;
        try {
            return handle(request).freeze(True); // TODO GG move ".freeze()" into the "handle" and add support for streaming there
        } catch (RequestAborted e) {
            return e.makeResponse();
        } catch (Exception e) {
            return onError?(request, e).freeze(True) : throw e;
        } finally {
            this.request = Null;
        }
    }

    /**
     * A virtual child class that wraps the specified BinaryInput as a service (part of *this*
     * service).
     */
    class BinaryInputProxy(BinaryInput source)
            delegates BinaryInput(source);

    /**
     * For components -- including the application's [Authenticator](security.Authenticator) and
     * [session Broker](sessions.Broker) -- that may require "extra" `WebService`s to be available
     * as part of a deployment, the components can implement this interface to expose their
     * knowledge of those necessary `WebService`s.
     */
    static interface ExtrasAware {
        /**
         * The unique [Duplicable] [WebService] "extras" that this component is aware of.
         */
        @RO (Duplicable+WebService)[] extras;

        /**
         * For some collection of objects that may each be a [Duplicable] [WebService] "extra", or
         * [ExtrasAware], collect all of those "extras".
         *
         * @param extras  a list of extras already known about
         * @param objs    objects that may be a [Duplicable] [WebService] or [ExtrasAware]
         *
         * @return an array of unique [Duplicable] [WebService] extras
         */
        static (Duplicable+WebService)[] collectExtras((Duplicable+WebService)[] extras, Iterable objs) {
            for (Object o : objs) {
                extras = collectExtras(extras, o);
            }
            return extras;
        }

        /**
         * For an object that may itself be a [Duplicable] [WebService] "extra", or is [ExtrasAware]
         * and may know of other [Duplicable] [WebService] "extras", collect all of those "extras".
         *
         * @param extras  a list of extras already known about
         * @param o       an object that may be a [Duplicable] [WebService] or [ExtrasAware]
         *
         * @return an array of unique [Duplicable] [WebService] extras
         */
        static (Duplicable+WebService)[] collectExtras((Duplicable+WebService)[] extras, Object o) {
            if (o.is(Duplicable+WebService) && !extras.any(ws -> ws.path == o.path)) {
                extras += o;
            }
            if (o.is(ExtrasAware)) {
                extras = collectExtras(extras, o.extras);
            }
            return extras;
        }
    }
}