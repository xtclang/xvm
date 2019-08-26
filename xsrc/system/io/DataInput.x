import io.IllegalUTF;

/**
 * The DataInput interface represents a stream of values of various fundamental Ecstasy types. It
 * provides default implementations for some methods, but does not prescribe an underlying data
 * format. For example, integers could be fixed length or compressed, and characters could be 4-byte
 * Unicode values or encoded as UTF-8, etc.
 */
interface DataInput
    {
    /**
     * @return  a value of type Boolean read from the stream
     */
    Boolean readBoolean()
        {
        return readByte() != 0;
        }

    /**
     * @return  a value of type Char read from the stream
     */
    Char readChar();

    /**
     * @return  a value of type String read from the stream
     */
    String readString()
        {
        Int length = readInt();
        if (length == 0)
            {
            return "";
            }

        assert length > 0;
        Char[] chars = new Char[length];
        for (Int i : 0..length)
            {
            chars[i] = readChar();
            }
        return new String(chars);
        }

    /**
     * @return  a value of type X read from the stream
     */
    <EnumType extends Enum> EnumType readEnum(Enumeration<EnumType> enumeration)
        {
        Int n = readInt();
        assert n >= 0 && n < enumeration.count;
        return enumeration.values[n];
        }

    /**
     * @return  a value of type Int8 read from the stream
     */
    Int8 readInt8()
        {
        // the default implementation assumes that an 8-bit signed integer is present in the stream
        // as a single byte of data in the twos-complement format
        return new Int8(readByte().toBitArray());
        }

    /**
     * @return  a value of type Byte read from the stream
     */
    Byte readByte();

    /**
     * Read the specified number of bytes into the provided array.
     *
     * @param  bytes   the byte array to read into
     * @param  offset  the offset into the array to store the first byte read
     * @param  count   the number of bytes to read
     */
    void readBytes(Byte[] bytes, Int offset, Int count)
        {
        assert offset >= 0 && count >= 0;

        Int last = offset + count;
        while (offset < last)
            {
            bytes[offset++] = readByte();
            }
        }

    /**
     * @return  a value of type Int16 read from the stream
     */
    Int16 readInt16();

    /**
     * @return  a value of type UInt16 read from the stream
     */
    UInt16 readUInt16();

    /**
     * @return  a value of type Int32 read from the stream
     */
    Int32 readInt32();

    /**
     * @return  a value of type UInt32 read from the stream
     */
    UInt32 readUInt32();

    /**
     * @return  a value of type Int read from the stream
     */
    Int readInt();

    /**
     * @return  a value of type UInt64 read from the stream
     */
    UInt64 readUInt();

    /**
     * @return  a value of type Int128 read from the stream
     */
    Int128 readInt128();

    /**
     * @return  a value of type UInt128 read from the stream
     */
    UInt128 readUInt128();

    /**
     * @return  a value of type VarInt read from the stream
     */
    VarInt readVarInt();

    /**
     * @return  a value of type VarInt read from the stream
     */
    VarUInt readVarUInt();

    /**
     * @return  a value of type Dec64 read from the stream
     */
    Dec32 readDec32();

    /**
     * @return  a value of type Dec64 read from the stream
     */
    Dec64 readDec64();

    /**
     * @return  a value of type Dec128 read from the stream
     */
    Dec128 readDec128();

    /**
     * @return  a value of type VarDec read from the stream
     */
    VarDec readVarDec();

    /**
     * @return  a value of type Float16 read from the stream
     */
    Float16 readFloat16();

    /**
     * @return  a value of type BFloat16 read from the stream
     */
    BFloat16 readBFloat16();

    /**
     * @return  a value of type Float32 read from the stream
     */
    Float32 readFloat32();

    /**
     * @return  a value of type Float64 read from the stream
     */
    Float64 readFloat64();

    /**
     * @return  a value of type Float128 read from the stream
     */
    Float128 readFloat128();

    /**
     * @return  a value of type VarFloat read from the stream
     */
    VarFloat readVarFloat();

    /**
     * @return  a value of type Date read from the stream
     */
    Date readDate();

    /**
     * @return  a value of type Time read from the stream
     */
    Time readTime();

    /**
     * @return  a value of type DateTime read from the stream
     */
    DateTime readDateTime();

    /**
     * @return  a value of type Duration read from the stream
     */
    Duration readDuration();


    // ----- helper functions ----------------------------------------------------------------------

    /**
     * Read an integer from the passed stream, which is encoded in the packed integer format.
     *
     * The packed integer format represents a signed, 2's-complement integer of 1-512 bytes in size.
     * The storage format is compressed as much as possible. There are four storage formats:
     *
     * * **Tiny**: For a value in the range -64..63 (7 bits), the value can be encoded in one byte.
     *   The least significant 7 bits of the value are shifted left by 1 bit, and the 0x1 bit is set
     *   to 1. When reading in a packed integer, if bit 0x1 of the first byte is 1, then it's Tiny.
     *
     * * **Small**: For a value in the range -4096..4095 (13 bits), the value can be encoded in two
     *   bytes. The first byte contains the value 0x2 in the least significant 3 bits (010), and
     *   bits 8-12 of the integer in bits 3-7; the second byte contains bits 0-7 of the integer.
     *
     * * **Medium**: For a value in the range -1048576..1048575 (21 bits), the value can be encoded
     *   in three bytes. The first byte contains the value 0x6 in the least significant 3 bits
     *   (110), and bits 16-20 of the integer in bits 3-7; the second byte contains bits 8-15 of the
     *   integer; the third byte contains bits 0-7 of the integer.
     *
     * * **Large**: For a value in the range -(2^511)..2^511-1 (4096 bits), a value with `s`
     *   significant bits can be encoded in no less than `1+max(1,(s+7)/8)}` bytes; let `b` be
     *   the selected encoding length, in bytes. The first byte contains the value 0x0 in the least
     *   significant 2 bits (00), and the least 6 significant bits of `(b-2)` in bits 2-7. The
     *   following `(b-1)` bytes contain the least significant `(b-1)*8` bits of the integer.
     *
     * TODO the following doc should move to (and only be present on) the write function
     * To maximize density and minimize pipeline stalls, this implementation uses the smallest
     * possible encoding for each value. Since an 8 significant-bit value can be encoded in two
     * bytes using either a Small or Large encoding, we choose Large to eliminate the potential for
     * a (conditional-induced) pipeline stall. Since a 14..16 significant-bit value can be encoded
     * in three bytes using either a Medium or Large encoding, we choose Large for the same reason.
     *
     *     significant bits  encoding
     *     ----------------  --------
     *           <= 7        Tiny
     *            8          Large
     *           9-13        Small
     *          14-16        Large
     *          17-21        Medium
     *          >= 22        Large
     *
     * @param in  the stream to read from
     *
     * @return the Int value from the stream
     *
     * @throws OutOfBounds  if the packed integer value can not fit into an Int
     */
    static Int readPackedInt(DataInput in)
        {
        // use a signed byte to get auto sign-extension when converting to an int
        Int8 b = in.readInt8();

        // Tiny format: the first bit of the first byte is used to indicate a single byte format,
        // in which the entire value is contained in the 7 MSBs
        if (b & 0x01 != 0)
            {
            return (b >> 1).toInt();
            }

        // Small and Medium formats are indicated by the second bit (and differentiated by the third
        // bit). Small format: bits 3..7 of the first byte are bits 8..12 of the result, and the
        // next byte provides bits 0..7 of the result. Medium format: bits 3..7 of the first byte
        // are bits 16..20 of the result, and the next byte provides bits 8..15 of the result, and
        // the next byte provides bits 0..7 of the result
        if (b & 0x02 != 0)
            {
            Int n = (b >> 3).toInt() << 8 | in.readByte().toInt();

            // the third bit is used to indicate Medium format (a second trailing byte)
            if (b & 0x04 != 0)
                {
                n = n << 8 | in.readByte().toInt();
                }
            return n;
            }

        // Large format: the first two bits of the first byte are 0, so bits 2..7 of the
        // first byte are the trailing number of bytes minus 1
        Int size = 1 + (b >>> 2).toInt();
        assert:bounds size <= 8;

        Int n = in.readInt8().toInt();
        while (--size > 0)
            {
            n = n << 8 | in.readByte().toInt();
            }
        return n;
        }

    // TODO GG when this was mistakenly marked as returning Int, it threw IllegalArgument from TypeConstant.adoptParameters()
    static VarInt readPackedVarInt(DataInput in)
        {
        // use a signed byte to get auto sign-extension when converting to an int
        Int8 b = in.readInt8();

        // Tiny format: the first bit of the first byte is used to indicate a single byte format,
        // in which the entire value is contained in the 7 MSBs
        if (b & 0x01 != 0)
            {
            return (b >> 1).toVarInt();
            }

        // Small and Medium formats are indicated by the second bit (and differentiated by the third
        // bit). Small format: bits 3..7 of the first byte are bits 8..12 of the result, and the
        // next byte provides bits 0..7 of the result. Medium format: bits 3..7 of the first byte
        // are bits 16..20 of the result, and the next byte provides bits 8..15 of the result, and
        // the next byte provides bits 0..7 of the result
        if (b & 0x02 != 0)
            {
            Int n = (b >> 3).toInt() << 8 | in.readByte().toInt();

            // the third bit is used to indicate Medium format (a second trailing byte)
            if (b & 0x04 != 0)
                {
                n = n << 8 | in.readByte().toInt();
                }
            return n.toVarInt();
            }

        // Large format: the first two bits of the first byte are 0, so bits 2..7 of the
        // first byte are the trailing number of bytes minus 1
        Int size = 1 + (b >>> 2).toInt();
        assert:bounds size <= 8;

        if (size <= 8)
            {
            Int n = in.readByte().toInt();
            while (--size > 0)
                {
                n = n << 8 | in.readInt8().toInt();
                }
            return n.toVarInt();
            }

        Byte[] bytes = new Byte[size];
        in.readBytes(bytes, 0, size);
        return new VarInt(bytes);
        }

    /**
     * Read a sequence of bytes from the stream corresponding to a single Unicode character that is
     * encoded in the UTF-8 format.
     *
     * @param in  the DataInput stream
     *
     * @return the character read from the stream in UTF-8 format
     *
     * @throws IllegalUTF if there is a flaw in the UTF-8 encoding or in the resulting codepoint
     */
    static Char readUTF8Char(DataInput in)
        {
        private static UInt32 trailing(DataInput in)
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
        return switch ((~b).leftmostBit)
            {
            case 0b10000000: n;
            case 0b00100000: n & 0b00011111 << 6
                             | trailing(in);
            case 0b00010000: n & 0b00001111 << 6
                             | trailing(in) << 6
                             | trailing(in);
            case 0b00001000: n & 0b00000111 << 6
                             | trailing(in) << 6
                             | trailing(in) << 6
                             | trailing(in);
            case 0b00000100: n & 0b00000011 << 6
                             | trailing(in) << 6
                             | trailing(in) << 6
                             | trailing(in) << 6
                             | trailing(in);
            case 0b00000010: n & 0b00000001 << 6
                             | trailing(in) << 6
                             | trailing(in) << 6
                             | trailing(in) << 6
                             | trailing(in) << 6
                             | trailing(in);
            default: throw new IllegalUTF($"initial byte: {b}");
            }.toChar();
        }

    /**
     * Read a sequence of bytes from the stream corresponding to a single Unicode character that is
     * encoded in the UTF-16 format.
     *
     * @param in  the DataInput stream
     *
     * @return the character read from the stream in UTF-16 format
     *
     * @throws IllegalUTF if there is a flaw in the UTF-16 encoding or in the resulting codepoint
     */
    static Char readUTF16Char(DataInput in)
        {
        UInt32 n = (in.readByte().toUInt32() <<  8)
                |  (in.readByte().toUInt32()      );

        // for surrogates, the high ten bits (in the range 0x000–0x3FF) are encoded in the range
        // 0xD800–0xDBFF, and the low ten bits (in the range 0x000–0x3FF) are encoded in the range
        // 0xDC00–0xDFFF
        if (n > 0xD7FF && n < 0xE000)
            {
            if (n >= 0xDC00)
                {
                throw new IllegalUTF($"low-surrogate not expected: {n}");
                }

            n = n & 0x3FF << 10; // store off the 10 "high" bits

            UInt32 nLo = (in.readByte().toUInt32() <<  8)
                      |  (in.readByte().toUInt32()      );
            if (nLo <= 0xD7FF || nLo >= 0xE000)
                {
                throw new IllegalUTF($"low-surrogate expected, but BMP codepoint found: {nLo}");
                }

            if (nLo < 0xDC00)
                {
                throw new IllegalUTF($"low-surrogate expected, but high-surrogate found: {nLo}");
                }

            n |= nLo & 0x3FF;
            }

        return new Char(n);
        }

    /**
     * Read a sequence of bytes from the stream corresponding to a single Unicode character that is
     * encoded in the UTF-8 format.
     *
     * @param in  the DataInput stream
     *
     * @return the character read from the stream in UTF-32 format
     *
     * @throws IllegalUTF if there is a flaw in the resulting codepoint
     */
    static Char readUTF32Char(DataInput in)
        {
        return new Char(in.readByte().toUInt32() << 8
                      | in.readByte().toUInt32() << 8
                      | in.readByte().toUInt32() << 8
                      | in.readByte().toUInt32()     );
        }

    /**
     * Read a sequence of bytes from the stream corresponding to a null-terminated ASCII string.
     *
     * @param in            the DataInput stream
     * @param convNonAscii  an optional function that is used to convert non-ASCII values to Unicode
     *                      characters
     *
     * @return the String read from the stream (but not including the closing null terminator)
     */
    static String readAsciiStringZ(DataInput in, function Char(Byte) convNonAscii = _ -> '?')
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