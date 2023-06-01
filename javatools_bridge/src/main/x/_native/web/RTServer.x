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
    String hostName;

    @Override
    UInt16 plainPort;

    @Override
    UInt16 tlsPort;

    /**
     * Decryptor used to encrypt/decrypt cookies.
     */
    @Unassigned
    Decryptor cookieDecryptor;

    @Override
    void configure(String hostName, KeyStore keystore,
                   UInt16 httpPort = 80, UInt16 httpsPort = 443,
                   String? tlsKey = Null, String? cookieKey  = Null) {
        configureImpl(hostName, keystore, httpPort, httpsPort, tlsKey);

        this.hostName  = hostName;
        this.plainPort = httpPort;
        this.tlsPort   = httpsPort;

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
        this.cookieDecryptor = &decryptor.maskAs(Decryptor);
    }

    @Override
    void configureService((service) svc) {
        if (svc.is(DecryptorDependent)) {
            svc.configureEncryption(cookieDecryptor);
        }
    }

    /**
     * Native implementation of "configure" that runs on the service context.
     */
    private void configureImpl(String hostName, KeyStore keystore,
                               UInt16 httpPort, UInt16 httpsPort, String? tlsKey) {TODO("Native");}

    @Override
    void start(Handler handler) {TODO("Native");}

    @Override
    void send(RequestContext context, Int status, String[] headerNames, String[] headerValues, Byte[] body) {TODO("Native");}

    @Override
    Byte[] getClientAddressBytes(RequestContext context) {TODO("Native");}

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
     * Injectable server.
     */
    static interface HttpServer
            extends Closeable {
        /**
         * The host name that was used to start the HttpServer.
         */
        @RO String hostName;

        /**
         * The server port number that is used for plain text requests.
         */
        @RO UInt16 plainPort;

        /**
         * The server port number that provides "transport layer security".
         */
        @RO UInt16 tlsPort;

        /**
         * Configure the server.
         *
         * @param hostName   the host name (e.g. "www.xqiz.it")
         * @param keystore   the KeyStore to use for tls certificates and encryption
         * @param httpPort   the port for plain text (insecure) communications
         * @param httpsPort  the port for encrypted (tls) communications
         * @param tlsKey     the name of the public/private key pair in the keystore to use for tls
         * @param cookieKey  the name of the secret key in the keystore to use for cookie encryption
         */
        void configure(String hostName, KeyStore keystore,
                       UInt16 httpPort = 80, UInt16 httpsPort = 443,
                       String? tlsKey = Null, String? cookieKey  = Null);

        /**
         * Configure a naturally implemented service.
         */
        void configureService((service) svc);

        /**
         * Start the server, routing all incoming requests to the specified handler.
         */
        void start(Handler handler);

        /**
         * Send a response.
         */
        void send(RequestContext context, Int status, String[] headerNames, String[] headerValues, Byte[] body);

        // ----- request attributes accessors ------------------------------------------------------

        /**
         * Obtain the IP address that the request was sent from.
         *
         * @param context  the context that was passed to a `Handler` for a request
         *
         * @return the bytes of the client IP address, in either v4 or v6 form
         */
        Byte[] getClientAddressBytes(RequestContext context);

        /**
         * Obtain the port number on the client that the request was sent from
         *
         * @param context  the context that was passed to a `Handler` for a request
         *
         * @return the client port number
         */
        UInt16 getClientPort(RequestContext context);

        /**
         * Obtain the IP address that the request was received on.
         *
         * @param context  the context that was passed to a `Handler` for a request
         *
         * @return the bytes of the server IP address, in either v4 or v6 form
         */
        Byte[] getServerAddressBytes(RequestContext context);

        /**
         * Obtain the port number on the server that the request was received on
         *
         * @param context  the context that was passed to a `Handler` for a request
         *
         * @return the server port number
         */
        UInt16 getServerPort(RequestContext context);

        /**
         * Obtain the HTTP method name (such as "GET" or "PUT") that is indicated by the request.
         *
         * @param context  the context that was passed to a `Handler` for a request
         *
         * @return the HTTP method name
         */
        String getMethodString(RequestContext context);

        /**
         * Obtain the HTTP URI that is indicated by the request.
         * REVIEW is this the whole URI? or just the path/query from the request line? the JavaDoc is not clear
         *
         * @param context  the context that was passed to a `Handler` for a request
         *
         * @return the HTTP URI string
         */
        String getUriString(RequestContext context);

        /**
         * Obtain the HTTP protocol name (such as "HTTP/1.1") that is indicated by the request. In HTTP
         * 1.1, this is part of the "request line", which is the first line of text in the request.
         *
         * @param context  the context that was passed to a `Handler` for a request
         *
         * @return the HTTP protocol name
         */
        String getProtocolString(RequestContext context);

        /**
         * Obtain all the header names.
         *
         * @param context  the context that was passed to a `Handler` for a request
         *
         * @return the array of all header names
         */
        String[] getHeaderNames(RequestContext context);

        /**
         * Obtain all of the values for the specified header name.
         *
         * @param context  the context that was passed to a `Handler` for a request
         * @param name     the case-insensitive header name
         *
         * @return True if there is at least one header for the specified name
         * @return (conditional) an array of one or more values associated with the specified name
         */
        conditional String[] getHeaderValuesForName(RequestContext context, String name);

        /**
         * Obtain all of the bytes in the request body.
         *
         * @param context  the context that was passed to a `Handler` for a request
         *
         * @return True if there is a body
         * @return (conditional) an array of `Byte` representing the body content
         */
        conditional Byte[] getBodyBytes(RequestContext context);

        /**
         * Determine if the body contains nested information (e.g. multi-part) with its own headers, etc.
         *
         * @param context  the context that was passed to a `Handler` for a request
         *
         * @return True if there is one or more nested bodies
         * @return (conditional) an array of `context` objects, each representing the one nested body
         */
        conditional RequestContext[] containsNestedBodies(RequestContext context);
    }

    /**
     * HttpRequest handler.
     */
    static interface Handler {
        void handle(RequestContext context, String uri, String method, Boolean tls);
    }

    /**
     * Duck-type based marker interface that identifies service that need to be injected with a
     * [Decryptor]. The name of this interface is irrelevant.
     */
    static interface DecryptorDependent {
        void configureEncryption(Decryptor cookieDecryptor);
    }
}