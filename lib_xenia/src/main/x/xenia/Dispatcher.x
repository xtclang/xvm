import net.URI;

import web.Catalog;
import web.Header;
import web.HttpServer;
import web.HttpStatus;
import web.Request;
import web.Response;

import web.routing.UriTemplate;

/**
 * Dispatcher is responsible for finding an endpoint and creating a call chain for a request.
 */
service Dispatcher
    {
    construct(Catalog catalog)
        {
        this.catalog = catalog;

        }

    /**
     * The catalog.
     */
    protected Catalog catalog;

    /**
     * Pending request counter.
     */
    @Atomic Int pendingRequests;

    /**
     * Dispatch the "raw" request.
     */
    void dispatch(HttpServer httpServer, Object context, String uriString, String methodName,
                String[] headerNames, String[][] headerValues, Byte[] body)
        {
        import Catalog.WebServiceInfo;
        import Catalog.EndpointInfo;

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
                        Map<String, UriTemplate.Value> values := endpoint.template.matches(uri))
                    {
                    pendingRequests--;

                    Request request = TODO createRequest(uriString, methodName, headerNames, headerValues, body);

                    ChainBundle chain = TODO ensureCallChain(endpoint);

                    @Future Response response = TODO chain?.webService.route^(session, request, chain?.handler) : assert;

                    &response.whenComplete((r, e) ->
                        {
                        pendingRequests--;

                        if (r == Null)
                            {
                            httpServer.send(context, HttpStatus.InternalServerError.code, [], [], []);
                            }
                        else
                            {
                            String[]   argNames  = TODO;
                            String[][] argValues = TODO;

                            // TODO GG shouldn't compile; needs "?: []" at the end
                            httpServer.send(context, r.status.code, argNames, argValues, r.body?.bytes);
                            }
                        });
                    return;
                    }
                }
            }
        httpServer.send(context, HttpStatus.NotFound.code, [], [], []);
        }


    // ----- helper classes ------------------------------------------------------------------------

    /**
     * The chain bundle represents a set of lazily created call chain collections.
     */
    static class ChainBundle
        {

        }
    }