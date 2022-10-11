import HttpServer.RequestInfo;

import web.HttpStatus;
import web.WebService;

import web.codecs.Registry;

import web.responses.SimpleResponse;


/**
 * Dispatcher is responsible for finding an endpoint, creating a call chain for an HTTP request and
 * invoking it on a corresponding WebService.
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
    HttpStatus|Response|String handle(String      uriString,
                                      RequestInfo info,
                                     )
        {
        String[] parts = uriString.split('/');
        switch (String command = parts.size > 1 ? parts[1] : "")
            {
            case "session":     // e.g. "/xverify/session/12345"
                if (parts.size < 3)
                    {
                    return NotFound;
                    }

                Int redirect;
                try
                    {
                    redirect = new Int(parts[2]);
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
    protected HttpStatus|Response|String validateSessionCookies(String      uriString,
                                                                RequestInfo info,
                                                                Int         redirect)
        {
        (String? txtTemp, String? tlsTemp, String? consent, Int failures)
                = Dispatcher.extractSessionCookies(info);
        if (failures != 0)
            {
            // all of the failures should have been fixed up by the response that included the
            // cookies to verify
            return BadRequest;
            }

        // use the plain text cookie (which must exist) to find the session; use the session to then
        // validate the cookie
        SessionImpl session;
        if (txtTemp == Null)
            {
            return BadRequest;
            }
        else if (session := sessionManager.getSessionByCookie(txtTemp),
                session.cookieMatches_(PlainText, txtTemp) == Correct)
            {
            if (info.tls)
                {
                // use the session to validate the TLS cookie
                if (tlsTemp == Null || session.cookieMatches_(Encrypted, tlsTemp) != Correct)
                    {
                    return BadRequest;
                    }

                // use the session to validate the persistent consent cookie
                if (session.cookieConsent != None,
                        consent == Null || session.cookieMatches_(Consent, consent) != Correct)
                    {
                    return BadRequest;
                    }
                }
            }
        else
            {
            return BadRequest;
            }

        if (URI uri := session.claimRedirect_(redirect))
            {
            return uri.toString();
            }

        return NotFound;
        }


    // ----- internal ------------------------------------------------------------------------
    }