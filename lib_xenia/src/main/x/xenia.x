/**
 * The Web Server implementation.
 */
module xenia.xtclang.org
    {
    // external module dependencies
    package aggregate   import aggregate.xtclang.org;
    package collections import collections.xtclang.org;
    package crypto      import crypto.xtclang.org;
    package net         import net.xtclang.org;
    package json        import json.xtclang.org;
    package web         import web.xtclang.org;

    import crypto.KeyStore;

    import net.IPAddress;
    import net.SocketAddress;
    import net.Uri;

    import web.Handler;
    import web.ResponseOut;
    import web.RequestIn;
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
     *     ResponseOut intercept(Handler handle, RequestIn request)
     *         {
     *         // pre-processing here
     *         // ...
     *
     *         // pass the flow of control to the handler (passing either the original Request
     *         // object, or one that this method chooses to substitute for the original)
     *         ResponseOut response = handle(request);
     *
     *         // post-processing here
     *         // ...
     *
     *         // return the response (returning either the original response from the Handler,
     *         // or one that this method chooses to substitute for the original)
     *         return response;
     *         }
     */
    typedef function ResponseOut(Session, RequestIn, Handler) as Interceptor;

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
    typedef function void(Session, RequestIn) as Observer;

    /**
     * A function that adds a parameter value to the passed-in tuple of values. Used to collect
     * arguments for the endpoint method invocation.
     */
    typedef function Tuple(Session, RequestIn, Tuple) as ParameterBinder;

    /**
     * A function that converts a result of the endpoint method invocation into a ResponseOut object.
     */
    typedef function ResponseOut(RequestIn, Tuple) as Responder;

    /**
     * Create and start an HTTP/HTTPS server for the specified web application.
     *
     * @param webApp     the WebApp to dispatch the HTTP requests to
     * @param hostName   the server host name (e.g. "localhost")
     * @param keystore   the keystore to use for tls certificates and encryption
     * @param httpPort   the port for plain text (insecure) communications
     * @param httpsPort  the port for encrypted (tls) communications
     *
     * @return a function that allows to shutdown the server
     */
    function void () createServer(WebApp webApp, String hostName, KeyStore keystore,
                                  UInt16 httpPort = 80, UInt16 httpsPort = 443)
        {
        @Inject HttpServer server;

        server.configure(hostName, keystore, httpPort, httpsPort);

        HttpHandler handler = new HttpHandler(server, webApp);

        server.start(handler);

        return () -> handler.shutdown();
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * TODO this is temporary, but we do need a permanent way to log
     */
    static void log(String text)
        {
        @Inject Console console;
        console.print($"**Log: {text}");
        }

    /**
     * Obtain the user agent string.
     */
    static String extractUserAgent(HttpServer.RequestInfo requestInfo)
        {
        if (String[] values := requestInfo.getHeaderValuesForName(web.Header.UserAgent))
            {
            return values[0];
            }
        return "";
        }
    }