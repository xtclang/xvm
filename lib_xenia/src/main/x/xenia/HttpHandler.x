import web.HttpStatus;
import web.codecs.Registry;

import HttpServer.Handler;
import HttpServer.RequestContext;


/**
 * An HTTP request entry point.
 */
@Concurrent
service HttpHandler
        implements Handler
    {
    construct(HttpServer httpServer, WebApp app)
        {
        Catalog catalog = buildCatalog(app);

        this.httpServer     = httpServer;
        this.catalog        = catalog;
        this.dispatchers    = new Dispatcher[];
        this.busy           = new Boolean[];
        this.bundlePool     = new BundlePool(catalog);
        this.sessionManager = createSessionManager(catalog);

        Registry registry = app.registry_;
        registry.registerResource("sessionManager", this.sessionManager);
        registry.registerResource("catalog", this.catalog);
        }

    /**
     * The HttpServer.
     */
    HttpServer httpServer;

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
    Int pendingRequests.get()
        {
        return dispatchers.map(Dispatcher.pendingRequests).reduce(new aggregate.Sum<Int>());
        }


    // ----- Handler API ---------------------------------------------------------------------------

    @Override
    void handle(RequestContext context, String uriString, String methodName, Boolean tls)
        {
        if (closing)
            {
            httpServer.send(context, HttpStatus.Gone.code, [], [], []);
            return;
            }

        Int   index  = ensureDispatcher();
        Tuple result = dispatchers[index].dispatch^(httpServer, context, tls, uriString, methodName);
        &result.whenComplete((response, e) ->
            {
            if (e != Null)
                {
                httpServer.send(context, HttpStatus.InternalServerError.code, [], [], []);

                // temporary
                @Inject Console console;
                console.println(e);
                }
            busy[index] = False;
            });
        }

    /**
     * Shutdown this HttpHandler.
     */
    @Synchronized
    void shutdown()
        {
        closing = True;

        if (pendingRequests == 0)
            {
            httpServer.close();
            }
        else
            {
            // wait a second (TODO: repeat a couple of times)
            @Inject Timer timer;
            timer.schedule(SECOND, () -> httpServer.close());
            }
        }

    @Override
    String toString()
        {
        return $"HttpHandler@{httpServer}";
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
    private Int ensureDispatcher()
        {
        private Int lastIndex = -1;

        Dispatcher[] dispatchers = this.dispatchers;
        Int          count       = dispatchers.size;
        if (count == 0)
            {
            dispatchers.add(new Dispatcher(catalog, bundlePool, sessionManager));
            busy.add(True);
            lastIndex = 0;
            return 0;
            }

        Boolean[] busy = this.busy;
        Int       next = lastIndex + 1;
        for (Int i : 0 ..< count)
            {
            Int index = (i + next) % count;
            if (!busy[index])
                {
                lastIndex = index;
                return index;
                }
            }

        if (count < maxCount)
            {
            dispatchers.add(new Dispatcher(catalog, bundlePool, sessionManager));
            busy.add(True);
            return count; // don't change the lastIndex to retain some fairness
            }

        // TODO: check the total number of pending requests and throw a "reject" if over the limit

        // we are at the max; add the overload evenly
        return lastIndex++;
        }

    private static SessionManager createSessionManager(Catalog catalog)
        {
        import ecstasy.reflect.Annotation;
        import SessionManager.SessionProducer;

        SessionProducer sessionProducer;

        Class[] sessionMixins = catalog.sessionMixins;
        Int     mixinCount    = sessionMixins.size;

        if (mixinCount == 0)
            {
            sessionProducer = (mgr, id, info) -> new SessionImpl(mgr, id, info);
            }
        else
            {
            Annotation[] annotations  = new Annotation[mixinCount] (i -> new Annotation(sessionMixins[i]));
            Class        sessionClass = SessionImpl.annotate(annotations);

            sessionProducer = (mgr, id, info) ->
                {
                assert Struct structure := sessionClass.allocate();
                assert structure.is(SessionImpl:struct);

                SessionImpl.initialize(structure, mgr, id, info);

                return sessionClass.instantiate(structure).as(SessionImpl);
                };
            }

        return new SessionManager(new SessionStore(), sessionProducer);
        }
    }