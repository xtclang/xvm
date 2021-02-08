/**
 * An enum containing the valid HTTP methods.
 * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html.
 */
enum HttpMethod
    {
    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.2.
     */
    OPTIONS,

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.3.
     */
    GET,

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.4.
     */
    HEAD,

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.5.
     */
    POST,

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.6.
     */
    PUT,

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.7.
     */
    DELETE,

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.8.
     */
    TRACE,

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.9.
     */
    CONNECT,

    /**
     * See https://tools.ietf.org/html/rfc5789.
     */
    PATCH,

    /**
     * A custom non-standard HTTP method.
     */
    CUSTOM;

    /**
     * Returns a http method from the method name.
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