import net.IPAddress;


/**
 * `Session` represents the information that is (i) managed on a server, (ii) on behalf of a person
 * or entity, (iii) from a specific user agent (e.g. browser or client application), (iv) using a
 * specific application, (v) over some period of time. The `Session` capability enables stateful
 * application behavior, by maintaining live state on the server specific to that person and their
 * cumulative actions, which provides context for a sequence of otherwise-stateless operations.
 *
 * The `Session` compensates for the largely-stateless nature of HTTP. It allows an application to
 * appear to the user to be a smooth, contiguous, and singularly-focused experience, despite being
 * composed (on the server side, at least) of discrete units of stateless logic. A typical example
 * is a server application composed almost entirely of CRUD-like REST-ful operations invoked by a
 * reactive-style browser application, of which the modern web is replete.
 *
 * The session mechanism is established and maintained on the server to bridge the gaps between
 * stateless invocations. For example, by storing the authentication state of the user in the
 * session, the server knows to explicitly redirect the flow of processing when an unauthenticated
 * user attempts to access functionality that is marked as requiring authentication in order to
 * execute, i.e. the authentication must have already been successfully performed _before_ that
 * operation is permitted to execute. When such a request arrives, the server can first redirect the
 * flow of processing to the authentication process, and once authentication successfully completes,
 * the server can then redirect the flow back to the previously requested functionality. The result
 * is a seamless application experience, with the authentication process automatically occurring
 * exactly when it is first required, and all subsequent operations occurring on behalf of that
 * authenticated user identity.
 *
 * A session is automatically created by the server as soon as it is possible to do so, and the
 * session is maintained by the server until it is explicitly destroyed or it times out (if the
 * application is configured to time out its sessions for security purposes). The expected means of
 * associating the server's session object with incoming HTTP requests is to utilize one or more
 * HTTP cookies. The lifetime of any such cookies should not be assumed to be managed in the same
 * manner as the lifetime of the server session itself; the HTTP cookies could potentially persist
 * for the lifetime of the user agent device. It is further expected that an implementation would
 * always utilize two cookies: one with an expiry (which implies that the cookie should be stored
 * on a persistent storage device by the browser or user-agent), and one without an expiry (which
 * implies that the cookie will cease to exist if the tab and/or browser is closed, or if the
 * user-agent application terminates). This use of two cookies helps to detect an event that may
 * indicate the need for re-authentication, such as when the persistent cookie is passed in an HTTP
 * request but the ephemeral cookie is not (which may indicate that the browser or application was
 * closed and then later re-opened).
 *
 * For security reasons (including both man-in-the-middle and plain-text attacks), session
 * functionality **requires** Transport Layer Security (TLS), such as when the `HTTPS` protocol is
 * being used. Applications should always _default_ to always using TLS; for example, see
 * [The HTTPS-Only Standard](https://https.cio.gov/everything/) and
 * [HTTPS Everywhere](https://en.wikipedia.org/wiki/HTTPS_Everywhere). Any HTTP cookies must also
 * specify the `Secure` tag (which disallows browsers from sending the cookies outside of an HTTPS
 * connection). Any data passed without TLS should be assumed to be visible to the entire world;
 * allowing cookies to be passed outside of TLS is fundamentally insecure.
 *
 * There are also significant legal and regulatory issues related to the use of cookies, and every
 * application should carefully think through these issues up front. The best known case is the EU
 * GDPR directive, but many countries (and states, in the case of the US) have their own
 * similar-yet-different regulations, laws, and directives. Most of these boil down to: (i) the
 * definition of personal information, including metadata that can be used indirectly (or
 * eventually) as "personally identifying information"; (ii) the definition of user consent for the
 * collection of personal information; and (iii) the requirements for securely managing any such
 * personal information, including restrictions on what jurisdictions that information is allowed
 * to be collected, processed, and stored within. The use of cookies for maintaining session
 * continuity **may** be subject to these regulations.
 *
 * When it works as designed, the `Session` corresponds exactly to the person using the application,
 * i.e. the actual person in front of the screen. There are two related but separate concepts here:
 * (i) the person (or entity) _using_ the application, via a specific "user agent" (e.g. a browser),
 * on a specific device (e.g. a computer); and (ii) the security _subject_ aka [userId] which the
 * application authenticates and performs work on behalf of. While these two concepts are heavily
 * correlated, they actually represent an `m x n` matrix:
 *
 * * The person (or entity) is the one that can _consent_ to accepting cookies;
 * * The person may log in and out as different userIds, but the previous consent applies across all
 *   of those logins;
 * * The person indicates whether the device and user agent is shared or exclusive;
 * * Settings such as language, locale, time zone, and so on are set by and bound to the person, not
 *   to the authenticated user;
 * * A single user may be authenticated by multiple different people, and across any number of
 *   devices and user agents.
 *
 * The consent and device/user agent exclusivity also produce a matrix:
 *
 * * If consent is withheld, then no persistent cookies will be used;
 * * If consent is granted, but the device/user agent is shared (e.g. the login check-box that says
 *   "This is a public or shared computer"), then no persistent cookies will be used, even to record
 *   consent (because the consent of other people who use the same computer cannot be assumed);
 * * If consent is granted, and the device/user agent is not shared, then the consent can be
 *   recorded persistently, and a persistent session cookie can be used.
 *
 * In the case of a shared device/user agent ([exclusiveAgent]=`False`), the use of ephemeral
 * cookies means that the authenticated session is lost when the browser is closed, for example. For
 * security purposes, it is expected that a session time-out will also be used, so that failure to
 * close the browser would not allow a subsequent person to access the same session (assuming some
 * period of time has passed). The delineation between these two means that on a shared computer, a
 * person would have to provide cookie consent and log in each time that they use the application,
 * and that they might even have to log in more than once if there is some period of inactivity that
 * causes the session to time out. On the other hand, a person using their own machine might provide
 * consent and log in to the application only once -- ever! It is expected that session time-out
 * configuration can differ based on the value of `exclusiveAgent`; a banking application would
 * likely still time out sessions, while an e-commerce application is unlikely to.
 *
 * There are at least two other HTTP events that factor into the trust equation:
 *
 * * A TLS interruption: Implementations that have visibility to TLS session negotiation can raise
 *   an event when a TLS session is re-negotiated. The TLS session lifespan tends to match that of
 *   the persistent (e.g. "keep-alive") TCP connection itself.
 *
 * * An IP address change: When a worker logged into a work application takes their notebook home
 *   and opens it back up to continue working from home, the IP address that is associated with
 *   their session (their office IP address) will be different than the IP address that shows up on
 *   the next HTTP request (their home IP address).
 *
 * * A user agent change: An operating system upgrade, browser upgrade, or application upgrade may
 *   cause the `User-Agent` in the HTTP requests to differ from the `User-Agent` associated with the
 *   the session.
 *
 * * A user agent _fingerprint_ change: Minor details about the user agent can be gleaned from the
 *   incoming requests. These details are often abused in order to uniquely identify users by the
 *   set of details collected as part of the fingerprint, so the session interface design carefully
 *   avoids specifying any of the details that it collects, and the means that it uses to detect
 *   actionable changes in those details. Instead, if the feature is available and enabled, the
 *   session emits an event when the user agent fingerprint appears to have changed, such that a
 *   new user agent may have been somehow substituted for the former one.
 *
 * For security purposes, these events will tend to lower the trust level of the session, which may
 * result in re-authentication being required. The underlying reason is that these events may
 * indicate a man-in-the-middle or a cookie-stealing attack, i.e. that a cookie has been taken from
 * one application or device to be used on another. These types of attacks do exist, so in the
 * general case, this behavior should be configurable on an application basis, based on security
 * requirements of each application.
 *
 * There are details related to the session that are automatically managed by the server, and are
 * visible to the application logic running on the server, and there are operations that the session
 * makes available to the application logic. For example, the application can determine if the user
 * represented by the session has been authenticated (the [userId] property), and when that
 * authentication occurred (the [lastAuthenticated] property), and the application can explicitly
 * [authenticate] or [deauthenticate] a user. There are also notifications that occur on the session
 * of significant changes and events; these require the application to provide a session mix-in.
 *
 * The use of custom mix-ins allows an application (or framework, etc.) to enhance a session by
 * adding state and functionality to the session that is created and managed automatically by the
 * server. For example, if an application needs to hold onto some resource in the session, and
 * release the resource if the user logs out (or the session is destroyed), then it can place all of
 * that capability into a session mix-in:
 *
 *     mixin AppSession
 *             into Session
 *         {
 *         Resource? resource;
 *
 *         @Override
 *         void sessionDeauthenticated()
 *             {
 *             resource?.close();
 *             resource = Null;
 *             super();
 *             }
 *         }
 *
 * The _presence_ of a session mix-in is enough; the server will automatically incorporate each
 * present session mix-in into each session object.
 *
 * There are a few rules that must be followed when creating a session mix-in:
 *
 * 1. It is possible for multiple session mix-ins to exist, each with its own properties, methods,
 *    and override implementations of session event methods. Be cautious when adding methods and
 *    properties to the session mix-in, and carefully choose the names of any added methods and
 *    properties to avoid the potential for collisions.
 *
 * 2. Never override the session properties defined on the `Session` interface.
 *
 * 3. Never override the session control methods defined on the `Session` interface.
 *
 * 4. When overriding a session event method, always remember to invoke the `super` function.
 *
 * 5. Never use property or method names that end with the underscore `_` character.
 *
 * The prototypical session example is to hold shopping cart data in an e-commerce application.
 * However, modern e-commerce applications will almost always persist shopping cart information in
 * the database for authenticated users, so that they can place items into the cart on one device,
 * and later continue shopping (with those items still in the cart) on another device. Since the
 * session does not move from device to device, using the session to store the shopping cart is a
 * counter-example in this case. However, in the case of a non-authenticated user, it is likely that
 * the application would store the shopping cart information in the session, because there is no
 * user to associate the shopping cart data with. Upon authentication, the application would then
 * merge the ephemeral shopping cart information from the session into the persistent shopping cart
 * information associated with the user. This is why session events like [sessionAuthenticated]
 * exist for the application to place custom code on.
 *
 * To obtain the session for use in an [Endpoint], the endpoint method should include a parameter of
 * type session:
 *
 *     @Post("/{id}/items")
 *     Item addItem(Session session, @UriParam String id, @BodyParam Item item) {...}
 *
 * It is expected that user agent cookies will be used to manage the session identity. For any such
 * implementation, the cookie(s) should specify the "Secure" option to avoid session identity
 * hijacking attacks, unless sessions without TLS are desired, in which case it is advisable to bind
 * the session identity to the client address and to hide the session identity inside the cookie
 * using cryptographically secure encryption (which is always a good idea). Additionally, the
 * "HttpOnly" option should be used, if possible, to prevent any session information from being
 * exposed to client side JavaScript. Lastly, the "SameSite" cookie attribute should be specified;
 * its potential values are: `Strict`, `Lax`, or `None`.
 *
 * It is also desirable in many systems to allow a customer service representative view the same
 * page and data that a user (who has given explicit permission) is viewing. While the full
 * implementation of such a feature is beyond the scope of this documentation, it is clear that the
 * capability would require involvement of (and access to) the end user's session object. Instead of
 * maintaining a central and accessible registry of sessions keyed by their user identities, the
 * proposed model is to provide the means, within the application, for the end user to access a
 * "please help me" endpoint, which may require re-authentication and should require explicit
 * approval for the user's session to be accessed by a customer service representative.
 *
 * TODO specify how sessions are persisted to storage; define events (e.g. "loaded") for the same
 */
interface Session
        extends service
    {
    // ----- session properties --------------------------------------------------------------------

    /**
     * General purpose session attributes.
     *
     * While it is expected that applications will add their own properties to their session mixins
     * instead of using this generic dictionary structure, having this built in to the `Session`
     * interface simplifies the task of building reusable components and frameworks. Specifically,
     * they can rely on this built-in storage for their session-related information.
     *
     * The contents of this property may be modified by the application.
     */
    @RO Map<String, Shareable> attributes;

    /**
     * This is the date/time that the session was created.
     *
     * Generally, a session may last indefinitely -- literally until the client device is no
     * longer operational, which could be many years. This value does not indicate "when the user
     * started the app today", or "when the user logged on".
     */
    @RO Time created;

    /**
     * This is the date/time that the session was destroyed. This property has the value `Null`
     * until the session is destroyed.
     */
    @RO Time? destroyed;

    /**
     * The total number of requests that have been received that were associated with this session.
     */
    @RO Int requestCount;

    /**
     * The number of requests that are currently processing that are associated with this session.
     */
    @RO Int activeRequests;

    /**
     * This is the date/time that the most recent request related to this session was completed, or
     * if any requests are currently active, the date/time value will be "now".
     */
    @RO Time lastUse;

    /**
     * This is the last known IPAddress associated with the session. Each request has an IP address,
     * and that address is retained to detect when a session changes its IP address. For a secure
     * application, this change can be used to force re-authentication, as a means to defeat a
     * "cookie stealing" attack.
     *
     * Care must be used when using, holding, storing, or even just logging this information,
     * because it can be considered _personally identifying information_, and subject to personal
     * data protection laws.
     */
    @RO IPAddress ipAddress;

    /**
     * This is the last known `User-Agent` associated with the session. Each request may include
     * `User-Agent` information, and that information is retained to detect changes in the user
     * agent which could indicate a "cookie stealing" attack. (Normally, this value would only
     * change when the application is upgraded, or for a web application, when the OS or browser is
     * upgraded.) Such a change can be used to force re-authentication in a secure application.
     *
     * Care must be used when using, holding, storing, or even just logging this information,
     * because it can be considered _personally identifying information_, and subject to personal
     * data protection laws.
     */
    @RO String userAgent;

    /**
     * `True` indicates that the user agent is **not** shared with untrusted users; `False`
     * indicates that the device is shared, such as is indicated by the common checkbox "This is a
     * public or shared computer" that is part of a logic screen.
     *
     * This property may be modified by the application.
     */
    Boolean exclusiveAgent;

    /**
     * Information about the explicit cookie consent for this session.
     *
     * This property may be modified by the application.
     */
    CookieConsent cookieConsent;

    /**
     * This is a `String` representation of the authenticated user identity (aka "subject");
     * otherwise `Null`.
     */
    @RO String? userId;

    /**
     * This is the date/time that the session was last authenticated; otherwise `Null`.
     */
    @RO Time? lastAuthenticated;

    /**
     * This is the current trust level associated with this session. Authentication will tend to
     * set the trust level to its highest setting, and deauthentication to its lowest setting.
     * Changes to the [ipAddress] and [userAgent] will tend to degrade the trust level.
     *
     * It is possible for the trust level to be `None`, yet the [userId] have a value. This occurs
     * when the residual information about the previously authenticated user is retained (the user
     * is not explicitly deauthenticated), but when re-authentication is required to perform any
     * [@LoginRequired](LoginRequired) activity. In other words, the application still knows who is
     * likely to be using the application, but will force re-authentication for any operation that
     * needs to know who is using the application.
     *
     * This property may be modified by the application.
     */
    TrustLevel trustLevel;

    /**
     * This is a `String` representation of the _internal_ identity of the `Session` itself.
     *
     * Great care must be used when using, holding, storing, or even just logging this identity,
     * because this identity can easily be used to correlate _personally identifying information_.
     * In many jurisdictions, the _existence_ of personally identifying information is regulated;
     * the common example is GDPR in the EU. An application should only use this identity iff the
     * implications related to personal data protection laws are well understood and strictly
     * adhered to.
     */
    @RO String sessionId;


    // ----- session control -----------------------------------------------------------------------

    /**
     * This method allows an application to explicitly configure the authentication information for
     * the session. This method allows an application to perform its own explicit authentication.
     *
     * @param userId          the user identity to associate with the session
     * @param exclusiveAgent  pass `True` iff the device and `User-Agent` that the login is
     *                        occurring from is used exclusively by one person; pass `False` for a
     *                        public or shared device
     */
    void authenticate(String userId, Boolean exclusiveAgent);

    /**
     * This method allows an application to explicitly de-authenticate the session. One obvious
     * example usage would be to implement an application's "log out" feature.
     */
    void deauthenticate();

    /**
     * This method allows an application to explicitly destroy a session, which should also result
     * in any information about the session (such as cookies) being removed from the client.
     * Additionally, destruction of the session implies the revocation of cookie consent.
     */
    void destroy();


    // ----- session events ------------------------------------------------------------------------

    /**
     * This event is invoked when the session is created. It allows the application to set up the
     * initial state of the session.
     *
     * When implementing this method, remember to invoke the `super` function.
     */
    void sessionCreated();

    /**
     * This event is invoked when the session is destroyed. It allows the application to clean up
     * anything that the session is holding onto.
     *
     * When implementing this method, remember to invoke the `super` function.
     */
    void sessionDestroyed();

    /**
     * This event is invoked when the session is authenticated. It allows the application to set up
     * information related to the user in the session.
     *
     * Note: If a different `userId` was previously authenticated, then the [sessionDeauthenticated]
     * event will be invoked before this event.
     *
     * When implementing this method, remember to invoke the `super` function.
     *
     * @param user  the user that was is now authenticated
     */
    void sessionAuthenticated(String user);

    /**
     * This event is invoked when a previously authenticated user is "logged out". It allows the
     * application to clean up any information related to the user in the session.
     *
     * When implementing this method, remember to invoke the `super` function.
     *
     * @param user  the user that was previously authenticated but is being logged out
     */
    void sessionDeauthenticated(String user);

    /**
     * This event is invoked when a TLS connection is re-negotiated. It is possible for non-TLS
     * traffic to exist concurrently with TLS traffic, but this notification only exists for the
     * portion of the traffic that uses TLS, since resources that are **not** protected by TLS are
     * not protected to begin with.
     *
     * If this feature is supported, when a session had previously been accessed on TLS, and a new
     * request arrives on a new TLS connection, this notification is raised. Modern user agents are
     * expected to hold a TLS connection open for a long period of time, typically as long as
     * possible.
     *
     * When implementing this method, remember to invoke the `super` function.
     *
     * @return the suggested new `TrustLevel` for the session, based on the TLS interruption
     */
    TrustLevel tlsChanged()
        {
        // trust on a single-user device only degrades to the normal level
        return trustLevel.minOf(exclusiveAgent ? Normal : None);
        }

    /**
     * This event is invoked when the user agent's IP address changes. This most commonly occurs
     * when a user changes their device location, e.g. from home to work or vice versa, but it can
     * occur for any of a number of reasons. In a cookie stealing attack, for example, one would
     * expect the IP address to differ.
     *
     * When implementing this method, remember to invoke the `super` function.
     *
     * @param oldAddress  the previously known IP address
     * @param newAddress  the newly observed IP address
     *
     * @return the suggested new `TrustLevel` for the session, based on the IP address change
     */
    TrustLevel ipAddressChanged(IPAddress oldAddress, IPAddress newAddress)
        {
        // trust on a single-user device only degrades to the normal level
        return trustLevel.minOf(exclusiveAgent ? Normal : None);
        }

    /**
     * This event is invoked when the user's `User-Agent` changes.
     *
     * When implementing this method, remember to invoke the `super` function.
     *
     * @param oldAgent  the previously known `User-Agent` value
     * @param newAgent  the newly observed `User-Agent` value
     *
     * @return the suggested new `TrustLevel` for the session, based on the `User-Agent` change
     */
    TrustLevel userAgentChanged(String oldAgent, String newAgent)
        {
        if (exclusiveAgent)
            {
            // if it's more than a version change, then require re-authentication
            if (oldAgent.split('/')[0] != newAgent.split('/')[0])
                {
                return None;
                }

            return trustLevel.minOf(Normal);
            }
        else
            {
            // a user agent change on a shared device is very suspect, because the session cookie
            // should have been lost e.g. if the browser was upgraded
            return None;
            }
        }

    /**
     * This event is invoked when the user agent fingerprint (something other than the user agent
     * string itself) changes, iff user agent fingerprinting is enabled. Neither the details of what
     * composes a fingerprint nor what form a fingerprint takes is defined, and the fingerprint
     * information is purposefully hidden from user code.
     *
     * User agent fingerprinting is often used to track users against their will, which is a
     * capability that this design consciously avoids. However, the ability to detect even minor
     * changes in the details about a user agent can be extremely useful for safeguarding a user's
     * information, which is a worthwhile goal. It is a careful decision to expose the fact that the
     * fingerprint associated with the session has changed, but to hide any details of the
     * fingerprint information itself, including how the change was detected.
     *
     * When implementing this method, remember to invoke the `super` function.
     *
     * @return the suggested new `TrustLevel` for the session, based on the user agent fingerprint
     *         change
     *
     */
    TrustLevel fingerprintChanged()
        {
        // trust on a single-user device only degrades to the normal level
        return trustLevel.minOf(exclusiveAgent ? Normal : None);
        }
    }
