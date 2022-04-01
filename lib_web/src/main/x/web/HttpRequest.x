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

    typedef (String | String[]) as QueryParameter;

    /**
     * @return the HTTP parameters contained in the URI query string
     */
    @Lazy Map<String, QueryParameter> parameters.calc()
        {
        if (String query ?= uri.query, query.size != 0)
            {
            Map<String, String>         rawParams   = query.splitMap();
            Map<String, QueryParameter> queryParams = new HashMap();

            for ((String key, String value) : rawParams)
                {
                queryParams.process(key, e ->
                    {
                    if (e.exists)
                        {
                        QueryParameter prevValue = e.value;
                        if (prevValue.is(String))
                            {
                            String[] values = [prevValue, value];
                            e.value = values;
                            }
                        else
                            {
                            prevValue += value;
                            }
                        }
                    else
                        {
                        e.value = value;
                        }
                    });
                }
            return queryParams;
            }
        return [];
        }
    }