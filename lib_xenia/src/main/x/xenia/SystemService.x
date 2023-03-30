import HttpServer.RequestInfo;
import SessionCookie.CookieId;
import SessionImpl.Match_;

import web.Header;
import web.HttpStatus;
import web.WebService;

import web.codecs.Registry;

import web.responses.SimpleResponse;


/**
 * The SystemService provides end points for "system" functionality (like verifying HTTPS/TLS is
 * enabled, handling some forms of authentication, etc.), and is automatically added to every
 * Xenia-hosted web application.
 */
@WebService("/xverify")
service SystemService
    {
    // ----- properties ----------------------------------------------------------------------------

    /**
     * The cached `Catalog`.
     */
    @Lazy Catalog catalog.calc()
        {
        assert val catalog := webApp.registry_.getResource("catalog");
        return catalog.as(Catalog);
        }

    /**
     * The cached `SessionManager`.
     */
    @Lazy SessionManager sessionManager.calc()
        {
        assert val mgr := webApp.registry_.getResource("sessionManager");
        return mgr.as(SessionManager);
        }


    // ----- HTTP endpoints ------------------------------------------------------------------------

    /**
     * Handle a system service call. This is the "endpoint" for the service.
     *
     * @param uriString  the URI that identifies the system service endpoint being routed to
     * @param info       information about the request
     *
     * @return one of: an `HttpStatus` error code; a complete `Response`; or a new path to use in
     *         place of the passed `uriString`
     */
    HttpStatus|ResponseOut|String handle(String      uriString,
                                         RequestInfo info,
                                        )
        {
        String[] parts = uriString.split('/');
        switch (String command = parts.empty ? "" : parts[0])
            {
            case "session":     // e.g. "/xverify/session/12345" comes in as "session/12345"
                if (parts.size < 2)
                    {
                    return NotFound;
                    }

                Int64 redirect;
                try
                    {
                    redirect = new Int64(parts[1]);
                    }
                catch (Exception e)
                    {
                    return NotFound;
                    }

                return validateSessionCookies(uriString, info, redirect);

            default:
                return NotFound;
            }
        }

    /**
     * Implements the validation check for the session cookies when one or more session cookie has
     * been added.
     *
     * @param uriString  the URI that identifies the system service endpoint being routed to
     * @param info       information about the request
     * @param redirect   the redirection identifier previously registered with the session
     *
     * @return one of: an `HttpStatus` error code; a complete `Response`; or a new path to use in
     *         place of the passed `uriString`
     */
    protected HttpStatus|ResponseOut validateSessionCookies(String      uriString,
                                                            RequestInfo info,
                                                            Int64       redirect)
        {
        // find up to three session cookies that were passed in with the request
        (String? txtTemp, String? tlsTemp, String? consent, Int failures)
                = Dispatcher.extractSessionCookies(info);
        if (failures != 0)
            {
            // all of the failures should have been fixed up by the response that included the
            // cookies to verify
            return BadRequest;
            }

        // the plain text cookie must exist
        if (txtTemp == Null)
            {
            return BadRequest;
            }

        // the TLS cookies must NOT exist if we do not have TLS request
        Boolean tls = info.tls;
        if (!tls && (tlsTemp != Null || consent != Null))
            {
            // the TLS-only cookies should not have been passed (note that there is a
            // Firefox bug that does pass them, but we should have filtered them out in
            // the extractSessionCookies() method)
            return BadRequest;
            }

        // use the plain text cookie to find the session
        // use the session to validate the cookie
        SessionImpl session;
        if (session := sessionManager.getSessionByCookie(txtTemp))
            {
            Boolean repeat = False;
            (Match_ match, SessionCookie? cookie) = session.cookieMatches_(PlainText, txtTemp);
            switch (match)
                {
                case Correct:
                    session.cookieVerified_(cookie ?: assert);
                    break;

                case Older:
                    // need to redirect (again) because the session version was just incremented
                    // while we were waiting for the current redirect
                    repeat = True;
                    break;

                default:
                    return BadRequest;
                }

            if (tlsTemp == Null)
                {
                // if the redirect came in on TLS, then the TLS "temporary" cookie should have been
                // included
                if (tls)
                    {
                    return BadRequest;
                    }
                }
            else
                {
                (match, cookie) = session.cookieMatches_(Encrypted, tlsTemp);
                switch (match)
                    {
                    case Correct:
                        session.cookieVerified_(cookie ?: assert);
                        break;

                    case Older:
                        repeat = True;
                        break;

                    default:
                        return BadRequest;
                    }
                }

            if (consent == Null)
                {
                // the consent cookie is required over TLS if the session has sent it out, unless
                // we've already determined that we need to redirect yet again)
                if (tls && session.usePersistentCookie_ && !repeat,
                        (_, Time? sent) := session.getCookie_(Consent), sent != Null)
                    {
                    return BadRequest;
                    }
                }
            else
                {
                (match, cookie) = session.cookieMatches_(Consent, consent);
                switch (match)
                    {
                    case Correct:
                        session.cookieVerified_(cookie ?: assert);
                        break;

                    case Older:
                        repeat = True;
                        break;

                    default:
                        return BadRequest;
                    }
                }

            // handle the (rare) case in which we need to repeat the redirect
            if (repeat)
                {
                ResponseOut response = new SimpleResponse(TemporaryRedirect);
                Header      header   = response.header;
                for (CookieId cookieId : CookieId.from(session.desiredCookies_(tls)))
                    {
                    if ((SessionCookie resendCookie, Time? sent, Time? verified)
                            := session.getCookie_(cookieId), verified == Null)
                        {
                        header.add(Header.SetCookie, resendCookie.toString());
                        session.cookieSent_(resendCookie);
                        }
                    }

                // come back to verify that the user agent received and subsequently sent the
                // cookies
                Uri newUri = new Uri(path=$"{catalog.services[0].path}/session/{redirect}");
                header.put(Header.Location, newUri.toString());
                return response;
                }

            // handle the (common) case in which the redirect was successful
            if (Uri uri := session.claimRedirect_(redirect))
                {
                ResponseOut response = new SimpleResponse(TemporaryRedirect);
                response.header.put(Header.Location, uri.toString());
                return response;
                }
            }

        return BadRequest;
        }
    }