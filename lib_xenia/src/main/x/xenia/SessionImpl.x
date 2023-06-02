import collections.CircularArray;
import collections.IdentitySet;

import net.IPAddress;

import web.CookieConsent;
import web.Header;
import web.HttpStatus;
import web.TrustLevel;
import web.codecs.Base64Format;

import HttpServer.RequestInfo;
import SessionCookie.CookieId;


/**
 * An implementation of the `Session` interface.
 *
 * The Session implementation is a service because concurrent requests related to the same session
 * can be processing concurrently, and the session represents mutable state managed by the
 * application on behalf of (for the benefit of) the user. Despite the concurrent nature described
 * here, the SessionImpl service itself is not explicitly concurrent, because the use of the session
 * by various concurrent requests would almost certainly be more error prone if access to the
 * session were not serialized (i.e. performed in some order). The alternative is chaos.
 *
 * The implementation also does not conduct any I/O. From a persistence perspective, for example,
 * the session is a mostly-passive participant. The reasoning is simple: I/O operations have
 * unpredictable latencies, and each session is a natural bottleneck in a concurrent system.
 */
service SessionImpl
        implements Session {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a SessionImpl instance.
     *
     * @param manager      the SessionManager
     * @param sessionId    the internal session identifier
     * @param requestInfo  the request information
     */
    construct(SessionManager manager, Int64 sessionId, RequestInfo requestInfo) {
        initialize(this, manager, sessionId, requestInfo);
    }

    /**
     * Construction helper, used directly by the constructor, but also via reflection.
     *
     * @param structure    the structure of the SessionImpl being constructed
     * @param manager      the SessionManager
     * @param sessionId    the internal session identifier
     * @param requestInfo  the request information
     * @param tls          True if the request was received over a TLS connection
     */
    static void initialize((struct SessionImpl) structure,
                           SessionManager       manager,
                           Int64                sessionId,
                           RequestInfo          requestInfo) {
        Time now = clock.now;

        structure.manager_        = manager;
        structure.created         = now;
        structure.lastUse         = now;
        structure.versionChanged_ = now;
        structure.ipAddress       = requestInfo.getClientAddress();
        structure.userAgent       = extractUserAgent(requestInfo);
        structure.cookieConsent   = None;
        structure.trustLevel      = None;
        structure.roles           = [];
        structure.internalId_     = sessionId;
        structure.sessionId       = idToString_(sessionId);
        structure.prevTLS_        = requestInfo.tls;
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
     * The internal session identity.
     */
    public/private Int internalId_;

    /**
     * The current version of the session. Each time a significant change occurs to the user agent,
     * such as cookie consent, IP address change, etc., the version of the session is incremented.
     */
    public/private Int version_;

    /**
     * The time at which the version was last incremented. Tracking this information allows for
     * tolerance to concurrent requests which may already be in flight with older versions of the
     * session's cookies. Once the round trip verification of the updated session cookies has
     * completed, no new requests using the older cookies should be possible, but HTTP does not
     * guarantee strict ordering, and high request concurrency (including unpredictable delays for
     * some of those concurrent requests) can cause out of order processing of incoming requests.
     */
    protected/private Time versionChanged_;

    /**
     * A bitset of which `CookieId`s are known to have a cookie that has been created for this
     * session.
     */
    public/private Int knownCookies_ = CookieId.None;

    /**
     * The information tracked for each of the session cookies, indexed by the `CookieId.ordinal`.
     */
    protected/private SessionCookieInfo_?[] sessionCookieInfos_ = new SessionCookieInfo_?[CookieId.count];

    /**
     * A limited history by `IPAddress` of access to this session.
     */
    protected/private CircularArray<AddressAccess_> recentAddresses_ = new CircularArray(8);

    /**
     * All of the `IPAddress` that requests for this session have originated from.
     */
    protected/private Set<IPAddress> allAddresses_ = new HashSet();

    /**
     * When a session has to split, it is considered "abandoned" from that point forward, and all
     * user agents should be forced to switch to new split sessions as soon as they come back with
     * their next request.
     */
    protected/private Boolean abandoned_;

    /**
     * When a session has been split, it tracks the sessions that emerge as a result.
     */
    protected/private SessionSplit_[] splits_ = [];

    /**
     * A limited collection of recent requests that are held for debugging purposes and to allow a
     * management console to provide visibility to information that may help identify an application
     * level error.
     */
    protected/private CircularArray<RequestIn> recentRequests_ = new CircularArray(8);

    /**
     * A list of pending prepared system redirects.
     */
    protected/private PendingRedirect_[]? pendingRedirects_;

    /**
     * A limit to the number of redirects, in order to suppress DDOS attacks.
     */
    static protected Int MaxRedirects_ = 8;

    /**
     * The value representing a "default result" for "void" event handlers.
     */
    static Tuple<> Void = Tuple:();

    /**
     * Internal recording of events related to the session, maintained for security and debugging
     * purposes. This information will be pruned to prevent unlimited size growth.
     */
    protected/private List<LogEntry_> log_ = [];

    /**
     * A data structure to keep track of concurrently executing requests.
     *
     * Note: These are tracked primarily for debugging purposes; it is not possible through the
     * Session interface to query for the current request objects, but only for the count of current
     * requests.
     */
    protected/private IdentitySet<RequestIn> requests_ = new IdentitySet(7);

    /**
     * The actual storage for arbitrary named session attributes.
     */
    @Lazy(() -> new HashMap())
    protected/private Map<String, Shareable> attributes_;

    /**
     * The linked list of current "in flight" events.
     */
    private InFlight_? currentInFlight_;


    // ----- inner types ---------------------------------------------------------------------------

    /**
     * The AttributeMap_ inner class is used to provide a service reference to the map of arbitrary
     * session attributes.
     */
    class AttributeMap_
            delegates Map<String, Shareable>(actualMap) {
        Map<String, Shareable> actualMap.get() = attributes_;
    }

    /**
     * Information about a SessionCookie related to this session.
     *
     * * The [cookie] property holds the cookie data itself.
     * * The [sent] property indicates if the cookie has been sent to the user agent, and if so,
     *   when it was sent.
     * * The [verified] property indicates that the cookie was successfully received by the user
     *   agent, and if so, when it was verified as received.
     */
    protected static class SessionCookieInfo_(SessionCookie cookie,
                                              Time?         sent     = Null,
                                              Time?         verified = Null,
                                             ) {
        @Override
        String toString() = $"{cookie=}, {sent=}, {verified=}";
    }

    /**
     * A record of access to this session from a particular `IPAddress`.
     */
    protected static class AddressAccess_(IPAddress address, Time firstAccess, Time lastAccess);

    /**
     * A record of session migration, in which a new session identity is created to represent this
     * session when something weird is detected that could indicate a security problem.
     */
    protected static const SessionSplit_(IPAddress address, Int oldVersion, Int newId);

    /**
     * Information about a system redirect.
     */
    protected static const PendingRedirect_(Int64 id, Uri uri, Time created);

    /**
     * Information collected for each log entry.
     */
    static const LogEntry_(Time time, IPAddress address, Boolean tls, Int version, String text);

    /**
     * When matching a session cookie
     */
    enum Match_ {
        Correct,
        WrongSession,   // session ID doesn't match
        Older,          // should be considered to be "suspect"
        Newer,          // e.g. if the SessionManager didn't synchronously persist, then crashed
        Corrupt,        // could not deserialize
        WrongCookieId,  // cookie was copied from one name to another (an attempted hack!)
        Unexpected,     // cookie should not have been sent
    }

    /**
     * Possible session events.
     */
    enum Event_ {
        SessionCreated,
        SessionDestroyed,
        SessionMergedFrom,
        SessionForked,
        SessionAuthenticated,
        SessionDeauthenticated,
        TlsChanged,
        IPAddressChanged,
        UserAgentChanged,
        FingerprintChanged,
        ProtocolViolated,
    }

    /**
     * In flight event tracking.
     */
    private class InFlight_(Event_ event, InFlight_? next) {
        Boolean reached;
    }


    // ----- session implementation API ------------------------------------------------------------

    void requestBegin_(RequestIn request) {
        assert requests_.addIfAbsent(request);
    }

    void requestEnd_(RequestIn request) {
        assert requests_.removeIfPresent(request);
    }


    // ----- session interface ---------------------------------------------------------------------

    @Override
    @Lazy
    public/private Map<String, Shareable> attributes.calc() = new AttributeMap_();

    @Override
    public/private Time created;

    @Override
    public/private Time? destroyed;

    @Override
    public/private Int requestCount;

    @Override
    public Int activeRequests.get() = requests_.size;

    @Override
    public/private Time lastUse;

    @Override
    public/private IPAddress ipAddress;

    @Override
    public/private String userAgent;

    @Override
    Boolean exclusiveAgent.set(Boolean exclusive) {
        Boolean pre = usePersistentCookie_;
        super(exclusive);
        Boolean post = usePersistentCookie_;
        if (pre != post) {
            incrementVersion_();
        }
    }

    @Override
    CookieConsent cookieConsent.set(CookieConsent consent) {
        Boolean pre = usePersistentCookie_;
        super(consent);
        Boolean post = usePersistentCookie_;
        if (pre != post) {
            incrementVersion_();
        }
    }

    @Override
    public/private String? userId;

    @Override
    public/private Time? lastAuthenticated;

    @Override
    TrustLevel trustLevel;

    @Override
    immutable Set<String> roles;

    @Override
    public/private String sessionId;

    @Override
    void authenticate(String      userId,
                      Boolean     exclusiveAgent = False,
                      TrustLevel  trustLevel     = Highest,
                      Set<String> roles          = [],
                     ) {
        if (   this.userId         != userId
            || this.exclusiveAgent != exclusiveAgent
            || this.trustLevel     != trustLevel
            || this.roles          != roles
           ) {
            if (String oldUser ?= this.userId, oldUser != userId) {
                issueEvent_(SessionDeauthenticated, Void, &sessionDeauthenticated(oldUser),
                            () -> $|An exception in session {this.internalId_} occurred during a\
                                   | deauthentication event for user {oldUser.quoted()}
                           );
            }

            this.userId            = userId;
            this.exclusiveAgent    = exclusiveAgent;
            this.trustLevel        = trustLevel;
            this.roles             = roles.is(immutable) ? roles :
                                     roles.is(Freezable) ? roles.freeze() :
                                     new HashSet(roles).freeze(True);
            this.lastAuthenticated = clock.now;

            // reset failed attempt count since we succeeded in logging in
            // TODO

            issueEvent_(SessionAuthenticated, Void, &sessionAuthenticated(userId),
                        () -> $|An exception in session {this.internalId_} occurred during an\
                               | authentication event for user {userId.quoted()}
                       );
        }
    }

    @Override
    Boolean authenticationFailed(String? userId) {
        // accumulate failure information, both in absolute terms (number of attempts), and per
        // user id
        // TODO

        return False;
    }

    @Override
    void deauthenticate() {
        if (String oldUser ?= userId) {
            userId            = Null;
            exclusiveAgent    = False;
            trustLevel        = None;
            lastAuthenticated = Null;

            issueEvent_(SessionDeauthenticated, Void, &sessionDeauthenticated(oldUser),
                        () -> $|An exception in session {this.internalId_} occurred during a\
                               | deauthentication event for user {oldUser.quoted()}
                       );
        }
    }

    @Override
    void destroy() {
        issueEvent_(SessionDestroyed, Void, &sessionDestroyed(),
                    () -> $|An exception in session {this.internalId_} occurred during a\
                           | session-destroyed event
                   );

        manager_.unregisterSession(internalId_,
                                   sessionCookieInfos_[0]?.cookie : Null,
                                   sessionCookieInfos_[1]?.cookie : Null,
                                   sessionCookieInfos_[2]?.cookie : Null,
                                  );
    }

    @Override
    void sessionCreated() {
        confirmReached_(SessionCreated);
    }

    @Override
    void sessionDestroyed() {
        confirmReached_(SessionDestroyed);
    }

    @Override
    void sessionMergedFrom(Session temp) {
        confirmReached_(SessionMergedFrom);
    }

    @Override
    void sessionForked() {
        confirmReached_(SessionForked);
    }

    @Override
    void sessionAuthenticated(String user) {
        confirmReached_(SessionAuthenticated);
    }

    @Override
    void sessionDeauthenticated(String user) {
        confirmReached_(SessionDeauthenticated);
    }

    @Override
    TrustLevel tlsChanged() {
        confirmReached_(TlsChanged);
        return super();
    }

    @Override
    TrustLevel ipAddressChanged(IPAddress oldAddress, IPAddress newAddress) {
        confirmReached_(IPAddressChanged);
        return super(oldAddress, newAddress);
    }

    @Override
    TrustLevel userAgentChanged(String oldAgent, String newAgent) {
        confirmReached_(UserAgentChanged);
        return super(oldAgent, newAgent);
    }

    @Override
    TrustLevel fingerprintChanged() {
        confirmReached_(FingerprintChanged);
        return super();
    }

    @Override
    TrustLevel protocolViolated() {
        confirmReached_(ProtocolViolated);
        return super();
    }

    @Override
    Boolean anyEventsSince(Time time) = time < versionChanged_;


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Obtain the specified cookie information.
     *
     * @param cookieId  which session cookie to ask for
     *
     * @return True if the specified session cookie exists
     * @return (conditional) the SessionCookie
     * @return (conditional) the time that the SessionCookie was sent to the user agent
     * @return (conditional) the time that the SessionCookie was first sent back from the user agent
     * @return (conditional) the time that the SessionCookie will be expired by the user agent
     */
    conditional (SessionCookie cookie,
                 Time?         sent,
                 Time?         verified,
                ) getCookie_(CookieId cookieId) {
        SessionCookieInfo_? info = sessionCookieInfos_[cookieId.ordinal];
        return info == Null
                ? False
                : (True, info.cookie, info.sent, info.verified);
    }

    /**
     * Record that the specified cookie was sent to the user agent.
     *
     * @param cookie  one of the session cookies
     */
    void cookieSent_(SessionCookie cookie) {
        if (SessionCookieInfo_ info ?= sessionCookieInfos_[cookie.cookieId.ordinal],
                cookie.version == info.cookie.version) {
            info.sent = clock.now;
        }
    }

    /**
     * Record that the specified cookie was received by the user agent.
     *
     * @param cookie  one of the session cookies
     */
    void cookieVerified_(SessionCookie cookie) {
        if (SessionCookieInfo_ info ?= sessionCookieInfos_[cookie.cookieId.ordinal],
                cookie.version == info.cookie.version) {
            info.verified = clock.now;
        }
    }


    /**
     * Determine if the provided session cookie matches the one stored in this session.
     *
     * @param cookieId  specifies which cookie
     * @param value     the cookie contents
     *
     * @return a `Match_` value
     * @return a cookie, if the `Match_` value was `Correct`, `Older`, `Newer`, or `WrongId`, and
     *         sometimes `Corrupt`; otherwise, `Null`
     */
    (Match_ match, SessionCookie? cookie) cookieMatches_(CookieId cookieId, String value) {
        SessionCookieInfo_? info = sessionCookieInfos_[cookieId.ordinal];
        if (SessionCookie.textFromCookie(value) == info?.cookie.text) {
            info.verified ?:= clock.now;
            return Correct, info.cookie;
        }

        SessionCookie cookie;
        try {
            cookie = new SessionCookie(manager_, value);
        } catch (Exception _) {
            return Corrupt, Null;
        }

        if (cookie.cookieId != cookieId) {
            return WrongCookieId, cookie;
        }

        if (cookie.sessionId != this.internalId_) {
            return WrongSession, cookie;
        }

        return switch (cookie.version <=> this.version_) {
            case Lesser : (Older, cookie);
            case Greater: (Newer, cookie);
            case Equal  : (Corrupt, cookie); // everything "seems" right, but something didn't match
        };
    }

    /**
     * Indicates whether the persistent cookie should be used for this session.
     */
    Boolean usePersistentCookie_.get() = exclusiveAgent && cookieConsent.allows(Necessary);

    /**
     * Determine the cookies that should be shared with the user agent associated with this session.
     *
     * @param tls  True indicates that the request arrived on a TLS enabled connection
     *
     * @return a bitmask indicating the session cookies that the user agent should have
     */
    Byte desiredCookies_(Boolean tls) {
        return tls
                ? usePersistentCookie_
                        ? CookieId.All
                        : CookieId.BothTemp
                : CookieId.NoTls;
    }

    /**
     * Make sure that the specified cookies exist.
     *
     * @param cookieId  which session cookie to ask for
     */
    void ensureCookies_(Int requiredCookies) {
        if (requiredCookies & knownCookies_ == requiredCookies) {
            return;
        }

        // cookies need to be created, which causes the session version to change
        knownCookies_ |= requiredCookies;
        incrementVersion_();
    }

    /**
     * Check to see if the connection associated with the session has been modified.
     *
     * @param userAgent  a string indicating the user agent
     * @param ipAddress  the address from which the user agent connection is established
     *
     * @return True iff the connection has been modified
     */
    Boolean updateConnection_(String userAgent, IPAddress ipAddress) {
        String    oldAgent   = this.userAgent;
        IPAddress oldAddress = this.ipAddress;
        if (userAgent == oldAgent && ipAddress == oldAddress) {
            return False;
        }

        TrustLevel oldTrust = this.trustLevel;
        TrustLevel newTrust = oldTrust;

        if (userAgent != oldAgent) {
            newTrust = issueEvent_(UserAgentChanged, None, &userAgentChanged(oldAgent, userAgent),
                                   () -> $|An exception in session {this.internalId_} occurred during\
                                          | a user agent change event from {oldAgent.quoted()} to\
                                          | {userAgent.quoted()}
                                  ).notGreaterThan(newTrust);
        }

        if (ipAddress != oldAddress) {
            newTrust = issueEvent_(IPAddressChanged, None, &ipAddressChanged(oldAddress, ipAddress),
                                   () -> $|An exception in session {this.internalId_} occurred during\
                                          | an IPAddress change event from {oldAddress} to {ipAddress}
                                  ).notGreaterThan(newTrust);
        }

        this.userAgent  = userAgent;
        this.ipAddress  = ipAddress;
        this.trustLevel = newTrust;
        incrementVersion_();
        return True;
    }

    /**
     * Increment the session version as the result of a significant change to the session.
     *
     * @param newVersion  (optional) the suggested new version number
     */
    void incrementVersion_(Int? newVersion=Null) {
        assert knownCookies_ != 0;

        version_ = version_ + 1;
        if (newVersion? > version_) {
            version_ = newVersion;
        }

        Time now     = clock.now;
        Time expires = now + manager_.persistentCookieDuration;
        Byte desired = desiredCookies_(knownCookies_ & CookieId.BothTls != 0);
        for (CookieId cookieId : CookieId.values) {
            Int i = cookieId.ordinal;

            if (SessionCookieInfo_ oldInfo ?= sessionCookieInfos_[i]) {
                manager_.removeSessionCookie(this, oldInfo.cookie);
            }

            if (desired & cookieId.mask == 0) {
                sessionCookieInfos_[i] = Null;
            } else {
                // create the session cookie
                SessionCookie cookie = new SessionCookie(manager_, internalId_, cookieId,
                        knownCookies_, cookieConsent, cookieId.persistent ? expires : Null,
                        ipAddress, now, version_);
                sessionCookieInfos_[i] = new SessionCookieInfo_(cookie);
                manager_.addSessionCookie(this, cookie);
            }
        }
    }

    /**
     * Check if the session needs to be replaced.
     *
     * @param requestInfo  the incoming request
     *
     * @return True iff this session is abandoned
     * @return (conditional) the session object, or a `4xx`-range `HttpStatus` that indicates a
     *         failure
     */
    conditional SessionImpl|HttpStatus isAbandoned_(RequestInfo requestInfo) {
        return abandoned_
                ? (True, split_(requestInfo))
                : False;
    }

    /**
     * When a user agent cannot prove that it owns the session that it has a cookie for, it's
     * possible that the cookie was stolen either by that user agent, or by a different user agent.
     * Cookie theft can result in a stolen session; to reduce that impact, this method splits the
     * session such that both the good user agent and the bad user agent retain the session (albeit
     * two separate and subsequently diverging copies of the session), and reduces the trust level
     * to force re-authentication.
     *
     * @param requestInfo  the incoming request
     *
     * @return (conditional) the session object, or a `4xx`-range `HttpStatus` that indicates a
     *         failure
     */
    HttpStatus|SessionImpl split_(RequestInfo requestInfo) {
        if (!abandoned_) {
            // the first thing to do is to notify the session that an apparent theft was attempted;
            // if desired, the application can strip information out of the session or take other
            // actions appropriate to the level of concern
            trustLevel = issueEvent_(ProtocolViolated, None, &protocolViolated(),
                                     () -> $|An exception in session {this.internalId_} occurred\
                                            | during the processing of a protocol violation event
                                    ).notGreaterThan(trustLevel);

            // mark the old session as being "damaged goods", so if its other owner ever shows back
            // up, it will also abandon it, because if the cookie was indeed stolen, then the
            // likelihood of repeated attacks is significant (so it's a good idea to completely
            // abandon the old session id altogether)
            abandoned_ = True;
        }

        // create the new session
        HttpStatus|SessionImpl result = manager_.cloneSession(this, requestInfo);

        if (result.is(SessionImpl)) {
            // keep track of splits
            this.splits_ += new SessionSplit_(requestInfo.getClientAddress(), version_, result.internalId_);

            // create new cookies
            result.ensureCookies_(desiredCookies_(requestInfo.tls));

            // notify the new session that it was forked
            result.issueEvent_(SessionForked, Void, &sessionForked(),
                               () -> $|An exception occurred in session {result.internalId_} during\
                                      | the processing of a session-forked event
                              );
        }

        return result;
    }

    /**
     * Copy the contents of the passed session into this session.
     *
     * @param that  a session that this session is cloning
     */
    void cloneFrom_(SessionImpl that) {
        // TODO CP
    }

    /**
     * Merge the passed temporary session into this session.
     *
     * @param temp  a session that will be discarded, because it was determined to be a temporary
     *              session that was being used in lieu of this session
     */
    void merge_(SessionImpl temp) {
        this.lastUse         = this.lastUse.notLessThan(temp.lastUse);
        this.exclusiveAgent |= temp.exclusiveAgent;

        Date? tempConsent = temp.cookieConsent.lastConsent;
        Date? thisConsent = this.cookieConsent.lastConsent;
        if (tempConsent != Null && (thisConsent == Null || tempConsent > thisConsent)) {
            this.cookieConsent = temp.cookieConsent;
        }

        issueEvent_(SessionMergedFrom, Void, &sessionMergedFrom(temp),
                () -> $"An exception occurred merging session {temp.internalId_} into {this.internalId_}");

        temp.destroy();
    }

    /**
     * @param info  the current request
     *
     * @return a unique (within this session) integer identifier of the redirect, iff this session
     *         will permit a redirect to occur; otherwise the HttpStatus for an error that must be
     *         returned as a response to the provided `RequestInfo`
     */
    Int|HttpStatus prepareRedirect_(RequestInfo info) {
        // get rid of any excess pending redirects (size limit the array of "in flight" redirects)
        if (PendingRedirect_[] pending ?= pendingRedirects_, pending.size > MaxRedirects_) {
            // keep the most recent redirects, but one less than MaxRedirects_ to make room for the
            // new one we're about to add
            pendingRedirects_ = pending[pending.size-MaxRedirects_ >..< MaxRedirects_];
        }

        // prune any old redirects
        Time now     = clock.now;
        Time ancient = now - Duration:60s;
        pendingRedirects_ = pendingRedirects_?.removeAll(r -> r.created < ancient);

        // allocate a unique id
        Int64 id;
        do {
            id = rnd.int(100k).toInt64() + 1;
        } while (pendingRedirects_?.any(r -> r.id == id));

        PendingRedirect_ pending = new PendingRedirect_(id, info.getUri(), now);
        pendingRedirects_ = pendingRedirects_? + pending : [pending];

        return id;
    }

    /**
     * Claim (and clean up) a previously prepared redirect.
     *
     * @param id  an identifier returned from [prepareRedirect_] on this same session object
     *
     * @return True iff the specified id is a redirect that is registered on this session
     * @return (conditional) the Uri that caused the redirect
     */
    conditional Uri claimRedirect_(Int64 id) {
        if (PendingRedirect_[] redirects ?= pendingRedirects_) {
            for (PendingRedirect_ redirect : redirects) {
                if (redirect.id == id) {
                    // don't delete the redirect at this point; let it time out (in case some
                    // response gets lost, and the user agent resubmits the same request again)
                    return True, redirect.uri;
                }
            }
        }

        return False;
    }

    /**
     * Wrapper around event dispatch to handle exceptions, logging, and ensuring that the events
     * actually reach their destination.
     *
     * @param event          which event is being issued
     * @param defaultResult  the value to return by default if the event does not complete
     *                       successfully
     * @param handleEvent    the means to actually handle the event
     * @param renderLogText  the means to generate the text to log on a failure
     *
     * @return the event result
     */
    protected <Result> Result issueEvent_(Event_            event,
                                          Result            defaultResult,
                                          function Result() handleEvent,
                                          function String() renderLogText,
                                         ) {
        Result result = defaultResult;

        InFlight_ inFlight = new InFlight_(event, currentInFlight_);
        currentInFlight_ = inFlight;

        try {
            result = handleEvent();
            if (!inFlight.reached && manager_.shouldReport(event)) {
                log($|An override on the session {event} event handler did not call its "super" as\
                     | required. This is an error with unknown side-effects, and must be corrected\
                     | by the application developer. This error is likely to occur every time that\
                     | the event occurs, and so this message will not be repeated.
                   );
            }
        } catch (Exception e) {
            log($"{renderLogText()}: {e}");
        }

        assert currentInFlight_ == inFlight;
        currentInFlight_ = inFlight.next;

        return result;
    }

    /**
     * This method is used to confirm that event dispatching wasn't improperly terminated by someone
     * forgetting to call "super".
     *
     * @param event  the event being confirmed as having executed correctly
     */
    private void confirmReached_(Event_ event) {
        // the event to confirm will always be the first in the linked list, but since developers
        // do crazy things, be tolerant of unexpected things here
        for (InFlight_? current = currentInFlight_; current != Null; current = current.next) {
            if (current.event == event) {
                current.reached = True;
                return;
            }
        }
        // no assertion
    }

    /**
     * Convert an integer session id to a generic-looking (external) string identifier.
     *
     * @param the internal session id
     *
     * @return the session identifier string
     */
    static String idToString_(Int64 id) {
        Byte[] bytes = id == 0 ? [0] : id.toByteArray()[id.leadingZeroCount / 8 ..< 8];
        return Base64Format.Instance.encode(bytes);
    }

    /**
     * Convert a generic-looking (external) session id string to an implementation-specific internal
     * `Int` id value.
     *
     * @param str  the session identifier string
     *
     * @return the internal session id
     */
    static Int stringToId_(String str) {
        // the string contains a base-64 encoded set of up to 8 bytes, which are the 8 bytes of the
        // int session id
        Byte[] bytes = Base64Format.Instance.decode(str);

        // note: we need to take care to never communicate the exception text back to the user
        //       agent; this information should only be used on the server for debugging purposes
        assert bytes.size <= 8 as $"Invalid session {str.quoted()}, byte length={bytes.size}";

        return bytes.toInt64();
    }
}