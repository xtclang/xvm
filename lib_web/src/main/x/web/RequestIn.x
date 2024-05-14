import net.UriTemplate;


/**
 * A representation of an incoming HTTP request.
 */
interface RequestIn
        extends Request {
    /**
     * The request line. For HTTP v1, the request line is directly in the message; for HTTP v2 and
     * HTTP v3, this information is spread across a number of synthetic header entries, so the
     * `requestLine` has to be built from that information.
     */
    @RO String requestLine.get() {
        return $"{method} {path} {protocol}";
    }

    /**
     * The [Uri] that represents the *complete* request URL.
     *
     * Note: The string form of the URI may not be exactly what was displayed in the browser's
     * address bar, for example, but it will be an equivalent representation that would result in
     * the request arriving at this server in the manner that the actual request was received.
     */
    Uri url;

    /**
     * The IP address that indicates the IP address of the user agent. This information is not
     * trusted (because it can be forged), but it can be useful for differentiating among many
     * requests coming through the same proxy.
     */
    @RO IPAddress originator;

    /**
     * The IP address that the request was received from, which may be a proxy server.
     */
    @RO IPAddress client;

    /**
     * The IP address on which the request was received.
     */
    @RO IPAddress server;

    /**
     * The port number on which the request was received.
     */
    @RO UInt16 serverPort;

    /**
     * The HTTP parameters contained with the URI query string.
     */
    @RO Map<String, String|List<String>> queryParams;

    /**
     * The result of matching a UriTemplate against this request.
     */
    @RO UriTemplate.UriParameters matchResult;
}