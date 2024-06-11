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
 * * A web service endpoint is selected based on the request;
 * * The `Endpoint` is examined to determine if it requires authentication (or re-authentication);
 * * If so, then the `Authenticator` is invoked, and provided with the request.
 * * The Authenticator responds with a list of `Attempt` objects describing each authentication
 *   attempt that was found in the request.
 *
 * It is common that there is exactly zero or one authentication attempts in a request, but there
 * can be any number of attempts using any number of authentication forms (bearer token, digest,
 * etc.) Some may indicate valid authentication attempts; some may indicate failed attempts (for
 * various reasons, such as an incorrect password, or expired token); some may be only partially
 * complete attempts. The host (whoever is invoking the `Authenticator`) must decide based on the
 * resulting list of [Attempt] information, if the user is considered to be authenticated. The host
 * then takes the appropriate action:
 *
 * * If there is no authentication information, then in most cases, a challenge would be returned to
 *   the client;
 * * If the authentication is incomplete but proceeding as defined by the specific authentication
 *   process, then the appropriate response is sent to the client to advance the process;
 * * If an authentication error or failure occurs, then the appropriate error status will be sent to
 *   the client;
 * * If the user is sufficiently authenticated and authorized, then the endpoint will be invoked.
 */
interface Authenticator
            extends Duplicable, service {
    /**
     * When a request is received, it may include (or imply) information that can be used in the
     * authentication process, including claims of user identity and/or entitlement(s), and proof
     * of those claims.
     *
     * * A [User] represents a user identity, which may or may not be valid;
     * * An [Entitlement] represents an entitlement, which may or may not be valid;
     * * A `String` is used to represent an invalid user or entitlement when it is not possible to
     *   use a [User] or [Entitlement] object for that purpose.
     */
    typedef User|Entitlement|String as Subject;

    /**
     * An enumeration of potential authorization statuses, in the order of least applicable to most
     * applicable:
     *
     * * [NoSession] indicates that the `Authenticator` requires a `Session` to be established
     *   before the authentication process can proceed
     * * [NoData] indicates that the request contains no information relevant to the `Authenticator`
     *   (the Subject should be `Null`)
     * * [KnownNoSession] same as [Known] but additionally indicates [NoSession]
     * * [Known] indicates that the request contains no information relevant to the
     *   `Authenticator` (the Subject should be `Null`), but that the `Authenticator` does have
     *   reason to expect that the specific client will use this authentication scheme
     * * [InProgress] indicates that the `Authenticator` has started the process of authentication,
     *   but additional information from the client is still required to authenticate
     * * [Alert] indicates that authentication was attempted and failed in a manner that indicates
     *   an attack on the authentication system
     * * [Failed] indicates that authentication was attempted, but that the attempt failed because
     *   the supplied credentials were wrong; the client may be permitted to authenticate again, but
     *   should be prevented from doing so without limit (it could represent an attack)
     * * [Expired] indicates that authentication was attempted, but that the attempt failed because
     *   the user, entitlement, or credentials have expired, or that the user has been suspended;
     *   this represents a failure, but the information is correct from the client's point of view
     * * [Success] indicates that the client request contained valid authentication information
     */
    enum Status {NoSession, NoData, KnownNoSession, Known, InProgress, Alert, Failed, Expired, Success}

    /**
     * Represents a response from an `Authenticator` to the client, iff the `Authenticator` needs to
     * indicate a specific response to the client that differs from the "normal" request/response
     * flow (i.e. differs from the default response). Normally, when authentication is needed, a
     * response containing the [HttpStatus] of [Unauthorized](HttpStatus.Unauthorized) is returned
     * to the client. If an `AuthResponse` is provided by the Authenticator, it should be one of (in
     * order of preference):
     *
     * * A `String` value that should be provided to the client in a "WWW-Authenticate:" header;
     *   this is the most common form of an `Authenticator` response, because multiple
     *   `Authenticators` can provide "WWW-Authenticate:" header values, and those can be combined
     *   into a single response.
     *
     * * An [HttpStatus] if the status differs from the expected. Specifically, the server will
     *   respond to the client by default with one of the following statuses:
     * * * [Unauthorized](HttpStatus.Unauthorized) - when authentication is absent but required
     * * * [BadRequest](HttpStatus.BadRequest) - when an authentication response is poorly formed
     * * * [Forbidden](HttpStatus.Forbidden) - when processing an authentication response has failed
     *
     * * If the protocol is more complex, only then should the `Authenticator` provide an entire
     *   HTTP response by providing a [ResponseOut].
     */
    typedef String|HttpStatus|ResponseOut as AuthResponse;

    /**
     * A record of an attempt to authenticate.
     *
     * @param subject   the User, Entitlement, or claim thereof if present; otherwise `Null`
     * @param status    the status of the authentication claim in the request
     * @param response  a specific response to send to the client to advance the authentication
     *                  process or indicate an authentication failure, or delete information on the
     *                  client (in the case of the [containsSecrets] method); `Null` is the
     *                  preferred value, and indicates that the appropriate default response should
     *                  be utilized
     */
    static const Attempt(Subject? subject, Status status, AuthResponse? response=Null);

    /**
     * Check a request received in plain text to make sure that it does not have any secret tokens
     * or password material in it. An implementation of `Authenticator` that normally uses secret
     * material in an HTTPS request should always check for that material being present in any plain
     * text HTTP request, and if any is found, then the `Authenticator` must invalidate all future
     * use of that secret material, because all material transmitted in plain text form must
     * **always** be assumed to have been leaked to malicious actors.
     *
     * In addition to invalidating the secret material, the authenticator _may_ also need to respond
     * to the caller with an HTTP response; for example, a response could be used to modify or
     * delete specific cookies.
     *
     * @param request  a request that was received without TLS enabled
     *
     * @return `True` iff the request contained any secret material
     * @return (conditional) an array of zero or more `Attempt` records indicating the secret
     *         material, and optional responses to the client for each
     */
    conditional Attempt[] containsSecrets(RequestIn request);

    /**
     * Authenticate the client (or user) using the provided request. Authentication requires a TLS
     * connection, and is triggered by routing to any `Endpoint` that is annotated by
     * [LoginRequired] or [Restrict]. When a valid [Session] exists for the request, the
     * authentication information in the session is used, if it exists, before invoking the
     * `Authenticator`.
     *
     * @param request  a request that requires authentication
     *
     * @return an array of `Attempt` records enumerating each authentication attempt found in -- or
     *         missing from -- the request
     */
    Attempt[] authenticate(RequestIn request);
}
