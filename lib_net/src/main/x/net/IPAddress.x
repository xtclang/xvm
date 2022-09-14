/**
 * Represents an IPv4 or an IPv6 address.
 */
const IPAddress(Byte[] bytes)
        implements Destringable
    {
    // ----- predefined IPAddress values -----------------------------------------------------------

    /**
     * `INADDR_ANY` as an IP v4 address.
     */
    static IPAddress IPv4Any = new IPAddress([0,0,0,0]);

    /**
     * `INADDR_ANY` as an IP v6 address.
     */
    static IPAddress IPv6Any = new IPAddress([0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]);

    /**
     * The default IP v4 loop-back address.
     */
    static IPAddress IPv4Loopback = new IPAddress([127,0,0,1]);

    /**
     * The IP v6 loop-back address.
     */
    static IPAddress IPv6Loopback = new IPAddress([0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1]);


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an IPAddress by parsing a String containing a valid IP address in a textual format.
     *
     * @param text  the String containing the IP address
     */
    @Override
    construct(String text)
        {
        (Boolean success, this.bytes, String? error) = parse(text);
        assert:arg success as error ?: $"Illegal IP address: {text.quoted()}";
        }

    assert()
        {
        assert bytes.size == 4 || bytes.size == 16;
        }


    // ----- IPAddress methods and properties ------------------------------------------------------

    /**
     * The Internet Protocol version of the address; either 4 or 6.
     */
    Int version.get()
        {
        return v4 ? 4 : 6;
        }

    /**
     * True iff the address is an Internet Protocol version 4 address.
     */
    Boolean v4.get()
        {
        return bytes.size == 4;
        }

    /**
     * True iff the address is an Internet Protocol version 6 address.
     */
    Boolean v6.get()
        {
        return bytes.size == 16;
        }

    /**
     * True iff the address is a multicast address
     */
    Boolean multicast.get()
        {
        return v4
                ? bytes[0] & 0xF0 == 0xE0
                : bytes[0] == 0xFF;
        }

    /**
     * True iff the address is a loopback address.
     */
    Boolean loopback.get()
        {
        return v4
                ? bytes[0] == 127
                : this == IPv6Loopback;
        }

    /**
     * True iff the address is a link-local address.
     */
    Boolean linkLocal.get()
        {
        return v4
                ? bytes[0] == 169 && bytes[1] == 254
                : bytes[0] == 0xFE && bytes[1] & 0xC0 == 0x80;
        }

    /**
     * True iff the address is the "any" address.
     */
    Boolean any.get()
        {
        return v4
                ? this == IPv4Any
                : this == IPv6Any;
        }

    @Override
    String toString()
        {
        if (v4)
            {
            return $"{bytes[0]}.{bytes[1]}.{bytes[2]}.{bytes[3]}";
            }

        if (this == IPv6Any)
            {
            return "::0";
            }

        if (this == IPv6Loopback)
            {
            return "::1";
            }

        // scan for the biggest zero region (if any) to skip
        Int longestStart = -1;
        Int longestRun   = 0;
        Int currentStart = 0;
        Int currentRun   = 0;
        for (Int part : 0..7)
            {
            if (bytes[part*2] == 0 && bytes[part*2+1] == 0)
                {
                if (currentRun == 0)
                    {
                    currentStart = part;
                    }
                currentRun += 1;
                }
            else
                {
                if (currentRun >= 2 && currentRun > longestRun)
                    {
                    longestRun   = currentRun;
                    longestStart = currentStart;
                    }

                currentRun   = 0;
                currentStart = 0;
                }
            }
        if (currentRun >= 2 && currentRun > longestRun)
            {
            longestRun   = currentRun;
            longestStart = currentStart;
            }
        Int longestEnd = longestStart + longestRun;

        // build the IPv6 string; each cell represents 2 bytes, rendered as a hex value
        StringBuffer buf = new StringBuffer();
        Loop: for (Int part : 0..7)
            {
            if (part >= longestStart && part < longestEnd)
                {
                // this part is inside of the "zero run"
                if (part == longestStart)
                    {
                    buf.add(':').add(':');
                    }
                }
            else
                {
                if (!Loop.first && part != longestEnd)
                    {
                    buf.add(':');
                    }

                UInt16 n = bytes[part*2].toUInt16() << 8 | bytes[part*2+1];
                if (n == 0)
                    {
                    buf.add('0');
                    }
                else
                    {
                    Boolean nonZero = False;
                    for (Int shift = 12; shift >= 0; shift -= 4)
                        {
                        UInt16 hexit = n >>> shift & 0xF;
                        if (nonZero || hexit != 0)
                            {
                            nonZero = True;
                            buf.add(hexit.toHexit());
                            }
                        }
                    }
                }
            }

        return buf.toString();
        }


    // ----- String parsing helpers ----------------------------------------------------------------

    /**
     * Parse IPAddress information from a String, without relying on an exception to report failure.
     *
     * @param text  the String containing the IPAddress
     *
     * @return success  True iff the parsing succeeded and the IPAddress is lexically valid
     * @return bytes    the bytes of the IP address
     * @return error    if parsing failed for any reason, this may contain an explanation of the
     *                  parsing error
     */
    static (Boolean success, Byte[] bytes, String? error) parse(String text)
        {
        Int length = text.size;
        if (length == 0)
            {
            return False, [], "Empty IP address string";
            }

        return text[0] == '[' || text.indexOf(':')
                ? parseIPv6(text)
                : parseIPv4(text);
        }

    /**
     * Parse IPv4 address information from a String, without relying on an exception to report
     * failure.
     *
     * @param text  the String containing an IPv4 address
     *
     * @return success  True iff the parsing succeeded and the IPAddress is lexically valid
     * @return bytes    the bytes of the IP address
     * @return error    if parsing failed for any reason, this will contain the explanation of the
     *                  parsing error
     */
    static (Boolean success, Byte[] bytes, String? error) parseIPv4(String text)
        {
        Int length = text.size;
        if (length == 0)
            {
            return False, [], "Empty IPv4 address string";
            }

        Int    offset = 0;
        Int    remain = 0;
        Byte[] bytes  = new Byte[4];
        Cells: for (Int cell = 0; cell < 4; ++cell)
            {
            Int n = 0;
            Digits: while (offset < length)
                {
                Char ch = text[offset];
                if (Int digit := ch.asciiDigit())
                    {
                    n = n * 10 + digit;
                    if (n > UInt32.maxvalue)
                        {
                        return False, [], $"Part {cell+1} of the IPv4 address is out of range: {text.quoted()}";
                        }
                    }
                else if (ch == '.')
                    {
                    if (Digits.first)
                        {
                        return False, [], $"The IPv4 address contains a blank value: {text.quoted()}";
                        }

                    if (n > 0xFF)
                        {
                        return False, [], $"Part {cell+1} of the IPv4 address is out of range: {n}";
                        }

                    bytes[cell] = n.toUInt8();

                    // advance past the dot and process the next cell
                    ++offset;
                    continue Cells;
                    }
                else
                    {
                    return False, [], $"Part {cell+1} of the IPv4 address contains an invalid character: {ch.quoted()}";
                    }

                ++offset;
                }

            // the last value is in "n", but that part is allowed to be spread over multiple "parts"
            // of the IPv4 address; for example, "127.257" is 127.0.1.1; yeah, you really can't make
            // this silliness up
            if (n > 0)
                {
                remain = n;
                }

            // for part 0 = remain >> 24, part 1 = remain >> 16 & 0xFF, etc.
            Int shift = (3 - cell) * 8;
            n = remain >> shift;
            if (n > 0xFF)
                {
                return False, [], $"Part {cell+1} of the IPv4 address is out of range: {n} ({text.quoted()})";
                }
            bytes[cell] = n.toUInt8();
            remain     &= ~(n<<shift);
            }

        if (offset < length)
            {
            return False, [], $"More than 4 parts in the IPv4 address: {text.quoted()}";
            }
        else if (text[length-1] == '.')
            {
            return False, [], $"The IPv4 address ends with a '.': {text.quoted()}";
            }

        return True, bytes, Null;
        }

    /**
     * Parse IPv6 address information from a String, without relying on an exception to report
     * failure.
     *
     * @param text  the String containing an IPv6 address
     *
     * @return success  True iff the parsing succeeded and the IPAddress is lexically valid
     * @return bytes    the bytes of the IP address
     * @return error    if parsing failed for any reason, this will contain the explanation of the
     *                  parsing error
     *
     * @see https://www.ietf.org/rfc/rfc2732.txt
     */
    static (Boolean success, Byte[] bytes, String? error) parseIPv6(String text)
        {
        Int length = text.size;
        if (length == 0)
            {
            return False, [], "Empty IPv6 address string";
            }

        Int offset = 0;

        // an IPv6 address can be enclosed in square brackets, because URIs; see
        // https://www.ietf.org/rfc/rfc2732.txt
        if (text[offset] == '[' && text[length-1] == ']')
            {
            offset += 1; // chop off '['
            length -= 1; // chop off ']'
            if (offset >= length)
                {
                return False, [], $"Empty IPv6 address string: {text.quoted()}";
                }
            }

        Boolean skipped = False;
        Byte[]  bytes   = new Byte[16];
        Cells: for (Int cell = 0; cell < 8; ++cell)
            {
            Int n = 0;
            Hexits: while (offset < length)
                {
                Char ch = text[offset];
                if (Int hexit := ch.asciiHexit())
                    {
                    n = n * 0x10 + hexit;
                    if (n > 0xFFFF)
                        {
                        return False, [], $"Part {cell+1} of the IPv6 address is out of range: {text.quoted()}";
                        }
                    }
                else if (ch == ':')
                    {
                    bytes[cell*2  ] = (n >> 8 ).toUInt8();
                    bytes[cell*2+1] = (n & 0xF).toUInt8();
                    ++offset;

                    // check for "::"
                    if (offset < length && text[offset] == ':')
                        {
                        if (skipped)
                            {
                            return False, [], $|The IPv6 address contains more than one \"::\"\
                                               | skip construct: {text.quoted()}
                                              ;
                            }

                        // it's a "::" skip; skip the second colon
                        ++offset;

                        // calculate the number of remaining cells
                        Int colons = text.count(':');
                        if (colons > 6)
                            {
                            return False, [], $|Due to the \"::\" skip construct, the IPv6 address\
                                               | implicitly contains more than 8 parts: {text.quoted()}
                                              ;
                            }

                        Int remain = colons - cell - 1;
                        if (remain < 1)
                            {
                            return False, [], $|The IPv6 address contains an illegal \"::\"\
                                               | skip construct that skips past the end of the\
                                               | legal address range: {text.quoted()}
                                              ;
                            }

                        Int skipping = 7 - remain - cell;
                        if (skipping < 2)
                            {
                            return False, [], $|The IPv6 address contains an illegal \"::\"\
                                               | skip construct that skips fewer than two parts\
                                               | of the address: {text.quoted()}
                                              ;
                            }

                        cell   += skipping;
                        skipped = True;
                        continue Cells;
                        }

                    if (Hexits.first)
                        {
                        return False, [], $"The IPv6 address contains a blank value: {text.quoted()}";
                        }

                    if (cell == 7)
                        {
                        return False, [], $"The IPv6 address contains more than 8 parts: {text.quoted()}";
                        }

                    continue Cells;
                    }
                else
                    {
                    return False, [], $|Part {cell+1} of the IPv6 address contains an invalid\
                                       | character: {ch.quoted()}
                                      ;
                    }

                ++offset;
                }

            if (cell == 7)
                {
                bytes[14] = (n >> 8 ).toUInt8();
                bytes[15] = (n & 0xF).toUInt8();
                }
            else
                {
                return False, [], $"The IPv6 address contains only {cell+1} parts: {text.quoted()}";
                }
            }

        if (offset < length)
            {
            return False, [], $"The IPv6 address contains more than 8 parts: {text.quoted()}";
            }

        return True, bytes, Null;
        }
    }