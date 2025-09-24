import Catalog.EndpointInfo;
import Catalog.MethodInfo;
import Catalog.WebServiceInfo;

import HttpServer.RequestInfo;

import web.ErrorHandler;
import web.Header;
import web.HttpStatus;
import web.RequestAborted;

import web.responses.SimpleResponse;

import web.security.Authenticator;
import web.security.Authenticator.Attempt;

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
        while (True) {
            // select the service to delegate request processing to; the service infos are sorted
            // with the most specific path first (so the first path match wins)
            Int             uriSize     = uriString.size;
            WebServiceInfo? serviceInfo = Null;
            for (WebServiceInfo info : catalog.services) {
                // the info.path, which represents a "directory", never ends with '/' (see the
                // validatePath function in Catalog) -- except for the root path "/". a legitimately
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
                    break;
                } else if (uriSize > pathSize && uriString.startsWith(path)) {
                    Char ch = uriString[pathSize];
                    if (ch == '/' || ch == '?' || ch == '#') {
                        serviceInfo = info;
                        uriString   = uriString.substring(pathSize);
                        break;
                    }
                }
            }

            ChainBundle? bundle = Null;
            @Future ResponseOut response;
            ProcessRequest: if (serviceInfo == Null) {
                RequestIn request = new Http1Request(requestInfo, sessionBroker);
                handlePlainTextSecrets(tls, requestInfo, request);
                response = catalog.webApp.handleUnhandledError^(request, NotFound);
            } else {
                // split what's left of the URI into a path, a query, and a fragment
                String? query    = Null;
                String? fragment = Null;
                if (!uriString.empty) {
                    if (Int fragmentOffset := uriString.indexOf('#')) {
                        fragment  = uriString.substring(fragmentOffset+1);
                        uriString = uriString[0 ..< fragmentOffset];
                    }
                    if (Int queryOffset := uriString.indexOf('?')) {
                        query     = uriString.substring(queryOffset+1);
                        uriString = uriString[0 ..< queryOffset];
                    }
                }

                EndpointInfo  endpoint;
                UriParameters uriParams = [];
                Int           wsid      = serviceInfo.id;
                FindEndpoint: {
                    Uri uri;
                    try {
                        uri = new Uri(path=uriString, query=query, fragment=fragment);
                    } catch (Exception e) {
                        handlePlainTextSecrets(tls, requestInfo);
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
                            handlePlainTextSecrets(tls, requestInfo, request, bundle=bundle);
                            response = onError^(request, NotFound);
                            break ProcessRequest;
                        } else {
                            bundlePool.releaseBundle(bundle);
                            bundle = Null;
                        }
                    }
                    handlePlainTextSecrets(tls, requestInfo, request);
                    response = catalog.webApp.handleUnhandledError^(request, NotFound);
                    break ProcessRequest;
                }

                // if the endpoint requires HTTPS (or some other form of TLS), the server responds
                // either with an error or with a redirect to a URL that uses a TLS-enabled protocol
                if (!tls && endpoint.requiresTls) {
                    handlePlainTextSecrets(tls, requestInfo);
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
                        uriParams, bindRequired=True, streaming=endpoint.allowRequestStreaming);
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
                    if (!tls, sendNow ?= handlePlainTextSecrets(tls, requestInfo, request,
                                            bundle=bundle, createResponse=True)) {
                        response = sendNow;
                    } else {
                        // this is the "normal" i.e. "actual" request processing
                        bundle = bundlePool.allocateBundle(wsid);
                        Handler handle = bundle.ensureCallChain(endpoint);
                        response = handle^(request);
                    }
                } else {
                    handlePlainTextSecrets(tls, requestInfo, request);
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

                (Int status, String[] names, String[] values, Int responseLength) =
                    Http1Response.prepare(r);

                // TODO: REMOVE!!!!
                names  += Header.CORSAllowCredentials;
                values += "true";

                names  += Header.CORSAllowMethods;
                values += "GET, POST, PUT, DELETE, PATCH, OPTIONS";

                names  += Header.CORSAllowHeaders;
                values += "Content-Type, Accept, Origin, X-Requested-With";

                if (String[] origin := requestInfo.getHeaderValuesForName("Origin")) {
                    names  += Header.CORSAllowOrigin;
                    values += origin[0];
                }

                if (responseLength > 0) {
                    // fixed size body
                    requestInfo.respond(status, names, values, r.body?.bytes) : assert;
                } else if (responseLength < 0) {
                    // no body
                    requestInfo.respond(status, names, values, []);
                } else {
                    // streaming
                    requestInfo.setHeaders(status, names, values, 0);
                    requestInfo.streamBodyBytes(r.body?.bodyReader());
                }
            });
            return;
        }
    }

    /**
     * Check the request to make sure that no plain text credentials are potentially leaked by the
     * request. If any credential may have been leaked, revoke those credentials and produce a
     * response to the client if required.
     *
     * @param tls             `True` iff the client sent a request protected by TLS
     * @param requestInfo      the incoming [RequestInfo] from the client
     * @param request          (optional) [RequestIn], iff there already exists a request;
     *                         otherwise `Null`
     * @param bundle           (optional) [ChainBundle], iff one has already been allocated;
     *                         otherwise `Null`
     * @param createResponse   (optional) specifies whether a response needs to be produced if
     *                         plain text credentials are potentially leaked
     *
     * @return a [ResponseOut] to send back to the client in case the response is requested and
     *                         there are plain text credential leaks; `Null` otherwise
     */
    protected ResponseOut? handlePlainTextSecrets(Boolean      tls,
                                                  RequestInfo  requestInfo,
                                                  RequestIn?   request        = Null,
                                                  ChainBundle? bundle         = Null,
                                                  Boolean      createResponse = False,
                                                 ) {
        // only requests that are not TLS protected are assumed to leak credentials
        if (!tls) {
            // obtain an Authenticator
            Authenticator authenticator = bundle?.authenticator : catalog.webApp.authenticator;

            // ensure that a request exists
            request ?:= new Http1Request(requestInfo, sessionBroker);

            // ask the Authenticator to scan for (and revoke) any plain text credentials
            Attempt[] leaks = authenticator.findAndRevokeSecrets(request);

            // if anything may have leaked, create a request to respond as best as possible to
            // the assumed leaks
            if (createResponse && !leaks.empty) {
                // grab the first ResponseOut from the Attempt, if any is provided
                HttpStatus? status = Null;
                for (Attempt leak : leaks) {
                    if (ResponseOut response := leak.response.is(ResponseOut)) {
                        return response;
                    } else {
                        status ?:= leak.response.is(HttpStatus)?;
                    }
                }
                return new SimpleResponse(status ?: Unauthorized);
            }
        }
        return Null;
    }
}