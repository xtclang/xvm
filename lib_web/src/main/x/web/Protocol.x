/**
 * A representation of a protocol used for web services.
 */
const Protocol(String string, Scheme scheme, Version? version, String? ALPN_Id=Null)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * HTTP/1.0. HTTP over TCP/IP, with generally only one request/response per TCP/IP connection.
     *
     * This is an obsolete and insecure standard, and is almost never encountered.
     */
    static Protocol HTTP1 = new Protocol("HTTP/1.0", HTTP, v:1.0);

    /**
     * HTTP/1.1. HTTP over TCP/IP, with the ability to reuse the TCP/IP connection.
     *
     * This is an old and insecure standard, but is still encountered, although its use is
     * declining. Since a large number of embedded implementations use this protocol, it is likely
     * to have a "half life", and may never disappear entirely. However, applications should avoid
     * using it for anything but a "home page" (a pre-login welcome mat) by requiring a secure
     * connection by default.
     */
    static Protocol HTTP1_1 = new Protocol("HTTP/1.1", HTTP, v:1.1);

    /**
     * HTTP/1.1 over an SSL connection.
     *
     * This is an old and _potentially_ insecure standard, but is still encountered, although its
     * use (like HTTP/1.1) is beginning to decline, and it will similarly require support for
     * embedded implementations for decades to come. Servers supporting HTTP/1.1 over SSL should be
     * configured to severely limit the set of accepted ciphers; from SSL 3.0, only DHE-RSA with
     * forward secrecy is considered secure, so all other SSL 3.0 ciphers should be disabled unless
     * they are required to support specific embedded clients.
     */
    static Protocol HTTPS1_1 = new Protocol("HTTP/1.1", HTTPS, v:1.1);

    /**
     * HTTP/2. HTTP over TCP/IP, but supporting any number of concurrent streams (in-flight requests
     * and/or responses) over a single TCP/IP socket connection, based on the SPDY protocol.
     *
     * This unsecured protocol is almost never encountered, because all known browser
     * implementations that support HTTP/2 require a TLS connection; see [HTTPS2].
     */
    static Protocol HTTP2 = new Protocol("HTTP/2", HTTP, v:2, "h2c");

    /**
     * HTTP/2 over a TLS connection.
     *
     * This is a commonly encountered protocol, and its usage is likely to grow.
     */
    static Protocol HTTPS2  = new Protocol("HTTP/2", HTTPS, v:2, "h2");

    /**
     * HTTP/3. HTTP over UDP/IP, using the QUIC protocol. The capabilities are similar to HTTP/2,
     * but the use of UDP is intended to avoid the "head of line blocking" problem that exists in
     * HTTP/2.
     *
     * Like HTTP/2, HTTP/3 is almost never encountered, because all known browser implementations
     * that support HTTP/3 require TLS; see [HTTPS3].
     */
    static Protocol HTTP3   = new Protocol("HTTP/3", HTTP, v:3);

    /**
     * HTTP/3 with TLS.
     *
     * This is becoming a commonly encountered protocol, and its usage is likely to grow.
     */
    static Protocol HTTPS3  = new Protocol("HTTP/3", HTTPS, v:3, "h3");

    /**
     * Web Socket protocol.
     */
    static Protocol WS13 = new Protocol("WS/13", WS, v:13);

    /**
     * Web Socket Secure protocol, which is the Web Socket protocol with TLS.
     */
    static Protocol WSS13 = new Protocol("WSS/13", WSS, v:13);

    /**
     * Protocol lookup table by string.
     */
    static Map<String, Protocol> byProtocolString =
            [
            HTTP1.string    =  HTTP1,
            HTTP1_1.string  =  HTTP1_1,
            HTTPS1_1.string =  HTTPS1_1,
            HTTP2.string    =  HTTP2,
            HTTPS2.string   =  HTTPS2,
            HTTP3.string    =  HTTP3,
            HTTPS3.string   =  HTTPS3,
            WS13.string     =  WS13,
            WSS13.string    =  WSS13,
            ];


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The HTTP protocol string, which is the full text of the protocol and version as it would
     * appear in an [HttpMessage]. For example, an "HTTP-Version" string, such as "HTTP/1.1", is
     * located at the end of the first line of an HTTP 1.x request.
     */
    String string;

    /**
     * The scheme of the protocol, which is usually HTTP, HTTPS, or WS.
     */
    Scheme scheme;

    /**
     * The version of the protocol, such as HTTP version `1.1`, `2`, or `3`, or WS version `13`.
     */
    Version? version;

    /**
     * True iff the protocol provides "transport layer security", which is the case for HTTPS and
     * WSS.
     */
    Boolean TLS;

    /**
     * The Application-Layer Protocol Negotiation (ALPN) identifier for the protocol.
     */
    String? ALPN_Id;
    }