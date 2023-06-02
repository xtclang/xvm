/**
 * A representation of an HTTP-related scheme.
 */
const Scheme(String name, Boolean tls, Scheme? upgradeToTls = Null) {
    assert() {
        assert tls ^ (upgradeToTls != Null);
    }


    // ----- constants -----------------------------------------------------------------------------

    /**
     * The clear text HTTP scheme.
     */
    static Scheme HTTP = new Scheme("http", False, HTTPS);

    /**
     * The TLS-secured HTTP scheme.
     */
    static Scheme HTTPS = new Scheme("https", True);

    /**
     * Web Socket scheme.
     */
    static Scheme WS = new Scheme("ws", False, WSS);

    /**
     * Web Socket Secure scheme, which is the Web Socket scheme with TLS.
     */
    static Scheme WSS = new Scheme("wss", True);

    /**
     * Scheme lookup table by name.
     */
    static Map<String, Scheme> byName =
            [
            HTTP.name  = HTTP,
            HTTPS.name = HTTPS,
            WS.name    = WS,
            WSS.name   = WSS,
            ];


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The name, which is the full text of the scheme as it would appear in a URI.
     */
    String name;

    /**
     * True iff the scheme implies "transport layer security", which is the case for HTTPS and WSS.
     */
    Boolean tls;

    /**
     * The related scheme that provides "transport layer security", or Null iff `tls==True`.
     */
    Scheme? upgradeToTls;

    /**
     * Obtain the scheme that is the same as this one, but has TLS enabled.
     */
    Scheme tlsScheme.get() {
        return upgradeToTls ?: this;
    }
}