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
                if (parts.size < 3)
                    {
                    return NotFound;
                    }

                Int64 redirect;
                Int64 version;
                try
                    {
                    redirect = new Int64(parts[1]);
                    version  = new Int64(parts[2]);
                    }
                catch (Exception e)
                    {
                    return NotFound;
                    }

                return validateSessionCookies(uriString, info, redirect, version);

            default:
                return NotFound;
            }
        }

    /**
     * Implements the validation check for the session cookies when one or more session cookie has
     * been added.
     *
     * There are five expected outcomes:
     *
     * * Confirm - 99.9+% of the time, everything is perfect and exactly as expected
     * * Repeat - Occasionally, the redirect will need to be repeated because the session version
     *   has already advanced for some reason before this redirect request was able to be processed
     * * Split - If anything is actually _wrong_, then the session is split, and a redirect is
     *   returned for the user agent to validate against the split session
     * * New - If a session can't be found to validate against, or some other unrecoverable problem
     *   appears to exist, then a new session is created, and a redirect is returned for the user
     *   agent to validate the new session
     * * Abort - If something so fundamental is wrong that none of the above is an option, then the
     *   method simply aborts with an error code
     *
     * @param uriString  the URI that identifies the system service endpoint being routed to
     * @param info       information about the request
     * @param redirect   the redirection identifier previously registered with the session
     * @param version    the session version being validated against
     *
     * @return one of: an `HttpStatus` error code; a complete `Response`; or a new path to use in
     *         place of the passed `uriString`
     */
    protected HttpStatus|ResponseOut validateSessionCookies(String      uriString,
                                                            RequestInfo info,
                                                            Int64       redirect,
                                                            Int64       version,
                                                           )
        {
        enum Action {Confirm, Repeat, Split, New}

        static Action evaluate(SessionImpl session, CookieId cookieId, String cookieText)
            {
            (Match_ match, SessionCookie? cookie) = session.cookieMatches_(cookieId, cookieText);
            switch (match)
                {
                case Correct:
                    session.cookieVerified_(cookie ?: assert);
                    return Confirm;

                case Older:
                    // need to redirect (again) because the session version was just incremented
                    // while we were waiting for the current redirect
                    return Repeat;

                case Newer:
                    // this is inconceivable, since we just redirected the user agent to confirm its
                    // cookies, and we supposedly already handled the case that the user agent had
                    // a "newer" cookie; treat this as a protocol violation
                    return Split;

                case Corrupt:
                case WrongSession:
                    // this is inconceivable, since we just found the session using this cookie,
                    // but the cookie doesn't match; assume something so weird is going on that we
                    // should just give up and return a new session to minimize any further damage
                case WrongCookieId:
                case Unexpected:
                    // these are serious violations of the protocol; assume something so weird is
                    // going on that we should just give up and return a new session to minimize
                    // any further damage
                    // TODO CP should report this to the session(s)
                default:
                    return New;
                }
            }

        // find up to three session cookies that were passed in with the request
        (String? txtTemp, String? tlsTemp, String? consent, Int failures)
                = Dispatcher.extractSessionCookies(info);
        if (failures != 0)
            {
            // something is deeply wrong with the cookies coming from the user agent, and it's
            // unlikely that we can fix them (because by the time that we got here, we supposedly
            // would have already tried to fix whatever that mess is)
            return BadRequest;
            }

        Boolean tls    = info.tls;
        Action  action = Confirm;

        // the plain-text cookie is required; use the plain text cookie to find the session; the
        // absence of either the cookie or the session implies that we should create a new session
        // TODO GG @Unassigned
        SessionImpl? session; // note: assumed unassigned iff "action==New"
        Validate: if (txtTemp != Null, session := sessionManager.getSessionByCookie(txtTemp))
            {
            // validate the plaint text temporary cookie that we just used to look up the session
            action = evaluate(session, PlainText, txtTemp);

            // validate the temporary TLS cookie
            if (tlsTemp == Null)
                {
                // if the redirect came in on TLS, then the TLS "temporary" cookie should have been
                // included; treat it as a protocol violation and split the session
                if (tls && action != Repeat)
                    {
                    action = action.notLessThan(Split);
                    }
                }
            else
                {
                action = action.notLessThan(evaluate(session, Encrypted, tlsTemp));
                }

            // validate the persistent TLS cookie
            if (consent == Null)
                {
                // the consent cookie is required over TLS if the session has sent it out, unless
                // we've already determined that we need to redirect yet again)
                if (tls && session.usePersistentCookie_ && action != Repeat,
                        (_, Time? sent) := session.getCookie_(Consent), sent != Null)
                    {
                    action = action.notLessThan(Split);
                    }
                }
            else
                {
                action = action.notLessThan(evaluate(session, Consent, consent));
                }
            }
        else
            {
            // unable to locate the specified session, so attempt to create a new session
            action  = New;
            session = Null; // TODO remove
            }

        switch (action)
            {
            case Confirm:
                assert session != Null; // TODO remove
                // handle the most common case in which the redirect was successful
                ResponseOut response = new SimpleResponse(TemporaryRedirect);
                response.header.put(Header.Location, session.claimRedirect_(redirect)?.toString() : "/");
                return response;

            case Repeat:
                // handle the (rare) case in which we need to repeat the redirect, but only if the
                // actual session version is ahead of the version that this method was called to
                // validate (otherwise, repeating the validation isn't going to change anything)
                assert session != Null; // TODO remove
                if (session.version_ > version)
                    {
                    break;
                    }
                continue;
            case Split:
                // protocol error: split the session
                assert session != Null; // TODO remove
                HttpStatus|SessionImpl result = session.split_(info);
                if (result.is(HttpStatus))
                    {
                    return result;
                    }
                session = result;
                break;

            case New:
                // create a new session
                HttpStatus|SessionImpl result = sessionManager.createSession(info);
                if (result.is(HttpStatus))
                    {
                    return result;
                    }
                session = result;
                break;
            }

        // send desired cookies (unless they've already been verified, in which case we never
        // re-send them)
        ResponseOut response = new SimpleResponse(TemporaryRedirect);
        Header      header   = response.header;
        Byte        desired  = session.desiredCookies_(tls);
        for (CookieId cookieId : CookieId.from(desired))
            {
            if ((SessionCookie resendCookie, Time? sent, Time? verified)
                    := session.getCookie_(cookieId), verified == Null)
                {
                header.add(Header.SetCookie, resendCookie.toString());
                if (sent == Null)
                    {
                    session.cookieSent_(resendCookie);
                    }
                }
            }

        // erase undesired cookies
        Byte present = (txtTemp == Null ? 0 : CookieId.NoTls)
                     | (tlsTemp == Null ? 0 : CookieId.TlsTemp)
                     | (consent == Null ? 0 : CookieId.OnlyConsent);
        for (CookieId cookieId : CookieId.from(present & ~desired))
            {
            header.add(Header.SetCookie, cookieId.eraser);
            }

        // come back to verify that the user agent received and subsequently sent the
        // cookies
        Uri newUri = new Uri(path=$"{catalog.services[0].path}/session/{redirect}/{session.version_}");
        header.put(Header.Location, newUri.toString());
        return response;
        }
    }