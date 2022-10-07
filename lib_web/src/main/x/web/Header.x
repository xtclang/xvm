/**
 * A representation of the headers for an HTTP request or response.
 *
 * Note: HTTP header keys are case-**in**sensitive.
 */
interface Header
        extends Freezable
    {
    /**
    * Tha header names as specified by HTTP/1.1
    *
    * @see http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
    */
    static String ACCEPT                 = "Accept";
    static String ACCEPT_CHARSET         = "Accept-Charset";
    static String ACCEPT_ENCODING        = "Accept-Encoding";
    static String ACCEPT_LANGUAGE        = "Accept-Language";
    static String AUTHORIZATION          = "Authorization";
    static String AUTHORIZATION_RESPONSE = "Authorization-Response";
    static String CACHE_CONTROL          = "Cache-Control";
    static String CONTENT_ENCODING       = "Content-Encoding";
    static String CONTENT_LANGUAGE       = "Content-Language";
    static String CONTENT_LENGTH         = "Content-Length";
    static String CONTENT_LOCATION       = "Content-Location";
    static String CONTENT_TYPE           = "Content-Type";
    static String DATE                   = "Date";
    static String ETAG                   = "ETag";
    static String EXPIRES                = "Expires";
    static String HOST                   = "Host";
    static String IF_MATCH               = "If-Match";
    static String IF_MODIFIED_SINCE      = "If-Modified-Since";
    static String IF_NONE_MATCH          = "If-None-Match";
    static String IF_RANGE               = "If-Range";
    static String IF_UNMODIFIED_SINCE    = "If-Unmodified-Since";
    static String LAST_MODIFIED          = "Last-Modified";
    static String LOCATION               = "Location";
    static String USER_AGENT             = "User-Agent";
    static String VARY                   = "Vary";
    static String WWW_AUTHENTICATE       = "WWW-Authenticate";
    static String COOKIE                 = "Cookie";
    static String SET_COOKIE             = "Set-Cookie";

    /**
     * `True` if this `Header` is for a [Request]; `False` if it is for a [Response].
     */
    @RO Boolean isRequest;

    /**
     * True iff the headers can be modified. When a request or response is received, the headers are
     * not modifiable, and when sending a request or a response, there is a point at which the
     * headers are no longer modifiable.
     */
    @RO Boolean modifiable.get()
        {
        return !this.is(immutable);
        }

    /**
     * Each `Entry` that appears in the header portion of an [HttpMessage] is simply a `String` name
     * (encoded as bytes) and a `String` value (also encoded as bytes).
     */
    typedef Tuple<String, String> as Entry;

    /**
     * Obtain the HTTP header entries as list of names, one for each `Entry`.
     *
     * @return a list of names of the HTTP headers
     */
    @RO List<String> names.get()
        {
        List<Entry> entries = this.entries;

        return entries.map(e -> e[0], new String[](entries.size)).as(List<String>);
        }

    /**
     * Obtain the HTTP headers as list of [Entry] objects.
     *
     * @return a list whose contents correspond to the HTTP headers
     */
    @RO List<Entry> entries;

    /**
     * Obtain all of the header values for the specified case-insensitive header name.
     *
     * @param name         the case-insensitive header name
     * @param expandDelim  pass a character delimiter, such as `','`, to indicate that a single
     *                     header entry may be expanded to multiple values, if that delimiter occurs
     *                     within the associated value
     *
     * @return True iff the specified name is found at least once
     * @return (conditional) all of the corresponding values from the HTTP header
     */
    Iterator<String> valuesOf(String name, Char? expandDelim=Null)
        {
        import ecstasy.collections.CaseInsensitive;
        Iterator<String> iter = entries.iterator().filter(e -> CaseInsensitive.areEqual(e[0], name))
                                                  .map(e -> e[1]);

        if (expandDelim != Null)
            {
            iter = iter.flatMap(s -> s.split(expandDelim).iterator());
            }

        return iter.map(s -> s.trim());
        }

    /**
     * Obtain the header value, as-is (not comma-expanded) for the specified case-insensitive header
     * name. If the header name occurs multiple times within the HTTP header, then only the first
     * instance is returned.
     *
     * @param name         the case-insensitive header name
     * @param expandDelim  pass a character delimiter, such as `','`, to indicate that a single
     *                     header entry may be expanded to multiple values, if that delimiter occurs
     *                     within the associated value
     *
     * @return True iff the specified name is found
     * @return (conditional) the corresponding value from the HTTP header
     */
    conditional String firstOf(String name, Char? expandDelim=Null)
        {
        return valuesOf(name, expandDelim).next();
        }

    /**
     * Obtain the header value, as-is (not comma-expanded) for the specified case-insensitive header
     * name. If the header name occurs multiple times within the HTTP header, then only the last
     * instance is returned.
     *
     * @param name         the case-insensitive header name
     * @param expandDelim  pass a character delimiter, such as `','`, to indicate that a single
     *                     header entry may be expanded to multiple values, if that delimiter occurs
     *                     within the associated value
     *
     * @return True iff the specified name is found
     * @return (conditional) the corresponding value from the HTTP header
     */
    conditional String lastOf(String name, Char? expandDelim=Null)
        {
        return valuesOf(name, expandDelim).reversed().next();
        }

    /**
     * Erase all HTTP headers for the specified name.
     *
     * @param name  the case-insensitive header name
     *
     * @throws IllegalState  if [headersModifiable] is `False`
     */
    void removeAll(String name)
        {
        List<Entry> entries = this.entries;
        if (!entries.empty)
            {
            val cursor = entries.cursor();
            while (cursor.exists)
                {
                if (cursor.value[0] == name)
                    {
                    cursor.delete();
                    }
                else
                    {
                    cursor.advance();
                    }
                }
            }
        }

    /**
     * Add or replace the value of the specified header name. (Any and all previously existent
     * headers with the same name will be replaced.)
     *
     * @param name   the case-insensitive header name
     * @param value  the value or values to use for the header name
     */
    void put(String name, String|String[] value)
        {
        removeAll(name);
        add(name, value);
        }

    /**
     * Add the specified header entry. (Any previously existent headers with the same name will be
     * left unchanged.)
     *
     * @param name   the case-insensitive header name
     * @param value  the value or values to use for the header name
     */
    void add(String name, String|String[] value)
        {
        if (value.is(String[]))
            {
            value = value.appendTo(new StringBuffer(value.estimateStringLength(",", "", "")), ",", "", "").toString();
            }
        entries.add((name, value));
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Determine if the specified header name is specified as having values that can contain an
     * unescaped and unquoted comma. Certain header names are flexible in that they can either
     * appear multiple times in the header to define multiple values, or they may have a
     * comma-delimited list of values. Other header names can have a value that has a comma as part
     * of its textual format, precluding the use of a value list. The most common example is any
     * header value that contains date information, since a date in an HTTP header always contains
     * a comma in its format.
     *
     * @param name  the header name
     *
     * @return True iff the name is a header name that is known to allow a comma as part of its
     *         textual format
     */
    static Boolean isCommaAllowedInValue(String name)
        {
        return switch (name)
            {
            case AUTHORIZATION:                 // comma delimited sub-values
            case AUTHORIZATION_RESPONSE:        // comma delimited sub-values
            case DATE:                          // unquoted date containing a comma
            case EXPIRES:                       // unquoted date containing a comma
            case IF_MODIFIED_SINCE:             // unquoted date containing a comma
            case IF_RANGE:                      // unquoted date containing a comma
            case IF_UNMODIFIED_SINCE:           // unquoted date containing a comma
            case LAST_MODIFIED:                 // unquoted date containing a comma
            case SET_COOKIE:                    // unquoted date containing a comma
            // REVIEW: maybe: "Warning" - contains date (but in quotes)
                True;

            default:
                False;
            };
        }

    /**
     * Test the specified header name against the rules of the HTTP specification.
     *
     * @param name  a header name
     *
     * @return True iff the name does not meet the rules of the HTTP specification
     * @return (conditional) an explanation of what is wrong with the passed name
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