/**
 * The Web Server implementation.
 */
module xenia.xtclang.org
    {
    // external module dependencies
    package aggregate   import aggregate.xtclang.org;
    package collections import collections.xtclang.org;
    package net         import net.xtclang.org;
    package json        import json.xtclang.org;
    package web         import web.xtclang.org;

    import net.HostPort;
    import net.IPAddress;
    import net.SocketAddress;
    import net.Uri;

    import web.Handler;
    import web.Response;
    import web.Request;
    import web.Session;
    import web.WebApp;

    /**
     * The clock used within this module.
     */
    @Inject Clock clock;

    /**
     * The random number generator used within this module.
     */
    @Inject Random rnd;

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
     * A function that adds a parameter value to the passed-in tuple of values. Used to collect
     * arguments for the endpoint method invocation.
     */
    typedef function Tuple(Session, Request, Tuple) as ParameterBinder;

    /**
     * A function that converts a result of the endpoint method invocation into a Response object.
     */
    typedef function Response(Request, Tuple) as Responder;

    /**
     * Create and start an HTTP/HTTPS server for the specified web application.
     *
     * @param webApp     the WebApp to dispatch the HTTP requests to
     * @param hostName   the server host name (e.g. "localhost")
     * @param keyStore   the keystore to use for tls certificates and encryption
     * @param password   the keystore password
     * @param httpPort   the port for plain text (insecure) communications
     * @param httpsPort  the port for encrypted (tls) communications
     *
     * @return a function that allows to shutdown the server
     */
    function void () createServer(WebApp webApp, String hostName,
                                  File|Byte[] keyStore, String password,
                                  UInt16 httpPort = 80, UInt16 httpsPort = 443)
        {
        @Inject HttpServer server;

        Byte[] keys = keyStore.is(File) ? keyStore.contents : keyStore;
        server.configure(hostName, keys, password, httpPort, httpsPort);

        HttpHandler handler = new HttpHandler(server, webApp);

        server.start(handler);

        return () -> handler.shutdown();
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Obtain the user agent string.
     */
    static String extractUserAgent(HttpServer.RequestInfo requestInfo)
        {
        if (String[] values := requestInfo.getHeaderValuesForName(web.Header.USER_AGENT))
            {
            return values[0];
            }
        return "";
        }
    }