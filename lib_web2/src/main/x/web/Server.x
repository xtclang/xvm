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
    typedef function Response(Request) as Handler;

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
    typedef function Response(Handler, Request) as Interceptor;

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
    typedef function void(Request) as Observer;

    /**
     * A function that is called when an exception occurs (or an internal error represented by a
     * `String` description) is called an `ErrorHandler`.
     */
    typedef function Response(Request, Response?, Exception|String) as ErrorHandler;


    // ----- factory (concurrency) support ---------------------------------------------------------

    /**
     * A means to produce multiple WebService instances, so that the server can concurrently execute
     * handlers for multiple incoming requests.
     */
    typedef function WebService() as WebServiceFactory;

    /**
     * A means to produce Handlers tied to specific WebService instances.
     */
    typedef function Handler(WebService) as HandlerFactory;

    /**
     * A means to produce multiple [Interceptor] handlers, to allow the server to increase concurrent
     * request handling execution.
     */
    typedef function Interceptor(WebService) as InterceptorFactory;

    /**
     * A means to produce multiple [Observer] handlers, to allow the server to increase concurrent
     * request handling execution.
     */
    typedef function Observer(WebService) as ObserverFactory;

    /**
     * A factory for one of the three routing types.
     */
    typedef HandlerFactory | InterceptorFactory | ObserverFactory as RoutingFactory;


    // ----- route registration --------------------------------------------------------------------

    /**
     * Registers the specified `RoutingFactory` for the specified `Route`.
     *
     * * If registering a `HandlerFactory` for an `Endpoint`, in cases of conflict, the last
     *   registered handler wins.
     *
     * * If registering an `InterceptorFactory`, the server will delegate each matching incoming
     *   request to the interceptor, with the order of registration being significant, with the most
     *   recently added `Interceptor` executed first.
     *
     * * If registering an `ObserverFactory`, the server will dispatch each matching incoming
     *   request, but: (1) The order in which [Observer]s are added is not significant, and the
     *   `Observer` operations are not guaranteed to run in any specific order, and may be executed
     *   before, during (concurrently), or even after the actual request processing.
     *
     * @param webServiceFactory  the means to produce a new instance of a WebService; the identity
     *                           of this factory function is assumed to identify the WebService
     *                           class (from a uniqueness point of view)
     * @param route              the URI template that indicates the requests that can be handled
     * @param routingFactory     the means to produce handlers, interceptors, or observers that can
     *                           handle, intercept, or observe URIs that match the specified URI
     *                           template
     */
    void route(WebServiceFactory webServiceFactory,
               Route             route,
               RoutingFactory    routingFactory);

    /**
     * Register the specified `HandlerFactory` for all unhandled URIs. If no `Handler` is provided
     * as a default handler, then one will be provided. If more than one `Handler` is provided as a
     * default handler, then the last one registered will be used.
     *
     * @param webServiceFactory  the means to produce a new instance of a WebService; the identity
     *                           of this factory function is assumed to identify the WebService
     *                           class (from a uniqueness point of view)
     * @param routingFactory     the means to produce handlers, interceptors, or observers that can
     *                           respond to any URI not handled by a route registered via
     *                           [addEndpoint]
     */
    void routeDefault(WebServiceFactory webServiceFactory,
                      RoutingFactory    routingFactory);

    /**
     * Register the specified `HandlerFactory` for all unhandled URIs. If no `ErrorHandler` is
     * provided, then one will be provided. More than one `ErrorHandler` can be registered, with the
     * last one registered being the last to execute.
     *
     * @param webServiceFactory  the means to produce a new instance of a WebService; the identity
     *                           of this factory function is assumed to identify the WebService
     *                           class (from a uniqueness point of view)
     * @param handlerFactory     the means to produce error handlers that can respond to any request
     *                           whose processing has failed and resulted in an exception or other
     *                           error
     */
    void routeErrors(WebServiceFactory webServiceFactory,
                     ErrorHandler      handlerFactory);
    }
