import ecstasy.collections.CaseInsensitiveHasher;

/**
 * A representation of the headers for a http request or response.
 *
 * Headers keys are case-insensitive.
 */
class HttpHeaders
        implements Stringable
    {
    construct()
        {
        headers = new HashMap(new CaseInsensitiveHasher());
        }

    /**
     * The map of headers.
     */
    private HashMap<String, String[]> headers;

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
            List<String> list;
            if (e.exists)
                {
                list = e.value;
                }
            else
                {
                list = new Array();
                }
            list.add(value);
            e.value = list;
            return list;
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
            List<String> list;
            if (e.exists)
                {
                list = e.value;
                }
            else
                {
                list = new Array();
                }
            list.addAll(values);
            e.value = list;
            return list;
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
        String[] values = [value];
        headers.put(name, values);
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
        if (values.size == 0)
            {
            return this;
            }

        validateHeaderName(name);
        String[] list = new Array();
        list.addAll(values);
        headers.put(name, list);

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
        map.putAll(headers);
        return map;
        }

    Tuple<String, String[]>[] toTuples()
        {
        Tuple<String, String[]>[] tuples = new Array(headers.size);
        Int                       i      = 0;
        for (HashMap.Entry entry : headers.entries)
            {
            tuples[i++] = (entry.key.as(String), entry.value.as(String[]));
            }
        return tuples;
        }

    // ----- Stringable interface implementation ---------------------------------------------------

    @Override
    public Int estimateStringLength()
        {
        return headers.estimateStringLength();
        }

    @Override
    public Appender<Char> appendTo(Appender<Char> buf)
        {
        return headers.appendTo(buf);
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
            switch(value)
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
