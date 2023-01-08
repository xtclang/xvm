/**
 * The BinaryInput interface represents an abstract input stream of binary data.
 *
 * Note that the inclusion of the Closeable interface is for the benefit of the stream
 * implementation, not the consumer of the stream. In other words, the stream is not required to
 * strictly reject calls after a call to `close()`; the purpose for the `close()` method is to allow
 * the stream to release any underlying resources, including those managed by an operating system,
 * and any subsequent calls may _or may not_ fail as a result.
 */
interface BinaryInput
        extends Closeable
    {
    /**
     * @return  a value of type Byte read from the stream
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws EndOfFile    if the end of the stream has been reached
     */
    Byte readByte();

    /**
     * Read bytes into the provided array.
     *
     * @param  bytes  the byte array to read into
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws EndOfFile    if the end of the stream has been reached
     */
    void readBytes(Byte[] bytes)
        {
        readBytes(bytes, 0, bytes.size);
        }

    /**
     * Read the specified number of bytes into the provided array.
     *
     * @param  bytes   the byte array to read into
     * @param  offset  the offset into the array to store the first byte read
     * @param  count   the number of bytes to read
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws EndOfFile    if the end of the stream has been reached
     */
    void readBytes(Byte[] bytes, Int offset, Int count)
        {
        assert:arg offset >= 0 && count >= 0;

        Int last = offset + count;
        assert last <= bytes.size;
        while (offset < last)
            {
            bytes[offset++] = readByte();
            }
        }

    /**
     * Read the specified number of bytes, returning those bytes as an array.
     *
     * @param count  the number of bytes to read
     *
     * @return an array of the specified size, containing the bytes read from the stream
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws EndOfFile    if the end of the stream has been reached
     */
    Byte[] readBytes(Int count)
        {
        assert:arg count >= 0;

        Byte[] bytes = new Byte[count];
        readBytes(bytes);
        return bytes;
        }

    /**
     * Pipe contents from this stream to the specified stream.
     *
     * @param out  the OutputStream to pipe to
     * @param max  the number of bytes to pipe
     *
     * @throws IOException  represents the general category of input/output exceptions
     * @throws EndOfFile    if the end of the stream has been reached
     */
    void pipeTo(BinaryOutput out, Int count)
        {
        if (count > 0)
            {
            Int    bufSize  = count.notGreaterThan(8192);
            Byte[] buf      = new Byte[bufSize];
            while (count > 0)
                {
                Int copySize = bufSize.notGreaterThan(count);
                readBytes(buf, 0, copySize);
                out.writeBytes(buf, 0, copySize);
                count -= copySize;
                }
            }
        else
            {
            // the only legal value for count is zero at this point, but the assertion is likely
            // to print a detailed message of the assertion condition, so it needs to be obvious
            assert:arg count >= 0;
            }
        }

    @Override
    void close(Exception? cause = Null)
        {
        }


    // ----- helper functions ----------------------------------------------------------------------

    /**
     * Read a sequence of bytes from the stream corresponding to a single Unicode character that is
     * encoded in the UTF-8 format. If the character is in the surrogate range, the second codepoint
     * in the pair will also be read, and joined with the first.
     *
     * @param in  the BinaryInput stream
     *
     * @return the character read from the stream in UTF-8 format
     *
     * @throws IllegalUTF if there is a flaw in the UTF-8 encoding or in the resulting codepoint
     */
    static Char readUTF8Char(BinaryInput in)
        {
        private UInt32 trailing(BinaryInput in)
            {
            Byte b = in.readByte();
            if (b & 0b11000000 != 0b10000000)
                {
                throw new IllegalUTF("trailing unicode byte does not match 10xxxxxx");
                }
            return (b & 0b00111111).toUInt32();
            }

        // otherwise the format is based on the number of high-order 1-bits:
        // #1s first byte  trailing  # trailing  bits  code-points
        // --- ----------  --------  ----------  ----  -----------------------
        //  0  0xxxxxxx    n/a           0         7   U+0000    - U+007F     (ASCII)
        //  2  110xxxxx    10xxxxxx      1        11   U+0080    - U+07FF
        //  3  1110xxxx    10xxxxxx      2        16   U+0800    - U+FFFF
        //  4  11110xxx    10xxxxxx      3        21   U+10000   - U+1FFFFF
        //  5  111110xx    10xxxxxx      4        26   U+200000  - U+3FFFFFF
        //  6  1111110x    10xxxxxx      5        31   U+4000000 - U+7FFFFFFF
        Byte   b = in.readByte();
        UInt32 n = b.toUInt32();
        switch ((~b).leftmostBit)
            {
            case 0b10000000:
                return n.toChar();

            case 0b00100000:
                return (n & 0b00011111 << 6 | trailing(in)).toChar();

            case 0b00010000:
                n = n & 0b00001111 << 6
                    | trailing(in) << 6
                    | trailing(in);
                break;

            case 0b00001000:
                n = n & 0b00000111 << 6
                    | trailing(in) << 6
                    | trailing(in) << 6
                    | trailing(in);
                break;

            case 0b00000100:
                n = n & 0b00000011 << 6
                    | trailing(in) << 6
                    | trailing(in) << 6
                    | trailing(in) << 6
                    | trailing(in);
                break;

            case 0b00000010:
                n = n & 0b00000001 << 6
                    | trailing(in) << 6
                    | trailing(in) << 6
                    | trailing(in) << 6
                    | trailing(in) << 6
                    | trailing(in);
                break;

            default:
                throw new IllegalUTF($"initial byte: {b}");
            }

        Char ch = n.toChar();
        return ch.requiresTrailingSurrogate()
                ? ch.addTrailingSurrogate(readUTF8Char(in))
                : ch;
        }

    /**
     * Read a sequence of either 2 or 4 bytes from the stream corresponding to a single Unicode
     * character that is encoded in the UTF-16BE format.
     *
     * @param in  the BinaryInput stream
     *
     * @return the character read from the stream in UTF-16BE format
     *
     * @throws IllegalUTF if there is a flaw in the UTF-16 encoding or in the resulting codepoint
     */
    static Char readUTF16BEChar(BinaryInput in)
        {
        Char ch = readUInt16BE(in).toChar();
        return ch.requiresTrailingSurrogate()
                ? ch.addTrailingSurrogate(readUInt16BE(in).toChar())
                : ch;
        }

    /**
     * Read a sequence of either 2 or 4 bytes from the stream corresponding to a single Unicode
     * character that is encoded in the UTF-16LE format.
     *
     * @param in  the BinaryInput stream
     *
     * @return the character read from the stream in UTF-16LE format
     *
     * @throws IllegalUTF if there is a flaw in the UTF-16 encoding or in the resulting codepoint
     */
    static Char readUTF16LEChar(BinaryInput in)
        {
        Char ch = readUInt16LE(in).toChar();
        return ch.requiresTrailingSurrogate()
                ? ch.addTrailingSurrogate(readUInt16LE(in).toChar())
                : ch;
        }

    /**
     * Read a sequence of bytes from the stream corresponding to a single Unicode character that is
     * encoded in the UTF-32BE format.
     *
     * @param in  the BinaryInput stream
     *
     * @return the character read from the stream in UTF-32BE format
     *
     * @throws IllegalUTF if there is a flaw in the UTF-32 encoding or in the resulting codepoint
     */
    static Char readUTF32BEChar(BinaryInput in)
        {
        Char ch = readUInt32BE(in).toChar();
        return ch.requiresTrailingSurrogate()
                ? ch.addTrailingSurrogate(readUInt32BE(in).toChar())
                : ch;
        }

    /**
     * Read a sequence of bytes from the stream corresponding to a single Unicode character that is
     * encoded in the UTF-32LE format.
     *
     * @param in  the BinaryInput stream
     *
     * @return the character read from the stream in UTF-32LE format
     *
     * @throws IllegalUTF if there is a flaw in the UTF-32 encoding or in the resulting codepoint
     */
    static Char readUTF32LEChar(BinaryInput in)
        {
        Char ch = readUInt32LE(in).toChar();
        return ch.requiresTrailingSurrogate()
                ? ch.addTrailingSurrogate(readUInt32LE(in).toChar())
                : ch;
        }

    /**
     * Read a sequence of bytes from the stream corresponding to a big-endian encoded, 16-bit
     * unsigned integer value.
     *
     * @param in  the BinaryInput stream
     *
     * @return the UInt16 value read from the stream
     */
    static UInt16 readUInt16BE(BinaryInput in)
        {
        return in.readByte().toUInt16() << 8
             | in.readByte().toUInt16();
        }

    /**
     * Read a sequence of bytes from the stream corresponding to a little-endian encoded, 16-bit
     * unsigned integer value.
     *
     * @param in  the BinaryInput stream
     *
     * @return the UInt16 value read from the stream
     */
    static UInt16 readUInt16LE(BinaryInput in)
        {
        return in.readByte().toUInt16()
            | (in.readByte().toUInt16() <<  8);
        }

    /**
     * Read a sequence of bytes from the stream corresponding to a big-endian encoded, 32-bit
     * unsigned integer value.
     *
     * @param in  the BinaryInput stream
     *
     * @return the UInt32 value read from the stream
     */
    static UInt32 readUInt32BE(BinaryInput in)
        {
        return in.readByte().toUInt32() << 8
             | in.readByte().toUInt32() << 8
             | in.readByte().toUInt32() << 8
             | in.readByte().toUInt32();
        }

    /**
     * Read a sequence of bytes from the stream corresponding to a little-endian encoded, 32-bit
     * unsigned integer value.
     *
     * @param in  the BinaryInput stream
     *
     * @return the UInt32 value read from the stream
     */
    static UInt32 readUInt32LE(BinaryInput in)
        {
        return in.readByte().toUInt32()
            | (in.readByte().toUInt32() <<  8)
            | (in.readByte().toUInt32() << 16)
            | (in.readByte().toUInt32() << 24);
        }

    /**
     * Read a sequence of bytes from the stream corresponding to a null-terminated ASCII string.
     *
     * @param in            the BinaryInput stream
     * @param convNonAscii  an optional function that is used to convert non-ASCII values to Unicode
     *                      characters
     *
     * @return the String read from the stream (but not including the closing null terminator)
     */
    static String readAsciiStringZ(BinaryInput in, function Char(Byte) convNonAscii = _ -> '?')
        {
        StringBuffer buf = new StringBuffer();
        Byte b = in.readByte();
        while (b != 0)
            {
            buf.add(b <= 0x7F ? b.toChar() : convNonAscii(b));
            b = in.readByte();
            }
        return buf.toString();
        }
    }