import io.IllegalUTF;

/**
 * The LegacyDataInput mixin uses the same wire formats as defined by the Java `DataInputStream` for
 * bytes, characters, strings, and for the subset of the number types supported by that language.
 */
mixin JavaDataInput
        into BinaryInput
        implements DataInput
    {
    @Override
    Char readChar()
        {
        // Java's DataInputStream uses the UTF-16 format for individual characters; not that this
        // implementation will consume 32 bits if the first 16 bits are a surrogate codepoint (as
        // per the Unicode standard, since a surrogate codepoint by itself is an illegal character)
        return readUTF16Char(this);
        }

    @Override
    String readString()
        {
        // read the number of bytes (not the number of chars)
        Int length = readUInt16().toInt();
        if (length == 0)
            {
            return "";
            }

        // because of the design of the data format, the reader must keep track of the number of
        // bytes read (because the number of chars is unknown)
        DataInputStream in = this.is(DataInputStream)
                ? this
                : new @JavaDataInput ByteArrayInputStream(readBytes(length));

        StringBuffer sb = new StringBuffer(length);
        Int next = in.offset + length;
        while (in.offset < next)
            {
            UInt32 codepoint = readUTF8Codepoint(in);
            if (codepoint >= 0xD800)
                {
                if (codepoint <= 0xDBFF)
                    {
                    // codepoint is just the first ("high") of two values in a surrogate pair
                    UInt32 low = readUTF8Codepoint(in);
                    if (low >= 0xDC00 && low <= 0xDFFF)
                        {
                        codepoint = (codepoint & 0x3FF << 10) | (low & 0x3FF);
                        }
                    else
                        {
                        // this is only valid as the second of two values in a surrogate pair
                        throw new IllegalUTF("high surrogate ({codepoint}) encountered without an"
                                + $" immediately following low surrogate ({low})");
                        }
                    }
                else if (codepoint <= 0xDFFF)
                    {
                    // this is only valid as the second of two values in a surrogate pair
                    throw new IllegalUTF($"low surrogate ({codepoint}) encountered"
                            + " without preceding high surrogate");
                    }
                }
            sb.append(codepoint.toChar());
            }

        // if the stream isn't corrupted, then the offset will always end in the exact right spot
        if (in.offset != next)
            {
            throw new IllegalUTF();
            }

        return sb.toString();
        }
    }