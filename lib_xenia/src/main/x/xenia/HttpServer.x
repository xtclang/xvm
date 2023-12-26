import crypto.Decryptor;

import web.Header;
import web.HttpMethod;
import web.Protocol;
import web.Scheme;


/**
 * An injectable server.
 */
interface HttpServer
        extends Closeable {
    /**
     * An opaque native immutable object that represents an http request.
     */
    typedef immutable Object as RequestContext;

    /**
     * HttpRequest handler.
     */
    static interface Handler {
        /**
         * Configure the handler.
         *
         * @param decryptor  a decryptor that can be used to encrypt/decrypt application specific
         *                   information (e.g. cookies)
         */
        void configure(Decryptor decryptor);

        /**
         * Handle the incoming request.
         */
        void handle(RequestContext context, String uri, String method, Boolean tls);
    }

    /**
     * The address the server is bound to.
     */
    @RO String bindAddr;

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
     * @param bindAddr   the address (name) to bind the server to
     * @param httpPort   the port for plain text (insecure) communications
     * @param httpsPort  the port for encrypted (tls) communications
     */
    void configure(String bindAddr, UInt16 httpPort=80, UInt16 httpsPort=443);

    /**
     * Add a route for this server. Each incoming request that has a "Host" header value equal to
     * the specified 'hostName' will be passed to the specified handler.
     *
     * This method can be called before and/or after [start].
     *
     * @param hostName   the host name the handler may receive requests for
     * @param handler    the request handler
     * @param keystore   the KeyStore to use for tls certificates and encryption
     * @param tlsKey     the name of the key pair in the keystore to use for Tls; if
     *                   not specified, the keystore must have one and only one
     *                   [key-pair](crypto.KeyForm.Pair) entry, which will be used for Tls encryption
     * @param cookieKey  (optional) the name of the secret key in the keystore to use for cookie
     *                   encryption; if not specified, the keystore must have one and only one
     *                   [secret](crypto.KeyForm.Secret) key, which will be used for cookies encryption
     */
    void addRoute(String hostName, Handler handler, KeyStore keystore,
                  String? tlsKey=Null, String? cookieKey=Null);

    /**
     * Remove the specified route. After this method returns, no more requests are going to be
     * passed to this route's handler. However, it's a responsibility of the caller to shutdown
     * (deal with all in-flight requests for) the corresponding handler.
     *
     * @param hostName  the host name to stop processing requests for
     */
    void removeRoute(String hostName);

    /**
     * Start the server, routing each incoming request to the handler that corresponds to the
     * request's "Host" header value.
     */
    void start();

    /**
     * Send a response.
     */
    void send(RequestContext context, Int status, String[] headerNames, String[] headerValues, Byte[] body);


    // ----- context attributes --------------------------------------------------------------------

    /**
     * Obtain the IP address that the request was sent from.
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the bytes of the client IP address, in either v4 or v6 form
     */
    Byte[] getClientAddressBytes(RequestContext context);

    /**
     * Obtain the host name that the request was sent from.
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the host name or Null if the name cannot be resolved
     */
    String? getClientHostName(RequestContext context);

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


    // ----- RequestInfo ---------------------------------------------------------------------------

    /**
     * An object that provides access to information about a request.
     */
    static const RequestInfo(HttpServer server, RequestContext context, Boolean tls) {
        /**
         * Obtain the IP address that the request was sent from.
         *
         * @return the client IP address, in either v4 or v6 form
         */
        IPAddress getClientAddress() {
            return new IPAddress(server.getClientAddressBytes(context));
        }

        /**
         * Obtain the host name that the request was sent from.
         *
         * @return the host name or Null if the name cannot be resolved
         */
        String? getClientHostName() {
          return server.getClientHostName(context);
        }

        /**
         * Obtain the port number on the client that the request was sent from.
         *
         * @return the client port number
         */
        UInt16 getClientPort() {
            return server.getClientPort(context);
        }

        /**
         * Obtain the IP address that the request was received on.
         *
         * @return the server IP address, in either v4 or v6 form
         */
        IPAddress getServerAddress() {
            return new IPAddress(server.getServerAddressBytes(context));
        }

        /**
         * Obtain the port number on the server that the request was received on.
         *
         * @return the server port number
         */
        UInt16 getServerPort() {
            return server.getServerPort(context);
        }

        /**
         * Obtain the HTTP method name (such as "GET" or "PUT") that is indicated by the request.
         *
         * @return the HTTP method name
         */
        HttpMethod getMethod() {
            return HttpMethod.of(server.getMethodString(context));
        }

        /**
         * Obtain the HTTP URI that is indicated by the request.
         *
         * @return the URI from the request
         */
        Uri getUri() {
            return new Uri(server.getUriString(context));
        }

        /**
         * Obtain the HTTP protocol name (such as "HTTP/1.1") that is indicated by the request. In HTTP
         * 1.1, this is part of the "request line", which is the first line of text in the request.
         *
         * @return the HTTP protocol name
         */
        String getProtocolString() {
            return server.getProtocolString(context);
        }

        /**
         * Obtain the HTTP protocol that is indicated by the request.
         *
         * @return the HTTP protocol
         */
        Protocol getProtocol() {
            if (Protocol protocol := Protocol.byProtocolString.get(getProtocolString())) {
                if (tls && !protocol.TLS) {
                    assert protocol ?= protocol.upgradeToTls;
                }
                return protocol;
            }
            assert as $"Unknown protocol: {getProtocolString().quoted()}";
        }

        /**
         * Obtain all the header names.
         *
         * @return the array of all header names
         */
        String[] getHeaderNames() {
            return server.getHeaderNames(context);
        }

        /**
         * Obtain all of the values for the specified header name.
         *
         * @param name  the case-insensitive header name
         *
         * @return True if there is at least one header for the specified name
         * @return (conditional) an array of one or more values associated with the specified name
         */
        conditional String[] getHeaderValuesForName(String name) {
            return server.getHeaderValuesForName(context, name);
        }

        /**
         * Obtain all of the bytes in the request body.
         *
         * @return True if there is a body
         * @return (conditional) an array of `Byte` representing the body content
         */
        conditional Byte[] getBodyBytes() {
            return server.getBodyBytes(context);
        }

        /**
         * Determine if the body contains nested information (e.g. multi-part) with its own headers,
         * etc.
         *
         * @return True if there is one or more nested bodies
         * @return (conditional) an array of `context` objects, each representing the one nested
         *         body
         */
        conditional RequestInfo[] containsNestedBodies() {
            if (RequestContext[] contexts := server.containsNestedBodies(context)) {
                return True, new RequestInfo[contexts.size](ctx -> new RequestInfo(server, ctx, tls));
            }

            return False;
        }

        /**
         * Obtain the user agent string.
         */
        String getUserAgent() {
            if (String[] values := getHeaderValuesForName(Header.UserAgent)) {
                return values[0];
            }
            return "";
        }

        /**
         * Obtain the URL that converts this request to the corresponding TLS request.
         *
         * @return the URL string representing the TLS request
         */
        String convertToTlsUrl() {
            assert !tls as "already a TLS request";

            Scheme scheme    = getProtocol().scheme;
            Scheme tlsScheme = scheme.upgradeToTls? : assert as $"cannot upgrade {scheme}";
            UInt16 tlsPort   = server.tlsPort;
            String hostName  = server.getClientHostName(context)? : assert as $"cannot upgrade {scheme}";

            // if the client request comes from the standard http port 80, and the server http port
            // is not standard, it's an indication that a reverse proxy or the Network Address
            // Translation (NAT) is in play (e.g. using "pfctl" on Mac OS), in which case we should
            // not add the server port the redirecting Url
            Boolean showPort = getClientPort() != 80 && tlsPort != 443;

            return $|{tlsScheme.name}://{hostName}\
                    |{{if (showPort) {$.add(':').append(tlsPort);}}}\
                    |{server.getUriString(context)}
                    ;
        }
    }
}