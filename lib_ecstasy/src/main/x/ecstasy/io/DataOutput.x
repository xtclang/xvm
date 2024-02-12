/**
 * The DataOutput interface represents a output stream of values of various fundamental Ecstasy
 * types. It provides default implementations for some methods, but does not prescribe an underlying
 * data format. For example, integers could be fixed length or compressed, and characters could be
 * encoded as UTF-8, UTF-16, UTF-32, or even ASCII.
 */
interface DataOutput
        extends BinaryOutput {
    /**
     * Write a Boolean value to the stream.
     *
     * @param value  a value of type Boolean to write to the stream
     */
    void writeBoolean(Boolean value) {
        writeByte(value.toUInt8());
    }

    /**
     * Write a Char value to the stream.
     *
     * @param value  a value of type Char to write to the stream
     */
    void writeChar(Char value) {
        writeUInt32(value.codepoint);
    }

    /**
     * Write a String value to the stream.
     *
     * @param value  a value of type String to write to the stream
     */
    void writeString(String value) {
        writeUInt64(value.size.toUInt64());
        for (Char ch : value) {
            writeChar(ch);
        }
    }

    /**
     * Write an Enum value to the stream.
     *
     * @param value  a value of an enumerated type to write to the stream
     */
    void writeEnum(Enum value) {
        writeUInt16(value.ordinal.toUInt16());
    }

    /**
     * Write an Int8 value to the stream.
     *
     * @param value  a value of type Int8 to write to the stream
     */
    void writeInt8(Int8 value) {
        writeByte(value.toByteArray()[0]);  // TODO CP sliceByte
    }

    /**
     * Write an Int16 value to the stream.
     *
     * @param value  a value of type Int16 to write to the stream
     */
    void writeInt16(Int16 value) {
        writeBytes(value.toByteArray());
    }

    /**
     * Write an Int32 value to the stream.
     *
     * @param value  a value of type Int32 to write to the stream
     */
    void writeInt32(Int32 value) {
        writeBytes(value.toByteArray());
    }

    /**
     * Write a 64-bit Int value to the stream.
     *
     * @param value  a value of type Int to write to the stream
     */
    void writeInt64(Int64 value) {
        writeBytes(value.toByteArray());
    }

    /**
     * Write an Int128 value to the stream.
     *
     * @param value  a value of type Int128 to write to the stream
     */
    void writeInt128(Int128 value) {
        writeBytes(value.toByteArray());
    }

    /**
     * Write a IntN value to the stream.
     *
     * @param value  a value of type IntN to write to the stream
     */
    void writeIntN(IntN value) {
        Byte[] bytes = value.toByteArray();
        writeUInt64(bytes.size.toUInt64());
        writeBytes(bytes);
    }

    /**
     * Write a UInt8 (aka a "Byte") value to the stream.
     *
     * @param value  a value of type UInt8 to write to the stream
     */
    void writeUInt8(UInt8 value) {
        writeByte(value);
    }

    /**
     * Write a UInt16 value to the stream.
     *
     * @param value  a value of type UInt16 to write to the stream
     */
    void writeUInt16(UInt16 value) {
        writeBytes(value.toByteArray());
    }

    /**
     * Write a UInt32 value to the stream.
     *
     * @param value  a value of type UInt32 to write to the stream
     */
    void writeUInt32(UInt32 value) {
        writeBytes(value.toByteArray());
    }

    /**
     * Write a 64-bit UInt value to the stream.
     *
     * @param value  a value of type UInt64 to write to the stream
     */
    void writeUInt64(UInt64 value) {
        writeBytes(value.toByteArray());
    }

    /**
     * Write a UInt128 value to the stream.
     *
     * @param value  a value of type UInt128 to write to the stream
     */
    void writeUInt128(UInt128 value) {
        writeBytes(value.toByteArray());
    }

    /**
     * Write a UIntN value to the stream.
     *
     * @param value  a value of type UIntN to write to the stream
     */
    void writeUIntN(UIntN value) {
        Byte[] bytes = value.toByteArray();
        writeUInt64(bytes.size.toUInt64());
        writeBytes(bytes);
    }

    /**
     * Write a Dec32 value to the stream.
     *
     * @param value  a value of type Dec64 to write to the stream
     */
    void writeDec32(Dec32 value) {
        writeBytes(value.toByteArray());
    }

    /**
     * Write a Dec64 value to the stream.
     *
     * @param value  a value of type Dec64 to write to the stream
     */
    void writeDec64(Dec64 value) {
        writeBytes(value.toByteArray());
    }

    /**
     * Write a Dec128 value to the stream.
     *
     * @param value  a value of type Dec128 to write to the stream
     */
    void writeDec128(Dec128 value) {
        writeBytes(value.toByteArray());
    }

    /**
     * Write a DecN value to the stream.
     *
     * @param value  a value of type DecN to write to the stream
     */
    void writeDecN(DecN value) {
        Byte[] bytes = value.toByteArray();
        writeUInt64(bytes.size.toUInt64());
        writeBytes(bytes);
    }

    /**
     * Write a Float16 value to the stream.
     *
     * @param value  a value of type Float16 to write to the stream
     */
    void writeFloat16(Float16 value) {
        writeBytes(value.toByteArray());
    }

    /**
     * Write a BFloat16 value to the stream.
     *
     * @param value  a value of type BFloat16 to write to the stream
     */
    void writeBFloat16(BFloat16 value) {
        writeBytes(value.toByteArray());
    }

    /**
     * Write a Float32 value to the stream.
     *
     * @param value  a value of type Float32 to write to the stream
     */
    void writeFloat32(Float32 value) {
        writeBytes(value.toByteArray());
    }

    /**
     * Write a Float64 value to the stream.
     *
     * @param value  a value of type Float64 to write to the stream
     */
    void writeFloat64(Float64 value) {
        writeBytes(value.toByteArray());
    }

    /**
     * Write a Float128 value to the stream.
     *
     * @param value  a value of type Float128 to write to the stream
     */
    void writeFloat128(Float128 value) {
        writeBytes(value.toByteArray());
    }

    /**
     * Write a FloatN value to the stream.
     *
     * @param value  a value of type FloatN to write to the stream
     */
    void writeFloatN(FloatN value) {
        Byte[] bytes = value.toByteArray();
        writeUInt64(bytes.size.toUInt64());
        writeBytes(bytes);
    }

    /**
     * Write a Date value to the stream.
     *
     * @param value  a value of type Date to write to the stream
     */
    void writeDate(Date value) {
        writeInt32(value.epochDay);
    }

    /**
     * Write a TimeOfDay value to the stream.
     *
     * @param value  a value of type TimeOfDay to write to the stream
     */
    void writeTime(TimeOfDay value) = writeInt(value.picos);

    /**
     * Write a Time value to the stream.
     *
     * @param value  a value of type Time to write to the stream
     */
    void writeTime(Time value) {
        writeInt128(value.epochPicos);
        writeTimeZone(value.timezone);
    }

    /**
     * Write a TimeZone value to the stream.
     *
     * @param value  a value of type TimeZone to write to the stream
     */
    void writeTimeZone(TimeZone value) {
        if (value.isUTC) {
            writeByte(0);
        } else if (value.isNoTZ) {
            writeByte(3);
        } else if (value.rules.size > 0) {
            writeByte(2);
            writeString(value.name ?: assert);      // TODO
        } else {
            writeByte(1);
            writeInt64(value.picos);
        }
    }

    /**
     * Write a Duration value to the stream.
     *
     * @param value  a value of type Duration to write to the stream
     */
    void writeDuration(Duration value) {
        writeInt128(value.picoseconds);
    }


    // ----- aliases -------------------------------------------------------------------------------

    /**
     * Alias for the `writeInt64()` method, since the `Int64` type is aliased as `Int`.
     */
    static Method<DataOutput, <Int>, <>> writeInt = writeInt64;

    /**
     * Alias for the `writeUInt64()` method, since the `UInt64` type is aliased as `UInt`.
     */
    static Method<DataOutput, <UInt>, <>> writeUInt = writeUInt64;


    // ----- helper functions ----------------------------------------------------------------------

    /**
     * Calculate the number of bytes required to write an integer to a stream, encoding it using the
     * packed integer format.
     *
     * @param n  the Int value
     *
     * @return the number of bytes required to write the value as a packed value
     */
    static Int packedIntLength(Int n) {
        // test for Tiny
        if (-64 <= n <= 127) {
            return 1;
        }

        // test for medium (2-byte format)
        Int bitCount = 65 - Int.maxOf(n, ~n).leadingZeroCount;
        if (bitCount <= 13)
            {
            return 2;
            }

        // calculate the large format size
        // TODO return 1 + (cBits + 7 >>> 3);
        Int byteCount = bitCount + 7 >>> 3;
        assert 2 <= byteCount <= 8;
        return 1 + byteCount;
    }

    /**
     * Calculate the number of bytes required to write an integer to a stream, encoding it using the
     * packed integer format.
     *
     * @param n  the `IntN` value
     *
     * @return the number of bytes required to write the value as a packed value
     */
    static Int packedIntNLength(IntN n) {
        if (Int.MinValue <= n <= Int.MaxValue) {
            return packedIntLength(n.toInt64());
        }

        Int byteCount = n.toByteArray().size;
        return byteCount <= 32
                ? 1 + byteCount
                : 1 + packedIntLength(byteCount) + byteCount;
    }

    /**
     * Write an integer to the passed stream, encoding it using the packed integer format. The format (XVM Integer
     * Packing, or "XIP") uses a variable length compression scheme. It defines four internal formats for XIP'd
     * integers:
     *
     * * **Small**: For a value in the range `-64..127`, the value is encoded as the least significant byte of the
     *   integer, as is. When reading in a packed integer, if the leftmost bits of the first byte are **not** `10`,
     *   then the XIP'd integer is in small format, and simply requires sign extension of the first byte to the desired
     *   integer size in order to form the integer value.
     *
     * * **Medium**: For a value in the range `-4096..4095` (13 bits), the value is encoded in two bytes. The first
     *   byte contains the binary value `100` in the most significant 3 bits, indicating the medium format. The 2's
     *   complement integer value is formed from the least significant 5 bits of the first byte, sign extended to the
     *   desired integer size and then left-shifted 8 bits, or'd with the bits of the second byte.
     *
     * * **Large**: For any 2's complement integer value from `2..32` bytes (in the range `-(2^255)..2^255-1`),
     *   let `b` be that number of bytes, with the XIP'd value encoded in `1+b` bytes. The first byte contains the
     *   binary value `101` in the most significant 3 bits, and the remaining 5 bits are the unsigned value `b-1`,
     *   indicating the large format. (Note: Since `b` is at least `2`, the value `b-1` is always non-zero.)
     *   The following `b` bytes hold the 2's complement integer value.
     *
     * * **Huge**: For any integer value `n` larger than 32 bytes, let `b` be that number of bytes. The first
     *   byte of the XIP'd integer is `0xA0`, which indicates the huge format. The following bytes contain the value
     *   `b`, encoded as a XIP'd integer. The following `b` bytes hold the 2's complement integer value `n`.
     *
     * The algorithm here uses the smallest possible encoding for each value.
     *
     * @param out  the DataOutput stream to write to
     * @param n    the Int value to write to the stream
     */
    static void writePackedInt(DataOutput out, Int n) {     // TODO CP why not BinaryOutput?!?!?!?!?!?!?!??!
        // test for small format
        if (-64 <= n <= 127) {
            out.writeByte(n.toByte());
            return;
        }

        // test for medium format
        Int bitCount = 65 - Int.maxOf(n, ~n).leadingZeroCount;
        if (bitCount <= 13) {
            out.writeByte((n >>> 8 & 0x1F | 0x80).toByte());
            out.writeByte(n.toByte());
            return;
        }

        // large format
        Int byteCount = bitCount + 7 >> 3;
        assert 2 <= byteCount <= 8;
        out.writeByte((0xA0 | byteCount - 1).toByte());
        out.writeBytes(n.toByteArray(), 8-byteCount, byteCount);
    }

    /**
     * Write an integer to the passed stream, encoding it using the packed integer format.
     *
     * The packed integer format represents a signed, 2's-complement integer of any size.
     *
     * @see writePackedInt
     *
     * @param out  the DataOutput stream to write to
     * @param n    the IntN value to write to the stream
     */
    static void writePackedIntN(DataOutput out, IntN n) {
        if (Int.MinValue <= n <= Int.MaxValue) {
            // small/medium/large formats (up to 64-bit values)
            writePackedInt(out, n.toInt64());
            return;
        }

        Byte[] bytes     = n.toByteArray();
        Int    byteSize  = bytes.size;
        Int    bitCount  = byteSize * 8 + 1 - IntN.maxOf(n, ~n).leadingZeroCount;
        Int    byteCount = bitCount + 7 >> 3;
        if (byteCount <= 32) {
            // large format
            out.writeByte((0xA0 | byteCount - 1).toByte());
        } else {
            // huge format
            out.writeByte(0xA0);
            writePackedInt(out, byteCount);
        }
        out.writeBytes(bytes, byteSize-byteCount, byteCount);
    }

    /**
     * Write a Char to the stream using the UTF-8 format.
     *
     * @param out  the DataOutput stream to write to
     * @param ch   the character to write to the stream in UTF-8 format
     *
     * @throws IllegalUTF if the data cannot be written as valid UTF data
     */
    static void writeUTF8Char(BinaryOutput out, Char ch) {
        writeUTF8Codepoint(out, ch.codepoint);
    }

    /**
     * Write a Unicode codepoint to the stream using the UTF-8 format.
     *
     * @param out        the DataOutput stream to write to
     * @param codepoint  the Unicode codepoint to write to the stream in UTF-32 format
      *
     * @throws IllegalUTF if the data cannot be written as valid UTF data
     */
    static void writeUTF8Codepoint(BinaryOutput out, UInt32 codepoint) {
        if (codepoint & ~0x7F == 0) {
            // ASCII - single byte 0xxxxxxx format
            out.writeByte(codepoint.toUInt8());
            return;
        }

        // otherwise the format is based on the number of significant bits:
        // bits  code-points             first byte  trailing  # trailing
        // ----  ----------------------- ----------  --------  ----------
        //  11   U+0080    - U+07FF      110xxxxx    10xxxxxx      1
        //  16   U+0800    - U+FFFF      1110xxxx    10xxxxxx      2
        //  21   U+10000   - U+1FFFFF    11110xxx    10xxxxxx      3
        //  26   U+200000  - U+3FFFFFF   111110xx    10xxxxxx      4
        //  31   U+4000000 - U+7FFFFFFF  1111110x    10xxxxxx      5
        Int trail;
        switch (codepoint.leftmostBit) {
        case 0b00000000000000000000000010000000:
        case 0b00000000000000000000000100000000:
        case 0b00000000000000000000001000000000:
        case 0b00000000000000000000010000000000:
            out.writeByte(0b11000000 | (codepoint >>> 6).toUInt8());
            trail = 1;
            break;

        case 0b00000000000000000000100000000000:
        case 0b00000000000000000001000000000000:
        case 0b00000000000000000010000000000000:
        case 0b00000000000000000100000000000000:
        case 0b00000000000000001000000000000000:
            out.writeByte(0b11100000 | (codepoint >>> 12).toUInt8());
            trail = 2;
            break;

        case 0b00000000000000010000000000000000:
        case 0b00000000000000100000000000000000:
        case 0b00000000000001000000000000000000:
        case 0b00000000000010000000000000000000:
        case 0b00000000000100000000000000000000:
            out.writeByte(0b11110000 | (codepoint >>> 18).toUInt8());
            trail = 3;
            break;

        case 0b00000000001000000000000000000000:
        case 0b00000000010000000000000000000000:
        case 0b00000000100000000000000000000000:
        case 0b00000001000000000000000000000000:
        case 0b00000010000000000000000000000000:
            out.writeByte(0b11111000 | (codepoint >>> 24).toUInt8());
            trail = 4;
            break;

        case 0b00000100000000000000000000000000:
        case 0b00001000000000000000000000000000:
        case 0b00010000000000000000000000000000:
        case 0b00100000000000000000000000000000:
        case 0b01000000000000000000000000000000:
            out.writeByte(0b11111100 | (codepoint >>> 30).toUInt8());
            trail = 5;
            break;

        default: throw new IllegalUTF($"illegal character codepoint: {codepoint}");
        }

        // write out trailing bytes; each has the same "10xxxxxx" format with 6
        // bits of data
        while (trail > 0) {
            out.writeByte(0b10_000000 | (codepoint >>> --trail * 6 & 0b00_111111).toByte());
        }
    }

    /**
     * Write a Char to the stream using the UTF-16 format.
     *
     * @param out  the DataOutput stream to write to
     * @param ch   the character to write to the stream in UTF-16 format
     *
     * @throws IllegalUTF if the data cannot be written as valid UTF data
     */
    static void writeUTF16Char(DataOutput out, Char ch) {
        writeUTF16Codepoint(out, ch.codepoint);
    }

    /**
     * Write a Unicode codepoint to the stream using the UTF-16 format.
     *
     * @param out        the DataOutput stream to write to
     * @param codepoint  the Unicode codepoint to write to the stream in UTF-32 format
     *
     * @throws IllegalUTF if the data cannot be written as valid UTF data
     */
    static void writeUTF16Codepoint(DataOutput out, UInt32 codepoint) {
        if (codepoint > 0xFFFF) {
            // the high ten bits (in the range 0x000–0x3FF) are encoded in the range 0xD800–0xDBFF;
            // the low ten bits (in the range 0x000–0x3FF) are encoded in the range 0xDC00–0xDFFF
            codepoint -= 0x10000;
            UInt32 nHiLo = 0xD800DC00
                         | (codepoint & 0xFFC << 6)
                         | (codepoint & 0x3FF     );
            out.writeBytes(nHiLo.toByteArray());
        } else {
            out.writeBytes(codepoint.toByteArray(), 2, 2);
        }
    }

    /**
     * Write a Char to the stream using the UTF-32 format.
     *
     * @param out  the DataOutput stream to write to
     * @param ch   the character to write to the stream in UTF-32 format
     *
     * @throws IllegalUTF if the data cannot be written as valid UTF data
     */
    static void writeUTF32Char(DataOutput out, Char ch) {
        writeUTF32Codepoint(out, ch.codepoint);
    }

    /**
     * Write a Unicode codepoint to the stream using the UTF-32 format.
     *
     * @param out        the DataOutput stream to write to
     * @param codepoint  the Unicode codepoint to write to the stream in UTF-32 format
     *
     * @throws IllegalUTF if the data cannot be written as valid UTF data
     */
    static void writeUTF32Codepoint(DataOutput out, UInt32 codepoint) {
        out.writeBytes(codepoint.toByteArray());
    }

    /**
     * Write a String to the DataOutput stream as a null-terminated ASCII string.
     *
     * @param out          the DataOutput stream to write to
     * @param s            the String to write to the stream as a null-terminated ASCII string
     * @param convToAscii  an optional function that is used to convert a Unicode codepoint that is
     *                     outside of the ASCII range into a Byte that can be written to the stream
     *
     */
    static void writeAsciiStringZ(DataOutput out, String s, function Byte(Char) convToAscii = _ -> '?'.toByte()) {
        for (Char ch : s) {
            UInt32 codepoint = ch.codepoint;
            Byte   byte      = 0 < codepoint <= 0x7F ? codepoint.toByte() : convToAscii(ch);
            out.writeByte(byte);
            if (byte == 0) {
                return;
            }
        }
        out.writeByte(0);
    }
}