import io.IllegalUTF;

/**
 * The LegacyDataOutput mixin uses the same wire formats as defined by the Java `DataOutputStream`
 * for bytes, characters, strings, and for the subset of the number types supported by that
 * language.
 */
mixin JavaDataOutput
        into BinaryOutput
        implements DataOutput
    {
    @Override
    void writeChar(Char value)
        {
        // Java's DataOutputStream uses the UTF-16 format for individual characters; note that this
        // will normally write 2 bytes, but will write 4 bytes for codepoints over 0xFFFF, as a
        // Unicode surrogate pair (as prescribed by the UTF-16 standard); Java does not handle this
        // scenario by default
        writeUTF16Char(this, value);
        }

    @Override
    void writeString(String value)
        {
        // the format is a 2-byte unsigned integer specifying the number of bytes, followed by that
        // many bytes worth of UTF-8 formatted UTF-16 values (i.e. not Unicode character codepoints,
        // but a UTF-8 re-encoding of a UTF-16 encoding of Unicode character codepoints)
        Int length = 0;
        for (Char ch : value)
            {
            UInt32 codepoint = ch.codepoint;
            if (codepoint <= 0x7F)
                {
                // there is a strange case of the 0 codepoint in that it is encoded in the 2-byte
                // format (to avoid any 0 bytes in the UTF8 portion of the stream)
                length += codepoint == 0 ? 2 : 1;
                }
            else if (codepoint <= 0x7FF)
                {
                length += 2;
                }
            else if (codepoint <= 0xFFFF)
                {
                length += 3;
                }
            else
                {
                // this codepoint will be split into a pair of codepoints called "surrogates", each
                // of which will be in the range 0xD800-0xDFFF, and each of which will be
                // individually encoded using the UTF-8 encoding
                length += 6;
                }
            }
        if (length > UInt16.maxvalue)
            {
            throw new IllegalUTF($"length ({length}) exceeds the Java limit ({UInt16.maxvalue}})");
            }

        writeUInt16(length.toUInt16());
        if (length == value.size)
            {
            // simple: all characters are in the ASCII range
            for (Char ch : value)
                {
                writeByte(ch.toByte());
                }
            }
        else
            {
            for (Char ch : value)
                {
                UInt32 codepoint = ch.codepoint;
                if (codepoint == 0)
                    {
                    // the 0 value is encoded using the 2-byte format (avoiding 0x00 in the UTF8);
                    // for a 2-byte format, the first byte is 110xxxxx and the second is 10xxxxxx
                    writeUInt16(0b11000000_10000000);
                    }
                else if (codepoint <= 0xFFFF)
                    {
                    // Java encodes all other Unicode "BMP" values as UTF8
                    writeUTF8Codepoint(this, codepoint);
                    }
                else
                    {
                    // split the codepoint into surrogates using UTF16, then write each using UTF8
                    codepoint -= 0x10000;
                    writeUTF8Codepoint(this, codepoint >>> 10 & 0x3FF | 0xD800);
                    writeUTF8Codepoint(this, codepoint        & 0x3FF | 0xDC00);
                    }
                }
            }
        }
    }