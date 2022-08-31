/**
 * The Web Server implementation.
 */
module xenia.xtclang.org
    {
    // external module dependencies
    package aggregate   import aggregate.xtclang.org;
    package collections import collections.xtclang.org;
    package net         import net.xtclang.org;
    package web         import web.xtclang.org;

    import web.Handler;
    import web.Response;
    import web.Request;
    import web.Session;
    import web.WebApp;

    import web.routing.Catalog;
    import web.routing.UriTemplate;

    /**
     * The clock used within this module.
     */
    @Inject Clock clock;

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
     * TODO a function that adds a parameter value to the passed-in tuple of values
     *      (session, request, values)
     */
    typedef function Tuple(Session, Request, Tuple) as ParameterBinder;

    /**
     * TODO (results)
     */
    typedef function Response(Request, Tuple) as Responder;

    /**
     * An opaque native immutable object that represents an http request.
     */
    typedef immutable Object as RequestContext;

    /**
     * TODO
     */
    void createServer(String address, WebApp app)
        {
        @Inject(opts=address) HttpServer server;

        Catalog     catalog = app.catalog_;
        HttpHandler handler = new HttpHandler(server, catalog);

        TODO what else?
        }
    }