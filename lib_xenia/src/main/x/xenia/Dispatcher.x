import net.URI;

import web.Catalog.EndpointInfo;
import web.Catalog.MethodInfo;
import web.Catalog.WebServiceInfo;
import web.ErrorHandler;
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

        ChainBundle?     bundle;
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

            if (serviceInfo.id == -1)
                {
                // this is a redirect call; validate the info and respond accordingly
                return;
                }

            (Session session, Boolean redirect) = TODO
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

                    (bundle, Handler handle) = ensureCallChain(endpoint);

                    response = handle^(session, request);
                    break ComputeResponse;
                    }
                }

            Request     request     = new Http1Request(new RequestInfo(httpServer, context), []);
            MethodInfo? onErrorInfo = catalog.findOnError(serviceInfo.id);
            if (onErrorInfo != Null)
                {
                (bundle, ErrorHandler? onError) = ensureErrorHandler(onErrorInfo.wsid);
                if (onError != Null)
                    {
                    response = onError^(session, request, HttpStatus.NotFound);
                    break ComputeResponse;
                    }
                }

            bundle   = Null;
            response = catalog.webApp.handleUnhandledError^(session, request, HttpStatus.NotFound);
            }
        while (False);

        pendingRequests++;

        &response.whenComplete((r, e) ->
            {
            pendingRequests--;
            bundle?.isBusy = False;

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

    /**
     * Ensure a call chain for the specified endpoint.
     */
    private (ChainBundle, Handler) ensureCallChain(EndpointInfo endpoint)
        {
        ChainBundle? bundle = bundleBySid[endpoint.wsid];
        if (bundle != Null && !bundle.isBusy)
            {
            bundle.isBusy = True;
            return bundle, bundle.ensureCallChain(endpoint);
            }

        TODO look up

        if (bundle == Null)
            {
            ChainBundle newBundle = new ChainBundle(catalog);
            bundle = newBundle;
            }

        bundle.isBusy = True;
        return bundle, bundle.ensureCallChain(endpoint);
        }

    /**
     * Ensure an ErrorHandler on the specified service.
     */
    private (ChainBundle, ErrorHandler?) ensureErrorHandler(Int wsid)
        {
        ChainBundle? bundle = bundleBySid[wsid];
        if (bundle != Null && !bundle.isBusy)
            {
            bundle.isBusy = True;
            return bundle, bundle.ensureErrorHandler(wsid);
            }

        TODO look up

        if (bundle == Null)
            {
            ChainBundle newBundle = new ChainBundle(catalog);
            bundle = newBundle;
            }

        return bundle, bundle.ensureErrorHandler(wsid);
        }
    }