/**
 * Parses a HTTP query string into a path string and key-value query parameter pairs.
 *
 * This decoder can also decode the content of an HTTP POST request whose content type
 * is application/x-www-form-urlencoded by constructing the parser with the hasPath
 * property set to False.
 *
 * Decoding takes place lazily.
 *
 * HashDOS vulnerability fix:
 * As a workaround to the https://netty.io/s/hashdos vulnerability, the decoder limits
 * the maximum number of decoded key-value parameter pairs, up to 1024 by default. You
 * can configure maxParams when you construct the decoder by passing an different value.
 */
class UriQueryStringParser
    {
    /**
     * Creates a new decoder that decodes the specified URI encoded in the
     * specified charset.
     */
    construct(String uri, Boolean hasPath = True, Int maxParams = 1024, Boolean semicolonIsNormalChar = False) {
        this.uri                   = uri;
        this.maxParams             = maxParams;
        this.semicolonIsNormalChar = semicolonIsNormalChar;
        this.pathEndIdx            = hasPath ? -1 : 0;
        this.path                  = Null;
        this.params                = Null;
    }

    /**
     * The URI to decode.
     */
    private String uri;

    /**
     * The maximum number of parameters to decode,
     */
    private Int maxParams;

    /**
     * A flag indicating whether a simi-colon is treated as a normal character.
     */
    private Boolean semicolonIsNormalChar;

    /**
     * The end of the path value in the URI.
     */
    private Int pathEndIdx;

    /**
     * The decoded path value.
     */
    private String? path;

    /**
     * The decoded parameters.
     */
    private Map<String, List<String>>? params;

    /**
     * Returns the decoded path string of the URI.
     */
    String getPath()
        {
        if (path == Null)
            {
            path = decodeComponent(uri, 0, getPathEndIdx(), True);
            }
        return path.as(String);
        }

    /**
     * Returns the decoded key-value parameter pairs of the URI.
     */
    public Map<String, List<String>> getParameters()
        {
        if (params == null)
            {
            params = decodeParams(uri, getPathEndIdx());
            }
        return params.as(Map<String, List<String>>);
        }

    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Decode a URI component.
     */
    private String decodeComponent(String s, Int from, Int toExcluded, Boolean isPath)
        {
        Int len = toExcluded - from;
        if (len <= 0)
            {
            return "";
            }
        Int firstEscaped = -1;
        for (Int i = from; i < toExcluded; i++)
            {
            Char c = s[i];
            if (c == '%' || c == '+' && !isPath)
                {
                firstEscaped = i;
                break;
                }
            }
        if (firstEscaped == -1)
            {
            return s[from..toExcluded - 1];
            }

        // Each encoded byte takes 3 characters (e.g. "%20")
        Int          decodedCapacity = (toExcluded - firstEscaped) / 3;
        Array<Char>  buf             = new Array(decodedCapacity);
        StringBuffer strBuf          = new StringBuffer();
        Int          bufIdx;

        s[from..firstEscaped - 1].appendTo(strBuf);

        for (Int i = firstEscaped; i < toExcluded; i++)
            {
            Char c = s[i];
            if (c != '%')
                {
                strBuf.append((c != '+' || isPath) ? c : " ");
                continue;
                }

            bufIdx = 0;

            do
                {
                if (i + 3 > toExcluded)
                    {
                    throw new IllegalArgument($"unterminated escape sequence at index {i} of: {s}");
                    }
                buf[bufIdx++] = decodeHex(s, i + 1);
                i += 3;
            } while (i < toExcluded && s[i] == '%');
            i--;

            new String(buf[0..bufIdx - 1]).appendTo(strBuf);
            }
        return strBuf.toString();
        }

    /**
     * Decode parameters from a String.
     *
     * @param s     the String to decode
     * @param from  the starting index in the String to start decoding from
     *
     * @return the decoded parameters
     */
    private Map<String, List<String>> decodeParams(String s, Int from)
        {
        Int len = s.size;
        if (from >= len) 
            {
            return Map:[];
            }
        if (s[from] == '?') 
            {
            from++;
            }
        Map<String, List<String>> params     = new ListMap<String, List<String>>();
        Int                       nameStart  = from;
        Int                       valueStart = -1;
        Int                       i;

        loop:
        for (i = from; i < len; i++) 
            {
            switch (s[i]) 
                {
                case '=':
                    if (nameStart == i) 
                        {
                        nameStart = i + 1;
                        } 
                    else if (valueStart < nameStart) 
                        {
                        valueStart = i + 1;
                        }
                    break;
                case ';':
                    if (semicolonIsNormalChar) 
                        {
                        break loop;
                        }
                    // fall-through
                    continue;
                case '&':
                    if (addParam(s, nameStart, valueStart, i, params)) 
                        {
                        maxParams--;
                        if (maxParams == 0)
                            {
                            return params;
                            }
                        }
                    nameStart = i + 1;
                    break;
                case '#':
                    break loop;
                }
            }
        addParam(s, nameStart, valueStart, i, params);
        return params;
    }

    /**
     * Add a parsed parameter to the parameter map.
     */
    private Boolean addParam(String s, Int nameStart, Int valueStart,
            Int valueEnd, Map<String, List<String>> params)
        {
        if (nameStart >= valueEnd)
            {
            return False;
            }
        if (valueStart <= nameStart)
            {
            valueStart = valueEnd + 1;
            }

        String name   = decodeComponent(s, nameStart, valueStart - 1, false);
        String value  = decodeComponent(s, valueStart, valueEnd, false);

        if (List<String> values := params.get(name))
            {
            values.add(value);
            }
        else
            {
            values = new Array(1);
            values.add(value);
            params.put(name, values);
            }
        return True;
    }

    /**
     * Get the index in the URI that is the end of the path.
     * The index is lazily intiailized.
     */
    private Int getPathEndIdx()
        {
        if (pathEndIdx == -1)
            {
            pathEndIdx = findPathEndIndex(uri);
            }
        return pathEndIdx;
        }

    /**
     * Calculate the index in the URI that is the end of the path.
     */
    private Int findPathEndIndex(String uri)
        {
        Int len = uri.size;
        for (Int i = 0; i < len; i++)
            {
            Char c = uri[i];
            if (c == '?' || c == '#')
                {
                return i;
                }
            }
        return len;
        }

    /**
     * Decode the hex characters at index and index + 1 in String s
     * into a Char. For example passing in the String "%20" and index 1
     * will return the space character.
     */
    private Char decodeHex(String s, Int index)
        {
        if (Int hi := s[index].asciiHexit())
            {
            if (Int lo := s[index + 1].asciiHexit())
                {
                return new Char(hi << 4 + lo);
                }
            }
        throw new IllegalArgument($"Not a hex byte at index {index} of String {s}");
        }
    }
