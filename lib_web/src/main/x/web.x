/**
 * Web client and server APIs.
 *
 * TODO pre and post examples, and how to configure
 */
module web.xtclang.org {
    package aggregate   import aggregate.xtclang.org;
    package collections import collections.xtclang.org;
    package convert     import convert.xtclang.org;
    package crypto      import crypto.xtclang.org;
    package json        import json.xtclang.org;
    package net         import net.xtclang.org;
    package sec         import sec.xtclang.org;

    import ecstasy.reflect.Parameter;

    import net.HostPort;
    import net.IPAddress;
    import net.SocketAddress;

    typedef net.Uri as Uri;

    import sec.Credential;
    import sec.Entitlement;
    import sec.Principal;
    import sec.Realm;

    // ----- request/response support --------------------------------------------------------------

    /**
     * A function that can process an incoming request corresponding to a `Route` is called an
     * `Endpoint Handler`.
     */
    typedef function ResponseOut(RequestIn) as Handler;

    /**
     * A function that is called when an exception occurs (or an internal error represented by a
     * `String` description or an HttpsStatus code) is called an `ErrorHandler`.
     */
    typedef function ResponseOut(RequestIn, Exception|String|HttpStatus) as ErrorHandler;

    /**
     * `TrustLevel` is an enumeration that approximates a point-in-time level of trust associated
     * with a user request or session, or denotes a required level of trust for a specific
     * operation.
     */
    enum TrustLevel {None, Normal, High, Highest}

    // ----- WebApp annotations --------------------------------------------------------------------

    /**
     * A annotation to indicate the media-types produced by a particular component.
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
    annotation Produces(MediaType|MediaType[] produces)
            into Class<WebApp> | Class<WebService> | Endpoint;

    /**
     * A annotation to indicate the media-types consumed by a particular component.
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
     *     HttpStatus updateItem(@UriParam String id, @BodyParam Item item) {...}
     */
    annotation Consumes(MediaType|MediaType[] consumes)
            into Class<WebApp> | Class<WebService> | Endpoint;

    /**
     * This annotation, `@SessionRequired`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as requiring a [Session].
     *
     * A method (any `Endpoint`, such as `@Get` or `@Post`) that handles a web service call will
     * require a [Session] iff:
     *
     * * the web service method is annotated with `SessionRequired`, or
     * * the web service method is not annotated with `SessionOptional`, and the parent class
     *   requires a [Session].
     *
     * A parent class will require a [Session] iff:
     *
     * * the class is annotated with `SessionRequired`, or
     * * the class is not annotated with `SessionOptional`, and there is a containing class, and the
     *   containing class requires a [Session], or
     * * the class is a module (and thus has no containing class), and the host for the module has
     *   been configured to require a [Session] by default.
     *
     * The purpose of this design is to allow the use of annotations for specifying the requirement
     * for a session, but only requiring those annotations within the class hierarchy at the
     * few points where a change occurs from "requiring a session" to "not requiring a session", or
     * vice versa.
     */
    annotation SessionRequired
            into Class<WebApp> | Class<WebService> | Endpoint;

    /**
     * This annotation, `@SessionOptional`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as **not** requiring a [Session].
     *
     * For more information, see the detailed description of the [@SessionRequired](SessionRequired)
     * annotation.
     */
    annotation SessionOptional
            into Class<WebApp> | Class<WebService> | Endpoint;

    /**
     * This annotation, `@LoginRequired`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as requiring authentication.
     *
     * When this annotation is used, the endpoint is implicitly [HttpsRequired] (which this
     * annotation extends), because (i) any and all traffic that is sent over HTTP (not HTTPS)
     * **must be assumed to have been captured by a hostile entity**, and (ii) that information may
     * **immediately** be used to attack the application. Thus, access to any `@LoginRequired`
     * endpoint cannot (and **must** not) be performed over an HTTP connection. See [HttpsRequired]
     * for more information.
     *
     * When `LoginRequired` applies to an endpoint and the application is using the Ecstasy Xenia
     * web server, a security step is inserted by the `ChainBundle.generateRestrictCheck()` method
     * as part of the request routing to the endpoint. This security step first checks the user
     * [Session] for a cached grant of access, and otherwise invokes the application's
     * [security.Authenticator] to either validate the information already included in the request,
     * or to determine that some further user action is required to authenticate.
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
    annotation LoginRequired(TrustLevel security = Normal, Boolean autoRedirect = False)
            extends HttpsRequired(autoRedirect);

    /**
     * This annotation, `@LoginOptional`, is used to mark a web service endpoint, or the web service
     * that contains it, up to the level of the web module itself -- as **not** requiring
     * authentication.
     *
     * For more information, see the detailed description of the [@LoginRequired](LoginRequired)
     * annotation.
     */
    annotation LoginOptional
            into Class<WebApp> | Class<WebService> | Endpoint;

    /**
     * This annotation, `@Restrict`, is used to mark a web service endpoint, or the web service
     * that contains it, up to the level of the web module itself -- as requiring authorization by
     * meeting the permission requirements specified by the annotation.
     *
     * The annotation can imply or specify a required permission, or it can specify a method to be
     * used for authorization; for example:
     *
     *     @Restrict                    // implied @Restrict("GET:/accounts/{id}")
     *     @Get("/accounts/{id}")
     *     AccountInfo getAccount(Int id) {...}
     *
     *     @Restrict("create:/accounts")
     *     @LoginRequired
     *     @Put
     *     HttpStatus openBankAccount(@BodyParam AccountInfo info) {...}
     *
     *     @Restrict(accountAccess)     // specifies the method (below)
     *     @Post("/accounts/{from}/transfer{?to,amount}")
     *     ResponseOut transferFunds(Int from, Int to, Dec amount) {...}
     *
     *     Boolean accountAccess(RequestIn request) {...}
     */
    annotation Restrict(String?|Method<WebService, <>, <Boolean>> permission = Null,
                        Boolean autoRedirect = False)
            extends HttpsRequired(autoRedirect=autoRedirect);

    /**
     * This annotation, `@HttpsRequired`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as requiring Transport Level
     * Security (TLS), sometimes also referred to using the obsolete term "SSL".
     *
     * Traditionally, web servers and applications have automated the transition from HTTP to HTTPS
     * by using an automatic redirect, but the combination of (i) a number of real world security
     * incidents and (ii) the general move away from typing URLs into a web browser's "address bar"
     * means that most URLs now are being created and submitted (without any user awareness) by
     * Javascript application code, and thus this code should **never** be accidentally specifying
     * HTTP when HTTPS is required. In other words, developers should treat any HTTP request to an
     * HTTPS endpoint as an error, not as something to be silently swept under the rug. As a result,
     * the `autoRedirect` parameter of this annotation defaults to `False`. For more information,
     * see: [Your API Shouldn't Redirect HTTP to HTTPS](https://jviide.iki.fi/http-redirects)
     *
     * Additionally, any and all traffic that is sent over HTTP (not HTTPS) **must be assumed to
     * have been captured by a hostile entity**. When an application accidentally sends a request
     * over HTTP, any information in that request may **immediately** be used to attack the
     * application. Developers **must** ensure that the any potentially leaked information --
     * including tokens, API keys, passwords, etc. -- is **immediately** invalidated.
     *
     * When an application is using the Ecstasy Xenia web server, all authentication/authorization
     * data that arrives on an HTTP (not HTTPS) connection is automatically invalidated: The
     * `handlePlainTextSecrets()` method on the Xenia `Dispatcher` service automatically invokes
     * [security.Authenticator.findAndRevokeSecrets].
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
     * for TLS, but only requiring those annotations within the class hierarchy at the few points
     * where a change occurs from "requiring TLS" to "not requiring TLS", or vice versa.
     */
    annotation HttpsRequired(Boolean autoRedirect = False)
            into Class<WebApp> | Class<WebService> | Endpoint;

    /**
     * This annotation, `@HttpsOptional`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as **not** requiring Transport
     * Level Security (TLS).
     *
     * For more information, see the detailed description of the [@HttpsRequired](HttpsRequired)
     * annotation.
     */
    annotation HttpsOptional
            into Class<WebApp> | Class<WebService> | Endpoint;

    /**
     * This annotation, `@StreamingRequest`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as **not** requiring the content
     * of the incoming HTTP request to be fully buffered. This may allow the web server to save
     * memory by streaming the content, instead of reading it entirely into memory before (or while)
     * processing the request.
     */
    annotation StreamingRequest
            into Class<WebApp> | Class<WebService> | Endpoint;

    /**
     * This annotation, `@StreamingResponse`, is used to mark a web service endpoint as **not**
     * requiring the content of the outgoing HTTP response to be fully buffered, in order to save
     * server resources. Streaming requires the endpoint to return either a [File] or a
     * [BinaryInput]. This allows the web server implementation to save memory by streaming the
     * content, instead of holding it entirely in memory before starting to send it back to the
     * client. For example:
     *
     *     @StreamingResponse
     *     @Get("stream{/name}")
     *     File streamOut(String name) {
     *         @Inject Directory curDir;
     *         return curDir.fileFor(name);
     *     }
     */
    annotation StreamingResponse
            into Endpoint;

    // ----- handler method annotations ------------------------------------------------------------

    /**
     * A generic HTTP endpoint.
     *
     * Example:
     *
     *     @Endpoint(GET, "/{id}", v:3)
     *
     * @param httpMethod  the HTTP method
     * @param template    an optional URI Template describing a path to reach this endpoint
     * @param api         the [Version] at which this `Endpoint` was introduced, or (for `Endpoints`
     *                    that have been removed) the range of versions that the `Endpoint` _was_
     *                    part of the API; `Null` indicates that the API is not versioned or that
     *                    this `Endpoint` has been present since the original version of the API
     */
    annotation Endpoint(HttpMethod httpMethod, String template = "", Version?|Range<Version> api = Null)
            into Method<WebService>;

    /**
     * An HTTP `GET` method.
     *
     * @param template  an optional URI Template describing a path to reach this endpoint
     * @param api       the [Version] at which this `Endpoint` was introduced, or (for `Endpoints`
     *                  that have been removed) the range of versions that the `Endpoint` _was_
     *                  part of the API; `Null` indicates that the API is not versioned or that
     *                  this `Endpoint` has been present since the original version of the API
     */
    annotation Get(String template = "", Version?|Range<Version> api = Null)
            extends Endpoint(GET, template, api);

    /**
     * An HTTP `POST` method.
     *
     * @param template  an optional URI Template describing a path to reach this endpoint
     * @param api       the [Version] at which this `Endpoint` was introduced, or (for `Endpoints`
     *                  that have been removed) the range of versions that the `Endpoint` _was_
     *                  part of the API; `Null` indicates that the API is not versioned or that
     *                  this `Endpoint` has been present since the original version of the API
     */
    annotation Post(String template = "", Version?|Range<Version> api = Null)
            extends Endpoint(POST, template, api);

    /**
     * An HTTP `PATCH` method.
     *
     * @param template  an optional URI Template describing a path to reach this endpoint
     * @param api       the [Version] at which this `Endpoint` was introduced, or (for `Endpoints`
     *                  that have been removed) the range of versions that the `Endpoint` _was_
     *                  part of the API; `Null` indicates that the API is not versioned or that
     *                  this `Endpoint` has been present since the original version of the API
     */
    annotation Patch(String template = "", Version?|Range<Version> api = Null)
            extends Endpoint(PATCH, template, api);

    /**
     * An HTTP `PUT` method.
     *
     * @param template  an optional URI Template describing a path to reach this endpoint
     * @param api       the [Version] at which this `Endpoint` was introduced, or (for `Endpoints`
     *                  that have been removed) the range of versions that the `Endpoint` _was_
     *                  part of the API; `Null` indicates that the API is not versioned or that
     *                  this `Endpoint` has been present since the original version of the API
     */
    annotation Put(String template = "", Version?|Range<Version> api = Null)
            extends Endpoint(PUT, template, api);

    /**
     * An HTTP `DELETE` method.
     *
     * @param template  an optional URI Template describing a path to reach this endpoint
     * @param api       the [Version] at which this `Endpoint` was introduced, or (for `Endpoints`
     *                  that have been removed) the range of versions that the `Endpoint` _was_
     *                  part of the API; `Null` indicates that the API is not versioned or that
     *                  this `Endpoint` has been present since the original version of the API
     */
    annotation Delete(String template = "", Version?|Range<Version> api = Null)
            extends Endpoint(DELETE, template, api);

    /**
     * An HTTP `OPTIONS` method.
     *
     * @param template  an optional URI Template describing a path to reach this endpoint
     * @param api       the [Version] at which this `Endpoint` was introduced, or (for `Endpoints`
     *                  that have been removed) the range of versions that the `Endpoint` _was_
     *                  part of the API; `Null` indicates that the API is not versioned or that
     *                  this `Endpoint` has been present since the original version of the API
     */
    annotation Options(String template = "", Version?|Range<Version> api = Null)
            extends Endpoint(OPTIONS, template, api);

    /**
     * Default route on a WebService, if no other route could be found. At the moment, it only
     * applies to `@Get` endpoints.
     *
     * Example:
     *
     *     @Default @Get
     *     ResponseOut handleMiss() {...}
     */
    annotation Default
            into Endpoint;

    /**
     * Interceptor for all requests of a certain HttpMethod on (or under) a WebService.
     *
     * Example:
     *
     *     @Intercept(GET)
     *     ResponseOut interceptGet(RequestIn request, Handler handle) {...}
     */
    annotation Intercept(HttpMethod? httpMethod = Null)
            into Method<WebService, <RequestIn, Handler>, <ResponseOut>>;

    /**
     * Request observer (possibly asynchronous to the request processing itself) for all requests on
     * (or under) a WebService.
     *
     * Example:
     *
     *     @Observe
     *     void logAllRequests(RequestIn request) {...}
     *
     * And/or:
     *
     *     @Observe(DELETE)
     *     void logDeleteRequests(RequestIn request) {...}
     */
    annotation Observe(HttpMethod? httpMethod = Null)
            into Method<WebService, <RequestIn>, <>>;

    /**
     * Specify a method as handling all errors on (or under) the WebService.
     *
     * Example:
     *
     *     @OnError
     *     ResponseOut handleErrors(RequestIn request, Exception|String) {...}
     */
    annotation OnError
            into Method<WebService, <RequestIn, Exception|String|HttpStatus>, <ResponseOut>>;

    // ----- parameter annotations -----------------------------------------------------------------

    /**
     * A annotation to indicate that a Parameter is bound to a request value.
     *
     * @param bindName  indicates the source of the value that will be bound to the parameter
     * @param format    indicates the textual format that the parameter will
     */
    @Abstract annotation ParameterBinding(String? bindName = Null,
                                          String? format   = Null)
            into Parameter;

    /**
     * A annotation to indicate that a Parameter is bound to a request URI path segment.
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
    annotation UriParam(String? bindName = Null,
                        String? format   = Null)
            extends ParameterBinding(bindName, format);

    /**
     * A annotation to indicate that a Parameter is bound to a request URI query parameter.
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
    annotation QueryParam(String? bindName = Null,
                          String? format   = Null)
            extends ParameterBinding(bindName, format);

    /**
     * A annotation to indicate that a Parameter is bound to a request header value.
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
    annotation HeaderParam(String? bindName = Null,
                           String? format   = Null)
            extends ParameterBinding(bindName, format);

    /**
     * A annotation to indicate that a Parameter is bound to a named cookie value.
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
    annotation CookieParam(String? bindName = Null,
                           String? format   = Null)
            extends ParameterBinding(bindName, format);

    /**
     * A annotation to indicate that a value is bound to a request or response body.
     *
     *     @Post("/{id}/items")
     *     @Consumes(Json)
     *     @Produces(Json)
     *     (Item, HttpStatus) addItem(@UriParam String id, @BodyParam Item item) {...}
     *
     * @param format  allows an explicit text [Format] to be specified for instances when the body
     *                is a text-based [MediaType] that does not imply the necessary `Format`
     */
    annotation BodyParam(String? format = Null)
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
            extends Exception(text, cause) {

        /**
         * Create a [response](ResponseOut) based on the exception information.
         */
        ResponseOut makeResponse() {
            import responses.SimpleResponse;
            return text == Null
                    ? new SimpleResponse(status)
                    : new SimpleResponse(status, text);
        }
    }
}