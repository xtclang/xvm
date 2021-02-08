/**
 * A representation of a http request.
 */
class HttpRequest(URI uri, HttpHeaders headers, HttpMethod method, Object? body)
        extends HttpMessage(headers, body)
    {

    construct (URI uri, Map<String, String[]> headerMap, HttpMethod method, Object? body)
        {
        HttpHeaders headers = new HttpHeaders();
        for (Map<String, String[]>.Entry entry : headerMap.entries)
            {
            headers.set(entry.key, new Array(Mutable, entry.value));
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
        UriQueryStringParser parser = new UriQueryStringParser(uri.toString());
        return parser.getParameters();
        }
    }
