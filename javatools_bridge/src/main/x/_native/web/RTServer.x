import libcrypto.Algorithm;
import libcrypto.CryptoKey;
import libcrypto.Decryptor;
import libcrypto.KeyStore;

/**
 * The native HttpServer service implementation.
 */
@Concurrent
service RTServer
        implements HttpServer {

    typedef immutable Object as RequestContext;

    @Override
    String bindAddr;

    @Override
    UInt16 plainPort;

    @Override
    UInt16 tlsPort;

    @Override
    void configure(String bindAddr, UInt16 httpPort=80, UInt16 httpsPort=443) {
        configureImpl(bindAddr, httpPort, httpsPort);

        this.bindAddr  = bindAddr;
        this.plainPort = httpPort;
        this.tlsPort   = httpsPort;
    }

    @Override
    void addRoute(String hostName, Handler handler, KeyStore keystore,
                  String? tlsKey=Null, String? cookieKey=Null) {
        addRouteImpl(hostName, handler, keystore, tlsKey);

        CryptoKey secretKey;
        findKey:
        if (cookieKey == Null) {
            for (String keyName : keystore.keyNames) {
                if (CryptoKey key := keystore.getKey(keyName), key.form == Secret) {
                    secretKey = key;
                    break findKey;
                }
            }
            throw new IllegalState("The key store is missing a cookie encryption key");
        } else {
            assert secretKey := keystore.getKey(cookieKey)
                    as $|Key is missing "{cookieKey}"
                       ;
            assert secretKey.form == Secret
                    as $|Key "{cookieKey}" must be a symmetrical secret key
                       ;
        }

        import crypto.RTAlgorithms;
        import crypto.RTEncryptionAlgorithm;

        String algName                 = secretKey.algorithm;
        (Int blockSize, Object cipher) = RTAlgorithms.getAlgorithmInfo(algName, SymmetricCipher);

        Algorithm algorithm  = new RTEncryptionAlgorithm(algName, blockSize, secretKey.size, Secret, cipher);
        Decryptor decryptor  = algorithm.allocate(secretKey).as(Decryptor);

        handler.configure(decryptor);
    }

    @Override
    void removeRoute(String hostName) {
        removeRouteImpl(hostName);
    }

    @Override
    void start() {TODO("Native");}

    @Override
    void send(RequestContext context, Int status, String[] headerNames, String[] headerValues, Byte[] body) {TODO("Native");}

    @Override
    Byte[] getClientAddressBytes(RequestContext context) {TODO("Native");}

    @Override
    String? getClientHostName(RequestContext context) {TODO("Native");}

    @Override
    UInt16 getClientPort(RequestContext context) {TODO("Native");}

    @Override
    Byte[] getServerAddressBytes(RequestContext context) {TODO("Native");}

    @Override
    UInt16 getServerPort(RequestContext context) {TODO("Native");}

    @Override
    String getMethodString(RequestContext context) {TODO("Native");}

    @Override
    String getUriString(RequestContext context) {TODO("Native");}

    @Override
    String getProtocolString(RequestContext context) {TODO("Native");}

    @Override
    String[] getHeaderNames(RequestContext context) {TODO("Native");}

    @Override
    conditional String[] getHeaderValuesForName(RequestContext context, String name) {TODO("Native");}

    @Override
    conditional Byte[] getBodyBytes(RequestContext context) {TODO("Native");}

    @Override
    conditional RequestContext[] containsNestedBodies(RequestContext context) {TODO("Native");}

    @Override
    void close(Exception? cause = Null) {TODO("Native");}

    @Override
    String toString() {
        return "HttpServer";
    }

    /**
     * Native implementation of "configure" that runs on the service context.
     */
    private void configureImpl(String bindAddr, UInt16 httpPort, UInt16 httpsPort)
        {TODO("Native");}

    /**
     * Native implementation of "addRoute" that runs on the service context.
     */
    private void addRouteImpl(String hostName, Handler handler, KeyStore keystore, String? tlsKey)
        {TODO("Native");}

    /**
     * Native implementation of "removeRoute" that runs on the service context.
     */
    private void removeRouteImpl(String hostName) {TODO("Native");}

    /**
     * Injectable server. This API must be equivalent (duck-type) to [xenia.HttpServer] interface.
     */
    static interface HttpServer
            extends Closeable {
        @RO String bindAddr;
        @RO UInt16 plainPort;
        @RO UInt16 tlsPort;

        void configure(String bindAddr, UInt16 httpPort=80, UInt16 httpsPort=443);

        void addRoute(String hostName, Handler handler, KeyStore keystore,
                      String? tlsKey=Null, String? cookieKey=Null);

        void removeRoute(String hostName);

        void start();

        void send(RequestContext context, Int status, String[] headerNames, String[] headerValues, Byte[] body);

        // ----- request attributes accessors ------------------------------------------------------

        String? getClientHostName(RequestContext context);

        Byte[] getClientAddressBytes(RequestContext context);

        UInt16 getClientPort(RequestContext context);

        Byte[] getServerAddressBytes(RequestContext context);

        UInt16 getServerPort(RequestContext context);

        String getMethodString(RequestContext context);

        String getUriString(RequestContext context);

        String getProtocolString(RequestContext context);

        String[] getHeaderNames(RequestContext context);

        conditional String[] getHeaderValuesForName(RequestContext context, String name);

        conditional Byte[] getBodyBytes(RequestContext context);

        conditional RequestContext[] containsNestedBodies(RequestContext context);
    }

    /**
     * HttpRequest handler. This API must be equivalent (duck-type) to [xenia.HttpServer.Handler]
     * interface.
     */
    static interface Handler {
        void configure(Decryptor decryptor);

        void handle(RequestContext context, String uri, String method, Boolean tls);
    }
}