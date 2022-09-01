import collections.IdentitySet;

import net.IPAddress;

import web.CookieConsent;
import web.TrustLevel;


/**
 * An implementation of the `Session` interface.
 *
 * The implementation of `Session` uses three cookies:
 *
 * 1. `xplaintext` - a non-persistent cookie that is subject to hijacking (not protected by TLS)
 *    * this cookie is created when any request arrives without a `xplaintext` cookie
 *    * specifies `Path=/;SameSite=Strict;HttpOnly`; does not specify `Expires` or `Domain`
 *    * contains base64 encoding of encrypted data: session id, cookie id, change counter, creation
 *      date, known consents, known cookies (1/2/3), ip address, random salt
 *
 * 2. `__Host-xtemporary` - a non-persistent cookie protected by TLS
 *    * this cookie is created when a request comes in over a TLS connection and there is no
 *      `__Host-xtemporary` cookie in the request
 *    * specifies `Path=/;SameSite=Strict;secure;HttpOnly`; does not specify `Expires` or `Domain`
 *    * contains base64 encoding of encrypted data: session id, cookie id, change counter, creation
 *      date, known consents, known cookies (1/2/3), ip address, random salt
 *
 * 3. `__Host-xconsented` - a persistent cookie protected by TLS
 *    * this cookie is only created when a request comes in over a TLS connection, and the session
 *      [cookieConsent] has been set, and there is no `__Host-xconsented` cookie in the request
 *    * specifies `Path=/;SameSite=Strict;secure;HttpOnly;Expires=...` ; does not specify `Domain`
 *    * contains human readable consent string and date/time of consent (?)
 *    * contains base64 encoding of encrypted data: session id, cookie id, change counter, creation
 *      date, expiry, known consents, known cookies (1/2/3), ip address, random salt
 *    * if consent is withheld, then the session id and IP address are **not** recorded in the
 *      persistent portion.
 *
 * Notes:
 *
 * * All three cookies may show up on a secure connection, but it's always possible that any
 *   combination of the cookies can show up with any request. Here is how the server handles an
 *   incoming request with some combination of cookies 1, 2, and 3, with (1) or without (0) TLS:
 *
 *     1 2 3 TLS  description/action
 *     - - - ---  ------------------
 *            0   create new session; create cookie 1; redirect to verification
 *            1   create new session; create cookies 1 and 2; redirect to verification
 *     x      0   validate cookie 1
 *     x      1   validate cookie 1 & verify [prevTLS_] `== False`; create cookie 2;
 *                redirect to verification; set [prevTLS_] `= True`
 *       x    0   error (no TLS, so cookie 2 is illegally present; also missing cookie 1)
 *       x    1   error (missing cookie 1)
 *     x x    0   error (no TLS, so cookie 2 is illegally present)
 *     x x    1   validate cookie 1 & 2; if [cookieConsent] has been set, create cookie 3 and
 *                redirect to verification
 *         x  0   error (no TLS, so cookie 3 is illegally present)
 *         x  1   validate cookie 3; assume temporary cookies absent due to user agent restart;
 *                create cookie 1 & 2 and redirect to verification
 *     x   x  0   error (no TLS, so cookie 3 is illegally present)
 *     x   x  1   validate cookie 1 & 3 (1 must be newer than 3), and verify [prevTLS_] `== False`;
 *                use session specified by cookie 3 if possible, otherwise 1; create cookie 2;
 *                redirect to verification; set [prevTLS_] `= True`
 *       x x  0   error (no TLS, so cookie 2 and 3 are illegally present)
 *       x x  1   error (missing cookie 1)
 *     x x x  0   error (no TLS, so cookies 2 and 3 are illegally present)
 *     x x x  1   validate cookie 1 & 2 & 3 (must be identical)
 *
 * * When a cookie is created, the counter inside the cookie is incremented, and any existing
 *   cookies are also re-written so their contents are in agreement (although each necessarily
 *   differing at a binary level, in order to make spoofing of one cookie from another cookie's
 *   contents impossible)
 *
 * * Cookies need to be replaced whenever an IP address etc. changes, also after a period of time.
 *
 * * Persistent cookies are only used when permission has been given to use persistent cookies.
 *
 * * When a session is first created on a plaintext connection, it uses a temporary cookie and no
 *   significant information is expected to be related to that session (because of the lack of TLS).
 *   Subsequently, when transitioning to a TLS connection, a previously existent persistent
 *   connection may suddenly show up with the first request over TLS, and for which session data
 *   (perhaps even persistent session data) may exist. In that case, the session indicated by the
 *   persistent (and TLS) session ID is used, possibly with a few details lifted from the non-TLS
 *   session before it is discarded, and all three cookies are then re-written.
 *
 * TODO
 * - dispatcher gets a request
 *
 * - assume anonymous user
 * - assume shared device (allow user to explicitly indicate that device is not shared)
 * - assume no consent (allow user to consent, which enables persistent cookies if device is explicitly not shared)
 *
 * - doc why is it not concurrent
 * - doc why it must not do any I/O
 */
service SessionImpl
        implements Session
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(SessionManager manager, Int sessionId)
        {
        manager_ = manager;
        // TODO CP: store the sessionId as a String?
        }


    // ----- session implementation properties -----------------------------------------------------

    /**
     * All of the `Session` objects within an application are managed by a `SessionManager`.
     */
    protected/private SessionManager manager_;

    /**
     * This flag tracks whether the session has been accessed via a TLS connection previously.
     */
    protected/private Boolean prevTLS_;

    /**
     * A data structure to keep track of concurrently executing requests.
     *
     * Note: These are tracked primarily for debugging purposes; it is not possible through the
     * Session interface to query for the current request objects, but only for the count of current
     * requests.
     */
    protected/private IdentitySet<Request> requests_ = new IdentitySet(7);


    // ----- session implementation API ------------------------------------------------------------

    void requestBegin(Request request)
        {
        assert requests_.addIfAbsent(request);
        }

    void requestEnd(Request request)
        {
        assert requests_.removeIfPresent(request);
        }


    // ----- session interface ---------------------------------------------------------------------

    @Override
    @Lazy(() -> new HashMap())
    public/private Map<String, Shareable> attributes;

    @Override
    public/private Time created;

    @Override
    public/private Time? destroyed;

    @Override
    public/private Int requestCount;

    @Override
    public Int activeRequests.get()
        {
        return requests_.size;
        }

    @Override
    public/private Time lastUse;

    @Override
    public/private IPAddress ipAddress;

    @Override
    public/private String userAgent;

    @Override
    Boolean exclusiveAgent;

    @Override
    CookieConsent cookieConsent;

    @Override
    public/private String? userId;

    @Override
    public/private Time? lastAuthenticated;

    @Override
    TrustLevel trustLevel;

    @Override
    public/private String sessionId;

    @Override
    void authenticate(String userId, Boolean exclusiveAgent)
        {
        this.userId            = userId;
        this.exclusiveAgent    = exclusiveAgent;
        this.lastAuthenticated = clock.now;
        }

    @Override
    void deauthenticate()
        {
        TODO
        }

    @Override
    void destroy()
        {
        TODO
        }

    @Override
    void sessionCreated()
        {
        // TODO on each of these events, the execution needs to reach this level, otherwise we need
        //      to issue a warning to the developer (but just the first time)
        //
        //      idea: use a mixin with state that tracks whether each method has been invoked, and
        //      then records when it gets to this point, so we can determine when a call is made to
        //      a session event and it doesn't get to the end of the event chain
        }

    @Override
    void sessionDestroyed()
        {
        }

    @Override
    void sessionAuthenticated(String user)
        {
        }

    @Override
    void sessionDeauthenticated(String user)
        {
        }
    }