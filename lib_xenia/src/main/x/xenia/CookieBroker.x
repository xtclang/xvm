import HttpServer.RequestInfo;

import web.sessions.Broker as SessionBroker;

/**
 * The `CookieBroker` is a traditional session broker that uses cookies and HTTP redirects to
 * establish and verify HTTP sessions for browser-based (and other cookie- and redirect-friendly)
 * HTTP clients.
 */
service CookieBroker(SessionManager sessionMgr)
        implements SessionBroker {

    // ----- constructors --------------------------------------------------------------------------

    @Override
    construct(CookieBroker that) {
        this.sessionMgr = that.sessionMgr;
    }

    // ----- Broker interface ----------------------------------------------------------------------

    @Override
    conditional Session findSession(RequestIn request) {
        // TODO
        return False;
    }

    @Override
    conditional (Session|ResponseOut) requireSession(RequestIn request) {
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
}
