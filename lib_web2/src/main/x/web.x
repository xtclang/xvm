/**
 * Web client and server APIs.
 *
 * TODO pre and post examples, and how to configure
 * TODO auth and auth, and examples
 */
module web.xtclang.org
    {
    package aggregate   import aggregate.xtclang.org;
    package collections import collections.xtclang.org;
    package crypto      import crypto.xtclang.org;
    package json        import json.xtclang.org;
    package net         import net.xtclang.org;

    import ecstasy.reflect.Parameter;

    // ----- fill in name here ------------------------------------------------------------

    /**
     * A function that can process an incoming request corresponding to a `Route` is called an
     * `Endpoint Handler`.
     */
    typedef function Response(Session, Request) as Handler;

    /**
     * A function that is called when an exception occurs (or an internal error represented by a
     * `String` description or an HttpsStatus code) is called an `ErrorHandler`.
     */
    typedef function Response(Session, Request, Exception|String|HttpStatus) as ErrorHandler;

    /**
     * `TrustLevel` is an enumeration that approximates a point-in-time level of trust associated
     * with a user request or session, or denotes a required level of trust for a specific
     * operation.
     */
    enum TrustLevel {None, Normal, High, Highest}


    // ----- fill in name here ------------------------------------------------------------

    /**
     * A mixin to indicate the media-types produced by a particular component.
     *
     * The String `mediaType` value must **not** include wild-cards.
     *
     * Specifying this annotation on a `WebApp` or `WebService` is a convenient way of specifying a
     * default, so that the default does not need to be specified on each `EndPoint`. When walking
     * up from an `EndPoint` to its containing `WebService` and ultimately the `WebApp` module, the
     * first `@Produces` annotation encountered is the one used. In the absence of any `@Produces`
     * annotation, the default is `Json`.
     *
     * Example:
     *
     *     @Get("/{id}")
     *     @Produces(Json)
     *     conditional Cart getCart(@PathParam("id") String id, Session session) {...}
     */
    mixin Produces(MediaType|MediaType[] produces)
            into WebApp | WebService | Endpoint;

    /**
     * A mixin to indicate the media-types consumed by a particular component.
     *
     * The String `mediaType` value may include wild-cards, as allowed in an "Accept" header.
     *
     * Specifying this annotation on a `WebApp` or `WebService` is a convenient way of specifying a
     * default, so that the default does not need to be specified on each `EndPoint`. When walking
     * up from an `EndPoint` to its containing `WebService` and ultimately the `WebApp` module, the
     * first `@Consumes` annotation encountered is the one used. In the absence of any `@Consumes`
     * annotation, the default is `Json`.
     *
     * Example:
     *
     *     @Patch("/{id}/items")
     *     @Consumes(Json)
     *     HttpStatus updateItem(@PathParam String id, @Body Item item) {...}
     */
    mixin Consumes(MediaType|MediaType[] consumes)
            into WebApp | WebService | Endpoint;

    /**
     * This annotation, `@LoginRequired`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as requiring authentication.
     *
     * A method (any `Endpoint`, such as `@Get` or `@Post`) that handles a web service call will
     * require authentication iff:
     *
     * * the web service method is annotated with `LoginRequired`, or
     * * the web service method is not annotated with `LoginOptional`, and the parent class requires
     *   authentication.
     *
     * A parent class will require authentication iff:
     *
     * * the class is annotated with `LoginRequired`, or
     * * the class is not annotated with `LoginOptional`, and there is a containing class, and the
     *   containing class requires authentication, or
     * * the class is a module (and thus has no containing class), and the host for the module has
     *   been configured to require authentication by default.
     *
     * The purpose of this design is to allow the use of annotations for specifying the requirement
     * for authentication, but only requiring those annotations within the class hierarchy at the
     * few points where a change occurs from "requiring authentication" to "not requiring
     * authentication", or vice versa.
     *
     * @param security  [TrustLevel] of security that is required by the annotated operation or
     *                  web service
     */
    mixin LoginRequired(TrustLevel security=Normal)
            into WebApp | WebService | Endpoint
            extends HttpsRequired;

    /**
     * This annotation, `@LoginOptional`, is used to mark a web service endpoint, or the web service
     * that contains it, up to the level of the web module itself -- as **not** requiring
     * authentication.
     *
     * For more information, see the detailed description of the [@LoginRequired](LoginRequired)
     * annotation.
     */
    mixin LoginOptional
            into WebApp | WebService | Endpoint;

    /**
     * This annotation, `@Restrict`, is used to mark a web service endpoint, or the web service
     * that contains it, up to the level of the web module itself -- as requiring a user to be
     * logged in, and for that user to meet the role requirements specified by the annotation.
     *
     * Example:
     *
     *     @Restrict("admin")
     *     void shutdown() {...}
     *
     *     @Restrict(["admin", "manager"])
     *     conditional User createUser(@PathParam String id) {...}
     */
    mixin Restrict(String|String[] subject)
            into WebApp | WebService | Endpoint
            extends LoginRequired;

    /**
     * This annotation, `@HttpsRequired`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as requiring Transport Level
     * Security (TLS), sometimes also referred to using the obsolete term "SSL".
     *
     * A method (any `Endpoint`, such as `@Get` or `@Post`) that handles a web service call will
     * require TLS iff:
     *
     * * the web service method is annotated with `HttpsRequired`, or
     * * the web service method is not annotated with `HttpsOptional`, and the parent class requires
     *   TLS.
     *
     * A parent class will require TLS iff:
     *
     * * the class is annotated with `HttpsRequired`, or
     * * the class is not annotated with `HttpsOptional`, and there is a containing class, and the
     *   containing class requires TLS, or
     * * the class is a module (and thus has no containing class), and the host for the module has
     *   been configured to require TLS by default.
     *
     * The purpose of this design is to allow the use of annotations for specifying the requirement
     * for TLS, but only requiring those annotations within the class hierarchy at the
     * few points where a change occurs from "requiring TLS" to "not requiring
     * TLS", or vice versa.
     */
    mixin HttpsRequired
            into WebApp | WebService | Endpoint;

    /**
     * This annotation, `@HttpsOptional`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as **not** requiring Transport
     * Level Security (TLS).
     *
     * For more information, see the detailed description of the [@HttpsRequired](HttpsRequired)
     * annotation.
     */
    mixin HttpsOptional
            into WebApp | WebService | Endpoint;

    /**
     * This annotation, `@StreamingRequest`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as **not** requiring the content
     * of the incoming HTTP request to be fully buffered. This may allow the web server to save
     * memory by streaming the content, instead of reading it entirely into memory before (or while)
     * processing the request.
     */
    mixin StreamingRequest
            into WebApp | WebService | Endpoint;

    /**
     * This annotation, `@StreamingResponse`, is used to mark a web service call -- or any
     * containing class thereof, up to the level of the web module itself -- as **not** requiring
     * the content of the outgoing HTTP response to be fully buffered. This allows the web server
     * implementation to save memory by streaming the content, instead of holding it entirely in
     * memory before starting to send it back to the client.
     */
    mixin StreamingResponse
            into WebApp | WebService | Endpoint;


    // ----- handler method annotations ------------------------------------------------------------

    /**
     * A generic HTTP endpoint.
     *
     * Example:
     *
     *     @Endpoint(GET, "/{id}")
     *
     * @param httpMethod  the name of the HTTP method
     * @param path        the optional path to reach this endpoint
     */
    mixin Endpoint(HttpMethod httpMethod, String path = "")
            into Method<WebService>;

    /**
     * An HTTP `GET` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Get(String path = "")
            extends Endpoint(GET, path);

    /**
     * An HTTP `POST` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Post(String path = "")
            extends Endpoint(POST, path);

    /**
     * An HTTP `PATCH` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Patch(String path = "")
            extends Endpoint(PATCH, path);

    /**
     * An HTTP `PUT` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Put(String path = "")
            extends Endpoint(PUT, path);

    /**
     * An HTTP `DELETE` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Delete(String path = "")
            extends Endpoint(DELETE, path);

    /**
     * Default route on a WebService, if no other route could be found.
     *
     * Example:
     *
     *     @Default @Get
     *     Response handleMiss() {...}
     */
    mixin Default
            into Endpoint;

    /**
     * Interceptor for all requests of a certain HttpMethod on (or under) a WebService.
     *
     * Example:
     *
     *     @Intercept(GET)
     *     Response interceptGet(Session session, Request request, Handler handle) {...}
     */
    mixin Intercept(HttpMethod? httpMethod=Null)
            into Method<WebService, <Session, Request, Handler>, <Response>>;

    /**
     * Request observer (possibly asynchronous to the request processing itself) for all requests on
     * (or under) a WebService.
     *
     * Example:
     *
     *     @Observe
     *     void logAllRequests(Session session, Request request) {...}
     *
     * And/or:
     *
     *     @Observe(DELETE)
     *     void logDeleteRequests(Session session, Request request) {...}
     */
    mixin Observe(HttpMethod? httpMethod=Null)
            into Method<WebService, <Session, Request>, <>>;

    /**
     * Specify a method as handling all errors on (or under) the WebService.
     *
     * Example:
     *
     *     @OnError
     *     void handleErrors(Session session, Request request, Exception|String) {...}
     */
    mixin OnError
            into Method<WebService, <Session?, Request, Exception|String|HttpStatus>, <Response>>;


    // ----- parameter annotations -----------------------------------------------------------------

    /**
     * A mixin to indicate that a Parameter is bound to a request value.
     */
    @Abstract mixin ParameterBinding(String? templateParameter = Null)
            into Parameter;

    /**
     * A mixin to indicate that a Parameter is bound to a request URI path segment.
     *
     * Example:
     *
     *     @Delete("/{id}")
     *     HttpStatus deleteCart(@PathParam("id") String id) {...}
     */
    mixin PathParam(String? templateParameter = Null)
            extends ParameterBinding(templateParameter);

    /**
     * A mixin to indicate that a Parameter is bound to a request URI query parameter.
     *
     * Example:
     *
     *     @Get("/")
     *     @Produces(Json)
     *     Collection<Item> getItems(@QueryParam("tags")  String? tags     = Null,
     *                               @QueryParam("order") String  order    = "price",
     *                               @QueryParam("page")  Int     pageNum  = 1,
     *                               @QueryParam("size")  Int     pageSize = 10)
     *         {...}
     */
    mixin QueryParam(String? templateParameter = Null)
            extends ParameterBinding(templateParameter);

    /**
     * A mixin to indicate that a Parameter is bound to a request header value.
     *
     * Example:
     *
     *     @Get("login")
     *     @Produces("application/json")
     *     HttpStatus login(@HeaderParam("Authorization") String auth) {...}
     */
    mixin HeaderParam(String? templateParameter = Null)
            extends ParameterBinding(templateParameter);

    /**
     * A mixin to indicate that a value is bound to a request or response body.
     *
     *     @Post("/{id}/items")
     *     @Consumes(Json)
     *     @Produces(Json)
     *     (Item, HttpStatus) addItem(@PathParam String id, @BodyParam Item item) {...}
     */
    mixin BodyParam
            extends ParameterBinding;


    // ----- exceptions ----------------------------------------------------------------------------

    /**
     * An exception used to abort request processing and return a specific HTTP error status to the
     * client that issued the request.
     *
     * Generally, request processing should complete normally, even when the result of the
     * processing is an error. This exception allows that normal completion to be bypassed, but its
     * general use is discouraged.
     */
    const RequestAborted(HttpStatus status, String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);
    }