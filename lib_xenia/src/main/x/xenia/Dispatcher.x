import Catalog.EndpointInfo;
import Catalog.MethodInfo;
import Catalog.WebServiceInfo;

import HttpServer.RequestInfo;

import web.ErrorHandler;
import web.Header;
import web.HttpStatus;
import web.RequestAborted;

import web.responses.SimpleResponse;

import web.sessions.Broker as SessionBroker;

import net.UriTemplate.UriParameters;


/**
 * Dispatcher is responsible for finding an endpoint, creating a call chain for an HTTP request and
 * invoking it on a corresponding WebService.
 */
service Dispatcher {
    construct(Catalog        catalog,
              BundlePool     bundlePool,
              SessionManager sessionManager) {
        this.catalog       = catalog;
        this.bundlePool    = bundlePool;
        this.sessionBroker = catalog.webApp.sessionBroker.duplicate();
    }

    /**
     * The Catalog.
     */
    protected @Final Catalog catalog;

    /**
     * The pool of call chain bundles.
     */
    protected @Final BundlePool bundlePool;

    /**
     * The session broker, which encapsulates the process of establishing and identifying sessions.
     */
    protected/private @Final SessionBroker sessionBroker;

    /**
     * Pending request counter.
     */
    public/private @Atomic Int pendingRequests;

    /**
     * Dispatch the "raw" request.
     */
    void dispatch(RequestInfo requestInfo) {
        Boolean tls       = requestInfo.tls;
        String  uriString = requestInfo.uriString;
        FromTheBeginning: while (True) {
            // select the service to delegate request processing to; the service infos are sorted
            // with the most specific path first (so the first path match wins)
            Int             uriSize     = uriString.size;
            WebServiceInfo? serviceInfo = Null;
            for (WebServiceInfo info : catalog.services) {
                // the info.path, which represents a "directory", never ends with '/' (see the
                // extractPath function in Catalog) -- except for the root path "/". a legitimately
                // matching uriString may or may not have '/' at the end; for example: if the path
                // is "test", then uri values such as "test/s", "test/" and "test" should all match
                // (the last two would be treated as equivalent), but "tests" should not
                String path     = info.path;
                Int    pathSize = path.size;
                if (uriSize == pathSize) {
                    if (uriString == path) {
                        serviceInfo = info;
                        uriString   = "";
                        break;
                    }
                } else if (pathSize == 1) { // root path ("/") matches everything
                    serviceInfo = info;
                    uriString   = uriString.substring(1);
                } else if (uriSize > pathSize && uriString[pathSize] == '/' && uriString.startsWith(path)) {
                    serviceInfo = info;
                    uriString   = uriString.substring(pathSize + 1);
                    break;
                }
            }

            ChainBundle? bundle = Null;
            @Future ResponseOut response;
            ProcessRequest: if (serviceInfo == Null) {
                RequestIn request = new Http1Request(requestInfo, sessionBroker);
                response = catalog.webApp.handleUnhandledError^(request, HttpStatus.NotFound);
            } else {
                // split what's left of the URI into a path, a query, and a fragment
                String? query    = Null;
                String? fragment = Null;
                if (Int fragmentOffset := uriString.indexOf('#')) {
                    fragment  = uriString.substring(fragmentOffset+1);
                    uriString = uriString[0 ..< fragmentOffset];
                }

                if (Int queryOffset := uriString.indexOf('?')) {
                    query     = uriString.substring(queryOffset+1);
                    uriString = uriString[0 ..< queryOffset];
                }

                if (uriString.empty) {
                    uriString = "/";
                }

                EndpointInfo  endpoint;
                UriParameters uriParams = [];
                Int           wsid      = serviceInfo.id;
                FindEndpoint: {
                    Uri uri;
                    try {
                        uri = new Uri(path=uriString, query=query, fragment=fragment);
                    } catch (Exception e) {
                        response = new SimpleResponse(BadRequest);
                        break ProcessRequest;
                    }

                    // find a matching endpoint
                    String methodName = requestInfo.method.name;
                    for (endpoint : serviceInfo.endpoints) {
                        if (endpoint.httpMethod.name == methodName,
                                uriParams := endpoint.matches(uri)) {
                            break FindEndpoint;
                        }
                    }

                    // no matching endpoint; check if there is a default endpoint
                    if (methodName == "GET", endpoint ?= serviceInfo.defaultGet) {
                        break FindEndpoint;
                    }

                    // there is no matching endpoint
                    RequestIn request = new Http1Request(requestInfo, sessionBroker);
                    if (MethodInfo onErrorInfo ?= catalog.findOnError(wsid)) {
                        Int errorWsid = onErrorInfo.wsid;
                        bundle = bundlePool.allocateBundle(errorWsid);
                        if (ErrorHandler onError ?= bundle.ensureErrorHandler(errorWsid)) {
                            response = onError^(request, HttpStatus.NotFound);
                            break ProcessRequest;
                        } else {
                            bundlePool.releaseBundle(bundle);
                            bundle = Null;
                        }
                    }
                    response = catalog.webApp.handleUnhandledError^(request, HttpStatus.NotFound);
                    break ProcessRequest;
                }

                // if the endpoint requires HTTPS (or some other form of TLS), the server responds
                // either with an error or with a redirect to a URL that uses a TLS-enabled protocol
                if (!tls && endpoint.requiresTls) {
                    response = new SimpleResponse(endpoint.redirectTls ? PermanentRedirect : Forbidden);
                    response.header.add(Header.Location, requestInfo.httpsUrl.toString());
                    break ProcessRequest;
                }

                // create a Request object to represent the incoming request, and the reason that it
                // matched; obtain the corresponding Session object if it exists, or create one if
                // a Session is required by the request; there is a "chicken and egg" problem here,
                // because to obtain a Session, we need a request, but the Request itself must know
                // about the Session that it is associated with
                Session?  session = Null;
                RequestIn request = new Http1Request(requestInfo, sessionBroker, endpoint.template,
                                                     uriParams, bindRequired=True);
                ResponseOut? sendNow = Null;
                // check if there is already a Session for the Request
                if ((session, sendNow) := sessionBroker.findSession(request)) {
                    // sending the "sendNow" response is handled below
                } else if (endpoint.requiresSession) {
                    // the selected endpoint requires a Session, we need to create one
                    if ((session, sendNow) := sessionBroker.requireSession(request)) {
                        assert session != Null || sendNow != Null;
                    } else {
                        // the Broker could not create a Session; return an error response instead
                        // of throwing an assertion
                        sendNow = new SimpleResponse(NotImplemented);
                    }
                }
                request.bindSession(session);

                if (sendNow == Null) {
                    // this is the "normal" i.e. "actual" request processing
                    bundle = bundlePool.allocateBundle(wsid);
                    Handler handle = bundle.ensureCallChain(endpoint);
                    response = handle^(request);
                } else {
                    response = sendNow;
                }
            }

            // at this point, the request processing has been kicked off asynchronously; the
            // dispatcher (having done its job) will return immediately, freeing it up to be used
            // by another incoming request; when this current request finally finishes, it will
            // update statistics and release the remaining resources that it is using
            pendingRequests++;
            &response.whenComplete((r, e) -> {
                pendingRequests--;
                bundlePool.releaseBundle(bundle?);

                if (r == Null) {
                    if (e.is(RequestAborted)) {
                        r = e.makeResponse();
                    } else {
                        // TODO GG: remove
                        @Inject Console console;
                        console.print($|Dispatcher: unhandled exception for \
                                       |"{requestInfo.uriString}": {e}
                                     );

                        requestInfo.respond(HttpStatus.InternalServerError.code, [], [], []);
                        return;
                    }
                }

                (Int status, String[] names, String[] values, Byte[] body) = Http1Response.prepare(r);
                requestInfo.respond(status, names, values, body);
            });
            return;
        }
    }
}