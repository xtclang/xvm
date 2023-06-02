/**
 * A representation of the headers for an HTTP request or response.
 *
 * Note: HTTP header keys are case-**in**sensitive.
 */
interface Header
        extends Freezable {
    /**
    * Tha header names as specified by HTTP/1.1
    *
    * @see http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
    */
    static String Accept                 = "Accept";
    static String AcceptCharset          = "Accept-Charset";
    static String AcceptEncoding         = "Accept-Encoding";
    static String AcceptLanguage         = "Accept-Language";
    static String Authorization          = "Authorization";
    static String AuthorizationResponse  = "Authorization-Response";
    static String CacheControl           = "Cache-Control";
    static String ContentEncoding        = "Content-Encoding";
    static String ContentLanguage        = "Content-Language";
    static String ContentLength          = "Content-Length";
    static String ContentLocation        = "Content-Location";
    static String ContentType            = "Content-Type";
    static String Date                   = "Date";
    static String ETag                   = "ETag";
    static String Expires                = "Expires";
    static String Host                   = "Host";
    static String IfMatch                = "If-Match";
    static String IfModifiedSince        = "If-Modified-Since";
    static String IfNoneMatch            = "If-None-Match";
    static String IfRange                = "If-Range";
    static String IfUnmodifiedSince      = "If-Unmodified-Since";
    static String LastModified           = "Last-Modified";
    static String Location               = "Location";
    static String UserAgent              = "User-Agent";
    static String Vary                   = "Vary";
    static String WWWAuthenticate        = "WWW-Authenticate";
    static String Cookie                 = "Cookie";
    static String SetCookie              = "Set-Cookie";

    /**
     * `True` if this `Header` is for a [Request]; `False` if it is for a [Response].
     */
    @RO Boolean isRequest;

    /**
     * True iff the headers can be modified. When a request or response is received, the headers are
     * not modifiable, and when sending a request or a response, there is a point at which the
     * headers are no longer modifiable.
     */
    @RO Boolean modifiable.get() {
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
    @RO List<String> names.get() {
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
    Iterator<String> valuesOf(String name, Char? expandDelim=Null) {
        import ecstasy.collections.CaseInsensitive;
        Iterator<String> iter = entries.iterator().filter(e -> CaseInsensitive.areEqual(e[0], name))
                                                  .map(e -> e[1]);

        if (expandDelim != Null) {
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
    conditional String firstOf(String name, Char? expandDelim=Null) {
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
    conditional String lastOf(String name, Char? expandDelim=Null) {
        return valuesOf(name, expandDelim).reversed().next();
    }

    /**
     * Erase all HTTP headers for the specified name.
     *
     * @param name  the case-insensitive header name
     *
     * @throws IllegalState  if [headersModifiable] is `False`
     */
    void removeAll(String name) {
        List<Entry> entries = this.entries;
        if (!entries.empty) {
            val cursor = entries.cursor();
            while (cursor.exists) {
                if (cursor.value[0] == name) {
                    cursor.delete();
                } else {
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
    void put(String name, String|String[] value) {
        removeAll(name);
        add(name, value);
    }

    /**
     * Add the specified header entry. (Any previously existent headers with the same name will be
     * left unchanged.)
     *
     * @param entry   the header entry to add
     */
    void add(Entry entry) {
        entries.add(entry);
    }

    /**
     * Add the specified header entry. (Any previously existent headers with the same name will be
     * left unchanged.)
     *
     * @param name   the case-insensitive header name
     * @param value  the value or values to use for the header name
     */
    void add(String name, String|String[] value) {
        if (value.is(String[])) {
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
    static Boolean isCommaAllowedInValue(String name) {
        return switch (name) {
            case Authorization:                 // comma delimited sub-values
            case AuthorizationResponse:        // comma delimited sub-values
            case Date:                          // unquoted date containing a comma
            case Expires:                       // unquoted date containing a comma
            case IfModifiedSince:             // unquoted date containing a comma
            case IfRange:                      // unquoted date containing a comma
            case IfUnmodifiedSince:           // unquoted date containing a comma
            case LastModified:                 // unquoted date containing a comma
            case SetCookie:                    // unquoted date containing a comma
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
    static conditional String isInvalidHeaderName(String name) {
        if (name.size == 0) {
            return True, "Header name is blank";
        }

        for (Char char : name) {
            switch (char) {
            case '\0'..' ', '\d':                               // "CTL" chars, and space
            case '(', ')', '<', '>', '@', ',', ';', ':':        // "separators"
            case '\\', '\"', '/', '[', ']', '?', '=', '{', '}': // "separators"
                return True, $"Header name {name.quoted()} contains a prohibited character: {char.quoted()}";

            default:
                if (char.codepoint > 0x7F) {
                    return True, $"Header name {name.quoted()} contains a non-ASCII character: {char.quoted()}";
                }
                break;
            }
        }

        return False;
    }
}