import Catalog.EndpointInfo;
import Catalog.MethodInfo;
import Catalog.WebServiceInfo;

import HttpServer.RequestContext;
import HttpServer.RequestInfo;

import SessionCookie.CookieId;

import web.CookieConsent;
import web.ErrorHandler;
import web.Header;
import web.HttpStatus;

import web.responses.SimpleResponse;

import net.UriTemplate.UriParameters;


/**
 * Dispatcher is responsible for finding an endpoint, creating a call chain for an HTTP request and
 * invoking it on a corresponding WebService.
 */
service Dispatcher(Catalog          catalog,
                   BundlePool       bundlePool,
                   SessionManager   sessionManager,
                  )
    {
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
    void dispatch(HttpServer     httpServer,
                  RequestContext context,
                  Boolean        tls,
                  String         uriString,
                  String         methodName,
                 )
        {
        FromTheTop: while (True)
            {
            // select the service to delegate request processing to; the service infos are sorted
            // with the most specific path first (so the first path match wins)
            Int             uriSize     = uriString.size;
            WebServiceInfo? serviceInfo = Null;
            for (WebServiceInfo info : catalog.services)
                {
                // the info.path, which represents a "directory", never ends with '/' (see Catalog),
                // but a legitimately matching uriString may or may not have '/' at the end; for
                // example: if the path is "test", then uri values such as "test/s", "test/" and
                // "test" should all match (the last two would be treated as equivalent), but
                // "tests" should not

                String path     = info.path;
                Int    pathSize = path.size;
                if (uriSize == pathSize)
                    {
                    if (uriString == path)
                        {
                        serviceInfo = info;
                        uriString   = "";
                        break;
                        }
                    }
                else if (pathSize == 1) // root path ("/") matches everything
                    {
                    serviceInfo = info;
                    uriString   = uriString.substring(1);
                    }
                else if (uriSize > pathSize && uriString[pathSize] == '/' && uriString.startsWith(path))
                    {
                    serviceInfo = info;
                    uriString   = uriString.substring(pathSize + 1);
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
                if (wsid == 0)
                    {
                    // this is a redirect or other system service call
                    bundle = bundlePool.allocateBundle(wsid);

                    SystemService svc = bundle.ensureWebService(wsid).as(SystemService);
                    HttpStatus|Response|String result = svc.handle(uriString, requestInfo);
                    if (result.is(String))
                        {
                        uriString = result;
                        continue FromTheTop;
                        }

                    response = result.is(Response)
                            ? result
                            : new SimpleResponse(result).makeImmutable();

                    break ProcessRequest;
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

                if (uriString == "")
                    {
                    uriString = "/";
                    }

                EndpointInfo  endpoint;
                UriParameters uriParams = [];
                FindEndpoint:
                    {
                    Uri uri;
                    try
                        {
                        uri = new Uri(path=uriString, query=query, fragment=fragment);
                        }
                    catch (Exception e)
                        {
                        response = new SimpleResponse(BadRequest);
                        break ProcessRequest;
                        }

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

                // if the endpoint requires HTTPS (or some other form of TLS), the server responds
                // with a redirect to a URL that uses a TLS-enabled protocol
                if (!tls && endpoint.requiresTls)
                    {
                    response = new SimpleResponse(PermanentRedirect);

                    response.header.put(Header.LOCATION, requestInfo.convertToTlsUrl());
                    break ProcessRequest;
                    }

                // either a valid existing session is identified by the request, or a session will
                // be created and a redirect to verify the session's successful creation will occur,
                // which will then redirect back to this same request
                (HttpStatus|SessionImpl result, Boolean redirect, Int eraseCookies) = ensureSession(requestInfo);
                if (result.is(HttpStatus))
                    {
                    Request  request = new Http1Request(requestInfo, []);
                    Session? session = getSessionOrNull(httpServer, context);
                    response = catalog.webApp.handleUnhandledError^(session, request, result);
                    break ProcessRequest;
                    }

                SessionImpl session = result;
                if (redirect)
                    {
                    Int|HttpStatus redirectResult = session.prepareRedirect_(requestInfo);
                    if (redirectResult.is(HttpStatus))
                        {
                        Request request = new Http1Request(requestInfo, []);
                        response = catalog.webApp.handleUnhandledError^(session, request, redirectResult);
                        break ProcessRequest;
                        }

                    Int redirectId = redirectResult;
                    response = new SimpleResponse(TemporaryRedirect);
                    Header header = response.header;
                    assert SessionCookie cookie := session.getCookie_(PlainText);
                    header.add(Header.SET_COOKIE, cookie.toString());

                    if (tls)
                        {
                        assert cookie := session.getCookie_(Encrypted);
                        header.add(Header.SET_COOKIE, cookie.toString());

                        CookieConsent cookieConsent = session.cookieConsent;
                        if (cookieConsent.lastConsent != Null)
                            {
                            assert cookie := session.getCookie_(Consent);
                            header.add(Header.SET_COOKIE, cookie.toString());
                            }
                        }

                    // come back to verify that the user agent received and subsequently sent the
                    // cookies
                    Uri newUri = new Uri(path=$"{catalog.services[0].path}/session/{redirectId}");
                    header.put(Header.LOCATION, newUri.toString());
                    break ProcessRequest;
                    }

                // each endpoint has a required trust level, and the session knows its own trust
                // level; if the endpoint requirement is higher, then we have to re-authenticate
                // the user agent
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
                    console.print("Unhandled exception: " + e);

                    httpServer.send(context, HttpStatus.InternalServerError.code, [], [], []);
                    }
                else
                    {
                    (Int status, String[] names, String[] values, Byte[] body) =
                        Http1Response.prepare(r);

                    httpServer.send(context, status, names, values, body);
                    }
                });

            return;
            }
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
    private (SessionImpl|HttpStatus result, Boolean redirect, Int eraseCookies)
            ensureSession(RequestInfo requestInfo)
        {
        (String? txtTemp, String? tlsTemp, String? consent, Int eraseCookies)
                = extractSessionCookies(requestInfo);
        if (eraseCookies != 0)
            {
            return BadRequest, False, eraseCookies;
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
        switch (txtTemp != Null, tlsTemp != Null, consent != Null, requestInfo.tls)
            {
            case (False, False, False, _):
                // create a new session
                return createSession(requestInfo), True, CookieId.None;

            case (True , False, False, False):  // validate cookie 1
                // validate cookie 1
                assert txtTemp != Null;
                if (SessionImpl session := sessionManager.getSessionByCookie(txtTemp))
                    {
                    (val matches, val cookie) = session.cookieMatches_(PlainText, txtTemp);
                    switch (matches)
                        {
                        case Newer:
                            assert cookie != Null;
                            session.incrementVersion_(cookie.version+1);
                            break;

                        case Corrupt:
                            suspectCookie(requestInfo, txtTemp);
                            break;
                        }

                    return session, matches != Correct, CookieId.None;
                    }

                // session cookie did not identify a session, so assume that the session was
                // valid but has since been destroyed; create a new session
                return createSession(requestInfo), True, CookieId.None;

            case (True , False, False, True ):
                // validate cookie 1 & verify that cookie 2/3 were NOT already sent & verified (if
                // they were, then this is an error, because it indicates either a manual deletion
                // of the tls cookie or a potential theft of the plain text cookie);
                // redirect to verification (send cookies 1 and 2, and 3 if [cookieConsent] has been
                // set)
                assert txtTemp != Null;
                if (SessionImpl session := sessionManager.getSessionByCookie(txtTemp))
                    {
                    if (session.knownCookies_ == CookieId.NoTls)
                        {
                        (val matches, val cookie) = session.cookieMatches_(PlainText, txtTemp);
                        switch (matches)
                            {
                            case Newer:
                                assert cookie != Null;
                                session.incrementVersion_(cookie.version+1);
                                break;

                            case Correct:
                                break;

                            default:
                                suspectCookie(requestInfo, txtTemp);
                                break;
                            }
                        }
                    else
                        {
                        suspectCookie(requestInfo, txtTemp);
                        }
                    session.ensureCookies_(CookieId.BothTemp);
                    return session, True, CookieId.None;
                    }

                // session cookie did not identify a session, so assume that the session was
                // valid but has since been destroyed; create a new session
                return createSession(requestInfo), True, CookieId.None;

            case (False, True , False, False):  // error (cookie 2 illegal; missing cookie 1)
            case (False, True , False, True ):  // error (missing cookie 1)
            case (True , True , False, False):  // error (cookie 2 illegal)
                suspectCookie(requestInfo, txtTemp?);
                suspectCookie(requestInfo, tlsTemp?);
                return createSession(requestInfo), True, CookieId.None;

            case (True , True , False, True ):
                // validate cookie 1 & 2; if [session.cookieConsent] has been set, redirect to
                // verification
                assert txtTemp != Null && tlsTemp != Null;
                if (SessionImpl session := sessionManager.getSessionByCookie(txtTemp))
                    {
                    if (session.knownCookies_ == CookieId.BothTemp)
                        {
                        (val matchesPln, val cookiePln) = session.cookieMatches_(PlainText, txtTemp);
                        (val matchesTls, val cookieTls) = session.cookieMatches_(Encrypted, tlsTemp);
                        switch (matchesPln, matchesTls)
                            {
                            case (Correct, Correct):
                                break;

                            case (Newer,   Newer  ):
                            case (Newer,   Correct):
                            case (Correct, Newer  ):
                                assert cookiePln != Null && cookieTls != Null;
                                session.incrementVersion_(Int.maxOf(cookiePln.version, cookieTls.version)+1);
                                break;

                            default:
                                suspectCookie(requestInfo, txtTemp);
                                break;
                            }
                        }
                    else
                        {
                        suspectCookie(requestInfo, txtTemp);
                        }
                    Boolean consentRequired = session.cookieConsent != None;
                    session.ensureCookies_(consentRequired ? CookieId.All : CookieId.BothTemp);
                    return session, consentRequired, CookieId.None;
                    }

                // session cookie did not identify a session, so assume that the session was
                // valid but has since been destroyed; create a new session
                return createSession(requestInfo), True, CookieId.None;

            case (False, False, True , False):  // error (cookie 3 illegal)
                suspectCookie(requestInfo, consent?);
                return createSession(requestInfo), True, CookieId.None;

            case (False, False, True , True ):
                // validate cookie 3; assume temporary cookies absent due to user agent discarding
                // temporary cookies; redirect to verification (send cookies 1 and 2)
                TODO

            case (True , False, True , False):  // error (cookie 3 illegal)
                suspectCookie(requestInfo, tlsTemp?);
                suspectCookie(requestInfo, consent?);
                return createSession(requestInfo), True, CookieId.None;

            case (True , False, True , True ):
                // validate cookie 1 & 3 (1 must be newer than 3), and verify that cookie 2 was NOT
                // already sent & verified; merge session for cookie 1 into session for cookie 3;
                // redirect to verification; (send cookies 1 and 2)
                TODO

            case (False, True , True , False): // error (cookie 2 and 3 illegal)
            case (False, True , True , True ): // error (missing cookie 1)
            case (True , True , True , False): // error (cookies 2 and 3 illegal)
                // TODO mark the passed in cookies as suspect
                return createSession(requestInfo), True, CookieId.None;

            case (True , True , True , True ):
                // validate cookie 1 & 2 & 3 (must be same session)
                TODO
            }
        }


    /**
     * Using the request information, find any session cookie values.
     *
     * @param requestInfo  the incoming request
     *
     * @return txtTemp   the temporary plaintext session cookie value, or Null if absent
     * @return tlsTemp   the temporary TSL-protected session cookie value, or Null if absent
     * @return consent   the persistent consent cookie value, or Null if absent
     * @return failures  a bitmask of any errors encountered per `CookieId`
     */
    static (String? txtTemp, String? tlsTemp, String? consent, Int failures)
            extractSessionCookies(RequestInfo requestInfo)
        {
        String? txtTemp  = Null;
        String? tlsTemp  = Null;
        String? consent  = Null;
        Int     failures = 0;
        Boolean tls      = requestInfo.tls;

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
                            failures |= cookieId.mask;
                            }

                        if (!tls && cookieId.tlsOnly)
                            {
                            // user agent should have hidden the cookie; this should be impossible
                            failures |= cookieId.mask;
                            }
                        }
                    }
                }
            }

        return txtTemp, tlsTemp, consent, failures;
        }


    /**
     * Using the request information, look up or create the session object.
     *
     * @param requestInfo  the incoming request
     *
     * @return the session object, or a `4xx`-range `HttpStatus` that indicates a failure
     */
    private HttpStatus|SessionImpl createSession(RequestInfo requestInfo)
        {
        HttpStatus|SessionImpl result = sessionManager.createSession(requestInfo);
        if (result.is(HttpStatus))
            {
            return result;
            }

        SessionImpl session = result;
        session.ensureCookies_(requestInfo.tls ? CookieId.BothTemp : CookieId.NoTls);
        return session;
        }

    /**
     * Using the request information, look up or create the session object.
     *
     * @param requestInfo  the incoming request
     *
     * @return the session object, or a `4xx`-range `HttpStatus` that indicates a failure
     */
    private void suspectCookie(RequestInfo requestInfo, String value)
        {
        TODO($"suspect {value}");
        }
    }