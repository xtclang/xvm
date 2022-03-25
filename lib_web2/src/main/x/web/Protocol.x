/**
 * A representation of a protocol used for web services.
 */
const Protocol(String name, Version version, Boolean TLS, String fullText)
    {
    /**
     * The name of the protocol, which is _normally_ either "HTTP" or "HTTPS".
     */
    String name;

    /**
     * The version of the protocol, such as HTTP version `1.1` or `2`.
     */
    Version version;

    /**
     * True iff the protocol provides "transport layer security", which is the case for HTTPS.
     */
    Boolean TLS;

    /**
     * The full text of the protocol or scheme portion of the [HttpMessage], such as "HTTP/2".
     */
    String fullText;
    }
