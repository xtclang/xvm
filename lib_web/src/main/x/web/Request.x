import ecstasy.collections.CaseInsensitive;

import net.UriTemplate;


/**
 * A representation of an HTTP request.
 *
 * The four fundamental parts of an HTTP request are:
 *
 * * Method
 * * Scheme
 * * Authority
 * * Path
 *
 * TODO doc how to get a request passed to an end point
 */
interface Request
        extends HttpMessage
    {
    /**
     * The HTTP method ("GET", "POST", etc.)
     */
    HttpMethod method;

    /**
     * Corresponds to the ":scheme" pseudo-header field in HTTP/2.
     */
    Scheme scheme;

    /**
     * Corresponds to the ":authority" pseudo-header field in HTTP/2. This includes the authority
     * portion of the target URI.
     */
    String authority;

    /**
     * Corresponds to the ":path" pseudo-header field in HTTP/2. This includes the path and query
     * parts of the target URI.
     */
    String path;

    /**
     * The request line. For HTTP v1, the request line is directly in the message; for HTTP v2 and
     * HTTP v3, this information is spread across a number of synthetic header entries, so the
     * `requestLine` has to be built from that information.
     */
    @RO String requestLine;

    /**
     * The URI of the request.
     */
    Uri uri;

    /**
     * The IP address and port number used by the client to issue the request, if it is known.
     */
    @RO SocketAddress? client;

    /**
     * The IP address and port number used by the server to receive the request, if it is known.
     */
    @RO SocketAddress? server;

    /**
     * The protocol over which the request was received, if the protocol is known.
     *
     * For an out-going request, the protocol is the requested protocol to use to send the request;
     * a client implementation may choose to use a different protocol if necessary.
     */
    Protocol? protocol;

    /**
     * The HTTP parameters contained with the URI query string.
     */
    Map<String, String|List<String>> queryParams;

    /**
     * The result of matching a UriTemplate against this request.
     */
    UriTemplate.UriParameters matchResult;

    /**
     * The accepted media types.
     */
    AcceptList accepts;

    /**
     * @return an `Iterator` of all cookie names in the request
     */
    Iterator<String> cookieNames()
        {
        return header.valuesOf(Header.COOKIE, ';')
                     .map(kv -> kv.extract('=', 0, "???").trim());
        }

    /**
     * @return an iterator of all cookie names and values in the request
     */
    Iterator<Tuple<String, String>> cookies()
        {
        return header.valuesOf(Header.COOKIE, ';')
                     .map(kv -> (kv.extract('=', 0, "???").trim(), kv.extract('=', 1).trim()));
        }

    /**
     * Obtain the value of the specified cookie, if it is included in the request.
     *
     * @return True iff the specified cookie name is in the header
     * @return (conditional) the specified cookie
     */
    conditional String getCookie(String name)
        {
        for (String value : header.valuesOf(Header.COOKIE, ';'))
            {
            if (name == value.extract('=', 0, "???"))
                {
                return True, value.extract('=', 1);
                }
            }
        return False;
        }

    /**
     * Add the specified cookie information to the request; if the cookie of the same name already
     * exists in the request, then it is replaced with the new value.
     *
     * @param name   the cookie name to include in the request
     * @param value  the cookie value
     */
    void setCookie(String name, String value)
        {
        // TODO remove any existing entry for this cookie name (or replace its value)
        // TODO validation of cookie name, validation of value
        header.add(Header.COOKIE, $"{name}={value}");
        }
    }