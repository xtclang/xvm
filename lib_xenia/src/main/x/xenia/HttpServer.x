import net.Host;
import net.HostPort;
import net.SocketAddress;

import web.Header;
import web.HttpMethod;
import web.Protocol;
import web.Scheme;

import web.http.HostInfo;

/**
 * An injectable HTTP/HTTPS server.
 */
interface HttpServer
        extends Closeable {

    // ----- network bindings ----------------------------------------------------------------------

    /**
     * A `ProxyCheck` is a function that identifies which addresses are trusted reverse proxies. If
     * an HTTP server is only reachable via secure reverse proxies, then each of those proxies is
     * considered to be a "trusted reverse proxy"; if an HTTP server is reachable in any other way,
     * then it is considered to have no "trusted reverse proxies", because the requests transmitted
     * to the HTTP server may have been forged.
     */
    typedef function Boolean(IPAddress) as ProxyCheck;

    /**
     * A simple implementation of a ProxyCheck that does not recognize any trusted proxies.
     */
    static ProxyCheck NoTrustedProxies = _ -> False;

    /**
     * Bind the server to the specified address and ports to listen for HTTP requests on. When a
     * request arrives via a binding, an attempt will be made to route that request to a specific
     * handler using the "host route" information.
     *
     * @param binding       the HostInfo for server binding
     * @param reverseProxy  (optional) a function that identifies which addresses are trusted
     *                      proxies, but only if the server cannot be reached by a client without
     *                      going through these reverse proxies; the default is that there are no
     *                      trusted reverse proxies (or that a client can reach the server directly)
     */
    void bind(HostInfo binding, ProxyCheck reverseProxy=NoTrustedProxies);

    /**
     * Unbind the server from the specified address and ports.
     *
     * @return `True` iff the server successfully unbound the specified address and ports (or if it
     *         was not bound); details of any failure would likely be located in a server log
     */
    Boolean unbind(HostInfo binding);

    /**
     * The information about the server's address/port bindings and the related reverse proxy
     * server validators.
     */
    @RO Map<HostInfo, ProxyCheck> bindings;


    // ----- host routes ---------------------------------------------------------------------------

    /**
     * An HTTP request handler that the HttpServer can deliver requests to.
     */
    static interface Handler {
        /**
         * Handle a received HTTP request.
         *
         * @param request  the [RequestInfo] that exposes all available HTTP request information
         */
        void handle(RequestInfo request);
    }

    /**
     * Add a host route for this server. Each incoming request that has a "Host" header value equal
     * to the specified 'hostName' and a matching port number will be passed to the specified
     * handler.
     *
     * @param route      the HostInfo for request routing that indicates which requests should be
     *                   delivered to the specified handler
     * @param handler    the request handler to route to
     * @param keystore   the KeyStore to use for TLS certificates and encryption
     * @param tlsKey     (optional) the name of the key pair in the keystore to use for TLS; if not
     *                   specified, the keystore must have one and only one
     *                   [key-pair](crypto.KeyForm.Pair) entry, which will be used for TLS
     * @param cookieKey  (optional) the name of the secret key in the keystore to use for session
     *                   cookie encryption; if not specified, and the keystore contains one and only
     *                   one [secret](crypto.KeyForm.Secret) key, then that key will be used to
     *                   encrypt session cookies
     *
     * @return the `HostInfo` object created to represent the new route
     */
     void addRoute(HostInfo route, Handler handler, KeyStore keystore,
                   String? tlsKey=Null, String? cookieKey=Null);

    /**
     * Update an existing route such that it will now route to the specified `Handler`. The purpose
     * of this method is to (as much as possible) atomically update a route, instead of removing and
     * adding it, such that minimal interruption (if any) to service will occur.
     *
     * @param handler  the mew request `Handler` to associate with the existing host information
     *
     * @return `True` iff the server had an existing route for the host and successfully updated the
     *         route to use the specified `Handler`; details of any failures would likely be located
     *         in a server log
     */
    Boolean replaceRoute(HostInfo route, Handler handler);

    /**
     * Remove the specified route. After this method returns, no more requests are going to be
     * passed to this route's handler. However, it's a responsibility of the caller to shutdown
     * (deal with all in-flight requests for) the corresponding handler.
     *
     * @param route  the route information that was returned from the previous call to [addRoute]
     *
     * @return `True` iff the server successfully unregistered the route for the specified host (or
     *         if that host was not registered); details of any failures would likely be located in
     *         a server log
     */
    Boolean removeRoute(HostInfo route);

    /**
     * The information about the hosts that the server is currently routing for, and the `Handler`
     * for each host that requests will be routed to. A host is typically a domain name, like
     * `example.com`, or `api.example.com`.
     *
     * This design allows an `HttpServer` to handle HTTP requests for several different sub-domains,
     * or even different domains.
     *
     * As a precaution, the keystore information that was provided to the [addRoute] method is not
     * surfaced here.
     */
    @RO Map<HostInfo, Handler> routes;


    // ----- request handling ----------------------------------------------------------------------

    /**
     * An object that provides access to low-level information about a request.
     */
    static interface RequestInfo {
        /**
         * The request's original URI string, before it was converted to a URI.
         */
        @RO String uriString;

        /**
         * The request's URI.
         */
        @RO Uri uri;

        /**
         * The request's HTTP method name.
         */
        @RO HttpMethod method;

        /**
         * Obtain the server IP address and port that the request was received on.
         *
         * @return this server IP address and port
         */
        @RO SocketAddress receivedAtAddress;

        /**
         * Obtain the IP address and port that the request was received from, which _may_ be a
         * reverse proxy or load balancer.
         *
         * @return the immediately preceding sender's IP address and port
         */
        @RO SocketAddress receivedFromAddress;

        /**
         * The [HostInfo] that represents the network binding on which the request was received.
         */
        @RO HostInfo binding;

        /**
         * The [HostInfo] that represents the host information used to select the request [Handler].
         */
        @RO HostInfo route;

        /**
         * True indicates that the communication from the claimed client to either a trusted proxy
         * or this HTTP server was conducted using Transport Layer Security (TLS). Note that there
         * is no way to verify that the network hops that occurred before reaching the trusted proxy
         * or this HTTP server were TLS hops, because that information can itself be forged; in
         * other words, the only trustworthy information is that the communication from the
         * [clientAddress] machine to the trusted proxy or this HTTP server was TLS, and that there
         * was no record (in the request header) of hops preceding that using a non-TLS protocol.
         */
        @RO Boolean tls;

        /**
         * Obtain the IP address and port that the request claims that it was sent from. This
         * address must not be trusted, because it uses headers of the request itself, and any such
         * headers can be forged by the request originator or any untrusted proxy that the request
         * is routed through. This information is useful, though, to differentiate among multiple
         * clients behind a shared client proxy, even though that differentiation must not be
         * considered to be trustworthy information.
         *
         * @return the IP address and port that the request claims that the user agent resides at
         */
        @RO SocketAddress userAgentAddress;

        /**
         * Obtain the IP address and port that the request was received from. This address can be
         * trusted, because it uses information from the HTTP server, or from trusted proxy servers
         * that route to the HTTP server. To obtain the untrusted client address information, use
         * the [userAgentAddress] property, which _should_ differ from this property's value iff
         * the request was transmitted through any untrusted proxies.
         *
         * @return the client IP address and port
         */
        @RO SocketAddress clientAddress;

        /**
         * The list of [SocketAddress] objects -- in reverse order -- representing how the request
         * arrived at this server. This is a sequence of `SocketAddress` objects representing each
         * HTTP/HTTPS hop, starting with this server, followed by any reverse proxy servers, then
         * any client proxies, and finally the _claimed_ originating client (the "user agent").
         */
        @RO SocketAddress[] backTrace;

        /**
         * The host name. This is the name (or address) that the user agent used to submit the
         * original request to.
         */
        @RO String hostName;

        /**
         * The HTTP protocol name (such as "HTTP/1.1") that is indicated by the request. In HTTP
         * 1.1, this is part of the "request line", which is the first line of text in the request.
         */
        @RO String protocolString;

        /**
         * The HTTP protocol that is indicated by the request.
         */
        @RO Protocol protocol;

        /**
         * The user agent string, if any.
         */
        @RO String? userAgent;

        /**
         * The message header names, as an array of String. The names in the array occur in the
         * order that they first occur in the received message; if the same name occurs multiple
         * times in the received message, then it will only occur once in the array. Names are
         * normalized, and treated case insensitively.
         */
        @RO String[] headerNames;

        /**
         * Obtain all of the values for the specified header name.
         *
         * @param name  the case-insensitive header name
         *
         * @return True if there is at least one header for the specified name
         * @return (conditional) an array of one or more values associated with the specified name
         */
        conditional String[] getHeaderValuesForName(String name);

        /**
         * Obtain all of the bytes in the request body.
         *
         * @return True if there is a body
         * @return (conditional) an array of `Byte` representing the body content
         */
        conditional Byte[] getBodyBytes();

        /**
         * Determine if the body contains nested information (e.g. multi-part) with its own headers,
         * etc.
         *
         * @return True if there is one or more nested bodies
         * @return (conditional) an array of `context` objects, each representing the one nested
         *         body
         */
        // conditional RequestInfo[] TODO CP: need a non-recursive API
        Boolean containsNestedBodies();

        /**
         * Build a URL that converts this request to a corresponding HTTPS request.
         *
         * @return a string holding a TLS-enabled request URL equivalent of this request
         */
        String convertToHttps() {
            assert !tls as "already a TLS request";

            Scheme   scheme    = protocol.scheme;
            Scheme   tlsScheme = scheme.upgradeToTls? : assert as $"cannot upgrade {scheme}";
            String   hostName  = route.host.toString();
            UInt16   tlsPort   = route.httpsPort;

            // TODO GG REVIEW
            // if the client request comes from the standard http port 80, and the server http port
            // is not standard, it's an indication that a reverse proxy or the Network Address
            // Translation (NAT) is in play (e.g. using "pfctl" on Mac OS), in which case we should
            // not add the server port the redirecting Url
            // Boolean showPort = getClientPort() != 80 && tlsPort != 443;
            Boolean showPort = tlsPort != 443;

            return $|{tlsScheme.name}://{hostName}\
                    |{{if (showPort) {$.add(':').append(tlsPort);}}}\
                    |{uriString}
                   ;
        }

        /**
         * Instruct the server to send a response to a previously received request.
         *
         * @param status        the response `Status-Code`, as defined by rfc2616
         * @param headerNames   an array of `field-name` strings, each as defined by rfc2616
         * @param headerValues  an array of `field-value` corresponding to `headerNames`
         * @param body          the `message-body` (if any), as defined by rfc2616
         *
         * @throws Exception  if a response cannot be sent for any reason, this call _may_ raise an
         *                    exception; it is important to understand that the lack of an exception
         *                    does **not** indicate a successful response to a client
         */
        void respond(Int status, String[] headerNames, String[] headerValues, Byte[] body);
    }
}