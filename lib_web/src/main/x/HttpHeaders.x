import ecstasy.collections.HasherMap;
import ecstasy.collections.CaseInsensitiveHasher;

/**
 * A representation of the headers for a http request or response.
 *
 * Headers keys are case-insensitive.
 */
class HttpHeaders
        delegates Stringable(headers)
    {
    /**
     * The map of headers.
     */
    private HasherMap<String, String[]> headers = new HasherMap(new CaseInsensitiveHasher());

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
                accepts.add(new MediaType(mt));
                }
            }
        return accepts.toArray();
        }

    /**
     * The request or response content type.
     *
     * @return the content type
     */
    MediaType? contentType.get()
        {
        if (String ct := get("Content-Type"))
            {
            return new MediaType(ct);
            }
        return Null;
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

    ListMap<String, String[]> toMap()
        {
        ListMap<String, String[]> map = new ListMap();
        return map.putAll(headers);
        }

    Tuple<String, String[]>[] toTuples()
        {
        Tuple<String, String[]>[] tuples = new Array(headers.size);
        Headers: for ((String key, String[] values) : headers)
            {
            tuples[Headers.count] = (key, values);
            }
        return tuples;
        }


    // ----- helper methods ----------------------------------------------------------------------------

    private void validateHeaderName(String name)
        {
        if (name.size == 0)
            {
            throw new IllegalArgument("a header name cannot be blank");
            }

        for (Char char : name)
            {
            Int value = char.toInt64();
            switch (value)
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
                    throw new IllegalArgument($"header name '{name}' contains '{char}' one of the prohibited characters: =,;: \\t\\r\\n\\v\\f");
                default:
                    if (value < 0 || value > 0x7F)
                        {
                        throw new IllegalArgument($"header name '{name}' contains non-ASCII character: {char}");
                        }
                    break;
                }
            }
        }
    }
