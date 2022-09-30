import collections.IdentitySet;

import net.IPAddress;

import web.CookieConsent;
import web.Header;
import web.TrustLevel;

import HttpServer.RequestInfo;
import SessionCookie.CookieId;


/**
 * An implementation of the `Session` interface.
 *
 * TODO CP
 * - doc why is it not concurrent
 * - doc why it must not do any I/O
 */
service SessionImpl
        implements Session
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(SessionManager manager, Int sessionId, RequestInfo requestInfo)
        {
        initialize(this, manager, sessionId, requestInfo);
        }

    /**
     * Construction helper to be used via reflection.
     */
    static void initialize(SessionImpl:struct structure,
                           SessionManager manager, Int sessionId, RequestInfo requestInfo)
        {
        Time now = xenia.clock.now;

        structure.manager_        = manager;
        structure.created         = now;
        structure.lastUse         = now;
        structure.versionChanged_ = now;
        structure.ipAddress       = requestInfo.getClientAddress();
        structure.userAgent       = extractUserAgent(requestInfo);
        structure.cookieConsent   = None;
        structure.trustLevel      = None;
        structure.sessionId       = sessionId.toString(); // TODO CP: use Base64?
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
     * TODO
     */
    Int version_;

    /**
     * TODO
     */
    Time versionChanged_;

    /**
     * TODO
     */
    SessionCookie?[] sessionCookies_ = new SessionCookie?[CookieId.count];

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