import net.URI;

import web.Catalog;
import web.Catalog.EndpointInfo;
import web.Catalog.WebServiceInfo;
import web.HttpStatus;

import web.routing.UriTemplate;


/**
 * An HTTP request entry point.
 */
@Concurrent
service HttpHandler
        implements HttpServer.Handler
    {
    construct(HttpServer httpServer, Catalog catalog)
        {
        this.httpServer = httpServer;
        this.catalog    = catalog;
        }
    finally
        {
        httpServer.attachHandler(this);
        }

    /**
     * The HttpServer.
     */
    HttpServer httpServer;

    /**
     * The catalog.
     */
    protected Catalog catalog;

    /**
     * Closing flag.
     */
    Boolean closing;

    /**
     * The dispatchers.
     */
    protected Dispatcher[] dispatchers = new Dispatcher[];

    /**
     * The dispatchers state.
     */
    Boolean[] busy;

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
    void handle(RequestContext context, String uriString, String methodName)
        {
        if (closing)
            {
            httpServer.send(context, HttpStatus.Gone.code, [], [], []);
            return;
            }

        Int   index  = ensureDispatcher();
        Tuple result = dispatchers[index].dispatch^(httpServer, context, uriString, methodName);
        &result.whenComplete((response, e) ->
            {
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
            dispatchers.add(new Dispatcher(catalog));
            busy.add(True);
            lastIndex = 0;
            return 0;
            }

        Boolean[] busy = this.busy;
        Int       next = lastIndex + 1;
        for (Int i : [0..count))
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
            dispatchers.add(new Dispatcher(catalog));
            busy.add(True);
            return count; // don't change the lastIndex to retain some fairness
            }

        // TODO: check the total number of pending requests and throw a "reject" if over the limit

        // we are at the max; add the overload evenly
        return lastIndex++;
        }
    }