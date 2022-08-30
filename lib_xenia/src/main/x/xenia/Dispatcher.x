import net.URI;

import web.Catalog.EndpointInfo;
import web.Catalog.MethodInfo;
import web.Catalog.WebServiceInfo;
import web.ErrorHandler;
import web.HttpStatus;

import web.routing.UriTemplate.UriParameters;

import HttpServer.RequestInfo;


/**
 * Dispatcher is responsible for finding an endpoint, creating a call chain for an HTTP request and
 * invoking it on a corresponding WebService.
 */
service Dispatcher
    {
    construct(Catalog catalog, BundlePool bundlePool)
        {
        this.catalog     = catalog;
        this.bundlePool  = bundlePool;
        }

    /**
     * The catalog.
     */
    protected Catalog catalog;

    /**
     * The pool of call chain bundles.
     */
    protected BundlePool bundlePool;

    /**
     * Pending request counter.
     */
    @Atomic Int pendingRequests;

    /**
     * Dispatch the "raw" request.
     */
    void dispatch(HttpServer httpServer, RequestContext context, String uriString, String methodName)
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

        ChainBundle?     bundle = Null;
        @Future Response response;
        ComputeResponse: do
            {
            if (serviceInfo == Null)
                {
                Request  request = new Http1Request(new RequestInfo(httpServer, context), []);
                Session? session = TODO getSessionOrNull();

                response = catalog.webApp.handleUnhandledError^(session, request, HttpStatus.NotFound);
                break;
                }

            Int wsid = serviceInfo.id;
            if (wsid == -1)
                {
                // this is a redirect call; validate the info and respond accordingly
                return;
                }

            (Session session, Boolean redirect) = computeSession();
            if (redirect)
                {
                TODO httpServer.send(context, HttpStatus.TemporaryRedirect.code, ...);
                return;
                }

            URI uri = new URI(uriString);

            for (EndpointInfo endpoint : serviceInfo.endpoints)
                {
                if (endpoint.httpMethod.name == methodName,
                        UriParameters uriParams := endpoint.template.matches(uri))
                    {
                    Request request = new Http1Request(new RequestInfo(httpServer, context), uriParams);

                    bundle = bundlePool.allocateBundle(wsid);

                    Handler handle = bundle.ensureCallChain(endpoint);

                    response = handle^(session, request);
                    break ComputeResponse;
                    }
                }

            Request     request     = new Http1Request(new RequestInfo(httpServer, context), []);
            MethodInfo? onErrorInfo = catalog.findOnError(wsid);
            if (onErrorInfo != Null)
                {
                Int errorWsid = onErrorInfo.wsid;

                bundle = bundlePool.allocateBundle(errorWsid);

                ErrorHandler? onError = bundle.ensureErrorHandler(errorWsid);
                if (onError != Null)
                    {
                    response = onError^(session, request, HttpStatus.NotFound);
                    break ComputeResponse;
                    }
                }

            response = catalog.webApp.handleUnhandledError^(session, request, HttpStatus.NotFound);
            }
        while (False);

        pendingRequests++;

        &response.whenComplete((r, e) ->
            {
            pendingRequests--;
            bundlePool.releaseBundle(bundle?);

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
        }

    (Session session, Boolean redirect) computeSession()
        {
        TODO
        }
    }