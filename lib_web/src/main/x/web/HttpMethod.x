/**
 * An enum containing the valid HTTP methods.
 *
 * @see [RFC 2616 §9.2](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html.
 */
enum HttpMethod(Boolean permitsRequestBody=False, Boolean requiresRequestBody=False)
    {
    /**
     * @see [RFC 2616 §9.2](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.2)
     */
    OPTIONS(True),

    /**
     * @see [RFC 2616 §9.3](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.3)
     */
    GET,

    /**
     * @see [RFC 2616 §9.4](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.4)
     */
    HEAD,

    /**
     * @see [RFC 2616 §9.5](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.5)
     */
    POST(True, True),

    /**
     * @see [RFC 2616 §9.6](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.6)
     */
    PUT(True, True),

    /**
     * @see [RFC 2616 §9.7](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.7)
     */
    DELETE(True),

    /**
     * @see [RFC 2616 §9.8](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.8)
     */
    TRACE,

    /**
     * @see [RFC 2616 §9.9](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.9)
     */
    CONNECT,

    /**
     * @see [RFC 25789](https://tools.ietf.org/html/rfc5789)
     */
    PATCH(True, True),

    /**
     * Any non-standard HTTP method.
     */
    OTHER(True),
    ;

    /**
     * Determine the HttpMethod from the "method name".
     *
     * @param name  the _method_ portion of an [HttpMessage], as defined by the HTTP standard
     *
     * @return the HttpMethod, or [OTHER] if none matches the name.
     */
    static HttpMethod fromName(String name)
        {
        if (HttpMethod method := HttpMethod.byName.get(name))
            {
            return method;
            }

        return OTHER;
        }
    }