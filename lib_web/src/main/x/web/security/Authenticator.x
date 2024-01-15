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
     * Authenticate the client (or user) using the provided request, session, and endpoint.
     * Authentication requires both an established session and a TLS connection, and is triggered by
     * an endpoint that is annotated by [LoginRequired] that specifies a [TrustLevel] higher than
     * the session's current `TrustLevel`.
     *
     * When the `Authenticator` successfully authenticates the client, it is responsible for
     * updating the session as appropriate, such as by calling [Session.authenticate].
     *
     * @param request   a request that requires authentication
     * @param session   the session associated with the request that requires authentication
     *
     * @return [True] to indicate the client has been authenticated; [False] to indicate that the
     *         client is not being permitted to authenticate for any reason; or an HTTP
     *         [ResponseOut] to deliver to the client to indicate the next step in the process of
     *         authentication
     */
    Boolean|ResponseOut authenticate(RequestIn request, Session session);
}
