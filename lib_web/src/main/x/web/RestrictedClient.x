/**
 * A [Client] implementation that restricts the `Client` functionality for the underlying client
 * based on the specified constraints.
 *
 * This is useful for injecting a `Client` into a child [Container], because that `Container` will
 * not be able to communicate with anything outside of the restricted domains.
 *
 * Note: the caller should *always* mask the created `RestrictedClient` prior to returning it.
 */
const RestrictedClient(Client underlying)
        implements Client {
    /**
     * Construct a `Client` that restricts access based on the specified lists.
     *
     * @param underlying  the underlying client
     * @param mode        the restriction mode
     * @param hostPorts   the list of allowed or disallowed [HostPort]s
     * @param protocols   the list of allowed or disallowed [Protocol]s
     */
    construct(Client              underlying,
              RestrictMode        mode,
              HostPort|HostPort[] hostPorts = [],
              Protocol|Protocol[] protocols = [],
              ) {
        this.underlying = underlying;
        this.mode       = mode;
        this.hostPorts  = hostPorts;
        this.protocols  = protocols;
    }

    /**
     * The restriction mode.
     */
    enum RestrictMode {Allow, Deny}

    RestrictMode mode;

    /**
     * The list of allowed or disallowed [HostPort]s.
     */
    HostPort|HostPort[] hostPorts;

    /**
     * The list of allowed or disallowed [Protocol]s.
     */
    Protocol|Protocol[] protocols;


    // ----- Client interface ----------------------------------------------------------------------

    @Override
    ResponseIn send(RequestOut request, PasswordCallback? callback = Null) {
        return allowed(request.uri)
                ? underlying.send(request, callback)
                : new responses.SimpleResponse(Forbidden);
    }

    /**
     * @return True if the specified Uri is allowed to be connected to
     */
    Boolean allowed(Uri uri) {
        TODO
    }

    @Override
    String toString() {
        return $|RestrictedClient {mode} \
                |  {{
                |  if (!(hostPorts.is(HostPort[]) && hostPorts.empty))
                |     {
                |     $.addAll("hosts={hostPorts}");
                |     }
                |  if (!(protocols.is(Protocol[]) && protocols.empty))
                |    {
                |    $.addAll("protocols={protocols}");
                |    }
                |  }}
                ;
    }
}