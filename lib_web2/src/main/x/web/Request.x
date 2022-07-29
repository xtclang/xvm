import net.SocketAddress;
import net.URI;

/**
 * A representation of an HTTP request.
 *
 * TODO doc how to get a request passed to an end point
 */
interface Request
        extends HttpMessage
    {
    /**
     * The request line. For HTTP v1, the request line is directly in the message; for HTTP v2 and
     * HTTP v3, this information is spread across a number of synthetic header entries, so the
     * `requestLine` has to be built from that information.
     */
    @RO String requestLine;

    /**
     * The protocol over which the request was received, if it is known.
     *
     * For an out-going request, the protocol is the requested protocol to use to send the request;
     * a client implementation may choose to use a different protocol if necessary.
     */
    @RO Protocol? protocol;

    /**
     * The IP address and port number used by the client to issue the request, if it is known.
     */
    @RO SocketAddress? client;

    /**
     * The IP address and port number used by the server to receive the request, if it is known.
     */
    @RO SocketAddress? server;

    /**
     * TODO
     */
    @RO String authority;

    /**
     * TODO
     */
    @RO String path;

    /**
     * The URI of the request.
     */
    URI uri;

    /**
     * The HTTP method ("GET", "POST", etc.)
     */
    HttpMethod method;

    /**
     * The accepted media types.
     */
    MediaType[] accepts;

    /**
     * The HTTP parameters contained with the URI query string.
     * REVIEW what about parameters located in the body?
     */
    Map<String, List<String>> parameters;


    // ----- cookie support ------------------------------------------------------------------------

    /**
     * Obtain the value of the specified cookie, if it is included in the request.
     *
     * @return True iff the specified cookie name is in the header
     * @return (conditional) the specified cookie
     */
    conditional String getCookie(String name)
        {
        for (String value : header.valuesOf("Cookie"))
            {
            // TODO parse ';' delimited list of key=value pairs
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
        header.add("Cookie", $"{name}={value}");
        }
    }
