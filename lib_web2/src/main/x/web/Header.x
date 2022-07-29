/**
 * A representation of the headers for an HTTP request or response.
 *
 * Note: HTTP header keys are case-**in**sensitive.
 */
interface Header
    {
    /**
     * `True` if this `Header` is for a [Request]; `False` if it is for a [Response].
     */
    @RO Boolean isRequest;

    /**
     * True iff the headers can be modified. When a request or response is received, the headers are
     * not modifiable, and when sending a request or a response, there is a point at which the
     * headers are no longer modifiable.
     */
    @RO Boolean modifiable;

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
     * @param name          the case-insensitive header name
     * @param expandCommas  pass True to prevent a value from being split into multiple
     *                                values if it contains one or more commas; some header names
     *                                will always suppress comma expansion, such as cookies and any
     *                                header entry that can contain a date string
     *
     * @return True iff the specified name is found at least once
     * @return (conditional) all of the corresponding values from the HTTP header
     */
    Iterator<String> valuesOf(String name, Boolean? expandCommas=Null)
        {
        // TODO CP case insens name
        // TODO CP expandCommas
        return entries.iterator().filter(e -> e[0] == name).map(e -> e[1]);
        }

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
    conditional String firstOf(String name)
        {
        return valuesOf(name).next();
        }

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
    conditional String lastOf(String name)
        {
        Iterator<String> values = valuesOf(name);
        if (String value := values.next())
            {
            while (value := values.next())
                {
                }
            return True, value;
            }

        return False;
        }

    /**
     * Erase all HTTP headers for the specified name.
     *
     * @param name  the case-insensitive header name
     *
     * @throws IllegalState  if [headersModifiable] is `False`
     */
    void removeAll(String name);

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
            case "Authorization":              // comma delimited sub-values
            case "Authorization-Response":     // comma delimited sub-values
            case "Date":                       // unquoted date containing a comma
            case "Expires":                    // unquoted date containing a comma
            case "If-Modified-Since":          // unquoted date containing a comma
            case "If-Range":                   // unquoted date containing a comma
            case "If-Unmodified-Since":        // unquoted date containing a comma
            case "Last-Modified":              // unquoted date containing a comma
            case "Set-Cookie":                 // unquoted date containing a comma
            // REVIEW: maybe: "Warning" - contains date (but in quotes)
                True;

            default:
                False;
            };
        }

    /**
     * Parse an `IMF-fixdate` String.
     *
     * The format of `IMF-fixdate` is fixed length, and defined by
     * [RFC 7231](https://datatracker.ietf.org/doc/html/rfc7231#section-7.1.1.1).
     *
     * @param text    an `IMF-fixdate` string
     * @param strict  (optional) pass `True` to strictly validate the details of the `IMF-fixdate`;
     *                `False` may skip some strict validations in order to save time
     *
     * @return True iff the `IMF-fixdate` was successfully parsed
     * @return (conditional) the `Time` value corresponding to the `IMF-fixdate`
     */
    static conditional Time parseImfFixDate(String text, Boolean strict=False)
        {
        //           1         2
        // 01234567890123456789012345678
        // Sun, 06 Nov 1994 08:49:37 GMT
        if (text.size >= 28,
                Byte day0   := text[ 5].asciiDigit(),
                Byte day1   := text[ 6].asciiDigit(),
                Char month0 := text[ 8].asciiUppercase(),
                Char month1 := text[ 9].asciiLowercase(),
                Char month2 := text[10].asciiLowercase(),
                Byte year0  := text[12].asciiDigit(),
                Byte year1  := text[13].asciiDigit(),
                Byte year2  := text[14].asciiDigit(),
                Byte year3  := text[15].asciiDigit(),
                Byte hour0  := text[17].asciiDigit(),
                Byte hour1  := text[18].asciiDigit(),
                Byte min0   := text[20].asciiDigit(),
                Byte min1   := text[21].asciiDigit(),
                Byte sec0   := text[23].asciiDigit(),
                Byte sec1   := text[24].asciiDigit()
                )
            {
            Int year  = year0 * 1000 + year1 * 100 + year2 * 10 + year3;
            Int month = switch(month0, month1, month2)
                {
                case ('J', 'a', 'n'): 1;
                case ('F', 'e', 'b'): 2;
                case ('M', 'a', 'r'): 3;
                case ('A', 'p', 'r'): 4;
                case ('M', 'a', 'y'): 5;
                case ('J', 'u', 'n'): 6;
                case ('J', 'u', 'l'): 7;
                case ('A', 'u', 'g'): 8;
                case ('S', 'e', 'p'): 9;
                case ('O', 'c', 't'): 10;
                case ('N', 'o', 'v'): 11;
                case ('D', 'e', 'c'): 12;
                default: -1;
                };
            Int day   = day0 * 10 + day1;
            if (!Date.isGregorian(year, month, day))
                {
                return False;
                }
            Date date = new Date(year, month, day);

            Int hour = hour0 * 10 + hour1;
            Int min  = min0 * 10 + min1;
            Int sec  = sec0 * 10 + sec1;
            if (!TimeOfDay.validate(hour, min, sec))
                {
                return False;
                }
            TimeOfDay timeOfDay = new TimeOfDay(hour, min, sec);

            if (strict)
                {
                String dow = date.dayOfWeek.name;
                if (!(text[ 0] == dow[0] &&
                      text[ 1] == dow[1] &&
                      text[ 2] == dow[2] &&
                      text[ 3] == ',' &&
                      text[ 4] == ' ' &&
                      text[ 7] == ' ' &&
                      text[11] == ' ' &&
                      text[16] == ' ' &&
                      text[19] == ':' &&
                      text[22] == ':' &&
                      text[25] == ' ' &&
                      text[26] == 'G' &&
                      text[27] == 'M' &&
                      text[28] == 'T' &&
                      text.substring(29).chars.all(Char.isWhitespace)))
                    {
                    return False;
                    }
                }

            return True, new Time(date, timeOfDay, UTC);
            }

        return False;
        }

    /**
     * Render a [Time] as an "IMF fix date".
     *
     * @param time  the time value
     *
     * @return an IMF fix date string
     */
    static String formatImfFixDate(Time time)
        {
        // GMT == UTC; it is the only supported timezone
        if (time.timezone != UTC)
            {
            time = time.with(timezone = UTC);
            }

        Date      date  = time.date;
        String    dow   = date.dayOfWeek.name;
        UInt32    day   = date.day.toUInt32();
        String    moy   = date.monthOfYear.name;
        UInt32    year  = date.year.toUInt32();
        TimeOfDay tod   = time.timeOfDay;
        UInt32    hour  = tod.hour.toUInt32();
        UInt32    min   = tod.minute.toUInt32();
        UInt32    sec   = tod.second.toUInt32();

        Char[] text = new Char[28];
        text[ 0] = dow[0];
        text[ 1] = dow[1];
        text[ 2] = dow[2];
        text[ 3] = ',';
        text[ 4] = ' ';
        text[ 5] = '0' + day / 10;
        text[ 6] = '0' + day % 10;
        text[ 7] = ' ';
        text[ 8] = moy[0];
        text[ 9] = moy[1];
        text[10] = moy[2];
        text[11] = ' ';
        text[12] = '0' + year / 1000 % 10;
        text[13] = '0' + year /  100 % 10;
        text[14] = '0' + year /   10 % 10;
        text[15] = '0' + year        % 10;
        text[16] = ' ';
        text[17] = '0' + hour / 10;
        text[18] = '0' + hour % 10;
        text[19] = ':';
        text[20] = '0' + min / 10;
        text[21] = '0' + min % 10;
        text[22] = ':';
        text[23] = '0' + sec / 10;
        text[24] = '0' + sec % 10;
        text[25] = ' ';
        text[26] = 'G';
        text[27] = 'M';
        text[28] = 'T';

        return new String(text);
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