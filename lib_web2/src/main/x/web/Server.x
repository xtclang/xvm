import routing.UriTemplate;

/**
 * A representation of an HTTP server.
 */
interface Server
    {
    /**
     * A route describes a means of matching against a request URI, using the [URI Template
     * specification (RFC6570)](https://tools.ietf.org/html/rfc6570).
     */
    typedef UriTemplate | String as Route;

    /**
     * A function that can process an incoming request corresponding to a `Route` is called an
     * `Endpoint Handler`.
     */
    typedef function Response(Session, Request) as Handler;

    /**
     * A function that is able to both pre- **and** post-process a request is called an
     * `Interceptor`. Conceptually, its form is something like:
     *
     *     Response intercept(Handler handle, Request request)
     *         {
     *         // pre-processing here
     *         // ...
     *
     *         // pass the flow of control to the handler (passing either the original Request
     *         // object, or one that this method chooses to substitute for the original)
     *         Response response = handle(request);
     *
     *         // post-processing here
     *         // ...
     *
     *         // return the response (returning either the original Response from the Handler,
     *         // or one that this method chooses to substitute for the original)
     *         return response;
     *         }
     */
    typedef function Response(Session, Request, Handler) as Interceptor;

    /**
     * A function that is called with each incoming request is called an `Observer`. Despite the
     * name, the `Observer` operations are not guaranteed to be run before the [Handler] and/or any
     * [Interceptor]s for the request.
     *
     * The `Observer` capability is useful for implementing simple capabilities, such as logging
     * each request, but the functionality is limited, and the `Observer` cannot change the Request
     * or alter the request processing control flow. For purposes of request processing, exceptions
     * from the `Observer` are ignored, including if the `Observer` throws a [RequestAborted].
     */
    typedef function void(Session, Request) as Observer;

    /**
     * A function that is called when an exception occurs (or an internal error represented by a
     * `String` description) is called an `ErrorHandler`. A Response may or may not already be known
     * at the point that the error occurs.
     */
    typedef function Response(Session, Request, Exception|String, Response?) as ErrorHandler;


    // ----- route registration --------------------------------------------------------------------

    /**
     * Register the specified `WebService` method to handle the specified `Route`. It is expected
     * that an implementation of `Server` will instantiate one or more instances of the `WebService`
     * as necessary to handle the incoming requests.
     *
     * Note that the handler being registered does not have a predefined set of parameters and
     * return values. It is the server's job to create the necessary wiring to the method, based on
     * the routing rules and capabilities defined by this web API, and utilizing any available
     * codecs as necessary.
     *
     * In case of registration overlap or conflict, the last registered handler wins.
     *
     * @param httpMethod  one of `GET`, `POST`, `PUT`, `DELETE`, etc.
     * @param path        a fixed path; for example, the path that maps to the web service
     * @param route       a URI Template to evaluate against the remaining portion of the path
     * @param handler     a method on a [WebService] to invoke to handle the specified URI route
     */
    void route(HttpMethod httpMethod, Path path, Route? route, Method<WebService> handler);

    /**
     * Register the specified `Interceptor` for the specified `Route`.
     *
     * The server will delegate each matching incoming request to the interceptor, with the order of
     * registration being significant, with the most recently added `Interceptor` executed first.
     *
     * @param interceptor  a method on a [WebService] to invoke to intercept incoming requests
     */
    void intercept(Method<WebService, <Session, Request, Handler>, <Response>> interceptor);

    /**
     * Register the specified `Handler` for the specified `Route`.
     *
     * The server will dispatch each matching incoming request to the observer, but:
     *
     * * The order in which [Observer]s are added is not significant; and
     * * `Observer` operations are not guaranteed to run in any specific order, and may be executed
     *   before, during (i.e. concurrently with), or even after the actual request processing.
     *
     * @param observer  a method on a [WebService] to invoke to observe incoming requests
     */
    void observe(Method<WebService, <Session, Request>, <>> observer);

    /**
     * Register a handler for all unhandled (unregistered route) URIs. If no `Handler` is provided
     * as a default handler, then one will be provided, responding with
     * [404 - NotFound](HttpStatus.NotFound). If more than one `Handler` is registered,  then the
     * last one registered will be used.
     *
     * @param handler  a method on a [WebService] to invoke to handle any unknown-route requests
     */
    void routeDefault(Method<WebService, <Session, Request>, <Response>> handler);

    /**
     * Register the specified method to handle all errors, either `Exception` or otherwise. If no
     * `ErrorHandler` gets registered, then a default error handler that produces a status of server
     * failure [500 - InternalServerError](HttpStatus.InternalServerError) will be provided. More
     * than one `ErrorHandler` can be registered, with the last one registered being the last to
     * execute (and thus the one that determines the final `Response`.)
     *
     * @param handler  a method on a [WebService] to invoke to handle any failed requests
     */
    void routeErrors(Method<WebService, <Session, Request, Exception|String, Response?>, <Response>> handler);
    }
