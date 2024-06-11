/**
 * An Authenticator is a service that is used when a user (client) authentication is required.
 * Authentication takes many forms, and as such, it is difficult to represent the potential forms
 * that authentication can take in an elegant, simple, pluggable API. This interface attempts to
 * achieve the necessary pluggability, but exposes some necessary complexity, due to the complexity
 * of the various approaches to authentication that already exist and are in use.
 *
 * The model itself is fairly simple:
 *
 * * A request is received from a client;
 *
 * * The session associated with the request is found;
 *
 * * An web service endpoint is selected based on the request;
 *
 * * The endpoint is determined to require authentication (or re-authentication);
 *
 * * The Authenticator is invoked, and provided with the known context: The request, the session,
 *   and the endpoint;
 *
 * * The Authenticator can respond in one of three ways:
 *
 * * * The user is sufficiently authenticated, and the server should invoke the endpoint;
 *
 * * * The user cannot be authenticated, and the server should respond with an appropriate error;
 *
 * * * The user is not authenticated, and the Authenticator can either begin (or advance) the
 *     authentication process, or report an error back to the client; in both cases, the mechanism
 *     is a response that is returned from the Authenticator.
 */
interface Authenticator
            extends service {
    /**
     * Determine if the authenticator relies of a session to exist. Some authentication (such as
     * with API keys) does not require a server-side session, while other approaches rely on cookies
     * or other session mechanisms to tie together multiple client requests under a single
     * authenticated "umbrella".
     *
     * @param request   a request that requires authentication, and for which a {Session} does not
     *                  exist
     *
     * @return True iff the [Authenticator] requires a [Session] to exist before the specified
     *         request can be authenticated
     */
    Boolean requiresSession(RequestIn request) = False;

    /**
     * Authenticate the client (or user) using the provided request, session, and endpoint.
     * Authentication requires both an established session and a TLS connection, and is triggered by
     * an endpoint that is annotated by [LoginRequired] that specifies a [TrustLevel] higher than
     * the session's current `TrustLevel`.
     *
     * When the `Authenticator` successfully authenticates the client, it is responsible for
     * updating the session as appropriate, such as by calling [Session.authenticate].
     *
     * @param session   the session associated with the request that requires authentication, or
     *                  `Null` if no session has been created
     * @param request   a request that requires authentication
     *
     * @return [Allowed] to indicate the client has been authenticated; [Unknown] to indicate that
     *         this Authenticator does not know how to process the specified request;
     *         [Forbidden] to indicate that the response has bee recognized, but client is not being
     *         permitted to authenticate for any reason; or an HTTP [ResponseOut] to deliver to the
     *         client to indicate the next step in the process of authentication
     */
    AuthStatus|ResponseOut authenticate(Session? session, RequestIn request);

    /**
     * Check each received plain text request to make sure that it does not have any secret tokens
     * or password material in it. An implementation of `Authenticator` that normally uses secret
     * material in an HTTPS request should always check for that material being present in any plain
     * text request, and invalidate all future use of that secret material since it must be assumed
     * to have been leaked to malicious actors if it is ever transmitted in plain text form.
     *
     * @param a request that was received without TLS enabled
     *
     * @return `True` iff the request contains secret material
     * @return (conditional) an HTTP status or response to immediately send to the client, because
     *         the request transmitted secret material in plain text across a network
     */
    conditional HttpStatus|ResponseOut checkNonTlsRequest(RequestIn request) = False;

    enum AuthStatus {Allowed, Unknown, Forbidden}
}
