/**
 * The Web Server implementation.
 */
module xenia.xtclang.org {
    package aggregate   import aggregate.xtclang.org;
    package collections import collections.xtclang.org;
    package crypto      import crypto.xtclang.org;
    package net         import net.xtclang.org;
    package json        import json.xtclang.org;
    package web         import web.xtclang.org;

    import crypto.CertificateManager;
    import crypto.KeyStore;

    import net.IPAddress;
    import net.SocketAddress;
    import net.Uri;

    import web.Handler;
    import web.ResponseOut;
    import web.RequestIn;
    import web.Session;
    import web.WebApp;
    import web.WebService;

    import web.http.HostInfo;

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
     *     ResponseOut intercept(Handler handle, RequestIn request) {
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
     *     }
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
     * Default HostInfo for an [HttpServer] request routing.
     */
    static HostInfo DefaultHost = new HostInfo("localhost");

    /**
     * Default HostInfo for an [HttpServer] binding.
     */
    static HostInfo DefaultBind = new HostInfo(IPAddress.IPv4Any);

    /**
     * Create and start an HTTP/HTTPS server for the specified web application. If a `keystore` is
     * not specified, a temporary one will be created with a self-signed certificate.
     *
     * @param webApp     the WebApp to dispatch the HTTP requests to
     * @param route      (optional) the HostInfo for request routing
     * @param binding    (optional) the HostInfo for server binding; defaults to the `route`
     * @param keystore   (optional) the keystore to use for tls certificates and encryption
     * @param tlsKey     (optional) the name of the key pair in the keystore to use for Tls; if not
     *                   specified, the first key pair will be used
     * @param extras     (optional) a map of WebService classes for processing requests for the
     *                   corresponding paths (see [HttpHandler])
     *
     * @return a function that allows to shutdown the server
     */
    function void () createServer(WebApp webApp,
                                  HostInfo route = DefaultHost, HostInfo? binding = Null,
                                  KeyStore? keystore = Null, String? tlsKey = Null,
                                  Map<Class<WebService>, WebService.Constructor> extras = []) {
        @Inject HttpServer server;
        binding ?:= route;
        try {
            server.bind(binding);

            EnsureKeystore:
            if (keystore == Null) {
                @Inject Directory tmpDir;

                String appName   = $"{webApp.qualifiedName}";
                String storeName = $"{appName}.p12";
                File   storeFile = tmpDir.fileFor(storeName);
                if (storeFile.exists) {
                    try {
                        @Inject(resourceName="keystore",
                                opts=new KeyStore.Info(storeFile.contents, "password")) KeyStore store;
                        keystore = store;
                        break EnsureKeystore;
                    } catch (Exception ignore) {}
                }
                // create a new one
                @Inject CertificateManager manager;
                String dName = CertificateManager.distinguishedName(route.host.toString(),
                                    org="xenia.xtclang.org", orgUnit=appName);

                manager.createCertificate(storeFile, "password", "certificate", dName);
                manager.createSymmetricKey(storeFile, "password", "cookies");

                @Inject(resourceName="keystore",
                        opts=new KeyStore.Info(storeFile.contents, "password")) KeyStore store;
                keystore = store;
                }

            HttpHandler handler = new HttpHandler(route, webApp, extras);
            server.addRoute(route, handler, keystore, tlsKey);

            return () -> {
                if (handler.shutdown()) {
                    server.close();
                } else {
                    // wait a second (TODO: repeat a couple of times)
                    @Inject Timer timer;
                    timer.schedule(Second, server.close);
                }};
            }
        catch (Exception e) {
            server.close(e);
            throw e;
        }
    }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * TODO this is temporary, but we do need a permanent way to log
     */
    static void log(String text) {
        @Inject Console console;
        console.print($"**Log: {text}");
    }
}