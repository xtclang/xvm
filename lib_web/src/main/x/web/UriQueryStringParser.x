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
    construct(String uri, Boolean hasPath = True, Int maxParams = 1024,
              Boolean semicolonIsNormalChar = False)
        {
        this.uri                   = uri;
        this.maxParams             = maxParams;
        this.semicolonIsNormalChar = semicolonIsNormalChar;
        this.pathEndIdx            = hasPath ? findPathEndIndex(uri) : 0;
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
     * REVIEW JK: why not make it public and @Lazy?
     */
    private String? path;

    /**
     * The decoded parameters.
     * REVIEW JK: why not make it public and @Lazy?
     */
    private Map<String, List<String>>? params;

    /**
     * Returns the decoded path string of the URI.
     */
    String getPath()
        {
        String? path = this.path;
        if (path == Null)
            {
            path = decodeComponent(uri, 0, pathEndIdx, True);
            }
        return path;
        }

    /**
     * Returns the decoded key-value parameter pairs of the URI.
     */
    public Map<String, List<String>> getParameters()
        {
        Map<String, List<String>>? params = this.params;
        if (params == Null)
            {
            params = decodeParams(uri, pathEndIdx);
            }
        return params;
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
            return s[from..toExcluded);
            }

        // Each encoded byte takes 3 characters (e.g. "%20")
        Int          decodedCapacity = (toExcluded - firstEscaped) / 3;
        Char[]       buf             = new Array(decodedCapacity);
        StringBuffer strBuf          = new StringBuffer();
        Int          bufIdx;

        s[from..firstEscaped).appendTo(strBuf);

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

            strBuf.addAll(buf[0..bufIdx));
            }
        return strBuf.toString();
        }

    /**
     * Decode parameters from a String.
     *
     * @param query the query string to decode
     * @param from  the starting index in the String to start decoding from
     *
     * @return the decoded parameters
     */
    private Map<String, List<String>> decodeParams(String query, Int from)
        {
        if (from >= query.size)
            {
            return Map:[];
            }

        Map<String, String>       rawParams   = query.splitMap();
        Map<String, List<String>> dedupParams = new ListMap();
        for ((String key, String value) : rawParams)
            {
            dedupParams.process(key, e ->
                {
                if (!e.exists)
                    {
                    e.value = new String[];
                    }
                e.value += value;
                });
            }
        return dedupParams;
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

        String name  = decodeComponent(s, nameStart, valueStart - 1, False);
        String value = decodeComponent(s, valueStart, valueEnd, False);

        if (List<String> values := params.get(name))
            {
            values.add(value);
            }
        else
            {
            params.put(name, new Array(Mutable, [value]));
            }
        return True;
    }

    /**
     * Calculate the index in the URI that is the end of the path.
     */
    private static Int findPathEndIndex(String uri)
        {
        Loop: for (Char c : uri)
            {
            if (c == '?' || c == '#')
                {
                return Loop.count;
                }
            }
        return uri.size;
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