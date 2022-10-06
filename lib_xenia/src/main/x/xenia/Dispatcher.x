import web.ErrorHandler;
import web.Header;
import web.HttpStatus;

import web.routing.Catalog.EndpointInfo;
import web.routing.Catalog.MethodInfo;
import web.routing.Catalog.WebServiceInfo;
import web.routing.UriTemplate.UriParameters;

import HttpServer.RequestContext;
import HttpServer.RequestInfo;
import SessionCookie.CookieId;


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
     * The Catalog.
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
    void dispatch(HttpServer httpServer, RequestContext context, Boolean tls, String uriString, String methodName)
        {
        // select the service to delegate request processing to; the service infos are sorted with
        // the most specific path first (so the first path match wins)
        WebServiceInfo? serviceInfo = Null;
        for (WebServiceInfo info : catalog.services)
            {
            String path = info.path;
            if (uriString.startsWith(path))
                {
                serviceInfo = info;
                uriString   = uriString.substring(path.size);
                break;
                }
            }

        RequestInfo      requestInfo = new RequestInfo(httpServer, context, tls);
        ChainBundle?     bundle      = Null;
        @Future Response response;
        ProcessRequest: if (serviceInfo == Null)
            {
            Request  request = new Http1Request(requestInfo, []);
            Session? session = getSessionOrNull(httpServer, context);

            response = catalog.webApp.handleUnhandledError^(session, request, HttpStatus.NotFound);
            }
        else
            {
            Int wsid = serviceInfo.id;
            if (wsid == -1)
                {
                // this is a redirect call; validate the info and respond accordingly
                // TODO handle the redirect here
                return;
                }

            // split what's left of the URI into a path, a query, and a fragment
            String? query    = Null;
            String? fragment = Null;
            if (Int fragmentOffset := uriString.indexOf('#'))
                {
                fragment  = uriString.substring(fragmentOffset+1);
                uriString = uriString[0 ..< fragmentOffset];
                }

            if (Int queryOffset := uriString.indexOf('?'))
                {
                query     = uriString.substring(queryOffset+1);
                uriString = uriString[0 ..< queryOffset];
                }

            // TODO CP: handle the path parsing more robustly
            if (uriString == "")
                {
                uriString = "/";
                }

            EndpointInfo  endpoint;
            UriParameters uriParams = [];
            FindEndpoint:
                {
                URI uri = new URI(path=new Path(uriString), query=query, fragment=fragment);
                for (EndpointInfo eachEndpoint : serviceInfo.endpoints)
                    {
                    if (eachEndpoint.httpMethod.name == methodName,
                            uriParams := eachEndpoint.template.matches(uri))
                        {
                        endpoint = eachEndpoint;
                        break FindEndpoint;
                        }
                    }

                if (EndpointInfo defaultEndpoint ?= serviceInfo.defaultEndpoint)
                    {
                    if (defaultEndpoint.httpMethod.name == methodName)
                        {
                        endpoint = defaultEndpoint;
                        break FindEndpoint;
                        }
                    }

                // there is no matching endpoint
                Request     request     = new Http1Request(requestInfo, []);
                Session?    session     = getSessionOrNull(httpServer, context);
                MethodInfo? onErrorInfo = catalog.findOnError(wsid);
                if (onErrorInfo != Null && session != Null)
                    {
                    Int errorWsid = onErrorInfo.wsid;
                    bundle = bundlePool.allocateBundle(errorWsid);
                    ErrorHandler? onError = bundle.ensureErrorHandler(errorWsid);
                    if (onError != Null)
                        {
                        response = onError^(session, request, HttpStatus.NotFound);
                        break ProcessRequest;
                        }
                    }

                response = catalog.webApp.handleUnhandledError^(session, request, HttpStatus.NotFound);
                break ProcessRequest;
                }

            // if the endpoint requires HTTPS (or some other form of TLS), the server responds with
            // a redirect to a URL that uses a TLS-enabled protocol
            if (!tls && endpoint.requiresTls)
                {
                // TODO handle the redirect here
                TODO response =
                break ProcessRequest;
                }

            // either a valid existing session is identified by the request, or a session will be
            // created and a redirect to verify the session's successful creation will occur, which
            // will then redirect back to this same request
            (HttpStatus|Session result, Boolean redirect) = ensureSession(requestInfo);
            if (result.is(HttpStatus))
                {
                Request  request = new Http1Request(requestInfo, []);
                Session? session = getSessionOrNull(httpServer, context);
                response = catalog.webApp.handleUnhandledError^(session, request, result);
                break ProcessRequest;
                }

            Session session = result;
            if (redirect)
                {
                // TODO add session to list of unverified sessions
                // TODO handle the redirect here
                TODO response =
                break ProcessRequest;
                }

            // each endpoint has a required trust level, and the session knows its own trust level;
            // if the endpoint requirement is higher, then we have to re-authenticate the user agent
            if (endpoint.requiredTrust > session.trustLevel)
                {
                // TODO handle the redirect here
                TODO response =
                break ProcessRequest;
                }

            // this is the "normal" i.e. "actual" request processing
            Request request = new Http1Request(requestInfo, uriParams);
            bundle = bundlePool.allocateBundle(wsid);
            Handler handle = bundle.ensureCallChain(endpoint);
            response = handle^(session, request);
            }

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

    /**
     * Use request cookies to identify an existing session, performing only absolutely necessary
     * validations. No session validation, security checks, etc. are performed. This method does not
     * attempt to redirect, create or destroy a session, etc.
     *
     * @param httpServer  the [HttpServer] that received the request
     * @param context     the [RequestContext] for the current request
     *
     * @return the [SessionImpl] indicated by the request, or Null if none
     */
    private SessionImpl? getSessionOrNull(HttpServer httpServer, RequestContext context)
        {
        if (String[] cookies := httpServer.getHeaderValuesForName(context, Header.COOKIE))
            {
            for (String cookieHeader : cookies)
                {
                for (String cookie : cookieHeader.split(';'))
                    {
                    if (Int      delim    := cookie.indexOf('='),
                        CookieId cookieId := CookieId.lookupCookie(cookie[0 ..< delim].trim()))
                        {
                        if (SessionImpl session := sessionManager.getSessionByCookie(
                                cookie.substring(delim+1).trim()))
                            {
                            return session;
                            }
                        else
                            {
                            return Null;
                            }
                        }
                    }
                }
            }

        return Null;
        }

    /**
     * Using the request information, look up or create the session object.
     *
     * @param requestInfo  the incoming request
     *
     * @return result    the session object, or a `4xx`-range `HttpStatus` that indicates a failure
     * @return redirect  True indicates that redirection is required to update the session cookies
     */
    private (SessionImpl|HttpStatus result, Boolean redirect) ensureSession(RequestInfo requestInfo)
        {
        String? txtTemp = Null;
        String? tlsTemp = Null;
        String? consent = Null;
        Boolean tls     = requestInfo.tls;
        if (String[] cookies := requestInfo.getHeaderValuesForName(Header.COOKIE))
            {
            for (String cookieHeader : cookies)
                {
                for (String cookie : cookieHeader.split(';'))
                    {
                    if (Int      delim    := cookie.indexOf('='),
                        CookieId cookieId := CookieId.lookupCookie(cookie[0 ..< delim].trim()))
                        {
                        String? oldValue;
                        String  newValue = cookie.substring(delim+1).trim();
                        switch (cookieId)
                            {
                            case PlainText:
                                oldValue = txtTemp;
                                txtTemp  = newValue;
                                break;

                            case Encrypted:
                                oldValue = tlsTemp;
                                tlsTemp  = newValue;
                                break;

                            case Consent:
                                oldValue = consent;
                                consent  = newValue;
                                break;
                            }

                        if (oldValue? != newValue)
                            {
                            // duplicate cookie detected but with a different value; this should be
                            // impossible
                            return BadRequest, False;
                            }

                        if (!tls && cookieId.tlsOnly)
                            {
                            // user agent should have hidden the cookie; this should be impossible
                            return BadRequest, False;
                            }
                        }
                    }
                }
            }

        // 1 2 3TLS description/action
        // - - - -  ------------------
        //       0  create new session; redirect to verification (send cookie 1)
        //       1  create new session; redirect to verification (send cookies 1 and 2)
        // x     0  validate cookie 1
        // x     1  validate cookie 1 & verify that cookie 2/3 were NOT already sent & verified (if
        //          they were, then this is an error, because it indicates the likely theft of the
        //          plain text cookie); redirect to verification (send cookies 1 and 2, and 3 if
        //          [cookieConsent] has been set)
        //   x   0  error (no TLS, so cookie 2 is illegally present; also missing cookie 1)
        //   x   1  error (missing cookie 1)
        // x x   0  error (no TLS, so cookie 2 is illegally present)
        // x x   1  validate cookie 1 & 2; if [cookieConsent] has been set, redirect to verification
        //     x 0  error (no TLS, so cookie 3 is illegally present)
        //     x 1  validate cookie 3; assume temporary cookies absent due to user agent discarding
        //          temporary cookies; redirect to verification (send cookies 1 and 2)
        // x   x 0  error (no TLS, so cookie 3 is illegally present)
        // x   x 1  validate cookie 1 & 3 (1 must be newer than 3), and verify that cookie 2 was NOT
        //          already sent & verified; merge session for cookie 1 into session for cookie 3;
        //          redirect to verification; (send cookies 1 and 2)
        //   x x 0  error (no TLS, so cookie 2 and 3 are illegally present)
        //   x x 1  error (missing cookie 1)
        // x x x 0  error (no TLS, so cookies 2 and 3 are illegally present)
        // x x x 1  validate cookie 1 & 2 & 3 (must be same session)
        switch (txtTemp != Null, tlsTemp != Null, consent != Null, tls)
            {
            case (False, False, False, _):
                // create a new session
                return sessionManager.createSession(requestInfo), True;

            case (True , False, False, False):  // validate cookie 1
                // validate cookie 1
                assert txtTemp != Null;
                if (SessionImpl session := sessionManager.getSessionByCookie(txtTemp))
                    {
                    return session, txtTemp != session.sessionCookieInfos_[0].cookie.text;
                    }
                else
                    {
                    // session cookie did not identify a session, so assume that the session was
                    // valid but has since been destroyed; create a new session
                    return sessionManager.createSession(requestInfo), True;
                    }

            case (True , False, False, True ):
                // validate cookie 1 & verify that cookie 2/3 were NOT already sent & verified (if
                // they were, then this is an error, because it indicates the likely theft of the
                // plain text cookie); redirect to verification (send cookies 1 and 2, and 3 if
                // [cookieConsent] has been set)
                assert txtTemp != Null;
                TODO

            case (False, True , False, False):  // error (cookie 2 illegal; missing cookie 1)
            case (False, True , False, True ):  // error (missing cookie 1)
            case (True , True , False, False):  // error (cookie 2 illegal)
                // TODO mark the passed in cookies as suspect
                return sessionManager.createSession(requestInfo), True;

            case (True , True , False, True ):
                // validate cookie 1 & 2; if [cookieConsent] has been set, redirect to verification
                TODO

            case (False, False, True , False):  // error (cookie 3 illegal)
                // TODO mark the passed in cookie as suspect
                return sessionManager.createSession(requestInfo), True;

            case (False, False, True , True ):
                // validate cookie 3; assume temporary cookies absent due to user agent discarding
                // temporary cookies; redirect to verification (send cookies 1 and 2)
                TODO

            case (True , False, True , False):  // error (cookie 3 illegal)
                // TODO mark the passed in cookie as suspect
                return sessionManager.createSession(requestInfo), True;

            case (True , False, True , True ):
                // validate cookie 1 & 3 (1 must be newer than 3), and verify that cookie 2 was NOT
                // already sent & verified; merge session for cookie 1 into session for cookie 3;
                // redirect to verification; (send cookies 1 and 2)
                TODO

            case (False, True , True , False): // error (cookie 2 and 3 illegal)
            case (False, True , True , True ): // error (missing cookie 1)
            case (True , True , True , False): // error (cookies 2 and 3 illegal)
                // TODO mark the passed in cookies as suspect
                return sessionManager.createSession(requestInfo), True;

            case (True , True , True , True ):
                // validate cookie 1 & 2 & 3 (must be same session)
                TODO
            }
        }
    }