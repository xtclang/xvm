import io.IllegalUTF;

const Char
        implements Sequential
        implements Stringable
        default('\u0000')
    {
    // ----- constructors --------------------------------------------------------------------------

    construct(UInt32 codepoint)
        {
        if (codepoint > 0x10FFFF ||                     // unicode limit
            codepoint > 0xD7FF && codepoint < 0xE000)   // surrogate values are illegal
            {
            throw new IllegalUTF($"illegal code-point: {codepoint}");
            }
        }

    construct(Byte b)
        {
        construct Char(b.toUInt32());
        }

    construct(Int n)
        {
        construct Char(n.toUInt32());
        }


    // ----- properties ----------------------------------------------------------------------------

    UInt32 codepoint;


    // ----- Sequential ----------------------------------------------------------------------------

    @Override
    conditional Char prev()
        {
        if (codepoint > 0)
            {
            return true, new Char(codepoint - 1);
            }
        return false;
        }

    @Override
    conditional Char next()
        {
        if (codepoint < UInt32.maxvalue)
            {
            return true, new Char(codepoint + 1);
            }
        return false;
        }

    @Override
    Int stepsTo(Char that)
        {
        return that.codepoint.as(Int) - this.codepoint.as(Int);
        }


    // ----- operators ---------------------------------------------------------------------------

    @Op("+")
    Char add(Int n)
        {
        return new Char(this.toInt() + n);
        }

    @Op("-")
    Char sub(Int n)
        {
        return new Char(this.toInt() - n);
        }

    @Op("-")
    Int sub(Char ch)
        {
        return this.toInt() - ch.toInt();
        }

    @Op("*")
    String dup(Int n)
        {
        if (n == 0)
            {
            return "";
            }

        assert n > 0;
        StringBuffer buf = new StringBuffer(n);
        for (Int i = 0; i < n; ++i)
            {
            buf.add(this);
            }
        return buf.toString();
        }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * A direct conversion from the Char to a Byte is supported because of ASCII. An
     * out-of-range value will result in an exception.
     */
    Byte toByte()
        {
        assert codepoint <= 0x7F;
        return codepoint.toByte();
        }

    /**
     * A conversion to Byte[] results in a byte array with between 1-6 bytes containing
     * a UTF-8 formatted codepoint.
     * <p>
     * Note: The current version 9 of Unicode limits code points to 0x10FFFF, which
     * means that all UTF-8 encoding will use between 1-4 bytes.
     */
    immutable Byte[] utf()
        {
        Int    length = calcUtf8Length();
        Byte[] bytes  = new Byte[length];
        Int    actual = formatUtf8(bytes, 0);
        assert actual == length;
        return bytes.makeImmutable();
        }

    UInt32 toUInt32()
        {
        return codepoint;
        }

    Int toInt()
        {
        return codepoint.toInt();
        }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return 1;
        }

    @Override
    void appendTo(Appender<Char> appender)
        {
        appender.add(this);
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Determine if the character acts as a line terminator.
     *
     * @param ch  the character to test
     *
     * @return True iff the character acts as a line terminator
     */
    Boolean isLineTerminator()
        {
        if (codepoint <= 0x7F)
            {
            // this handles the following cases:
            //   U+000A  10  LF   Line Feed
            //   U+000B  11  VT   Vertical Tab
            //   U+000C  12  FF   Form Feed
            //   U+000D  13  CR   Carriage Return
            return codepoint >= 0x0A && codepoint <= 0x0D;
            }

        // this handles the following cases:
        //   U+0085    133   NEL    Next Line
        //   U+2028   8232   LS     Line Separator
        //   U+2029   8233   PS     Paragraph Separator
        return codepoint == 0x0085 || codepoint == 0x2028 || codepoint == 0x2029;
        }

    /**
     * @return the minimum number of bytes necessary to encode the character in UTF8 format
     */
    Int calcUtf8Length()
        {
        if (codepoint <= 0x7f)
            {
            return 1;
            }

        UInt32 codepoint = this.codepoint >> 11;
        Int    length    = 2;
        while (codepoint != 0)
            {
            codepoint >>= 5;
            ++length;
            }

        return length;
        }

    /**
     * Encode this character into the passed byte array using the UTF8 format.
     *
     * @param bytes  the byte array to write the UTF8 bytes into
     * @param of     the offset into the byte array to write the first byte
     *
     * @return the number of bytes used to encode the character in UTF8 format
     */
    Int formatUtf8(Byte[] bytes, Int of)
        {
        UInt32 ch = codepoint;
        if (ch <= 0x7F)
            {
            // ASCII - single byte 0xxxxxxx format
            bytes[of] = ch.toByte();
            return 1;
            }

        // otherwise the format is based on the number of significant bits:
        // bits  code-points             first byte  trailing  # trailing
        // ----  ----------------------- ----------  --------  ----------
        //  11   U+0080    - U+07FF      110xxxxx    10xxxxxx      1
        //  16   U+0800    - U+FFFF      1110xxxx    10xxxxxx      2
        //  21   U+10000   - U+1FFFFF    11110xxx    10xxxxxx      3
        //  26   U+200000  - U+3FFFFFF   111110xx    10xxxxxx      4
        //  31   U+4000000 - U+7FFFFFFF  1111110x    10xxxxxx      5
        Int trailing;
        switch (ch.leftmostBit)
            {
            case 0b00000000000000000000000010000000:
            case 0b00000000000000000000000100000000:
            case 0b00000000000000000000001000000000:
            case 0b00000000000000000000010000000000:
                bytes[of++] = 0b11000000 | ch >>> 6;
                trailing = 1;
                break;

            case 0b00000000000000000000100000000000:
            case 0b00000000000000000001000000000000:
            case 0b00000000000000000010000000000000:
            case 0b00000000000000000100000000000000:
            case 0b00000000000000001000000000000000:
                bytes[of++] = 0b11100000 | ch >>> 12;
                trailing = 2;
                break;

            case 0b00000000000000010000000000000000:
            case 0b00000000000000100000000000000000:
            case 0b00000000000001000000000000000000:
            case 0b00000000000010000000000000000000:
            case 0b00000000000100000000000000000000:
                bytes[of++] = 0b11110000 | ch >>> 18;
                trailing = 3;
                break;

            case 0b00000000001000000000000000000000:
            case 0b00000000010000000000000000000000:
            case 0b00000000100000000000000000000000:
            case 0b00000001000000000000000000000000:
            case 0b00000010000000000000000000000000:
                bytes[of++] = 0b11111000 | ch >>> 24;
                trailing = 4;
                break;

            case 0b00000100000000000000000000000000:
            case 0b00001000000000000000000000000000:
            case 0b00010000000000000000000000000000:
            case 0b00100000000000000000000000000000:
            case 0b01000000000000000000000000000000:
                bytes[of++] = 0b11111100 | ch >>> 30;
                trailing = 5;
                break;

            default:
                // TODO: ch.toHexString() would be a better output
                throw new IllegalUTF("illegal character: " + ch);
            }
        Int length = trailing + 1;

        // write out trailing bytes; each has the same "10xxxxxx" format with 6
        // bits of data
        while (trailing > 0)
            {
            bytes[of++] = 0b10_000000 | (ch >>> --trailing * 6 & 0b00_111111);
            }

        return length;
        }
    }
