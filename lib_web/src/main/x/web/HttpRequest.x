/**
 * A representation of an HTTP request.
 */
class HttpRequest(URI uri, HttpHeaders headers, HttpMethod method, Byte[]? body)
        extends HttpMessage(headers, body)
    {
    construct (URI uri, Map<String, String[]> headerMap, HttpMethod method, Byte[]? body)
        {
        HttpHeaders headers = new HttpHeaders();
        for ((String key, String[] values) : headerMap)
            {
            headers.set(key, new Array(Mutable, values));
            }
        construct HttpRequest(uri, headers, method, body);
        }

    /**
     * @return the accepted media types.
     */
    MediaType[] accepts.get()
        {
        return headers.accepts;
        }

    /**
     * @return the HTTP parameters contained with the URI query string
     */
    @Lazy Map<String, List<String>> parameters.calc()
        {
        if (String query ?= uri.query)
            {
            return new UriQueryStringParser(query, hasPath=False).getParameters();
            }
        return [];
        }
    }