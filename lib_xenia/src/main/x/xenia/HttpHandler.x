import crypto.Decryptor;

import web.HttpStatus;

import web.http.HostInfo;

import web.codecs.Registry;

import web.security.Authenticator;

import HttpServer.Handler;
import HttpServer.RequestInfo;


/**
 * An low-level HTTP request entry point.
 */
@Concurrent
service HttpHandler
        implements Handler {
    /**
     * Construct an HttpHandler that provides the specified web application as the implementation of
     * the specified host.
     *
     * @param host  the HostInfo that routes to this handler
     * @param app   the `WebApp` application to route to
     */
    construct(HostInfo host, WebApp app) {
        Catalog catalog = buildCatalog(app);

        this.host           = host;
        this.catalog        = catalog;
        this.dispatchers    = new Dispatcher[];
        this.busy           = new Boolean[];
        this.bundlePool     = new BundlePool(catalog);
        this.sessionManager = createSessionManager(host, catalog);
        this.authenticator  = app.authenticator;

        Registry registry = app.registry_;
        registry.registerResource("sessionManager", this.sessionManager);
        registry.registerResource("catalog"       , this.catalog);
    }

    /**
     * The information about the host that this handler is servicing.
     */
    protected HostInfo host;

    /**
     * The Catalog.
     */
    protected Catalog catalog;

    /**
     * The dispatchers.
     */
    protected Dispatcher[] dispatchers;

    /**
     * The dispatchers state.
     */
    protected Boolean[] busy;

    /**
     * The ChainBundle pool.
     */
    protected BundlePool bundlePool;

    /**
     * The session manager.
     */
    protected SessionManager sessionManager;

    /**
     * The [WebApp]'s [Authenticator].
     */
    protected Authenticator authenticator;

    /**
     * Closing flag.
     */
    Boolean closing;

    /**
     * The max number of dispatchers.
     */
    Int maxCount = 32;

    /**
     * The total number of pending requests.
     */
    Int pendingRequests.get() {
        return dispatchers.map(Dispatcher.pendingRequests).reduce(new aggregate.Sum<Int>());
    }


    // ----- Handler API ---------------------------------------------------------------------------

    @Override
    void handle(RequestInfo request) {
        if (closing) {
            request.respond(HttpStatus.Gone.code, [], [], []);
            return;
        }

        Int   index  = ensureDispatcher();
        Tuple result = dispatchers[index].dispatch^(request);
        &result.whenComplete((response, e) -> {
            if (e != Null) {
                // TODO GG: remove
                @Inject Console console;
                console.print($"HttpHandler: unhandled exception for {request.uriString.quoted()}: {e}");

                request.respond(HttpStatus.InternalServerError.code, [], [], []);
            }
            busy[index] = False;
        });
    }


    // ----- HttpHandler specific methods ----------------------------------------------------------

    /**
     * Configure the handler.
     *
     * @param decryptor  a [Decryptor] that can be used to encrypt/decrypt application specific
     *                   information (e.g. cookies)
     */
    void configure(Decryptor decryptor) {
        sessionManager.configureEncryption(decryptor);
    }

    /**
     * Shutdown this HttpHandler; it will stop accepting any new requests.
     *
     * @return False iff there any pending requests
     */
    @Synchronized
    Boolean shutdown() {
        closing = True;
        return pendingRequests == 0;
    }

    @Override
    String toString() {
        return $"HttpHandler@{host}";
    }


    // ----- internal helpers ----------------------------------------------------------------------

    /**
     * Find an existing or add a new non-busy dispatcher and mark it as busy.
     *
     * Note: if the number of dispatchers grows beyond an allowed threshold, the result may point
     *       to a currently busy dispatcher.
     *
     * @return a dispatcher index
     */
    private Int ensureDispatcher() {
        private Int lastIndex = -1;

        Dispatcher[] dispatchers = this.dispatchers;
        Int          count       = dispatchers.size;
        if (count == 0) {
            dispatchers.add(new Dispatcher(catalog, bundlePool, sessionManager, authenticator));
            busy.add(True);
            lastIndex = 0;
            return 0;
        }

        Boolean[] busy = this.busy;
        Int       next = lastIndex + 1;
        for (Int i : 0 ..< count) {
            Int index = (i + next) % count;
            if (!busy[index]) {
                lastIndex = index;
                return index;
            }
        }

        if (count < maxCount) {
            dispatchers.add(new Dispatcher(catalog, bundlePool, sessionManager, authenticator));
            busy.add(True);
            return count; // don't change the lastIndex to retain some fairness
        }

        // TODO: check the total number of pending requests and throw a "reject" if over the limit

        // we are at the max; add the overload evenly
        return lastIndex++;
    }

    /**
     * Instantiate a SessionManager for the application described by the provided [Catalog] on
     * behalf of the specified host.
     *
     * @param host     the HostInfo
     * @param catalog  the application's Catalog
     */
    private static SessionManager createSessionManager(HostInfo host, Catalog catalog) {
        import ecstasy.reflect.Annotation;
        import SessionManager.SessionProducer;

        SessionProducer sessionProducer;

        Class[] sessionMixins = catalog.sessionMixins;
        Int     mixinCount    = sessionMixins.size;

        if (mixinCount == 0) {
            sessionProducer = (mgr, id, info) -> new SessionImpl(mgr, id, info);
        } else {
            Annotation[] annotations  = new Annotation[mixinCount] (i -> new Annotation(sessionMixins[i]));
            Class        sessionClass = SessionImpl.annotate(annotations);

            sessionProducer = (mgr, id, info) -> {
                assert Struct structure := sessionClass.allocate();
                assert structure.is(struct SessionImpl);

                SessionImpl.initialize(structure, mgr, id, info);

                return sessionClass.instantiate(structure).as(SessionImpl);
            };
        }

        return new SessionManager(new SessionStore(), sessionProducer, host.httpPort, host.httpsPort);
    }
}