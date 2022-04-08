import ecstasy.collections.HasherMap;
import ecstasy.collections.CaseInsensitiveHasher;

/**
 * A representation of the headers for an HTTP request or response.
 *
 * Headers keys are case-insensitive.
 */
class HttpHeaders
        delegates Stringable(headers)
    {
    construct(String[] names = [], String[][] values = [])
        {
        assert:arg names.size == values.size;
        }
    finally
        {
        for (Int i : [0..names.size))
            {
            addAll(names[i], values[i]);
            }
        }

    /**
     * The map of headers.
     */
    private HasherMap<String, String[]> headers = new HasherMap(CaseInsensitiveHasher);

    /**
     * Add the specified header value.
     * If one or more header values already exist for the specified name
     * the new value is appended to the list of values for the name.
     *
     * @param name   the name of the header to add the value to
     * @param value  the header value to add
     *
     * @return  the updated HttpHeaders
     */
    HttpHeaders add(String name, String value)
        {
        validateHeaderName(name);
        headers.process(name, e ->
            {
            String[] headers = e.exists
                ? e.value
                : new Array();
            e.value = headers.add(value);
            return headers;
            });

        return this;
        }

    /**
     * Add the specified header values.
     * If one or more header values already exist for the specified name
     * the new value is appended to the list of values for the name.
     *
     * @param name    the name of the header to add the values to
     * @param values  the header values to add
     *
     * @return  the updated HttpHeaders
     */
    HttpHeaders addAll(String name, String[] values)
        {
        if (values.size == 0)
            {
            return this;
            }

        validateHeaderName(name);
        headers.process(name, e ->
            {
            String[] headers = e.exists
                ? e.value
                : new Array();
            e.value = headers.addAll(values);
            return headers;
            });

        return this;
        }

    /**
     * Set the specified header value, overriding any existing header value
     * for the specified name.
     *
     * @param name   the name of the header to set
     * @param value  the header value
     *
     * @return  the updated HttpHeaders
     */
    HttpHeaders set(String name, String value)
        {
        validateHeaderName(name);
        headers.put(name, new Array(Mutable, [value]));
        return this;
        }

    /**
     * Set the specified header values, overriding any existing header value
     * for the specified name.
     *
     * @param name   the name of the header to set
     * @param value  the header values
     *
     * @return  the updated HttpHeaders
     */
    HttpHeaders set(String name, String[] values)
        {
        if (values.size > 0)
            {
            validateHeaderName(name);
            headers.put(name, new Array(Mutable, values));
            }
        return this;
        }

    /**
     * Returns the value of a header with the specified name.  If there are
     * more than one values for the specified name, the first value is returned.
     *
     * @param name  the name of the header value to obtain
     *
     * @return a True iff one or more header values are associated with the specified name
     * @return the first header value associated with the specified name
     */
    conditional String get(String name)
        {
        if (List<String> list := getAll(name))
            {
            return list.first();
            }
        return False;
        }

    /**
     * Returns all of the header values associated to the specified name.
     *
     * @param name  the name of the header values to obtain
     *
     * @return a True iff one or more header values are associated with the specified name
     * @return the first header value associated with the specified name
     */
    conditional List<String> getAll(String name)
        {
        return headers.get(name);
        }

    /**
     * Removes all of the values for the specified header name.
     *
     * @param name  the name of the header to remove
     *
     * @return  the updated HttpHeaders
     */
    HttpHeaders remove(String name)
        {
        headers.remove(name);
        return this;
        }

    /**
     * The accepted media types.
     *
     * @return the accepted media types.
     */
    MediaType[] accepts.get()
        {
        List<MediaType> accepts = new Array();
        if (List<String> list := getAll("Accept"))
            {
            for (String mt : list)
                {
                for (String s : mt.split(','))
                    {
                    accepts.add(new MediaType(s));
                    }
                }
            }
        return accepts.toArray();
        }

    /**
     * The request or response content type.
     *
     * @return the content type
     */
    MediaType? getContentType()
        {
        if (String ct := get("Content-Type"))
            {
            return new MediaType(ct);
            }
        return Null;
        }

    /**
     * Set the request or response content type.
     *
     * @param mediaType  the content type
     */
    void setContentType(MediaType? mediaType)
        {
        if (mediaType != Null)
            {
            set("Content-Type", mediaType.name);
            }
        else
            {
            headers.remove("Content-Type");
            }
        }

    /**
     * Set the request or response content type.
     *
     * @param mediaType  the content type
     */
    void setContentType(String? mediaType)
        {
        if (mediaType != Null)
            {
            set("Content-Type", mediaType);
            }
        else
            {
            headers.remove("Content-Type");
            }
        }

    /**
     * The request or response content length.
     *
     * @return a True iff the content length header is present
     * @return the content type
     */
    Int? contentLength.get()
        {
        if (String len := get("Content-Length"))
            {
            return new IntLiteral(len).toInt64();
            }
        return Null;
        }

    Map<String, String[]> toMap()
        {
        Map<String, String[]> map = new HashMap();
        return map.putAll(headers);
        }

    (String[], String[][]) toArrays()
        {
        String[]   headerNames  = new Array(headers.size);
        String[][] headerValues = new Array(headers.size);

        Headers: for ((String key, String[] values) : headers)
            {
            headerNames[Headers.count]  = key;
            headerValues[Headers.count] = values;
            }
        return (headerNames, headerValues);
        }


    /**
    * Tha header names as specified by HTTP/1.1
    *
    * @see http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
    */
    static String ACCEPT              = "Accept";
    static String ACCEPT_CHARSET      = "Accept-Charset";
    static String ACCEPT_ENCODING     = "Accept-Encoding";
    static String ACCEPT_LANGUAGE     = "Accept-Language";
    static String AUTHORIZATION       = "Authorization";
    static String CACHE_CONTROL       = "Cache-Control";
    static String CONTENT_ENCODING    = "Content-Encoding";
    static String CONTENT_LANGUAGE    = "Content-Language";
    static String CONTENT_LENGTH      = "Content-Length";
    static String CONTENT_LOCATION    = "Content-Location";
    static String CONTENT_TYPE        = "Content-Type";
    static String DATE                = "Date";
    static String ETAG                = "ETag";
    static String EXPIRES             = "Expires";
    static String HOST                = "Host";
    static String IF_MATCH            = "If-Match";
    static String IF_MODIFIED_SINCE   = "If-Modified-Since";
    static String IF_NONE_MATCH       = "If-None-Match";
    static String IF_UNMODIFIED_SINCE = "If-Unmodified-Since";
    static String LAST_MODIFIED       = "Last-Modified";
    static String LOCATION            = "Location";
    static String USER_AGENT          = "User-Agent";
    static String VARY                = "Vary";
    static String WWW_AUTHENTICATE    = "WWW-Authenticate";
    static String COOKIE              = "Cookie";
    static String SET_COOKIE          = "Set-Cookie";


    // ----- helper methods ----------------------------------------------------------------------------

    private void validateHeaderName(String name)
        {
        if (name.size == 0)
            {
            throw new IllegalArgument("a header name cannot be blank");
            }

        for (Char char : name)
            {
            switch (char.codepoint)
                {
                case 0:    // 0x00
                case 9:    // '\t'
                case 10:   // '\n'
                case 11:   // '\v'
                case 12:   // '\f'
                case 13:   // '\r'
                case 32:   // ' '
                case 44:   // ','
                case 58:   // ':'
                case 59:   // ';'
                case 61:   // '='
                    throw new IllegalArgument($|Header name {name.quoted()} contains a prohibited\
                                               | character: {char.quoted()}
                                             );
                default:
                    assert:arg char.codepoint <= 0x7F
                            as $"Header name {name.quoted()} contains a non-ASCII character: {char.quoted()}";
                    break;
                }
            }
        }
    }