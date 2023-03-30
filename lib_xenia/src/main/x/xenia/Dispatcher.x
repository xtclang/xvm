import Catalog.EndpointInfo;
import Catalog.MethodInfo;
import Catalog.WebServiceInfo;

import HttpServer.RequestContext;
import HttpServer.RequestInfo;

import SessionCookie.CookieId;

import SessionImpl.Match_;

import web.CookieConsent;
import web.Endpoint;
import web.ErrorHandler;
import web.Header;
import web.HttpStatus;

import web.responses.SimpleResponse;

import web.security.Authenticator;

import net.UriTemplate.UriParameters;


/**
 * Dispatcher is responsible for finding an endpoint, creating a call chain for an HTTP request and
 * invoking it on a corresponding WebService.
 */
service Dispatcher(Catalog        catalog,
                   BundlePool     bundlePool,
                   SessionManager sessionManager,
                   Authenticator  authenticator,
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
     * The user authenticator.
     */
    protected Authenticator authenticator;

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

            RequestInfo         requestInfo = new RequestInfo(httpServer, context, tls);
            ChainBundle?        bundle      = Null;
            @Future ResponseOut response;
            ProcessRequest: if (serviceInfo == Null)
                {
                RequestIn request = new Http1Request(requestInfo, []);
                Session?  session = getSessionOrNull(httpServer, context);

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
                    HttpStatus|ResponseOut|String result = svc.handle(uriString, requestInfo);
                    if (result.is(String))
                        {
                        uriString = result;
                        bundlePool.releaseBundle(bundle);
                        continue FromTheTop;
                        }

                    response = result.is(ResponseOut)
                            ? result
                            : new SimpleResponse(result);

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
                    RequestIn   request     = new Http1Request(requestInfo, []);
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

                    response.header.put(Header.Location, requestInfo.convertToTlsUrl());
                    break ProcessRequest;
                    }

                // either a valid existing session is identified by the request, or a session will
                // be created and a redirect to verify the session's successful creation will occur,
                // which will then redirect back to this same request
                (HttpStatus|SessionImpl result, Boolean redirect, Int eraseCookies) = ensureSession(requestInfo);
                if (result.is(HttpStatus))
                    {
                    response = new SimpleResponse(result);
                    for (CookieId cookieId : CookieId.from(eraseCookies))
                        {
                        response.header.add(Header.SetCookie, cookieId.eraser);
                        }
                    break ProcessRequest;
                    }

                // check for any IP address and/or user agent change in the connection
                SessionImpl session = result;
                redirect |= session.updateConnection_(extractUserAgent(requestInfo), requestInfo.getClientAddress());
                if (redirect)
                    {
                    Int|HttpStatus redirectResult = session.prepareRedirect_(requestInfo);
                    if (redirectResult.is(HttpStatus))
                        {
                        RequestIn request = new Http1Request(requestInfo, []);
                        response = catalog.webApp.handleUnhandledError^(session, request, redirectResult);
                        break ProcessRequest;
                        }

                    response = new SimpleResponse(TemporaryRedirect);
                    Int    redirectId = redirectResult;
                    Header header     = response.header;
                    Byte   desired    = session.desiredCookies_(tls);
                    for (CookieId cookieId : CookieId.values)
                        {
                        if (desired & cookieId.mask != 0)
                            {
                            if ((SessionCookie cookie, Time? sent, Time? verified)
                                    := session.getCookie_(cookieId), verified == Null)
                                {
                                header.add(Header.SetCookie, cookie.toString());
                                session.cookieSent_(cookie);
                                }
                            }
                        else if (eraseCookies & cookieId.mask != 0)
                            {
                            header.add(Header.SetCookie, cookieId.eraser);
                            }
                        }

                    // come back to verify that the user agent received and subsequently sent the
                    // cookies
                    Uri newUri = new Uri(path=$"{catalog.services[0].path}/session/{redirectId}");
                    header.put(Header.Location, newUri.toString());
                    break ProcessRequest;
                    }

                // create a Request object to represent the incoming request
                RequestIn request = new Http1Request(requestInfo, uriParams);

                // each endpoint has a required trust level, and the session knows its own trust
                // level; if the endpoint requirement is higher, then we have to re-authenticate
                // the user agent; the server must respond in a manner that causes the client to
                // authenticate, including (but not limited to) any of the following manners:
                // * with the `Unauthorized` error code (and related information) that indicates
                //   that the client must authenticate itself
                // * with a redirect to a URL that provides the necessary login user interface
                if (endpoint.requiredTrust > session.trustLevel)
                    {
                    Boolean|ResponseOut success = authenticator.authenticate(request, session, endpoint.method);
                    switch (success)
                        {
                        case True:
                            // Authenticator has verified that the user is authenticated (the
                            // Authenticator should have already updated the session accordingly)
                            if (endpoint.requiredTrust > session.trustLevel)
                                {
                                // the user is authenticated, but the user doesn't have the
                                // necessary security access
                                response = new SimpleResponse(Forbidden);
                                break ProcessRequest;
                                }

                            // the user is authenticated and has the necessary security access;
                            // continue processing the request
                            break;

                        case False:
                            // authentication didn't just fail, but it has been disallowed; respond
                            // with an HTTP "Forbidden"
                            response = new SimpleResponse(Forbidden);
                            break ProcessRequest;

                        default:
                            // "success" isn't a Boolean, it's an HTTP response; send the response
                            // back to the client as the next step in authenticating the client
                            response = success.as(ResponseOut);
                            break ProcessRequest;
                        }
                    }

                // this is the "normal" i.e. "actual" request processing
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
        if (String[] cookies := httpServer.getHeaderValuesForName(context, Header.Cookie))
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
     * @return result        the session object, or a `4xx`-range `HttpStatus` that indicates a
     *                       failure
     * @return redirect      True indicates that redirection is required to update the session
     *                       cookies
     * @return eraseCookies  a bit mask of the cookie ID ordinals to delete
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

        Boolean tls = requestInfo.tls;

        // 1 2 3TLS description/action
        // - - - -  ------------------
        //       0  create new session; redirect to verification (send cookie 1)
        //       1  create new session; redirect to verification (send cookies 1 and 2)
        // x     0  validate cookie 1
        // x     1  validate cookie 1 & verify that cookie 2/3 were NOT already sent & verified (if
        //          they were, then this is an error, because it indicates the likely theft of the
        //          plain text cookie); redirect to verification (send cookies 1 and 2, and 3 if
        //          [cookieConsent] has been set)
        //   x   0* error (no TLS, so cookie 2 is illegally present; also missing cookie 1)
        //   x   1  error (missing cookie 1)
        // x x   0* error (no TLS, so cookie 2 is illegally present)
        // x x   1  validate cookie 1 & 2; if [cookieConsent] has been set, redirect to verification
        //     x 0*  error (no TLS, so cookie 3 is illegally present)
        //     x 1  validate cookie 3; assume temporary cookies absent due to user agent discarding
        //          temporary cookies; redirect to verification (send cookies 1 and 2)
        // x   x 0* error (no TLS, so cookie 3 is illegally present)
        // x   x 1  validate cookie 1 & 3 (1 must be newer than 3), and verify that cookie 2 was NOT
        //          already sent & verified; merge session for cookie 1 into session for cookie 3;
        //          redirect to verification; (send cookies 1 and 2)
        //   x x 0* error (no TLS, so cookie 2 and 3 are illegally present)
        //   x x 1  error (missing cookie 1)
        // x x x 0* error (no TLS, so cookies 2 and 3 are illegally present)
        // x x x 1  validate cookie 1 & 2 & 3 (must be same session)
        //
        // * handled by the following "illegal" check

        // no cookies implies that we must create a new session
        Byte present = (txtTemp == Null ? 0 : CookieId.NoTls)
                     | (tlsTemp == Null ? 0 : CookieId.TlsTemp)
                     | (consent == Null ? 0 : CookieId.OnlyConsent);
        if (present == CookieId.None)
            {
            return createSession(requestInfo), True, CookieId.None;
            }

        // based on whether the request came in on plain-text or TLS, it might be illegal for some
        // of the cookies to have been included
        Byte accept  = tls ? CookieId.All : CookieId.NoTls;
        Byte illegal = present & ~accept;
        if (illegal != 0)
            {
            // these are cases that we have no intelligent way to handle: the browser has sent
            // cookies that should not have been sent. we can try to log the error against the
            // session, but the only obvious thing to do at this point is to delete the unacceptable
            // cookies and start over
            SessionImpl? sessionNoTls = Null;
            sessionNoTls := sessionManager.getSessionByCookie(txtTemp?);

            for (CookieId cookieId : CookieId.from(illegal))
                {
                String cookie = switch (cookieId)
                    {
                    case PlainText: txtTemp;
                    case Encrypted: tlsTemp;
                    case Consent:   consent;
                    } ?: assert as $"missing {cookieId} cookie";

                if (SessionImpl session := sessionManager.getSessionByCookie(cookie))
                    {
                    if (sessionNoTls?.internalId_ != session.internalId_)
                        {
                        // the sessions are different, so report to both sessions that the other
                        // cookie was for the wrong session
                        suspectCookie(requestInfo, sessionNoTls, cookie  , WrongSession);
                        suspectCookie(requestInfo, session     , txtTemp?, WrongSession);
                        }
                    else
                        {
                        // report to the session (from the illegal cookie) that that cookie was
                        // unexpected
                        suspectCookie(requestInfo, session, cookie, Unexpected);
                        }
                    }
                }

            return BadRequest, False, illegal;
            }

        // perform a "fast path" check: if all the present cookies point to the same session
        if ((SessionImpl session, Boolean redirect) := findSession(txtTemp, tlsTemp, consent))
            {
            Byte desired = session.desiredCookies_(tls);
            session.ensureCookies_(desired);
            return session, redirect || desired != present, present & ~desired;
            }

        // look up the session by each of the available cookies
        SessionImpl? txtSession = Null;
        SessionImpl? tlsSession = Null;
        SessionImpl? conSession = Null;

        txtSession := sessionManager.getSessionByCookie(txtTemp?);
        tlsSession := sessionManager.getSessionByCookie(tlsTemp?);
        conSession := sessionManager.getSessionByCookie(consent?);

        // common case: there's a persistent session that we found, but there was already a
        // temporary session created with a plain text cookie before the user agent connected over
        // TLS and sent us the persistent cookie
        if (conSession != Null)
            {
            (Match_ match, SessionCookie? cookie) = conSession.cookieMatches_(Consent, consent ?: assert);
            switch (match)
                {
                case Correct:
                case Older:
                case Newer:
                    // if the session specified by the plain text cookie is different from the session
                    // specified by the persistent cookie, then it may have more recent information that we
                    // want to retain (i.e. add to the session specified by the persistent cookie)
                    if (txtSession?.internalId_ != conSession.internalId_)
                        {
                        conSession.merge_(txtSession);
                        }

                    // there should NOT be a TLS cookie with a different ID from the persistent cookie,
                    // since they both go over TLS
                    if (tlsSession?.internalId_ != conSession.internalId_)
                        {
                        suspectCookie(requestInfo, conSession, tlsTemp ?: assert, WrongSession);
                        }

                    // treat this event as significant enough to warrant a new session version; this
                    // helps to force the bifurcation of the session in the case of cookie theft
                    conSession.incrementVersion_();

                    // redirect to make sure all cookies are up to date
                    return conSession, True, CookieId.None;
                }
            }

        // remaining cases: either no session was found (even if there are some session cookies), or
        // it's possible that sessions have been lost (e.g. from a server restart), and then the
        // user agent reestablished a connection on a non-TLS connection and the server created a
        // new session in response, and then the user agent just now switched to a TLS connection
        // and passed its older TLS cookie(s) from the old lost session (in which case we need to
        // just discard those abandoned cookies, after grabbing the consent data from the persistent
        // cookie, if there is any)
        if (tlsSession == Null && conSession == Null)
            {
            // create a new session if necessary
            HttpStatus|SessionImpl result = txtSession == Null
                    ? sessionManager.createSession(requestInfo)
                    : txtSession;
            if (result.is(HttpStatus))
                {
                // failed to create a session, which is reported back as an error, and we'll erase
                // any non-persistent cookies (we don't erase the persistent cookie because it may
                // contain consent info)
                return result, False, present & CookieId.BothTemp;
                }

            // don't lose previous consent information if there was any in the persistent cookie
            // (the "consent" variable holds the text content of the persistent cookie, and it
            // implies that the user agent had previously specified "exclusive agent" mode)
            SessionImpl session = result;
            if (consent != Null)
                {
                try
                    {
                    SessionCookie cookie = new SessionCookie(sessionManager, consent);
                    session.cookieConsent  = cookie.consent;
                    session.exclusiveAgent = True;
                    }
                catch (Exception _) {}
                }

            // create the desired cookies, and delete the undesired cookies
            Byte desired = session.desiredCookies_(tls);
            session.ensureCookies_(desired);
            return session, True, present & ~desired;
            }

        // every other case: blow it all up and start over
        suspectCookie(requestInfo, txtSession?, tlsTemp?, WrongSession);
        suspectCookie(requestInfo, txtSession?, consent?, WrongSession);
        suspectCookie(requestInfo, tlsSession?, txtTemp?, WrongSession);
        suspectCookie(requestInfo, tlsSession?, consent?, WrongSession);

        HttpStatus|SessionImpl result = createSession(requestInfo);
        Byte desired = result.is(SessionImpl) ? result.desiredCookies_(tls) : CookieId.None;
        return result, True, present & ~desired;
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

        if (String[] cookies := requestInfo.getHeaderValuesForName(Header.Cookie))
            {
            for (String cookieHeader : cookies)
                {
                NextCookie: for (String cookie : cookieHeader.split(';'))
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
                                // firefox bug: TLS-only cookies sent to localhost when TLS is false
                                if (!tls && requestInfo.getClientAddress().loopback)
                                    {
                                    continue NextCookie;
                                    }

                                oldValue = tlsTemp;
                                tlsTemp  = newValue;
                                break;

                            case Consent:
                                // firefox bug: TLS-only cookies sent to localhost when TLS is false
                                if (!tls && requestInfo.getClientAddress().loopback)
                                    {
                                    continue NextCookie;
                                    }

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
     * Look up the session specified by the provided cookies.
     *
     * @param txtTemp   the temporary plaintext session cookie value, or Null if absent
     * @param tlsTemp   the temporary TSL-protected session cookie value, or Null if absent
     * @param consent   the persistent consent cookie value, or Null if absent
     *
     * @return True iff the passed cookies are valid and all point to the same session
     * @return (conditional) the session, iff the provided cookies all point to the same session
     * @return (conditional) True if the user agent's cookies need to be updated
     */
    conditional (SessionImpl session, Boolean redirect)
            findSession(String? txtTemp, String? tlsTemp, String? consent)
        {
        SessionImpl? session  = Null;
        Boolean      redirect = False;

        for (CookieId cookieId : PlainText..Consent)
            {
            String? cookieText = switch(cookieId)
                {
                case PlainText: txtTemp;
                case Encrypted: tlsTemp;
                case Consent  : consent;
                };

            if (cookieText == Null)
                {
                continue;
                }

            if (SessionImpl current := sessionManager.getSessionByCookie(cookieText))
                {
                if (session == Null)
                    {
                    session = current;
                    }
                else if (session.internalId_ != current.internalId_)
                    {
                    return False;
                    }

                (Match_ match, SessionCookie? cookie) = current.cookieMatches_(cookieId, cookieText);
                switch (match)
                    {
                    case Correct:
                        break;

                    case Older:
                        // the redirect will update the cookies to the current one
                        redirect = True;
                        break;

                    case Newer:
                        // this can happen if the server crashed after incrementing the session
                        // version and after sending the cookie(s) back to the user agent, but
                        // before persistently recording the updated session information
                        assert cookie != Null;
                        current.incrementVersion_(cookie.version+1);
                        redirect = True;
                        break;

                    default:
                        return False;
                    }
                }
            else
                {
                return False;
                }
            }

        return session == Null
                ? False
                : (True, session, redirect);
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
     * This method is invoked when a cookie is determined to be suspect.
     *
     * @param requestInfo  the incoming request
     * @param session      the session against which the bad cookie is suspected
     * @param value        the cookie value
     * @param failure      the [SessionImpl.Match_] value indicating the failure
     */
    private void suspectCookie(RequestInfo        requestInfo,
                               SessionImpl        session,
                               String             value,
                               SessionImpl.Match_ failure)
        {
        // TODO CP
        @Inject Console console;
        console.print($|Suspect cookie {value.quoted()} with {failure=}\
                       | for session {session.internalId_}
                       | with known cookies {CookieId.from(session.knownCookies_)}
                     );
        }
    }