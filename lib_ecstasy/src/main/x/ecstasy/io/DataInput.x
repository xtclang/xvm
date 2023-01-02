import io.IllegalUTF;

/**
 * The DataInput interface represents a stream of values of various fundamental Ecstasy types. It
 * provides default implementations for some methods, but does not prescribe an underlying data
 * format. For example, integers could be fixed length or compressed, and characters could be
 * encoded as UTF-8, UTF-16, UTF-32, or even ASCII.
 */
interface DataInput
        extends BinaryInput
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
    Char readChar()
        {
        return new Char(readUInt32());
        }

    /**
     * @return  a value of type String read from the stream
     */
    String readString()
        {
        UInt64 length = readUInt64();
        if (length == 0)
            {
            return "";
            }

        assert length > 0;
        Char[] chars = new Char[length];
        for (Int i : 0 ..< length)
            {
            chars[i] = readChar();
            }
        return new String(chars);
        }

    /**
     * @return  a value of the specified enumeration type read from the stream
     */
    <EnumType extends Enum> EnumType readEnum(Enumeration<EnumType> enumeration)
        {
        Int n = readUInt16();
        assert 0 <= n < enumeration.count;
        return enumeration.values[n];
        }

    /**
     * @return  a value of type Int8 read from the stream
     */
    Int8 readInt8()
        {
        return new Int8(readBytes(1));
        }

    /**
     * @return  a value of type Int16 read from the stream
     */
    Int16 readInt16()
        {
        return new Int16(readBytes(2));
        }

    /**
     * @return  a value of type Int32 read from the stream
     */
    Int32 readInt32()
        {
        return new Int32(readBytes(4));
        }

    /**
     * @return  a value of type Int64 (aka "Int") read from the stream
     */
    Int64 readInt64()
        {
        return new Int64(readBytes(8));
        }

    /**
     * @return  a value of type Int128 read from the stream
     */
    Int128 readInt128()
        {
        return new Int128(readBytes(16));
        }

    /**
     * @return  a value of type IntN read from the stream
     */
    IntN readIntN()
        {
        return new IntN(readBytes(readUInt64()));
        }

    /**
     * @return  a value of type UInt8 read from the stream
     */
    UInt8 readUInt8()
        {
        return readByte();
        }

    /**
     * @return  a value of type UInt16 read from the stream
     */
    UInt16 readUInt16()
        {
        return new UInt16(readBytes(2));
        }

    /**
     * @return  a value of type UInt32 read from the stream
     */
    UInt32 readUInt32()
        {
        return new UInt32(readBytes(4));
        }

    /**
     * @return  a value of type UInt64 (aka "UInt") read from the stream
     */
    UInt64 readUInt64()
        {
        return new UInt64(readBytes(8));
        }

    /**
     * @return  a value of type UInt128 read from the stream
     */
    UInt128 readUInt128()
        {
        return new UInt128(readBytes(16));
        }

    /**
     * @return  a value of type UIntN read from the stream
     */
    UIntN readUIntN()
        {
        return new UIntN(readBytes(readUInt64()));
        }

    /**
     * @return  a value of type Dec64 read from the stream
     */
    Dec32 readDec32()
        {
        return new Dec32(readBytes(4));
        }

    /**
     * @return  a value of type Dec64 read from the stream
     */
    Dec64 readDec64()
        {
        return new Dec64(readBytes(8));
        }

    /**
     * @return  a value of type Dec128 read from the stream
     */
    Dec128 readDec128()
        {
        return new Dec128(readBytes(16));
        }

    /**
     * @return  a value of type DecN read from the stream
     */
    DecN readDecN()
        {
        return new DecN(readBytes(readUInt64()));
        }

    /**
     * @return  a value of type Float16 read from the stream
     */
    Float16 readFloat16()
        {
        return new Float16(readBytes(2));
        }

    /**
     * @return  a value of type BFloat16 read from the stream
     */
    BFloat16 readBFloat16()
        {
        return new BFloat16(readBytes(2));
        }

    /**
     * @return  a value of type Float32 read from the stream
     */
    Float32 readFloat32()
        {
        return new Float32(readBytes(4));
        }

    /**
     * @return  a value of type Float64 read from the stream
     */
    Float64 readFloat64()
        {
        return new Float64(readBytes(8));
        }

    /**
     * @return  a value of type Float128 read from the stream
     */
    Float128 readFloat128()
        {
        return new Float128(readBytes(16));
        }

    /**
     * @return  a value of type FloatN read from the stream
     */
    FloatN readFloatN()
        {
        return new FloatN(readBytes(readUInt64()));
        }

    /**
     * @return  a value of type Date read from the stream
     */
    Date readDate()
        {
        return new Date(readInt32());
        }

    /**
     * @return  a value of type TimeOfDay read from the stream
     */
    TimeOfDay readTime()
        {
        return new TimeOfDay(readUInt64());
        }

    /**
     * @return  a value of type Time read from the stream
     */
    Time readTime()
        {
        return new Time(readInt128(), readTimeZone());
        }

    /**
     * @return  a value of type TimeZone read from the stream
     */
    TimeZone readTimeZone()
        {
        switch (Byte b = readByte())
            {
            case 0:
                return TimeZone.UTC;

            case 3:
                return TimeZone.NoTZ;

            case 2:
                String name = readString();
                TODO Rules-based TimeZone

            case 1:
                return new TimeZone(readInt64());

            default:
                throw new IOException($"illegal timezone format indicator: {b}");
            }
        }

    /**
     * @return  a value of type Duration read from the stream
     */
    Duration readDuration()
        {
        return new Duration(readUInt128());
        }


    // ----- helper functions ----------------------------------------------------------------------

    /**
     * Read an integer from the passed stream, which is encoded in the packed integer format.
     *
     * The packed integer format represents a signed, 2's-complement integer of 1-512 bits (1-64
     * bytes) in size. There are four storage formats:
     *
     * * **Tiny**: For a value in the range -64..63 (7 bits), the value can be encoded in one byte.
     *   The least significant 7 bits of the value are shifted left by 1 bit, and the 0x1 bit is set
     *   to 1. When reading a packed integer, if bit 0x1 of the first byte is 1, then it's Tiny.
     *
     * * **Small**: For a value in the range -4096..4095 (13 bits), the value can be encoded in two
     *   bytes. The first byte contains the value 0x2 (010) in the least significant 3 bits, and
     *   bits 8-12 of the integer in bits 3-7; the second byte contains bits 0-7 of the integer.
     *
     * * **Medium**: For a value in the range -1048576..1048575 (21 bits), the value can be encoded
     *   in three bytes. The first byte contains the value 0x6 (110) in the least significant 3
     *   bits, and bits 16-20 of the integer in bits 3-7; the second byte contains bits 8-15 of the
     *   integer; the third byte contains bits 0-7 of the integer.
     *
     * * **Large**: For a value in the range -(2^511)..2^511-1 (up to 512 bits), a value with `s`
     *   significant bits can be encoded in no less than `1+max(1,(s+7)/8)` bytes; let `b` be
     *   the selected encoding length, in bytes. The first byte contains the value 0x0 in the least
     *   significant 2 bits (00), and the least 6 significant bits of `(b-2)` in bits 2-7. The
     *   following `(b-1)` bytes contain the least significant `(b-1)*8` bits of the integer.
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
            return b >> 1;
            }

        // Small and Medium formats are indicated by the second bit (and differentiated by the third
        // bit). Small format: bits 3..7 of the first byte are bits 8..12 of the result, and the
        // next byte provides bits 0..7 of the result. Medium format: bits 3..7 of the first byte
        // are bits 16..20 of the result, and the next byte provides bits 8..15 of the result, and
        // the next byte provides bits 0..7 of the result
        if (b & 0x02 != 0)
            {
            Int64 n = (b >> 3).toInt64() << 8 | in.readByte();

            // the third bit is used to indicate Medium format (a second trailing byte)
            if (b & 0x04 != 0)
                {
                n = n << 8 | in.readByte();
                }
            return n;
            }

        // Large format: the first two bits of the first byte are 0, so bits 2..7 of the
        // first byte are the trailing number of bytes minus 1
        Int size = 1 + (b >>> 2);
        assert:bounds size <= 16;

        Int128 n = in.readInt8();
        while (--size > 0)
            {
            n = n << 8 | in.readByte();
            }
        return n;
        }

    /**
     * Read an integer value that is formatted using the packed integer format, and return it as
     * a `IntN`.
     *
     * @param in  the DataInput stream containing the packed integer
     *
     * @return the resulting IntN value
     */
    static IntN readPackedIntN(DataInput in)
        {
        // use a signed byte to get auto sign-extension when converting to an int
        Int8 b = in.readInt8();

        // Tiny format: the first bit of the first byte is used to indicate a single byte format,
        // in which the entire value is contained in the 7 MSBs
        if (b & 0x01 != 0)
            {
            return b >> 1;
            }

        // Small and Medium formats are indicated by the second bit (and differentiated by the third
        // bit). Small format: bits 3..7 of the first byte are bits 8..12 of the result, and the
        // next byte provides bits 0..7 of the result. Medium format: bits 3..7 of the first byte
        // are bits 16..20 of the result, and the next byte provides bits 8..15 of the result, and
        // the next byte provides bits 0..7 of the result
        if (b & 0x02 != 0)
            {
            Int64 n = (b >> 3).toInt64() << 8 | in.readByte();

            // the third bit is used to indicate Medium format (a second trailing byte)
            if (b & 0x04 != 0)
                {
                n = n << 8 | in.readByte();
                }
            return n;
            }

        // Large format: the first two bits of the first byte are 0, so bits 2..7 of the
        // first byte are the trailing number of bytes minus 1
        Int size = 1 + (b >>> 2);
        assert:bounds size <= 8;

        if (size <= 16)
            {
            Int128 n = in.readInt8();                   // use sign extension on the first byte
            while (--size > 0)
                {
                n = n << 8 | in.readByte();             // additional bytes remain bitwise intact
                }
            return n;
            }

        Byte[] bytes = new Byte[size];
        in.readBytes(bytes, 0, size);
        return new IntN(bytes);
        }
    }