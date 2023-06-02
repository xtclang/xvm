/**
 * HTTP methods, such as "GET" and "POST".
 *
 * @see [RFC 2616 §9.2](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html.
 */
const HttpMethod(String name, BodyRule body=Permitted) {
    enum BodyRule {Forbidden, Permitted, Required}

    /**
     * @see [RFC 2616 §9.2](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.2)
     */
    static HttpMethod OPTIONS = new HttpMethod("OPTIONS", Permitted);

    /**
     * @see [RFC 2616 §9.3](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.3)
     */
    static HttpMethod GET = new HttpMethod("GET", Forbidden);

    /**
     * @see [RFC 2616 §9.4](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.4)
     */
    static HttpMethod HEAD = new HttpMethod("HEAD", Forbidden);

    /**
     * @see [RFC 2616 §9.5](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.5)
     */
    static HttpMethod POST = new HttpMethod("POST", Required);

    /**
     * @see [RFC 2616 §9.6](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.6)
     */
    static HttpMethod PUT = new HttpMethod("PUT", Required);

    /**
     * @see [RFC 2616 §9.7](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.7)
     */
    static HttpMethod DELETE = new HttpMethod("DELETE", Permitted);

    /**
     * @see [RFC 2616 §9.8](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.8)
     */
    static HttpMethod TRACE = new HttpMethod("TRACE", Forbidden);

    /**
     * @see [RFC 2616 §9.9](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.9)
     */
    static HttpMethod CONNECT = new HttpMethod("CONNECT", Forbidden);

    /**
     * @see [RFC 25789](https://tools.ietf.org/html/rfc5789)
     */
    static HttpMethod PATCH = new HttpMethod("PATCH", Required);

    /**
     * Determine the HttpMethod from the "method name".
     *
     * @param name  the _method_ portion of an [HttpMessage], as defined by the HTTP standard
     *
     * @return the HttpMethod, or [OTHER] if none matches the name.
     */
    static HttpMethod of(String name) {
        return switch (name) {
            case "OPTIONS": OPTIONS;
            case "GET":     GET;
            case "HEAD":    HEAD;
            case "POST":    POST;
            case "PUT":     PUT;
            case "DELETE":  DELETE;
            case "TRACE":   TRACE;
            case "CONNECT": CONNECT;
            case "PATCH":   PATCH;
            default: new HttpMethod(name);
        };
    }
}