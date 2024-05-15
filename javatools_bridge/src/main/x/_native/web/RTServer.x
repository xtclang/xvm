import libcrypto.Algorithm;
import libcrypto.CryptoKey;
import libcrypto.Decryptor;
import libcrypto.KeyStore;

import libnet.IPAddress;
import libnet.Host;
import libnet.SocketAddress;
import libnet.Uri;

import libweb.Header;
import libweb.HttpMethod;
import libweb.Protocol;
import libweb.Scheme;

import libweb.http.HostInfo;

/**
 * The native HttpServer service implementation.
 */
@Concurrent
service RTServer
        implements HttpServer {

    typedef immutable Object as RequestContext;

    @Override
    String toString() = "HttpServer";  // purposefully vague; do NOT leak any info!


    // ----- network bindings ----------------------------------------------------------------------

    @Override
    void bind(HostInfo binding, ProxyCheck reverseProxy=NoTrustedProxies) {
        // at the moment we only support a single mapping
        assert bindings.empty as "Multiple bindings are not supported";

        String|IPAddress bindAddr = binding.host;
        if (!bindAddr.is(String)) {
            bindAddr = bindAddr.toString();
        }

        bindImpl(binding, bindAddr, binding.httpPort, binding.httpsPort);
        bindings = bindings.put(binding, reverseProxy);
        assert bindings.is(immutable);
    }

    @Override
    Boolean unbind(HostInfo binding) {
        if (bindings.contains(binding)) {
            bindings = []; // at the moment we only support a single mapping
            close();
            return True;
        }
        return False;
    }

    @Override
    public/private Map<HostInfo, ProxyCheck> bindings = [];


    // ----- host routes ---------------------------------------------------------------------------

    /**
     * HttpRequest handler. This API must be equivalent (duck-type) to [xenia.HttpServer.Handler]
     * service API.
     */
    static interface Handler {
        void handle(RequestInfo request);
    }

    @Override
    void addRoute(HostInfo|String route, Handler handler, KeyStore? keystore = Null,
                  String? tlsKey = Null, String? cookieKey = Null) {

        import crypto.RTAlgorithms;
        import crypto.RTEncryptionAlgorithm;

        String hostName;
        if (route.is(String)) {
            hostName = route;
            route    = new HostInfo(route);
        } else {
            hostName = route.host.toString();
        }

        // we should be able to replace an exiting route, but must not add any ambiguous ones
        if (!routes.contains(route)) {
            UInt16 httpPort  = route.httpPort;
            UInt16 httpsPort = route.httpsPort;
            if (routes.keys.any(info -> info.host.toString() == hostName &&
                                       (info.httpPort == httpPort || info.httpsPort == httpsPort))) {
                throw new IllegalArgument($"Route is not unique: {route}");
            }
        }

        cookieDecryptor:
        if (keystore != Null && handler.is(DecryptorAware)) {
            CryptoKey secretKey;
            findKey:
            if (cookieKey == Null) {
                for (String keyName : keystore.keyNames) {
                    if (CryptoKey key := keystore.getKey(keyName), key.form == Secret) {
                        secretKey = key;
                        break findKey;
                    }
                }
                // no key - no cookie encryption
                break cookieDecryptor;
            } else {
                assert secretKey := keystore.getKey(cookieKey)
                        as $|Key is missing "{cookieKey}"
                           ;
                assert secretKey.form == Secret
                        as $|Key "{cookieKey}" must be a symmetrical secret key
                           ;
            }

            String algName                 = secretKey.algorithm;
            (Int blockSize, Object cipher) = RTAlgorithms.getAlgorithmInfo(algName, SymmetricCipher);

            Algorithm algorithm  = new RTEncryptionAlgorithm(algName, blockSize, secretKey.size, Secret, cipher);
            Decryptor decryptor  = algorithm.allocate(secretKey).as(Decryptor);

            handler.configure(decryptor);
        }

        addRouteImpl(hostName, new HandlerWrapper(handler), keystore, tlsKey);

        routes = routes.put(route, handler);
        assert routes.is(immutable);
    }

    @Override
    Boolean replaceRoute(HostInfo|String route, Handler handler) {
        String hostName;
        if (route.is(String)) {
            hostName = route;
            route    = new HostInfo(route);
        } else {
            hostName = route.host.toString();
        }
        if (routes.contains(route)) {
            if (replaceRouteImpl(hostName, new HandlerWrapper(handler))) {
                routes = routes.put(route, handler);
                assert routes.is(immutable);
                return True;
            }
        }
        return False;
    }

    @Override
    Boolean removeRoute(HostInfo|String route) {
        String hostName;
        if (route.is(String)) {
            hostName = route;
            route    = new HostInfo(route);
        } else {
            hostName = route.host.toString();
        }

        if (routes.contains(route)) {
            routes = routes.remove(route);
            removeRouteImpl(hostName);
            return True;
        }
        return False;
    }

    @Override
    public/private Map<HostInfo, Handler> routes = [];


    // ----- request handling ----------------------------------------------------------------------

    /**
     * An object that provides access to low-level information about a request.
     * This API must be equivalent (duck-type) to [xenia.HttpServer.RequestInfo] interface.
     */
    static interface RequestInfo {
        @RO String uriString;
        @RO Uri uri;
        @RO HttpMethod method;
        @RO SocketAddress receivedAtAddress;
        @RO SocketAddress receivedFromAddress;
        @RO HostInfo binding;
        @RO HostInfo route;
        @RO Boolean tls;
        @RO IPAddress userAgentAddress;
        @RO IPAddress clientAddress;
        @RO IPAddress[] routeTrace;
        @RO String hostName;
        @RO String protocolString;
        @RO Protocol protocol;
        @RO Uri httpsUrl;
        @RO String? userAgent;
        @RO String[] headerNames;
        conditional String[] getHeaderValuesForName(String name);
        conditional Byte[] getBodyBytes();
        Boolean containsNestedBodies();
        void respond(Int status, String[] headerNames, String[] headerValues, Byte[] body);
    }


    // ----- native implementations all run on the service context ---------------------------------

    private void bindImpl(HostInfo binding, String bindAddr, UInt16 httpPort, UInt16 httpsPort)                {TODO("Native");}
    private void addRouteImpl(String hostName, HandlerWrapper wrapper, KeyStore? keystore, String? tlsKey)     {TODO("Native");}
    private Boolean replaceRouteImpl(String hostName, HandlerWrapper wrapper)                                  {TODO("Native");}
    private void removeRouteImpl(String hostName)                                                              {TODO("Native");}
    (Byte[], UInt16) getReceivedAtAddress(RequestContext context)                                              {TODO("Native");}
    (Byte[], UInt16) getReceivedFromAddress(RequestContext context)                                            {TODO("Native");}
    conditional (String, UInt16) getHostInfo(RequestContext context)                                           {TODO("Native");}
    String getProtocolString(RequestContext context)                                                           {TODO("Native");}
    String[] getHeaderNames(RequestContext context)                                                            {TODO("Native");}
    conditional String[] getHeaderValuesForName(RequestContext context, String name)                           {TODO("Native");}
    conditional Byte[] getBodyBytes(RequestContext context)                                                    {TODO("Native");}
    Boolean containsNestedBodies(RequestContext context)                                                       {TODO("Native");}
    void respond(RequestContext context, Int status, String[] headerNames, String[] headerValues, Byte[] body) {TODO("Native");}
    @Override void close(Exception? cause = Null)                                                              {TODO("Native");}


    // ----- internal classes ----------------------------------------------------------------------

    service HandlerWrapper(Handler handler) {

        /**
         * This is the method called by the native request handler. It assumes that the handler
         * never throws -- which is the case in the only implementation today: xenia.HttpHandler.
         *
         * @param binding    the HTTP server binding that received the current request
         * @param context    the opaque RequestContext representing the current request
         * @param uriString  the requested URI
         * @param method     the HTTP method string, e.g. "GET"
         * @param tls        if the message was received (the last hop) over a TLS connection
         */
        void handle(HostInfo binding, RequestContext context, String uriString, String method, Boolean tls) {
            RequestInfo info   = new RequestInfoImpl(this.RTServer,
                    binding, bindings.get(binding) ?: HttpServer.NoTrustedProxies,
                    context, uriString, HttpMethod.of(method), tls);
            handler.handle^(&info.maskAs(RequestInfo));
        }
    }


    // ----- interfaces ----------------------------------------------------------------------------

    /**
     * Injectable server. This API must be equivalent (duck-type) to [xenia.HttpServer] interface.
     */
    static interface HttpServer
            extends Closeable {

        typedef function Boolean(IPAddress) as ProxyCheck;

        static ProxyCheck NoTrustedProxies = _ -> False;

        void bind(HostInfo binding, ProxyCheck reverseProxy=NoTrustedProxies);
        Boolean unbind(HostInfo binding);
        @RO Map<HostInfo, ProxyCheck> bindings;

        void addRoute(HostInfo|String route, Handler handler, KeyStore? keystore = Null,
                      String? tlsKey = Null, String? cookieKey = Null);
        Boolean replaceRoute(HostInfo|String route, Handler handler);
        Boolean removeRoute(HostInfo|String route);
        @RO Map<HostInfo, Handler> routes;
    }

    /**
     * This interface is used to duck-type to [xenia.HttpServer.Handler] service.
     */
    static interface DecryptorAware {
        void configure(Decryptor decryptor);
    }
}