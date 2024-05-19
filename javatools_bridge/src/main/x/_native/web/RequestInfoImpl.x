import libnet.Host;
import libnet.IPAddress;
import libnet.SocketAddress;
import libnet.Uri;

import libweb.Header;
import libweb.HttpMethod;
import libweb.Protocol;
import libweb.Scheme;

import libweb.http.HostInfo;

import RTServer.HttpServer.ProxyCheck;
import RTServer.RequestContext;
import RTServer.RequestInfo;

import ecstasy.collections.CaseInsensitive;

/**
 * The natural RequestInfo implementation.
 *
 * @param server          the HTTP server
 * @param binding         the binding on which current request was received
 * @param isTrustedProxy  the function to test if an IP address is a trusted proxy
 * @param context         the opaque RequestContext representing the current request
 * @param uriString       the requested URI
 * @param method          the HTTP method string, e.g. "GET"
 * @param lastHopTls      if the message was received (the last hop) over a TLS connection
 */
service RequestInfoImpl(RTServer       server,
                        HostInfo       binding,
                        ProxyCheck     isTrustedProxy,
                        RequestContext context,
                        String         uriString,
                        HttpMethod     method,
                        Boolean        lastHopTls)
        implements RequestInfo {

    private /* TODO GG static */ function Boolean(String, String) eqInsens = CaseInsensitive.areEqual;

    @Override
    @Lazy Uri uri.calc() = new Uri(uriString);

    @Override
    @Lazy SocketAddress receivedAtAddress.calc() {
        (Byte[] addressBytes, UInt16 port) = server.getReceivedAtAddress(context);
        return (new IPAddress(addressBytes), port);
    }

    @Override
    @Lazy SocketAddress receivedFromAddress.calc() {
        (Byte[] addressBytes, UInt16 port) = server.getReceivedFromAddress(context);
        return (new IPAddress(addressBytes), port);
    }

    @Override
    @Lazy HostInfo route.calc() {
        if ((String hostName, UInt16 port) := server.getHostInfo(context)) {
            assert HostInfo route := server.routes.keys.any(info ->
                    info.host.toString() == hostName && (info.httpPort == port || info.httpsPort == port));
            return route;
        }
        return binding;
    }

    /**
     * Configured by routeTrace.calc()
     */
    private Boolean[] hopsTls.get() {
        Boolean[] result = super();
        if (result.empty) {
            // the result is calculated as a side-effect of the routeTrace calculation
            IPAddress[] force = routeTrace;
            result = super();
            assert !result.empty;
        }
        return result;
    } = [];

    @Override
    @Lazy Boolean tls.calc() {
        Boolean[] hopsTls = this.hopsTls;
        Boolean   tls     = hopsTls[0];
        Int       hops    = hopsTls.size;
        if (tls && hops > 1) {
            IPAddress[] addrs = routeTrace;
            for (Int hop : 1 ..< hops) {
                Trusted: if (isTrustedProxy(addrs[hop])) {
                    // the rest of the hops all need to be verified as trusted proxies; otherwise,
                    // it would be possible to forge a header with a fake forward record by citing a
                    // trusted proxy address, which could then produce a false positive for TLS
                    for (Int revProxy : hop >..< hops) {
                        if (!isTrustedProxy(addrs[revProxy])) {
                            break Trusted;
                        }
                    }
                    return tls;
                }
                if (!hopsTls[hop]) {
                    return False;
                }
            }
        }
        return tls;
    }

    @Override
    IPAddress userAgentAddress.get() {
        // the user agent is the very first address in the route trace list, even if we can't
        // trust that part of the route-trace information
        return routeTrace[0];
    }

    @Override
    @Lazy IPAddress clientAddress.calc() {
        IPAddress[] addrs = routeTrace;
        if (addrs.size > 1) {
            // start with the address that sent the request to this server, and work backwards
            // toward the user agent, and return the first address that is NOT a trusted proxy
            for (Int i : addrs.size >.. 1) {
                IPAddress addr = addrs[i];
                if (!isTrustedProxy(addr)) {
                    return addr;
                }
            }
        }
        return addrs[0];
    }

    @Override
    @Lazy IPAddress[] routeTrace.calc() {
        // in simple scenarios, we have the browser (or other user agent) and the server; this is
        // very easy to build a route for, because the entire route is the [receivedFromAddress],
        // and the tls for the route is the [lastHopTls]
        String[] fwds  = getHeaderValuesForName("Forwarded")       ?: [];
        String[] xfwds = getHeaderValuesForName("X-Forwarded-For") ?: [];
        if (fwds.empty && xfwds.empty) {
            hopsTls = lastHopTls ? [True] : [False];
            return [receivedFromAddress[0]];
        }

        (IPAddress[] fwdAddrs, Boolean[] fwdTls) = fwds.empty  ? parseXForwardedHeaders(xfwds)
                                                 : xfwds.empty ? parseForwardedHeaders(fwds)
                                                 : mergeForwardedHeaders(fwds, xfwds);
        hopsTls = fwdTls + lastHopTls;
        return (fwdAddrs + receivedFromAddress[0]).freeze(True);
    }

    /**
     * Parse the "Forwarded" header to build address-route and per-hop-TLS arrays.
     *
     * @param fwdHeaders  the header values for "Forwarded" already obtained from the request
     *
     * @return an array of IP addresses that the request was forwarded on behalf of (i.e. this does
     *         not include the last proxy)
     * @return an array of TLS indicators corresponding to the hops represented by the first array
     */
    private (IPAddress[], Boolean[]) parseForwardedHeaders(String[] fwdHeaders) {
        // the "Forwarded" header(s) may contain "for" information which specifies sender addresses
        // (or obfuscating names that we cannot use), as well as "proto" TLS information (which may
        // or may not be present)
        IPAddress[] addrList = new IPAddress[];
        Boolean[]   tlsList  = new Boolean[];
        for (String header : fwdHeaders) {
            for (String forward : header.split(',', omitEmpty=True, trim=True)) {
                Map<String, String> kvs = forward.splitMap(entrySeparator=';');
                if (String addr := kvs.get("for"), Byte[] bytes := IPAddress.parse(addr)) {
                    addrList += new IPAddress(bytes);
                    tlsList  += eqInsens(kvs.get("proto") ?: "", "https");
                }
            }
        }
        return addrList, tlsList;
    }

    /**
     * Parse the "X-Forwarded-For", "X-Forwarded-Proto", "Front-End-Https", "X-Forwarded-Protocol",
     * "X-Forwarded-Ssl", and "X-Url-Scheme" headers to build address-route and per-hop-TLS arrays.
     *
     * @param xfwdHeaders  the header values for "X-Forwarded-For" already obtained from the request
     *
     * @return an array of IP addresses that the request was forwarded on behalf of (i.e. this does
     *         not include the last proxy)
     * @return an array of TLS indicators corresponding to the hops represented by the first array
     */
    private (IPAddress[], Boolean[]) parseXForwardedHeaders(String[] xfwdHeaders) {
        // the "X-Forwarded-For" header(s) contain IP addresses, and can be augmented with TLS
        // information from any combination of these non-standard headers:
        //     X-Forwarded-Proto: https
        //     Front-End-Https: on
        //     X-Forwarded-Protocol: https
        //     X-Forwarded-Ssl: on
        //     X-Url-Scheme: https
        // the challenge is how to infer TLS information when there are more than one forwards in
        // the "X-Forwarded-For" list, but not the same number of values in the various
        // forwarded-TLS-indicating headers
        assert !xfwdHeaders.empty;

        IPAddress[] addrList = new IPAddress[];
        Int         total    = 0;               // total present, NOT the list size
        for (String header : xfwdHeaders) {
            Int offset = 0;
            if (Int endOffset := header.indexOf(',')) {
                while (True) {
                    ++total;
                    if (Byte[] bytes := IPAddress.parse(header[offset ..< endOffset].trim())) {
                        addrList += new IPAddress(bytes);
                    }

                    if (endOffset >= header.size) {
                        break;
                    }

                    offset    = endOffset + 1;
                    endOffset = header.indexOf(',', offset) ?: header.size;
                }
            } else {
                ++total;
                if (Byte[] bytes := IPAddress.parse(header.trim())) {
                    addrList += new IPAddress(bytes);
                }
            }
        }

        // get out if there is no parseable addresses
        if (addrList.empty) {
            return [], [];
        }

        if (total == 1 && total == addrList.size) {
            // simple case is one proxy because we're only looking for one corresponding TLS value,
            // so we can use whichever one we stumble upon first
            String[] tlsHeaders = getHeaderValuesForName("X-Forwarded-Proto")
                               ?: getHeaderValuesForName("X-Forwarded-Protocol")
                               ?: getHeaderValuesForName("X-Url-Scheme")
                               ?: [];
            if (!tlsHeaders.empty) {
                return addrList, [tlsHeaders.size == 1 && eqInsens(tlsHeaders[0].trim(), "https")];
            }
            tlsHeaders = getHeaderValuesForName("X-Forwarded-Ssl")
                      ?: getHeaderValuesForName("Front-End-Https")
                      ?: [];
            return addrList, [tlsHeaders.size == 1 && eqInsens(tlsHeaders[0].trim(), "on")];
        }

        // otherwise, we need at least one of the headers to be a full record matching the total
        // number of hops
        Boolean[] h1 = parseBooleanList("X-Forwarded-Proto"   , "https");
        Boolean[] h2 = parseBooleanList("X-Forwarded-Protocol", "https");
        Boolean[] h3 = parseBooleanList("X-Url-Scheme"        , "https");
        Boolean[] h4 = parseBooleanList("X-Forwarded-Ssl"     , "on");
        Boolean[] h5 = parseBooleanList("Front-End-Https"     , "on");

        Boolean[] tlsList = h1;
        Int       minLen  = h1.size;
        (tlsList, minLen) = takeBest(tlsList, h2, minLen);
        (tlsList, minLen) = takeBest(tlsList, h3, minLen);
        (tlsList, minLen) = takeBest(tlsList, h4, minLen);
        (tlsList, minLen) = takeBest(tlsList, h5, minLen);

        if (tlsList.size == addrList.size == total) {
            return addrList, tlsList;
        }

        if (tlsList.empty                   // no data, so assume no HTTPS
                || tlsList.size > total) {  // illegal, so assume tampering (i.e. assume no HTTPS)
            return addrList, new Boolean[addrList.size];
        }

        // if the HTTPS indicators were all true, and matched the total hops length, then it is safe
        // to assume that all hops that we are reporting were HTTPS; otherwise, we are forced to
        // assume that none were HTTPS (because we do not have enough data to determine which were)
        Boolean tlsValue = tlsList.size == total && tlsList.all(tls -> tls);
        return addrList, new Boolean[addrList.size](tlsValue);
    }

    /**
     * Given information from both "Forwarded" and "X-Forwarded-For" headers, return the information
     * to use.
     *
     * @paramxfwdHeaders   the header values for "Forwarded" already obtained from the request
     * @param xfwdHeaders  the header values for "X-Forwarded-For" already obtained from the request
     *
     * @return an array of IP addresses that the request was forwarded on behalf of (i.e. this does
     *         not include the last proxy)
     * @return an array of TLS indicators corresponding to the hops represented by the first array
     */
    private (IPAddress[], Boolean[]) mergeForwardedHeaders(String[] fwdHeaders, String[] xfwdHeaders) {
        // load each (the standard "Forwarded" and the non-standard "x-" version)
        (IPAddress[]  fwdAddrs, Boolean[]  fwdTls) =  parseForwardedHeaders( fwdHeaders);
        (IPAddress[] xfwdAddrs, Boolean[] xfwdTls) = parseXForwardedHeaders(xfwdHeaders);

        // for now just take the one with more information; in theory, we could weave these together
        // (using additional information like: which ones are trusted reverse proxies)
        return fwdAddrs.size > xfwdAddrs.size
                ? ( fwdAddrs,  fwdTls)
                : (xfwdAddrs, xfwdTls);
    }

    /**
     * For a given header name that can appear any number of times in the header, and which can have
     * any number of values in any appearance, construct an array of Boolean values, each
     * corresponding to whether each value of that header matches (case-insensitively) the specified
     * "true" string.
     *
     * @param headerName   the name of the header entry or entries
     * @param truthString  the string that indicates that a header value corresponds to `True`
     *
     * @return an array of Boolean values, one for each header value of the specified name
     */
    private Boolean[] parseBooleanList(String headerName, String truthString) {
        if (String[] headers := getHeaderValuesForName(headerName)) {
            // allocation-free path: exactly one value
            if (headers.size == 1 && !headers[0].indexOf(',')) {
                return eqInsens(headers[0].trim(), truthString) ? [True] : [False];
            }

            Boolean[] result = new Boolean[];
            for (String header : headers) {
                if (header.indexOf(',')) {
                    // multiple comma-delimited values in this header entry
                    for (String value : header.split(',')) {
                        result.add(eqInsens(value.trim(), truthString));
                    }
                } else {
                    result.add(eqInsens(header.trim(), truthString));
                }
            }
            return result;
        }
        return [];
    }

    /**
     * Given two lists, choose the unambiguously largest one (of at least the specified minimum
     * length) and return it.
     *
     * @param list1   the first list
     * @param list2   the second list
     * @param minLen  the minimum number of elements that a list must be to even be considered
     *
     * @return the best list (may be an empty list)
     * @return the new minimum length to use
     */
    private (Boolean[] bestList, Int minLen) takeBest(Boolean[] list1, Boolean[] list2, Int minLen) {
        return switch (list1.size >= minLen, list2.size >= minLen) {
            case (False, False): ([], minLen);
            case (True , False): (list1, list1.size);
            case (False, True ): (list2, list2.size);
            case (True , True ): switch (list1.size <=> list2.size) {
                case Greater: (list1, list1.size);
                case Equal  : (list1 == list2 ? list1 : [], list1.size + 1);  // discard on conflict
                case Lesser : (list2, list2.size);
            };
        };
    }

    @Override
    String hostName.get() = route.host.toString();

    @Override
    String protocolString.get() = server.getProtocolString(context);

    @Override
    @Lazy Protocol protocol.calc() {
        String protocolString = this.protocolString;
        return Protocol.byProtocolString.get(protocolString) ?: new Protocol(protocolString);
    }

    @Override
    Uri httpsUrl.get() {
        Uri     uri      = this.uri;
        Scheme  scheme   = HTTPS;
        Host    host     = route.host;
        UInt16  tlsPort  = route.httpsPort;
        Boolean noPort   = tlsPort == 443;

        return uri.withoutPort().with(
            scheme = scheme.name,
            host   = host.toString(),
            port   = noPort ? Null : tlsPort,
        );
    }

    @Override
    @Lazy String? userAgent.calc() {
        if (String[] values := getHeaderValuesForName(Header.UserAgent)) {
            return values[0];
        }
        return Null;
    }

    @Override
    @Lazy String[] headerNames.calc() = server.getHeaderNames(context);

    @Override
    conditional String[] getHeaderValuesForName(String name) {
        return server.getHeaderValuesForName(context, name);
    }

    @Override
    conditional Byte[] getBodyBytes() = server.getBodyBytes(context);

    @Override
    Boolean containsNestedBodies() = server.containsNestedBodies(context);

    @Override
    void respond(Int status, String[] headerNames, String[] headerValues, Byte[] body) {
        server.respond(context, status, headerNames, headerValues, body);
    }

    @Override
    String toString() {
        return $"({uriString=}, {method.name=}, {tls=})";
    }
}
