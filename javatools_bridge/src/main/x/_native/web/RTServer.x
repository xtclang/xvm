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

    private ListMap<HostInfo, ProxyCheck> bindingsInternal = new ListMap();

    private HashMap<HostInfo, Handler> routesInternal = new HashMap();


    // ----- network bindings ----------------------------------------------------------------------

    @Override
    void bind(HostInfo binding, ProxyCheck reverseProxy=NoTrustedProxies) {

        // at the moment we only support a single mapping
        assert bindingsInternal.empty as "Multiple bindings are not supported";

        bindingsInternal.put(binding, reverseProxy);

        String|IPAddress bindAddr = binding.host;
        if (!bindAddr.is(String)) {
            bindAddr = bindAddr.toString();
        }
        bindImpl(bindAddr, binding.httpPort, binding.httpsPort);
    }

    @Override
    Boolean unbind(HostInfo binding) {
        if (bindingsInternal.contains(binding)) {
            // at the moment we only support a single mapping
            bindingsInternal.clear();
            close();
            return True;
        }
        return False;
    }

    @Override
    Map<HostInfo, ProxyCheck> bindings.get() = bindingsInternal.duplicate().makeImmutable();


    // ----- host routes ---------------------------------------------------------------------------

    @Override
    void addRoute(HostInfo|String route, Handler handler, KeyStore keystore,
                  String? tlsKey=Null, String? cookieKey=Null) {

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
        if (!routesInternal.contains(route)) {
            UInt16 httpPort  = route.httpPort;
            UInt16 httpsPort = route.httpsPort;
            if (routesInternal.keys.any(info -> info.host.toString() == hostName &&
                                  (info.httpPort == httpPort || info.httpsPort == httpsPort))) {
                throw new IllegalArgument($"Route is not unique: {route}");
            }
        }

        cookieDecryptor:
        if (handler.is(DecryptorAware)) {
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

        routesInternal.put(route, handler);
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
        if (routesInternal.contains(route)) {
            return replaceRouteImpl(hostName, new HandlerWrapper(handler));
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

        if (routesInternal.contains(route)) {
            routesInternal.remove(route);
            removeRouteImpl(hostName);
            return True;
        }
        return False;
    }

    @Override
    Map<HostInfo, Handler> routes.get() = routesInternal.duplicate().makeImmutable();


    // ----- life cycle ----------------------------------------------------------------------------

    @Override
    void close(Exception? cause = Null) {TODO("Native");}

    @Override
    String toString() {
        return "HttpServer";
    }

    // ----- native implementations all run on the service context ---------------------------------

    private void bindImpl(String bindAddr, UInt16 httpPort, UInt16 httpsPort) {TODO("Native");}

    private void addRouteImpl(String hostName, HandlerWrapper wrapper, KeyStore keystore, String? tlsKey)
        {TODO("Native");}

    private Boolean replaceRouteImpl(String hostName, HandlerWrapper wrapper)
        {TODO("Native");}

    private void removeRouteImpl(String hostName) {TODO("Native");}

    private (Byte[], UInt16) getReceivedAtAddress(RequestContext context) {TODO("Native");}

    private (Byte[], UInt16) getReceivedFromAddress(RequestContext context) {TODO("Native");}

    private conditional (String, UInt16) getClientHost(RequestContext context) {TODO("Native");}

    private String getProtocolString(RequestContext context) {TODO("Native");}

    private String[] getHeaderNames(RequestContext context) {TODO("Native");}

    private conditional String[] getHeaderValuesForName(RequestContext context, String name)
        {TODO("Native");}

    private conditional Byte[] getBodyBytes(RequestContext context) {TODO("Native");}

    private Boolean containsNestedBodies(RequestContext context)
        {TODO("Native");}

    private void respond(RequestContext context, Int status,
                            String[] headerNames, String[] headerValues, Byte[] body)
        {TODO("Native");}


    // ----- internal classes ----------------------------------------------------------------------

    service HandlerWrapper(Handler handler) {

        /**
         * This is the method called by the native request handler. It assumes that the handler
         * never throws (which is the case with the our only implementation by xenia.HttpHandler)
         */
        void handle(RequestContext context, String uriString, String method, Boolean tls) {
            RequestInfo info = new RequestInfoImpl(context, uriString, method, tls);
            info = &info.maskAs(RequestInfo);
            handler.handle^(info);
        }
    }

    /**
     * The natural RequestInfo implementation.
     */
    class RequestInfoImpl
            implements RequestInfo {

        construct(RequestContext context, String uriString, String method, Boolean tls) {
            this.context   = context;
            this.uriString = uriString;
            this.method    = HttpMethod.of(method);
            this.tls       = tls;
        }

        RequestContext context;

        @Override
        String uriString;

        @Override
        @Lazy Uri uri.calc() = new Uri(uriString);

        @Override
        HttpMethod method;

        @Override
        SocketAddress receivedAtAddress.get() {
            (Byte[] addressBytes, UInt16 port) = getReceivedAtAddress(context);
            return (new IPAddress(addressBytes), port);
        }

        @Override
        SocketAddress receivedFromAddress.get() {
            (Byte[] addressBytes, UInt16 port) = getReceivedFromAddress(context);
            return (new IPAddress(addressBytes), port);
        }

        @Override
        @Lazy HostInfo binding.calc() {
            assert HostInfo info := bindingsInternal.keys.iterator().next();
            return info;
        }

        @Override
        @Lazy HostInfo route.calc() {
            if ((String hostName, UInt16 port) := getClientHost(context)) {
                HostInfo route;
                if (tls) {
                    assert route := routesInternal.keys.any(info ->
                            info.host.toString() == hostName && info.httpsPort == port);
                } else {
                    assert route := routesInternal.keys.any(info ->
                            info.host.toString() == hostName && info.httpPort == port);
                }
                return route;
            }
            return binding;
        }

        @Override
        Boolean tls; // TODO CP: augment by the backTrace etc

        @Override
        SocketAddress userAgentAddress.get() {
            // the user agent is the very last address in the back-trace list, even if we can't
            // trust that part of the back-trace information
            assert SocketAddress addr := backTrace.last();
            return addr;
        }

        @Override
        @Lazy SocketAddress clientAddress.calc() {
            assert ProxyCheck isTrustedProxy := bindingsInternal.get(binding);

            // start with the address that sent the request to this server, and work backwards
            // toward the user agent
            SocketAddress[] addrs = backTrace;
            SocketAddress?  last  = Null;
            for (Int i = 1, Int c = addrs.size; i < c; ++i) {
                SocketAddress addr = addrs[i];
                if (isTrustedProxy(addr[0])) {
                    last = addr;
                } else {
                    // the client is assumed to be the first address that is NOT a trusted proxy
                    return addr;
                }
            }
            return last ?: assert;
        }

        @Override
        @Lazy SocketAddress[] backTrace.calc() {
            // TODO CP: use X-Forwarded-For etc. headers
            return [receivedAtAddress, receivedFromAddress];
        }

        @Override
        String hostName.get() = route.host.toString();

        @Override
        String protocolString.get() = getProtocolString(context);

        @Override
        @Lazy Protocol protocol.calc() {
            if (Protocol protocol := Protocol.byProtocolString.get(protocolString)) {
                if (tls && !protocol.TLS) {
                    assert protocol ?= protocol.upgradeToTls;
                }
                return protocol;
            }
            assert as $"Unknown protocol: {protocolString.quoted()}";
        }

        @Override
        String? userAgent.get() {
            if (String[] values := getHeaderValuesForName(Header.UserAgent)) {
                return values[0];
            }
            return Null;
        }

        @Override
        String[] headerNames.get() = getHeaderNames(context);

        @Override
        conditional String[] getHeaderValuesForName(String name) =
                this.RTServer.getHeaderValuesForName(context, name);

        @Override
        conditional Byte[] getBodyBytes() = this.RTServer.getBodyBytes(context);

        @Override
        Boolean containsNestedBodies() = this.RTServer.containsNestedBodies(context);

        @Override
        String convertToHttps() {
            assert !tls as "already a TLS request";

            Scheme  scheme    = protocol.scheme;
            Scheme  tlsScheme = scheme.upgradeToTls? : assert as $"cannot upgrade {scheme}";
            String  hostName  = route.host.toString();
            UInt16  tlsPort   = route.httpsPort;
            Boolean showPort  = tlsPort != 443;

            return $|{tlsScheme.name}://{hostName}\
                    |{{if (showPort) {$.add(':').append(tlsPort);}}}\
                    |{uriString}
                   ;
        }

        @Override
        void respond(Int status, String[] headerNames, String[] headerValues, Byte[] body) {
            this.RTServer.respond(context, status, headerNames, headerValues, body);
        }

        @Override
        String toString() {
            return $"({uriString=}, {method.name=}, {tls=})";
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

        void addRoute(HostInfo|String route, Handler handler, KeyStore keystore,
                      String? tlsKey=Null, String? cookieKey=Null);

        Boolean replaceRoute(HostInfo|String route, Handler handler);

        Boolean removeRoute(HostInfo|String route);

        @RO Map<HostInfo, Handler> routes;
    }

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

        @RO SocketAddress userAgentAddress;

        @RO SocketAddress clientAddress;

        @RO SocketAddress[] backTrace;

        @RO String hostName;

        @RO String protocolString;

        @RO Protocol protocol;

        @RO String? userAgent;

        @RO String[] headerNames;

        conditional String[] getHeaderValuesForName(String name);

        conditional Byte[] getBodyBytes();

        Boolean containsNestedBodies();

        String convertToHttps();

        void respond(Int status, String[] headerNames, String[] headerValues, Byte[] body);
    }

    /**
     * HttpRequest handler. This API must be equivalent (duck-type) to [xenia.HttpServer.Handler]
     * service API.
     */
    static interface Handler {
        void handle(RequestInfo request);
    }

    /**
     * This interface is used to duck-type to [xenia.HttpServer.Handler] service.
     */
    static interface DecryptorAware {
        void configure(Decryptor decryptor);
    }
}