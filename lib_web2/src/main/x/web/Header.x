/**
 * A representation of the headers for an HTTP request or response.
 *
 * Note: HTTP header keys are case-**in**sensitive.
 */
interface Header
    {
    /**
     * True iff the headers can be modified  TODO explain
     */
    @RO Boolean headersModifiable;

    /**
     * Each `Entry` that appears in an [HttpMessage] is simply a `String` name and a `String`
     * value.
     */
    typedef Tuple<String, String> as Entry;

    /**
     * Obtain the HTTP headers as list of [Entry] objects.
     *
     * @return a list whose contents correspond to the HTTP headers
     */
    @RO List<Entry> headerList;

    /**
     * Obtain the header value, as-is (not comma-expanded) for the specified case-insensitive header
     * name. If the header name occurs multiple times within the HTTP header, then only the first
     * instance is returned.
     *
     * @param name  the case-insensitive header name
     *
     * @return True iff the specified name is found
     * @return (conditional) the corresponding value from the HTTP header
     */
    conditional String firstHeaderFor(String name);

    /**
     * Obtain the header value, as-is (not comma-expanded) for the specified case-insensitive header
     * name. If the header name occurs multiple times within the HTTP header, then only the last
     * instance is returned.
     *
     * @param name  the case-insensitive header name
     *
     * @return True iff the specified name is found
     * @return (conditional) the corresponding value from the HTTP header
     */
    conditional String lastHeaderFor(String name);

    /**
     * Obtain all of the header values for the specified case-insensitive header name.
     *
     * @param name                    the case-insensitive header name
     * @param suppressCommaExpansion  pass True to prevent a value from being split into multiple
     *                                valuels if it contains one or more commas
     *
     * @return True iff the specified name is found at least once
     * @return (conditional) all of the corresponding values from the HTTP header
     */
    conditional String[] allHeadersFor(String name, Boolean suppressCommaExpansion=False);

    /**
     * Erase all HTTP headers for the specified name.
     *
     * @param name  the case-insensitive header name
     *
     * @throws IllegalState  if [headersModifiable] is `False`
     */
    void eraseHeadersFor(String name);

    /**
     * Add or replace the value of the specified header name. (Any and all previously existent
     * headers with the same name will be replaced.)
     *
     * @param name   the case-insensitive header name
     * @param value  the value or values to use for the header name
     */
    void addHeader(String name, String|List<String> value)
        {
        if (value.is(String[]))
            {
            value = value.appendTo(new StringBuffer(value.estimateStringLength(",", "", "")), ",", "", "").toString();
            }
        assert value.is(String); // TODO GG: should not be necessary
        headerList.add((name, value));
        }

    /**
     * Add or replace the value of the specified header name. (Any and all previously existent
     * headers with the same name will be replaced.)
     *
     * @param name   the case-insensitive header name
     * @param value  the value or values to use for the header name
     */
    void setHeader(String name, String|List<String> value)
        {
        eraseHeadersFor(name);
        addHeader(name, value);
        }


    // ----- helper methods ----------------------------------------------------------------------------

    /**
     * TODO doc
     */
    static conditional String isInvalidHeaderName(String name)
        {
        if (name.size == 0)
            {
            return True, "Header name is blank";
            }

        for (Char char : name)
            {
            switch (char)
                {
                case '\0'..' ', '\d':                               // "CTL" chars, and space
                case '(', ')', '<', '>', '@', ',', ';', ':':        // "separators"
                case '\\', '\"', '/', '[', ']', '?', '=', '{', '}': // "separators"
                    return True, $"Header name {name.quoted()} contains a prohibited character: {char.quoted()}";

                default:
                    if (char.codepoint > 0x7F)
                        {
                        return True, $"Header name {name.quoted()} contains a non-ASCII character: {char.quoted()}";
                        }
                    break;
                }
            }

        return False;
        }
    }
