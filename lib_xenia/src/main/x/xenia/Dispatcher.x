import net.URI;

import web.Catalog.EndpointInfo;
import web.Catalog.WebServiceInfo;
import web.HttpStatus;

import web.routing.UriTemplate.UriParameters;

import HttpServer.RequestInfo;


/**
 * Dispatcher is responsible for finding an endpoint and creating a call chain for a request.
 */
service Dispatcher
    {
    construct(Catalog catalog)
        {
        this.catalog     = catalog;
        this.bundleBySid = new ChainBundle?[catalog.serviceCount];
        }

    /**
     * The catalog.
     */
    private Catalog catalog;

    /**
     * The pool of call chain bundles.
     */
    private ChainBundle[] bundles = new ChainBundle[];

    /**
     * The cache of bundles for WebServices indexed by the service id.
     */
    private ChainBundle?[] bundleBySid;

    /**
     * Pending request counter.
     */
    @Atomic Int pendingRequests;

    /**
     * Dispatch the "raw" request.
     */
    void dispatch(HttpServer httpServer, RequestContext context, String uriString, String methodName,
                String[] headerNames, String[] headerValues, Byte[] body)
        {
        WebServiceInfo? serviceInfo = Null;
        for (WebServiceInfo info : catalog.services)
            {
            if (uriString.startsWith(info.path))
                {
                serviceInfo = info;
                uriString   = uriString.substring(info.path.size);
                break;
                }
            }

        if (serviceInfo != Null)
            {
            URI uri = new URI(uriString);

            for (EndpointInfo endpoint : serviceInfo.endpoints)
                {
                if (endpoint.httpMethod.name == methodName,
                        UriParameters uriParams := endpoint.template.matches(uri))
                    {
                    pendingRequests--;

                    Session session = TODO

                    Request request = new Http1Request(new RequestInfo(httpServer, context), uriParams);

                    Handler handle  = ensureCallChain(endpoint);

                    @Future Response response = handle^(session, request);

                    &response.whenComplete((r, e) ->
                        {
                        pendingRequests--;

                        if (r == Null)
                            {
                            httpServer.send(context, HttpStatus.InternalServerError.code, [], [], []);
                            }
                        else
                            {
                            String[] argNames  = TODO;
                            String[] argValues = TODO;

                            httpServer.send(context, r.status.code, argNames, argValues, r.body?.bytes : []);
                            }
                        });
                    return;
                    }
                }
            }

        // TODO shouldn't this be routed via the app's error handler
        httpServer.send(context, HttpStatus.NotFound.code, [], [], []);
        }

    /**
     * TODO
     */
    private Handler ensureCallChain(EndpointInfo endpoint)
        {
        ChainBundle? bundle = bundleBySid[endpoint.wsid];
        if (bundle != Null && !bundle.isBusy)
            {
            bundle.isBusy = True;
            return bundle.ensureCallChain(endpoint);
            }

        TODO look up

        if (bundle == Null)
            {
            ChainBundle newBundle = new ChainBundle(catalog);
            bundle = newBundle;
            }

        return bundle.ensureCallChain(endpoint);
        }


    // ----- helper classes ------------------------------------------------------------------------

    }