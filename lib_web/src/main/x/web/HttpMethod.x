/**
 * An enum containing the valid HTTP methods.
 *
 * @see [RFC 2616 §9.2](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html.
 */
enum HttpMethod
    {
    /**
     * @see [RFC 2616 §9.2](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.2)
     */
    OPTIONS,

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
    POST,

    /**
     * @see [RFC 2616 §9.6](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.6)
     */
    PUT,

    /**
     * @see [RFC 2616 §9.7](https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.7)
     */
    DELETE,

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
    PATCH,

    /**
     * A custom non-standard HTTP method.
     */
    CUSTOM;

    /**
     * Returns an HTTP method from the method name.
     */
    static HttpMethod fromName(String name)
        {
        for (HttpMethod method : HttpMethod.values)
            {
            if (name == method.name)
                {
                return method;
                }
            }
        return CUSTOM;
        }

    /**
     * Determine whether the given method requires a request body.
     *
     * @param method the HttpMethod
     * @return True if the method requires a request body
     */
    static Boolean requiresRequestBody(HttpMethod method)
        {
        return method == POST || method == PUT || method == PATCH;
        }

    /**
     * Determine whether the given method allows a request body.
     *
     * @param method the HttpMethod
     * @return True if the method requires a request body
     */
    static Boolean permitsRequestBody(HttpMethod method)
        {
        return requiresRequestBody(method)
                || method == OPTIONS
                || method == DELETE
                || method == CUSTOM;
        }

    }