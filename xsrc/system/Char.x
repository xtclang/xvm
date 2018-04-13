const Char
        implements Sequential
    {
    construct Char(UInt32 codepoint)
        {
        assert:always codepoint <= 0x10FFFF;
        this.codepoint = codepoint;
        }

    construct Char(Byte b)
        {
        construct Char(b.to<UInt32>());
        }

    construct Char(Int n)
        {
        construct Char(n.to<UInt32>());
        }

    UInt32 codepoint;

    /**
     * A direct conversion from the Char to a Byte is supported because of ASCII. An
     * out-of-range value will result in an exception.
     */
    Byte to<Byte>()
        {
        assert:always codepoint <= 0x7f;
        return codepoint.to<Byte>();
        }

    /**
     * A conversion to Byte[] results in a byte array with between 1-6 bytes containing
     * a UTF-8 formatted codepoint.
     * <p>
     * Note: The current version 9 of Unicode limits code points to 0x10FFFF, which
     * means that all UTF-8 encoding will use between 1-4 bytes.
     */
    Byte[] to<Byte[]>()
        {
        Int    length = calcUtf8Length();
        Byte[] bytes  = new Byte[length];
        Int    actual = formatUtf8(bytes, 0);
        assert:always actual == length;
        return bytes;
        }

    UInt32 to<UInt32>()
        {
        return codepoint;
        }

    Int to<Int>()
        {
        return codepoint.to<Int>();
        }

    @Auto String to<String>()
        {
        TODO
        }

    @Op("*") String dup(Int n)
        {
        TODO
        }

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

    Int formatUtf8(Byte[] bytes, Int of)
        {
        if (codepoint <= 0x7F)
            {
            // ASCII - single byte 0xxxxxxx format
            bytes[of] = codepoint.to<Byte>();
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
        Int cTrail;
        switch (codepoint.highestBit())             // REVIEW method or @RO property?
            {
            case 0b00000000000000000000000010000000:
            case 0b00000000000000000000000100000000:
            case 0b00000000000000000000001000000000:
            case 0b00000000000000000000010000000000:
                out.write(0b11000000 | ch >>> 6);
                cTrail = 1;
                break;

            case 0b00000000000000000000100000000000:
            case 0b00000000000000000001000000000000:
            case 0b00000000000000000010000000000000:
            case 0b00000000000000000100000000000000:
            case 0b00000000000000001000000000000000:
                out.write(0b11100000 | ch >>> 12);
                cTrail = 2;
                break;
            case 0b00000000000000010000000000000000:
            case 0b00000000000000100000000000000000:
            case 0b00000000000001000000000000000000:
            case 0b00000000000010000000000000000000:
            case 0b00000000000100000000000000000000:
                out.write(0b11110000 | ch >>> 18);
                cTrail = 3;
                break;
            case 0b00000000001000000000000000000000:
            case 0b00000000010000000000000000000000:
            case 0b00000000100000000000000000000000:
            case 0b00000001000000000000000000000000:
            case 0b00000010000000000000000000000000:
                out.write(0b11111000 | ch >>> 24);
                cTrail = 4;
                break;
            case 0b00000100000000000000000000000000:
            case 0b00001000000000000000000000000000:
            case 0b00010000000000000000000000000000:
            case 0b00100000000000000000000000000000:
            case 0b01000000000000000000000000000000:
                out.write(0b11111100 | ch >>> 30);
                cTrail = 5;
                break;

            default:
                throw new UTFDataFormatException("illegal character: " + intToHexString(ch));
            }

        // write out trailing bytes; each has the same "10xxxxxx" format with 6
        // bits of data
        while (cTrail > 0)
            {
            out.write(0b10_000000 | (ch >>> --cTrail * 6 & 0b00_111111));
            }
        }
    }
