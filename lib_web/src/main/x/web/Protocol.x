/**
 * A representation of a protocol used for web services.
 */
const Protocol(String string, Version? version, String? ALPN_Id=Null) {

    /**
     * Construct a protocol from a protocol string.
     *
     * @param text  a
     */
    construct(String string) {
        String[] parts = string.split('/', trim=True);
        assert parts.size == 2 && !parts[0].empty && !parts[1].empty;
        Version version = new Version(parts[1]);

        this.string  = string;
        this.version = version;
    }

    // ----- constants -----------------------------------------------------------------------------

    /**
     * HTTP/1.0. HTTP over TCP/IP, with generally only one request/response per TCP/IP connection.
     *
     * This is an obsolete and insecure standard, and is almost never encountered.
     */
    static Protocol HTTP1 = new Protocol("HTTP/1.0", v:1.0);

    /**
     * HTTP/1.1. HTTP over TCP/IP, with the ability to reuse the TCP/IP connection.
     *
     * This is an old and insecure standard, but is still encountered, although its use is
     * declining. Since a large number of embedded implementations use this protocol, it is likely
     * to have a "half life", and may never disappear entirely. However, applications should avoid
     * using it for anything but a "home page" (a pre-login welcome mat) by requiring a secure
     * connection by default.
     */
    static Protocol HTTP1_1 = new Protocol("HTTP/1.1", v:1.1);

    /**
     * HTTP/2. HTTP over TCP/IP, but supporting any number of concurrent streams (in-flight requests
     * and/or responses) over a single TCP/IP socket connection, based on the SPDY protocol.
     *
     * This unsecured protocol is almost never encountered, because all known browser
     * implementations that support HTTP/2 require a TLS connection; see [HTTPS2].
     */
    static Protocol HTTP2 = new Protocol("HTTP/2", v:2, "h2c");

    /**
     * HTTP/3. HTTP over UDP/IP, using the QUIC protocol. The capabilities are similar to HTTP/2,
     * but the use of UDP is intended to avoid the "head of line blocking" problem that exists in
     * HTTP/2.
     *
     * Like HTTP/2, HTTP/3 is almost never encountered, because all known browser implementations
     * that support HTTP/3 require TLS; see [HTTPS3].
     */
    static Protocol HTTP3   = new Protocol("HTTP/3", v:3);

    /**
     * Protocol lookup table by string.
     */
    static Map<String, Protocol> byProtocolString =
            [
            HTTP1.string    =  HTTP1,
            HTTP1_1.string  =  HTTP1_1,
            HTTP2.string    =  HTTP2,
            HTTP3.string    =  HTTP3,
            ];


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The HTTP protocol string, which is the full text of the protocol and version as it would
     * appear in an [HttpMessage]. For example, an "HTTP-Version" string, such as "HTTP/1.1", is
     * located at the end of the first line of an HTTP 1.x request.
     */
    String string;

    /**
     * The version of the protocol, such as HTTP version `1.1`, `2`, or `3`, or WS version `13`.
     */
    Version? version;

    /**
     * The Application-Layer Protocol Negotiation (ALPN) identifier for the protocol.
     */
    String? ALPN_Id;
}