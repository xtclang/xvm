import ecstasy.collections.CaseInsensitive;

import net.UriTemplate;


/**
 * A representation of an HTTP request.
 *
 * The four fundamental parts of an HTTP request are:
 *
 * * Method
 * * Scheme
 * * Authority
 * * Path
 *
 * TODO doc how to get a request passed to an end point
 */
interface Request
        extends HttpMessage {
    /**
     * The HTTP method ("GET", "POST", etc.)
     */
    @RO HttpMethod method;

    /**
     * The URI of the request.
     */
    @RO Uri uri;

    /**
     * Corresponds to the ":scheme" pseudo-header field in HTTP/2.
     */
    @RO Scheme scheme.get() {
        return Scheme.byName.getOrNull(uri.scheme?)? : assert;
    }

    /**
     * Corresponds to the ":authority" pseudo-header field in HTTP/2. This includes the authority
     * portion of the target URI.
     */
    @RO String authority.get() {
        return uri.authority ?: assert;
    }

    /**
     * Corresponds to the ":path" pseudo-header field in HTTP/2. This includes the path and query
     * parts of the target URI.
     */
    @RO String path.get() {
        return uri.path?.toString() : "";
    }

    /**
     * The protocol over which the request was received, if the protocol is known.
     *
     * For an out-going request, the protocol is the requested protocol to use to send the request;
     * a client implementation may choose to use a different protocol if necessary.
     */
    @RO Protocol? protocol.get() {
        if (String scheme ?= uri.scheme) {
            return Protocol.byProtocolString.getOrCompute(scheme,
                    () -> throw new IllegalState($"unknown protocol: {scheme}"));
        }
        return Null;
    }

    /**
     * The accepted media types.
     */
    @RO AcceptList accepts;

    /**
     * @return an iterator of all cookie names and values in this request
     */
    Iterator<Tuple<String, String>> cookies() {
        return header.valuesOf(Header.Cookie, ';')
                     .map(kv -> (kv.extract('=', 0, "???").trim(), kv.extract('=', 1).trim()));
    }

    /**
     * Obtain the value of the specified cookie, if it is included in this request.
     *
     * @return True iff the specified cookie name is in the header
     * @return (conditional) the specified cookie
     */
    conditional String getCookieValue(String name) {
        for (String value : header.valuesOf(Header.Cookie, ';')) {
            if (name == value.extract('=', 0, "???")) {
                return True, value.extract('=', 1);
            }
        }
        return False;
    }

    @Override
    Iterator<String> cookieNames() {
        return header.valuesOf(Header.Cookie, ';')
                     .map(kv -> kv.extract('=', 0, "???").trim());
    }
}