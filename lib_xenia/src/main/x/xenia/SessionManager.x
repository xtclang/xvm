import crypto.Decryptor;
import crypto.NullDecryptor;

import convert.formats.Base64Format;

import web.HttpStatus;

import HttpServer.RequestInfo;
import SessionCookie.CookieId;
import SessionImpl.Event_;
import SessionStore.IOResult;


/**
 * A service that keeps track of all of the `Session` objects.
 *
 * The implementation of `Session` uses three cookies:
 *
 * * A "plain text" temporary (i.e. not stored on disk by the user agent) cookie, which is not
 *   itself in plain text (since all cookies are encrypted), but rather it is a cookie that is
 *   allowed to be sent and received over a "plain text" (non TLS) connection;
 * * A "TLS only" temporary cookie, which plays the same role as the plain text cookie, but is only
 *   allowed to be sent and received over a TLS connection for security reasons; and
 * * A persistent "consent" cookie, i.e. a cookie that the user has given permission (like "remember
 *   me on this device") to store persistently, and which also includes the information about cookie
 *   consent so that it is not lost when the user agent restarts, etc.
 *
 * The assumption is that there is no consent to store persistent cookies unless the user explicitly
 * gives consent. This is based on a number of regulations by various nation states and by the EU.
 * While an application can override this by forcing fake consent information into the session,
 * doing so would be dangerous in terms of legal liabilities, and counter-productive in terms of
 * the effort generally required to implement consent tracking. As a result, the session management
 * is fully capable of operating without consent, because it does so using non-persistent cookies.
 * In some jurisdictions, it is considered acceptable to use persistent cookies for "necessary"
 * information without consent, and that "necessary" information may apply to session cookies; as
 * with any legal matter, a legal expert should be consulted when in doubt. Regardless, the approach
 * selected by this implementation is to always assume that the user desires anonymity unless
 * otherwise indicated; that the user is on a shared device unless otherwise indicated; and that
 * the user withholds all consent, unless otherwise indicated. These choices are made explicitly
 * for the benefit of the user; applications that desire to override these choices should do so with
 * the consent of the user. The exception is when the context of the application is well known, and
 * these defaults are contrary to that context -- and that the users would wholeheartedly agree;
 * this is often the case when building an application used within a specific company, for example,
 * in which the end users are employees or partners, and there is a reasonable expectation of
 * certain functionality being provided automatically, and there is no reasonable expectation of
 * either anonymity or privacy.
 */
@Concurrent
service SessionManager {
    // ----- constructors --------------------------------------------------------------------------

    construct(SessionStore store, SessionProducer instantiateSession,
              UInt16 plainPort = 80, UInt16 tlsPort = 443) {
        this.store              = store;
        this.instantiateSession = instantiateSession;

        plainTextCookieName = plainPort == 80  ? CookieId.PlainText.cookieName : $"{CookieId.PlainText.cookieName}_{plainPort}";
        encryptedCookieName = tlsPort   == 443 ? CookieId.Encrypted.cookieName : $"{CookieId.Encrypted.cookieName}_{tlsPort}";
        consentCookieName   = tlsPort   == 443 ? CookieId.Consent.cookieName   : $"{CookieId.Consent.cookieName}_{tlsPort}";
    }


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The means to persistently store sessions.
     */
    protected/private @Final SessionStore store;

    /**
     * The means to instantiate sessions.
     */
    typedef function SessionImpl(SessionManager, Int64, RequestInfo) as SessionProducer;
    protected/private @Final SessionProducer instantiateSession;

    /**
     * The name of the session cookie for non-TLS traffic.
     */
    public/private @Final String plainTextCookieName;

    /**
     * The name of the session cookie for TLS traffic.
     */
    public/private @Final String encryptedCookieName;

    /**
     * The name of the persistent session cookie (requires consent).
     */
    public/private @Final String consentCookieName;

    /**
     * Increment the session identifier "counter" by a large prime number.
     */
    private static Int64 ID_GAP = 0x0030_8DC2_CBEE_2A75;        // a fun prime: 13666666666666613

    /**
     * Wrap the session identifier counter around before it reaches the integer limit.
     */
    protected static Int64 ID_LIMIT = 0x0FFF_FFFF_FFFF_FFFF;    // don't use the entire 64-bit range

    /**
     * The most recently generated session id.
     */
    protected/private Int64 previousId = {
        // initialize to a random session id
        @Inject Random rnd;
        return rnd.int64() & ID_LIMIT;
    };

    /**
     * The total number of sessions created.
     */
    public/private Int createdCount = 0;

    /**
     * The total number of sessions destroyed.
     */
    public/private Int deletedCount = 0;

    /**
     * For each session, the status of the session may be known or unknown. This allows the bulk of
     * the session information to remain in persistent storage until it is explicitly requested.
     *
     * * `Unknown` - the session is not cached in memory, and it may or may not exist in persistent
     *    storage
     * * `Nonexistent` - the session is known to not exist
     * * `OnDisk` - the session is not cached in memory, but it is known to exist in persistent
     *   storage
     * * `InMemory` - the session is cached in memory
     */
    protected enum SessionStatus {
        Unknown,
        Nonexistent,
        OnDisk,
        InMemory,
    }

    /**
     * [Session] by id, or the [SessionStatus] if the `Session` is not `InMemory` but the status is
     * known.
     */
    protected/private Map<Int, SessionImpl|SessionStatus> sessions = new HashMap();

    /**
     * [Session] by cookie, or the [SessionStatus] if the `Session` is not `InMemory` but the status
     * is known.
     */
    protected/private Map<String, SessionImpl|SessionStatus> sessionByCookie = new HashMap();

    /**
     * The daemon responsible for cleaning up expired session data.
     */
    protected/private SessionPurger purger = new SessionPurger();

    /**
     * When a user has **not** explicitly indicated that their device is trusted, the session
     * cookies are not persistent and the time-out for the session is short.
     */
    public/private Duration untrustedDeviceTimeout = Duration:30M;     // default is half hour

    /**
     * When a user has explicitly indicated that their device is trusted, the session cookies may
     * (with consent) be persistent, and the time-out for the session may be dramatically longer.
     */
    public/private Duration trustedDeviceTimeout = Duration:60D;       // default is two months

    /**
     * When a persistent cookie is used, its expiry.
     */
    public/private Duration persistentCookieDuration = Duration:760D;  // default: 2 years + 1 month

    /**
     * The encryptor/decryptor used for session cookie values.
     */
    private Decryptor cookieDecryptor = NullDecryptor;

    /**
     * Tracks whether event dispatching failures have already been logged, per event.
     */
    private Boolean[] reported = new Boolean[Event_.count];


    // ----- cookie encoding support ---------------------------------------------------------------

    void configureEncryption(Decryptor cookieDecryptor) {
        assert this.cookieDecryptor == NullDecryptor as "Cookie decryptor is not re-assignable";
        this.cookieDecryptor = cookieDecryptor;
    }

    /**
     * Encrypt the passed readable string into an unreadable, tamper-proof, BASE-64 string
     *
     * @param text  the readable string
     *
     * @return the encrypted string in BASE-64 format
     */
    String encryptCookie(String text) {
        Byte[] bytes = text.utf8();

        bytes = cookieDecryptor.encrypt(bytes);

        return Base64Format.Instance.encode(bytes);
    }

    /**
     * Decrypt the passed string back into a readable String
     *
     * @param text  the encrypted string in BASE-64 format
     *
     * @return the readable string
     */
    conditional String decryptCookie(String text) {
        try {
            Byte[] bytes = Base64Format.Instance.decode(text);

            bytes = cookieDecryptor.decrypt(bytes);

            return True, bytes.unpackUtf8();
        } catch (Exception e) {
            return False;
        }
    }


    // ----- session control -----------------------------------------------------------------------

    /**
     * Obtain the session status for the specified session id.
     *
     * @param id  the session id
     *
     * @return the session status
     */
    SessionStatus getStatusById(Int id) {
        if (SessionImpl|SessionStatus status := sessions.get(id)) {
            return status.is(SessionImpl)
                    ? InMemory
                    : status;
        } else {
            return Unknown;
        }
    }

    /**
     * Add the session to the quick lookup by cookie.
     *
     * @param session  the `Session`
     * @param cookie   the `SessionCookie` to use to look up the `Session`
     */
    void addSessionCookie(SessionImpl session, SessionCookie cookie) {
        assert sessionByCookie.putIfAbsent(cookie.text, session);
    }

    /**
     * Remove the session from the quick lookup by cookie.
     *
     * @param session  the `Session`
     * @param cookie   the previously registered `SessionCookie`
     */
    void removeSessionCookie(SessionImpl session, SessionCookie cookie) {
        assert sessionByCookie.remove(cookie.text, session);
    }

    /**
     * Quick lookup of a session based on an opaque cookie value, or a slightly slower lookup based
     * on the decrypted form of the cookie value. This is just a lookup; it doesn't validate,
     * create, or destroy cookies or sessions.
     *
     * @param cookieText  the cookie value's text from the header
     *
     * @return `True` iff the session exists
     * @return (conditional) the session
     */
    conditional SessionImpl getSessionByCookie(String cookieText) {
        if (SessionImpl|SessionStatus session := sessionByCookie.get(
                SessionCookie.textFromCookie(cookieText))) {
            if (session.is(SessionImpl)) {
                return True, session;
            }

            if (session == Nonexistent) {
                return False;
            }

            // fall through to default handling
        }

        try {
            SessionCookie cookie = new SessionCookie(this, cookieText);
            return getSessionById(cookie.sessionId);
        } catch (Exception e) {
            return False;
        }
    }

    /**
     * Obtain the session for the specified session id.
     *
     * @param id  the session id
     *
     * @return `True` iff the session exists
     * @return (conditional) the session
     */
    conditional SessionImpl getSessionById(Int id) {
        if (SessionImpl|SessionStatus session := sessions.get(id)) {
            if (session.is(SessionImpl)) {
                return True, session;
            }

            switch (session) {
            case Nonexistent:
                return False;

            case Unknown:
            case OnDisk:
                SessionImpl|IOResult result = store.load(id);
                if (result.is(SessionImpl)) {
                    return True, result;
                } else {
                    switch (result) {
                    case Success:
                    case SerializationIncomplete:
                        // TODO how would this information be reported? incomplete would have to return the session
                        assert;

                    case NoSuchSession:
                        sessions.put(id, Nonexistent);
                        return False;

                    case SerializationFailure:
                    case IOFailure:
                        // TODO should there be an "error" placed in the session cache?
                        return False;
                    }
                }

            case InMemory:
                assert;
            }
        }

        return False;
    }

    /**
     * Determine if the specified id has session data in persistent storage.
     *
     * @param id  the session id
     *
     * @return True iff the specified id has session data in persistent storage
     */
    Boolean sessionExistsInStorage(Int id) {
        // TODO check sessions, and add flag to SessionImpl as well "on disk" vs. not and "up to date" vs not
        return False;
    }

    /**
     * Instantiate a new [SessionImpl] object, including any [Session] mix-ins that the [WebApp]
     * contains.
     *
     * @param requestInfo  the request information
     *
     * @return a new [SessionImpl] object, including any mixins declared by the application, or the
     *         [HttpStatus] describing why the session could not be created
     */
    HttpStatus|SessionImpl createSession(RequestInfo requestInfo) {
        Int64       id      = generateId();
        SessionImpl session = instantiateSession(this, id, requestInfo);
        sessions.put(id, session);

        purger.track^(id);

        return session;
    }

    /**
     * Instantiate a copy of the passed [SessionImpl] object.
     *
     * @param oldSession   the session to clone
     * @param requestInfo  the request information
     *
     * @return a clone of the [SessionImpl] object, or the [HttpStatus] describing why the session
     *         could not be cloned
     */
    HttpStatus|SessionImpl cloneSession(SessionImpl oldSession, RequestInfo requestInfo) {
        HttpStatus|SessionImpl result = createSession(requestInfo);
        if (result.is(HttpStatus)) {
            return result;
        }

        SessionImpl newSession = result;
        newSession.cloneFrom_(oldSession);
        return newSession;
    }

    /**
     * Generate a session ID.
     *
     * @return an unused session ID
     */
    Int64 generateId() {
        while (True) {
            Int64 id = previousId + ID_GAP & ID_LIMIT;
            previousId = id;

            switch (getStatusById(id)) {
            case Nonexistent:
                return id;

            case Unknown:
                if (!sessionExistsInStorage(id)) {
                    return id;
                }
                continue;
            case OnDisk:
            case InMemory:
                // strange but not impossible: we have collided with an existing session; to
                // compensate, increment the base by a different prime value than the gap; note
                // that we have to assume that this method is concurrent, i.e. not running all
                // at once (so the previousId may have already been changed by someone else,
                // after we changed it above)
                @Inject Random rnd;
                Int64 adjust = HashMap.PRIMES[rnd.int(HashMap.PRIMES.size)];
                previousId = previousId + adjust & ID_LIMIT;
                break;
            }
        }
    }

    /**
     * Explicitly destroy the specified session.
     *
     * @param id  the session id
     */
    void destroySession(Int id) {
        if (SessionImpl session := getSessionById(id)) {
            session.destroy^();
        }
    }

    /**
     * Unregister the specified session.
     *
     * @param id  the session id
     */
    void unregisterSession(Int            id,
                           SessionCookie? txtCookie,
                           SessionCookie? tlsCookie,
                           SessionCookie? consentCookie,
                          ) {
        sessions.put(id, Nonexistent);
        sessionByCookie.remove(txtCookie?.text);
        sessionByCookie.remove(tlsCookie?.text);
        sessionByCookie.remove(consentCookie?.text);
        store.erase^(id);
    }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Determine if an event that failed to be dispatched/super'd correctly should be reported.
     *
     * @param event  the event identity
     *
     * @return True iff the event has not previously been reported
     */
    Boolean shouldReport(Event_ event) {
        if (reported[event.ordinal]) {
            return False;
        }

        reported[event.ordinal] = True;
        return True;
    }
}