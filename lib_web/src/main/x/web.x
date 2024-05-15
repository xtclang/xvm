/**
 * Web client and server APIs.
 *
 * TODO pre and post examples, and how to configure
 */
module web.xtclang.org {
    package aggregate   import aggregate.xtclang.org;
    package collections import collections.xtclang.org;
    package crypto      import crypto.xtclang.org;
    package json        import json.xtclang.org;
    package net         import net.xtclang.org;

    import ecstasy.reflect.Parameter;

    import net.HostPort;
    import net.IPAddress;
    import net.SocketAddress;
    import net.Uri;


    // ----- request/response support --------------------------------------------------------------

    /**
     * A function that can process an incoming request corresponding to a `Route` is called an
     * `Endpoint Handler`.
     */
    typedef function ResponseOut(Session, RequestIn) as Handler;

    /**
     * A function that is called when an exception occurs (or an internal error represented by a
     * `String` description or an HttpsStatus code) is called an `ErrorHandler`.
     */
    typedef function ResponseOut(Session, RequestIn, Exception|String|HttpStatus) as ErrorHandler;

    /**
     * `TrustLevel` is an enumeration that approximates a point-in-time level of trust associated
     * with a user request or session, or denotes a required level of trust for a specific
     * operation.
     */
    enum TrustLevel {None, Normal, High, Highest}


    // ----- WebApp mixins -------------------------------------------------------------------------

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
     *     conditional Cart getCart(@UriParam("id") String id, Session session) {...}
     */
    mixin Produces(MediaType|MediaType[] produces)
            into Class<WebApp> | Class<WebService> | Endpoint;

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
     *     HttpStatus updateItem(@UriParam String id, @Body Item item) {...}
     */
    mixin Consumes(MediaType|MediaType[] consumes)
            into Class<WebApp> | Class<WebService> | Endpoint;

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
            into Class<WebApp> | Class<WebService> | Endpoint;

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
     *     conditional User createUser(@UriParam String id) {...}
     */
    mixin Restrict(String|String[] subject, TrustLevel security=Normal)
            extends LoginRequired(security);

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
     * few points where a change occurs from "requiring TLS" to "not requiring TLS", or vice versa.
     */
    mixin HttpsRequired
            into Class<WebApp> | Class<WebService> | Endpoint;

    /**
     * This annotation, `@HttpsOptional`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as **not** requiring Transport
     * Level Security (TLS).
     *
     * For more information, see the detailed description of the [@HttpsRequired](HttpsRequired)
     * annotation.
     */
    mixin HttpsOptional
            into Class<WebApp> | Class<WebService> | Endpoint;

    /**
     * This annotation, `@StreamingRequest`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as **not** requiring the content
     * of the incoming HTTP request to be fully buffered. This may allow the web server to save
     * memory by streaming the content, instead of reading it entirely into memory before (or while)
     * processing the request.
     */
    mixin StreamingRequest
            into Class<WebApp> | Class<WebService> | Endpoint;

    /**
     * This annotation, `@StreamingResponse`, is used to mark a web service call -- or any
     * containing class thereof, up to the level of the web module itself -- as **not** requiring
     * the content of the outgoing HTTP response to be fully buffered. This allows the web server
     * implementation to save memory by streaming the content, instead of holding it entirely in
     * memory before starting to send it back to the client.
     */
    mixin StreamingResponse
            into Class<WebApp> | Class<WebService> | Endpoint;


    // ----- handler method annotations ------------------------------------------------------------

    /**
     * A generic HTTP endpoint.
     *
     * Example:
     *
     *     @Endpoint(GET, "/{id}")
     *
     * @param httpMethod  the name of the HTTP method
     * @param template    an optional URI Template describing a path to reach this endpoint
     */
    mixin Endpoint(HttpMethod httpMethod, String template = "")
            into Method<WebService>;

    /**
     * An HTTP `GET` method.
     *
     * @param template  an optional URI Template describing a path to reach this endpoint
     */
    mixin Get(String template = "")
            extends Endpoint(GET, template);

    /**
     * An HTTP `POST` method.
     *
     * @param template  an optional URI Template describing a path to reach this endpoint
     */
    mixin Post(String template = "")
            extends Endpoint(POST, template);

    /**
     * An HTTP `PATCH` method.
     *
     * @param template  an optional URI Template describing a path to reach this endpoint
     */
    mixin Patch(String template = "")
            extends Endpoint(PATCH, template);

    /**
     * An HTTP `PUT` method.
     *
     * @param template  an optional URI Template describing a path to reach this endpoint
     */
    mixin Put(String template = "")
            extends Endpoint(PUT, template);

    /**
     * An HTTP `DELETE` method.
     *
     * @param template  an optional URI Template describing a path to reach this endpoint
     */
    mixin Delete(String template = "")
            extends Endpoint(DELETE, template);

    /**
     * Default route on a WebService, if no other route could be found. At the moment, it only
     * applies to `@Get` endpoints.
     *
     * Example:
     *
     *     @Default @Get
     *     ResponseOut handleMiss() {...}
     */
    mixin Default
            into Endpoint;

    /**
     * Interceptor for all requests of a certain HttpMethod on (or under) a WebService.
     *
     * Example:
     *
     *     @Intercept(GET)
     *     ResponseOut interceptGet(Session session, RequestIn request, Handler handle) {...}
     */
    mixin Intercept(HttpMethod? httpMethod=Null)
            into Method<WebService, <Session, RequestIn, Handler>, <ResponseOut>>;

    /**
     * Request observer (possibly asynchronous to the request processing itself) for all requests on
     * (or under) a WebService.
     *
     * Example:
     *
     *     @Observe
     *     void logAllRequests(Session session, RequestIn request) {...}
     *
     * And/or:
     *
     *     @Observe(DELETE)
     *     void logDeleteRequests(Session session, RequestIn request) {...}
     */
    mixin Observe(HttpMethod? httpMethod=Null)
            into Method<WebService, <Session, RequestIn>, <>>;

    /**
     * Specify a method as handling all errors on (or under) the WebService.
     *
     * Example:
     *
     *     @OnError
     *     void handleErrors(Session session, RequestIn request, Exception|String) {...}
     */
    mixin OnError
            into Method<WebService, <Session?, RequestIn, Exception|String|HttpStatus>, <ResponseOut>>;


    // ----- parameter annotations -----------------------------------------------------------------

    /**
     * A mixin to indicate that a Parameter is bound to a request value.
     *
     * @param bindName  indicates the source of the value that will be bound to the parameter
     * @param format    indicates the textual format that the parameter will
     */
    @Abstract mixin ParameterBinding(String? bindName = Null,
                                     String? format   = Null)
            into Parameter;

    /**
     * A mixin to indicate that a Parameter is bound to a request URI path segment.
     *
     * Example:
     *
     *     @Delete("/{id}")
     *     HttpStatus deleteCart(@UriParam("id") String id) {...}
     *
     * Since the `bindName` defaults to the parameter name, the above could be simplified
     * to the following:
     *
     *     @Delete("/{id}")
     *     HttpStatus deleteCart(@UriParam String id) {...}
     *
     * @param bindName  indicates the matched `{name}` from the [UriTemplate] matching process
     * @param format    allows a textual format to be specified if the default format assumption is
     *                  incorrect, or if the default format cannot be determined
     */
    mixin UriParam(String? bindName = Null,
                   String? format   = Null)
            extends ParameterBinding(bindName, format);

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
     *
     * Since the `bindName` defaults to the parameter name, and since `Json` is the
     * implicit production, the above could be simplified to the following:
     *
     *     @Get("/")
     *     Collection<Item> getItems(@QueryParam          String? tags     = Null,
     *                               @QueryParam          String  order    = "price",
     *                               @QueryParam("page")  Int     pageNum  = 1,
     *                               @QueryParam("size")  Int     pageSize = 10)
     *         {...}
     *
     * @param bindName  the query parameter name
     * @param format    allows a textual format to be specified if the default format assumption is
     *                  incorrect, or if the default format cannot be determined
     */
    mixin QueryParam(String? bindName = Null,
                     String? format   = Null)
            extends ParameterBinding(bindName, format);

    /**
     * A mixin to indicate that a Parameter is bound to a request header value.
     *
     * Example:
     *
     *     @Get("login")
     *     @Produces("application/json")
     *     HttpStatus login(@HeaderParam("Authorization") String auth) {...}
     *
     * @param bindName  the header entry name
     * @param format    allows a textual format to be specified if the default format assumption is
     *                  incorrect, or if the default format cannot be determined
     */
    mixin HeaderParam(String? bindName = Null,
                      String? format   = Null)
            extends ParameterBinding(bindName, format);

    /**
     * A mixin to indicate that a Parameter is bound to a named cookie value.
     *
     * Example:
     *
     *     @Get("/orderDate")
     *     conditional String getOrderDate(@QueryParam  String id,
     *                                     @CookieParam String dateFormat="YYYY-MM-DD")
     *         {...}
     *
     * @param bindName  the cookie name
     * @param format    allows a textual format to be specified if the default format assumption is
     *                  incorrect, or if the default format cannot be determined
     */
    mixin CookieParam(String? bindName = Null,
                      String? format   = Null)
            extends ParameterBinding(bindName, format);

    /**
     * A mixin to indicate that a value is bound to a request or response body.
     *
     *     @Post("/{id}/items")
     *     @Consumes(Json)
     *     @Produces(Json)
     *     (Item, HttpStatus) addItem(@UriParam String id, @BodyParam Item item) {...}
     *
     * @param format  allows an explicit text [Format] to be specified for instances when the body
     *                is a text-based [MediaType] that does not imply the necessary `Format`
     */
    mixin BodyParam(String? format = Null)
            extends ParameterBinding(format=format);


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