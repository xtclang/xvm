import net.URI;

import web.ErrorHandler;
import web.HttpStatus;

import web.routing.Catalog.EndpointInfo;
import web.routing.Catalog.MethodInfo;
import web.routing.Catalog.WebServiceInfo;
import web.routing.UriTemplate.UriParameters;

import HttpServer.RequestContext;
import HttpServer.RequestInfo;


/**
 * Dispatcher is responsible for finding an endpoint, creating a call chain for an HTTP request and
 * invoking it on a corresponding WebService.
 */
service Dispatcher
    {
    construct(Catalog catalog, BundlePool bundlePool, SessionManager sessionManager)
        {
        this.catalog        = catalog;
        this.bundlePool     = bundlePool;
        this.sessionManager = sessionManager;
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
     * The session manager.
     */
    protected SessionManager sessionManager;

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
            RequestInfo requestInfo = new RequestInfo(httpServer, context);
            if (serviceInfo == Null)
                {
                Request  request = new Http1Request(requestInfo, []);
                Session? session = getSessionOrNull(httpServer, context);

                response = catalog.webApp.handleUnhandledError^(session, request, HttpStatus.NotFound);
                break;
                }

            Int wsid = serviceInfo.id;
            if (wsid == -1)
                {
                // this is a redirect call; validate the info and respond accordingly
                return;
                }

            (Session session, Boolean redirect) = computeSession(requestInfo);
            if (redirect)
                {
                TODO httpServer.send(context, HttpStatus.TemporaryRedirect.code, ...);
                return;
                }

            // split what's left of the URI into a path, a query, and a fragment
            String? query    = Null;
            String? fragment = Null;
            if (Int fragmentOffset := uriString.indexOf('#'))
                {
                fragment  = uriString.substring(fragmentOffset+1);
                uriString = uriString[0..fragmentOffset);
                }

            if (Int queryOffset := uriString.indexOf('?'))
                {
                query     = uriString.substring(queryOffset+1);
                uriString = uriString[0..queryOffset);
                }

            // TODO CP: handle the path parsing more robustly
            URI uri = new URI(path=new Path(uriString), query=query, fragment=fragment);

            for (EndpointInfo endpoint : serviceInfo.endpoints)
                {
                if (endpoint.httpMethod.name == methodName,
                        UriParameters uriParams := endpoint.template.matches(uri))
                    {
                    Request request = new Http1Request(requestInfo, uriParams);

                    bundle = bundlePool.allocateBundle(wsid);

                    Handler handle = bundle.ensureCallChain(endpoint);

                    response = handle^(session, request);
                    break ComputeResponse;
                    }
                }

            Request     request     = new Http1Request(requestInfo, []);
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
                @Inject Console console;
                console.println("Unhandled exception: " + e);

                httpServer.send(context, HttpStatus.InternalServerError.code, [], [], []);
                }
            else
                {
                (Int status, String[] names, String[] values, Byte[] body) =
                    Http1Response.prepare(r);

                httpServer.send(context, status, names, values, body);
                }
            });
        }

    private Session? getSessionOrNull(HttpServer httpServer, RequestContext context)
        {
        // TODO: use cookies to find an existing session; quick validation - no redirect
        return Null;
        }

    private (Session session, Boolean redirect)
            computeSession(RequestInfo requestInfo)
        {
        // TODO: use cookies to find an existing session; full validation - redirect if necessary
        return sessionManager.createSession(requestInfo), False;
        }
    }