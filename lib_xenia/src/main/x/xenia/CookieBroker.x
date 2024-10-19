import HttpServer.RequestInfo;
import SessionCookie.CookieId;

import web.HttpStatus;

import web.sessions.Broker;



/**
 * The `CookieBroker` is a traditional [Session] [Broker] that uses cookies and HTTP redirects to
 * establish and verify HTTP sessions for browser-based (and other cookie- and redirect-friendly)
 * HTTP clients.
 */
service CookieBroker
        implements Broker {

    // ----- constructors --------------------------------------------------------------------------

    construct(SessionManager sessionManager) {
        this.sessionManager      = sessionManager;
        this.plainTextCookieName = sessionManager.plainTextCookieName;
        this.encryptedCookieName = sessionManager.encryptedCookieName;
        this.consentCookieName   = sessionManager.consentCookieName;
    }

    @Override
    construct(CookieBroker that) {
        this.sessionManager      = that.sessionManager;
        this.plainTextCookieName = that.plainTextCookieName;
        this.encryptedCookieName = that.encryptedCookieName;
        this.consentCookieName   = that.consentCookieName;
    }

    // ----- Broker interface ----------------------------------------------------------------------

    /**
     * The session manager.
     */
    protected @Final SessionManager sessionManager;

    /**
     * The name of the session cookie for non-TLS traffic (copied from the session manager).
     */
    protected @Final String plainTextCookieName;

    /**
     * The name of the session cookie for TLS traffic (copied from the session manager).
     */
    protected @Final String encryptedCookieName;

    /**
     * The name of the persistent session cookie (copied from the session manager).
     */
    protected @Final String consentCookieName;

    // ----- Broker interface ----------------------------------------------------------------------

    @Override
    conditional (Session, ResponseOut?) findSession(RequestIn request) {
        // TODO
        return False;
    }

    @Override
    conditional (Session?, ResponseOut?) requireSession(RequestIn request) {
        // TODO
        return False;

//        // handle the error result (no session returned)
//        if (result.is(HttpStatus)) {
//            response = new SimpleResponse(result);
//            for (CookieId cookieId : CookieId.from(eraseCookies)) {
//                response.header.add(Header.SetCookie, eraseCookie(cookieId));
//            }
//            break ProcessRequest;
//        }
//
//        // if we're already redirecting, defer the version increment that comes from an IP
//        // address or user agent change; let's first verify that the user agent has the
//        // correct cookies for the current version of the session
//        if (!redirect) {
//            // check for any IP address and/or user agent change in the connection
//            redirect = session.updateConnection_(requestInfo.userAgent ?: "<unknown>",
//                    requestInfo.clientAddress);
//        }
//
//        if (redirect) {
//            Int|HttpStatus redirectResult = session.prepareRedirect_(requestInfo);
//            if (redirectResult.is(HttpStatus)) {
//                RequestIn request = new Http1Request(requestInfo, sessionBroker);
//                response = catalog.webApp.handleUnhandledError^(session, request, redirectResult);
//                break ProcessRequest;
//            }
//
//            response = new SimpleResponse(TemporaryRedirect);
//            Int    redirectId = redirectResult;
//            Header header     = response.header;
//            Byte   desired    = session.desiredCookies_(tls);
//            for (CookieId cookieId : CookieId.values) {
//                if (desired & cookieId.mask != 0) {
//                    if ((SessionCookie cookie, Time? sent, Time? verified)
//                            := session.getCookie_(cookieId), verified == Null) {
//                        header.add(Header.SetCookie, cookie.toString());
//                        session.cookieSent_(cookie);
//                    }
//                } else if (eraseCookies & cookieId.mask != 0) {
//                    header.add(Header.SetCookie, eraseCookie(cookieId));
//                }
//            }
//
//            // come back to verify that the user agent received and subsequently sent the
//            // cookies
//            Uri newUri = new Uri(path=
//                    $"{catalog.services[0].path}/session/{redirectId}/{session.version_}");
//            header.put(Header.Location, newUri.toString());
//            break ProcessRequest;
    }

    /**
     * Use request cookies to identify an existing session, performing only absolutely necessary
     * validations. No session validation, security checks, etc. are performed. This method does not
     * attempt to redirect, create or destroy a session, etc.
     *
     * @param request  the [RequestInfo] for the current request
     *
     * @return the [SessionImpl] indicated by the request, or Null if none
     */
    private SessionImpl? getSessionOrNull(RequestInfo request) {
        if (String[] cookies := request.getHeaderValuesForName(Header.Cookie)) {
            for (String cookieHeader : cookies) {
                for (String cookie : cookieHeader.split(';')) {
                    if (Int delim := cookie.indexOf('='), lookupCookie(cookie[0 ..< delim].trim())) {
                        if (SessionImpl session := sessionManager.getSessionByCookie(
                                cookie.substring(delim+1).trim())) {
                            return session;
                        } else {
                            return Null;
                        }
                    }
                }
            }
        }

        return Null;
    }

// TODO move to cookie broker
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
            ensureSession(RequestInfo requestInfo) {
        (String? txtTemp, String? tlsTemp, String? consent, Int eraseCookies)
                = extractSessionCookies(requestInfo);
        if (eraseCookies != 0) {
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
        if (present == CookieId.None) {
            return createSession(requestInfo), True, CookieId.None;
        }

        // based on whether the request came in on plain-text or TLS, it might be illegal for some
        // of the cookies to have been included
        Byte accept  = tls ? CookieId.All : CookieId.NoTls;
        Byte illegal = present & ~accept;
        if (illegal != 0) {
            // these are cases that we have no intelligent way to handle: the browser has sent
            // cookies that should not have been sent. we can try to log the error against the
            // session, but the only obvious thing to do at this point is to delete the unacceptable
            // cookies and start over
            SessionImpl? sessionNoTls = Null;
            sessionNoTls := sessionManager.getSessionByCookie(txtTemp?);

            for (CookieId cookieId : CookieId.from(illegal)) {
                String cookie = switch (cookieId) {
                    case PlainText: txtTemp;
                    case Encrypted: tlsTemp;
                    case Consent:   consent;
                } ?: assert as $"missing {cookieId} cookie";

                if (SessionImpl session := sessionManager.getSessionByCookie(cookie)) {
                    if (sessionNoTls?.internalId_ != session.internalId_) {
                        // the sessions are different, so report to both sessions that the other
                        // cookie was for the wrong session
                        suspectCookie(requestInfo, sessionNoTls, cookie  , WrongSession);
                        suspectCookie(requestInfo, session     , txtTemp?, WrongSession);
                    } else {
                        // report to the session (from the illegal cookie) that that cookie was
                        // unexpected
                        suspectCookie(requestInfo, session, cookie, Unexpected);
                    }
                }
            }

            return BadRequest, False, illegal;
        }

        // perform a "fast path" check: if all the present cookies point to the same session
        if ((SessionImpl session, Boolean redirect) := findSession(txtTemp, tlsTemp, consent)) {
            // check for split
            if ((HttpStatus|SessionImpl result, _, eraseCookies)
                    := replaceSession(requestInfo, session, present)) {
                if (result.is(HttpStatus)) {
                    // failed to create a session, which is reported back as an error, and
                    // we'll erase any non-persistent cookies (we don't erase the persistent
                    // cookie because it may contain consent info)
                    return result, False, eraseCookies;
                }

                session  = result;
                redirect = True;
            } else {
                Byte desired = session.desiredCookies_(tls);
                session.ensureCookies_(desired);
                redirect    |= desired != present;
                eraseCookies = present & ~desired;
            }

            return session, redirect, eraseCookies;
        }

        // look up the session by each of the available cookies
        SessionImpl? txtSession = sessionManager.getSessionByCookie(txtTemp?)? : Null;
        SessionImpl? tlsSession = sessionManager.getSessionByCookie(tlsTemp?)? : Null;
        SessionImpl? conSession = sessionManager.getSessionByCookie(consent?)? : Null;

        // common case: there's a persistent session that we found, but there was already a
        // temporary session created with a plain text cookie before the user agent connected over
        // TLS and sent us the persistent cookie
        if (conSession != Null) {
            (Match_ match, SessionCookie? cookie) = conSession.cookieMatches_(Consent, consent ?: assert);
            switch (match) {
            case Correct:
            case Older:
            case Newer:
                // if the session specified by the plain text cookie is different from the
                // session specified by the persistent cookie, then it may have more recent
                // information that we want to retain (i.e. add to the session specified by
                // the persistent cookie)
                Boolean shouldMerge = txtSession?.internalId_ != conSession.internalId_ : False;

                // there should NOT be a TLS cookie with a different ID from the persistent cookie,
                // since they both go over TLS
                if (tlsSession?.internalId_ != conSession.internalId_) {
                    suspectCookie(requestInfo, conSession, tlsTemp ?: assert, WrongSession);
                }

                // replace (split) the session if the session has been abandoned
                eraseCookies = CookieId.None;
                Boolean split = False;
                if ((HttpStatus|SessionImpl result, _, eraseCookies) := replaceSession(
                        requestInfo, conSession, present)) {
                    if (result.is(HttpStatus)) {
                        // failed to create a session, which is reported back as an error, and
                        // we'll erase any non-persistent cookies (we don't erase the persistent
                        // cookie because it may contain consent info)
                        return result, False, eraseCookies;
                    }

                    conSession = result;
                    split      = True;
                }

                if (shouldMerge) {
                    conSession.merge_(txtSession?);
                }

                if (!split) {
                    // treat this event as significant enough to warrant a new session version;
                    // this helps to force the bifurcation of the session in the case of cookie
                    // theft
                    conSession.incrementVersion_();
                }

                // redirect to make sure all cookies are up to date
                return conSession, True, eraseCookies;
            }
        }

        // remaining cases: either no session was found (even if there are some session cookies), or
        // it's possible that sessions have been lost (e.g. from a server restart), and then the
        // user agent reestablished a connection on a non-TLS connection and the server created a
        // new session in response, and then the user agent just now switched to a TLS connection
        // and passed its older TLS cookie(s) from the old lost session (in which case we need to
        // just discard those abandoned cookies, after grabbing the consent data from the persistent
        // cookie, if there is any)
        if (tlsSession == Null && conSession == Null) {
            // create a new session if necessary
            HttpStatus|SessionImpl result = txtSession == Null
                    ? sessionManager.createSession(requestInfo)
                    : txtSession;
            if (result.is(HttpStatus)) {
                // failed to create a session, which is reported back as an error, and we'll erase
                // any non-persistent cookies (we don't erase the persistent cookie because it may
                // contain consent info)
                return result, False, present & CookieId.BothTemp;
            }

            SessionImpl session = result;
            if ((result, _, eraseCookies) := replaceSession(requestInfo, session, present)) {
                if (result.is(HttpStatus)) {
                    // failed to create a session, which is reported back as an error, and we'll erase
                    // any non-persistent cookies (we don't erase the persistent cookie because it may
                    // contain consent info)
                    return result, False, eraseCookies;
                }

                session = result;
            }

            // don't lose previous consent information if there was any in the persistent cookie
            // (the "consent" variable holds the text content of the persistent cookie, and it
            // implies that the user agent had previously specified "exclusive agent" mode)
            if (consent != Null) {
                try {
                    SessionCookie cookie = new SessionCookie(sessionManager, consent);
                    session.cookieConsent  = cookie.consent;
                    session.exclusiveAgent = True;
                } catch (Exception _) {}
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
        if (result.is(SessionImpl)) {
            Byte desired = result.desiredCookies_(tls);
            return result, True, present & ~desired;
        } else {
            return result, False, present;
        }
    }

// TODO move to cookie broker
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
    (String? txtTemp, String? tlsTemp, String? consent, Int failures)
            extractSessionCookies(RequestInfo requestInfo) {
        String? txtTemp  = Null;
        String? tlsTemp  = Null;
        String? consent  = Null;
        Int     failures = 0;
        Boolean tls      = requestInfo.tls;

        if (String[] cookies := requestInfo.getHeaderValuesForName(Header.Cookie)) {
            for (String cookieHeader : cookies) {
                NextCookie: for (String cookie : cookieHeader.split(';')) {
                    if (Int      delim    := cookie.indexOf('='),
                        CookieId cookieId := lookupCookie(cookie[0 ..< delim].trim())) {
                        String? oldValue;
                        String  newValue = cookie.substring(delim+1).trim();
                        switch (cookieId) {
                        case PlainText:
                            oldValue = txtTemp;
                            txtTemp  = newValue;
                            break;

                        case Encrypted:
                            // firefox bug: TLS-only cookies sent to localhost when TLS is false
                            if (!tls && requestInfo.clientAddress.loopback) {
                                continue NextCookie;
                            }

                            oldValue = tlsTemp;
                            tlsTemp  = newValue;
                            break;

                        case Consent:
                            // firefox bug: TLS-only cookies sent to localhost when TLS is false
                            if (!tls && requestInfo.clientAddress.loopback) {
                                continue NextCookie;
                            }

                            oldValue = consent;
                            consent  = newValue;
                            break;
                        }

                        if (oldValue? != newValue) {
                            // duplicate cookie detected but with a different value; this should be
                            // impossible
                            failures |= cookieId.mask;
                        }

                        if (!tls && cookieId.tlsOnly) {
                            // user agent should have hidden the cookie; this should be impossible
                            failures |= cookieId.mask;
                        }
                    }
                }
            }
        }

        return txtTemp, tlsTemp, consent, failures;
    }

// TODO move to cookie broker
    /**
     * Determine if a cookie name indicates a session cookie.
     *
     * @param cookieName  the name of the cookie
     *
     * @return True iff the name indicates a session cookie
     * @return (conditional) the CookieId
     */
    conditional CookieId lookupCookie(String cookieName) {
        if (cookieName == plainTextCookieName) {
            return True, PlainText;
        } else if (cookieName == encryptedCookieName) {
            return True, Encrypted;
        } else if (cookieName == consentCookieName) {
            return True, Consent;
        } else {
            return False;
        }
    }

// TODO move to cookie broker
    /**
     * Obtain the session cookie name for the specified cookie id.
     *
     * @param id  the session CookieId
     *
     * @return the name of the cookie to use for the specified CookieId
     */
    String cookieNameFor(CookieId id) {
        return switch (id) {
            case PlainText: plainTextCookieName;
            case Encrypted: encryptedCookieName;
            case Consent:   consentCookieName;
        };
    }

// TODO move to cookie broker?
    /**
     * Obtain a header entry that will erase the session cookie for the specified cookie id.
     *
     * @param id  the session CookieId
     *
     * @return a header entry that will erase the session cookie
     */
    String eraseCookie(CookieId id) {
        return $"{cookieNameFor(id)}=; expires=Thu, 01 Jan 1970 00:00:00 GMT{id.attributes}";
    }

// TODO move to cookie broker
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
            findSession(String? txtTemp, String? tlsTemp, String? consent) {
        SessionImpl? session  = Null;
        Boolean      redirect = False;

        for (CookieId cookieId : PlainText..Consent) {
            String? cookieText = switch(cookieId) {
                case PlainText: txtTemp;
                case Encrypted: tlsTemp;
                case Consent  : consent;
            };

            if (cookieText == Null) {
                continue;
            }

            if (SessionImpl current := sessionManager.getSessionByCookie(cookieText)) {
                if (session == Null) {
                    session = current;
                } else if (session.internalId_ != current.internalId_) {
                    return False;
                }

                (Match_ match, SessionCookie? cookie) = current.cookieMatches_(cookieId, cookieText);
                switch (match) {
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
            } else {
                return False;
            }
        }

        return session == Null
                ? False
                : (True, session, redirect);
    }


    /**
     * Using the request information, create a new session object.
     *
     * @param requestInfo  the incoming request
     *
     * @return the session object, or a `4xx`-range `HttpStatus` that indicates a failure
     */
    private HttpStatus|SessionImpl createSession(RequestInfo requestInfo) {
        HttpStatus|SessionImpl result = sessionManager.createSession(requestInfo);
        if (result.is(HttpStatus)) {
            return result;
        }

        SessionImpl session = result;
        session.ensureCookies_(requestInfo.tls ? CookieId.BothTemp : CookieId.NoTls);
        return session;
    }

    /**
     * Given the request information and a session, determine if that session needs to be replaced
     * with a different session due to a previous split.
     *
     * @param requestInfo  the incoming request
     * @param session      the session implied by that request
     * @param present      the cookies that are present in the request
     *
     * @return               True iff the session needs to be replaced
     * @return result        (conditional) the session object, or a `4xx`-range `HttpStatus` that
     *                       indicates a failure
     * @return redirect      (conditional) True indicates that redirection is required to update the
     *                       session cookies
     * @return eraseCookies  (conditional) a bit mask of the cookie ID ordinals to delete
     */
    private conditional (SessionImpl|HttpStatus result, Boolean redirect, Int eraseCookies)
            replaceSession(RequestInfo requestInfo, SessionImpl session, Byte present) {
        // check if the session itself has been abandoned and needs to be replaced with a
        // new session as the result of a session split
        if (HttpStatus|SessionImpl result := session.isAbandoned_(requestInfo)) {
            if (result.is(HttpStatus)) {
                return True, result, False, CookieId.None;
            }

            // some cookies from the old session may need to be erased if they are not used
            // by the new session
            session = result;
            return True, session, True, present & ~session.desiredCookies_(requestInfo.tls);
        }

        return False;
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
                               SessionImpl.Match_ failure) {
        // TODO CP
        @Inject Console console;
        console.print($|Suspect cookie {value.quoted()} with {failure=}\
                       | for session {session.internalId_}
                       | with known cookies {CookieId.from(session.knownCookies_)}
                     );
    }
}
