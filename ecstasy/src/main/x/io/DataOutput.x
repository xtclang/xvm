import numbers.BFloat16;
import numbers.Dec32;
import numbers.Dec64;
import numbers.Dec128;
import numbers.Float16;
import numbers.Float32;
import numbers.Float64;
import numbers.Float128;
import numbers.Int8;
import numbers.Int16;
import numbers.Int32;
import numbers.Int128;
import numbers.UInt8;
import numbers.UInt16;
import numbers.UInt32;
import numbers.UInt128;
import numbers.DecN;
import numbers.FloatN;
import numbers.IntN;
import numbers.UIntN;

/**
 * The DataOutput interface represents a output stream of values of various fundamental Ecstasy
 * types. It provides default implementations for some methods, but does not prescribe an underlying
 * data format. For example, integers could be fixed length or compressed, and characters could be
 * encoded as UTF-8, UTF-16, UTF-32, or even ASCII.
 */
interface DataOutput
        extends BinaryOutput
    {
    /**
     * Write a Boolean value to the stream.
     *
     * @param value  a value of type Boolean to write to the stream
     */
    void writeBoolean(Boolean value)
        {
        writeByte(value.toByte());
        }

    /**
     * Write a Char value to the stream.
     *
     * @param value  a value of type Char to write to the stream
     */
    void writeChar(Char value)
        {
        writeUInt32(value.codepoint);
        }

    /**
     * Write a String value to the stream.
     *
     * @param value  a value of type String to write to the stream
     */
    void writeString(String value)
        {
        writeInt(value.size);
        for (Char ch : value)
            {
            writeChar(ch);
            }
        }

    /**
     * Write an Enum value to the stream.
     *
     * @param value  a value of an enumerated type to write to the stream
     */
    void writeEnum(Enum value)
        {
        writeInt(value.ordinal);
        }

    /**
     * Write an Int8 value to the stream.
     *
     * @param value  a value of type Int8 to write to the stream
     */
    void writeInt8(Int8 value)
        {
        writeByte(value.toByteArray()[0]);
        }

    /**
     * Write an Int16 value to the stream.
     *
     * @param value  a value of type Int16 to write to the stream
     */
    void writeInt16(Int16 value)
        {
        writeBytes(value.toByteArray());
        }

    /**
     * Write a UInt16 value to the stream.
     *
     * @param value  a value of type UInt16 to write to the stream
     */
    void writeUInt16(UInt16 value)
        {
        writeBytes(value.toByteArray());
        }

    /**
     * Write an Int32 value to the stream.
     *
     * @param value  a value of type Int32 to write to the stream
     */
    void writeInt32(Int32 value)
        {
        writeBytes(value.toByteArray());
        }

    /**
     * Write a UInt32 value to the stream.
     *
     * @param value  a value of type UInt32 to write to the stream
     */
    void writeUInt32(UInt32 value)
        {
        writeBytes(value.toByteArray());
        }

    /**
     * Write a 64-bit Int value to the stream.
     *
     * @param value  a value of type Int to write to the stream
     */
    void writeInt(Int value)
        {
        writeBytes(value.toByteArray());
        }

    /**
     * Write a 64-bit UInt value to the stream.
     *
     * @param value  a value of type UInt64 to write to the stream
     */
    void writeUInt(UInt value)
        {
        writeBytes(value.toByteArray());
        }

    /**
     * Write an Int128 value to the stream.
     *
     * @param value  a value of type Int128 to write to the stream
     */
    void writeInt128(Int128 value)
        {
        writeBytes(value.toByteArray());
        }

    /**
     * Write a UInt128 value to the stream.
     *
     * @param value  a value of type UInt128 to write to the stream
     */
    void writeUInt128(UInt128 value)
        {
        writeBytes(value.toByteArray());
        }

    /**
     * Write a IntN value to the stream.
     *
     * @param value  a value of type IntN to write to the stream
     */
    void writeIntN(IntN value)
        {
        Byte[] bytes = value.toByteArray();
        writeInt(bytes.size);
        writeBytes(bytes);
        }

    /**
     * Write a UIntN value to the stream.
     *
     * @param value  a value of type UIntN to write to the stream
     */
    void writeUIntN(UIntN value)
        {
        Byte[] bytes = value.toByteArray();
        writeInt(bytes.size);
        writeBytes(bytes);
        }

    /**
     * Write a Dec32 value to the stream.
     *
     * @param value  a value of type Dec64 to write to the stream
     */
    void writeDec32(Dec32 value)
        {
        writeBytes(value.toByteArray());
        }

    /**
     * Write a Dec64 value to the stream.
     *
     * @param value  a value of type Dec64 to write to the stream
     */
    void writeDec64(Dec64 value)
        {
        writeBytes(value.toByteArray());
        }

    /**
     * Write a Dec128 value to the stream.
     *
     * @param value  a value of type Dec128 to write to the stream
     */
    void writeDec128(Dec128 value)
        {
        writeBytes(value.toByteArray());
        }

    /**
     * Write a DecN value to the stream.
     *
     * @param value  a value of type DecN to write to the stream
     */
    void writeDecN(DecN value)
        {
        Byte[] bytes = value.toByteArray();
        writeInt(bytes.size);
        writeBytes(bytes);
        }

    /**
     * Write a Float16 value to the stream.
     *
     * @param value  a value of type Float16 to write to the stream
     */
    void writeFloat16(Float16 value)
        {
        writeBytes(value.toByteArray());
        }

    /**
     * Write a BFloat16 value to the stream.
     *
     * @param value  a value of type BFloat16 to write to the stream
     */
    void writeBFloat16(BFloat16 value)
        {
        writeBytes(value.toByteArray());
        }

    /**
     * Write a Float32 value to the stream.
     *
     * @param value  a value of type Float32 to write to the stream
     */
    void writeFloat32(Float32 value)
        {
        writeBytes(value.toByteArray());
        }

    /**
     * Write a Float64 value to the stream.
     *
     * @param value  a value of type Float64 to write to the stream
     */
    void writeFloat64(Float64 value)
        {
        writeBytes(value.toByteArray());
        }

    /**
     * Write a Float128 value to the stream.
     *
     * @param value  a value of type Float128 to write to the stream
     */
    void writeFloat128(Float128 value)
        {
        writeBytes(value.toByteArray());
        }

    /**
     * Write a FloatN value to the stream.
     *
     * @param value  a value of type FloatN to write to the stream
     */
    void writeFloatN(FloatN value)
        {
        Byte[] bytes = value.toByteArray();
        writeInt(bytes.size);
        writeBytes(bytes);
        }

    /**
     * Write a Date value to the stream.
     *
     * @param value  a value of type Date to write to the stream
     */
    void writeDate(Date value)
        {
        writeInt(value.epochDay);
        }

    /**
     * Write a Time value to the stream.
     *
     * @param value  a value of type Time to write to the stream
     */
    void writeTime(Time value)
        {
        writeInt(value.picos);
        }

    /**
     * Write a DateTime value to the stream.
     *
     * @param value  a value of type DateTime to write to the stream
     */
    void writeDateTime(DateTime value)
        {
        writeInt128(value.epochPicos);
        writeTimeZone(value.timezone);
        }

    /**
     * Write a TimeZone value to the stream.
     *
     * @param value  a value of type TimeZone to write to the stream
     */
    void writeTimeZone(TimeZone value)
        {
        if (value.isUTC)
            {
            writeByte(0);
            }
        else if (value.isNoTZ)
            {
            writeByte(3);
            }
        else if (value.rules.size > 0)
            {
            writeByte(2);
            writeString(value.name ?: assert);      // TODO
            }
        else
            {
            writeByte(1);
            writeInt(value.picos);
            }
        }

    /**
     * Write a Duration value to the stream.
     *
     * @param value  a value of type Duration to write to the stream
     */
    void writeDuration(Duration value)
        {
        writeInt128(value.picoseconds);
        }


    // ----- helper functions ----------------------------------------------------------------------

    /**
     * Calculate the number of bytes required to write an integer to a stream, encoding it using the
     * packed integer format.
     *
     * @param n  the Int value
     *
     * @return the number of bytes required to write the value as a packed value
     */
    static Int packedIntLength(Int n)
        {
        // test for Tiny
        if (-64 <= n <= 63)
            {
            return 1;
            }

        // test for Small and Medium
        Int bitCount = 65 - n.maxOf(~n).leadingZeroCount;
        if (1 << bitCount & 0x3E3E00 != 0)              // test against bits 9-13 and 17-21
            {
            return bitCount <= 13 ? 2 : 3;
            }

        return 1 + (bitCount + 7 >>> 3);
        }

    /**
     * Calculate the number of bytes required to write an integer to a stream, encoding it using the
     * packed integer format.
     *
     * @param n  the `IntN` value
     *
     * @return the number of bytes required to write the value as a packed value
     */
    static Int packedIntNLength(IntN n)
        {
        return Int.minvalue <= n <= Int.maxvalue
                ? packedIntLength(n.toInt())
                : 1 + n.toByteArray().size;
        }

    /**
     * Write an integer to the passed stream, encoding it using the packed integer format.
     *
     * The packed integer format represents a signed, 2's-complement integer of 1-512 bytes in size.
     * The storage format is compressed as much as possible. There are four storage formats:
     *
     * * **Tiny**: For a value in the range -64..63 (7 bits), the value can be encoded in one byte.
     *   The least significant 7 bits of the value are shifted left by 1 bit, and the 0x1 bit is set
     *   to 1. When writing in a packed integer, if bit 0x1 of the first byte is 1, then it's Tiny.
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
     * @param out  the DataOutput stream to write to
     * @param n    the Int value to write to the stream
     */
    static void writePackedInt(DataOutput out, Int n)
        {
        // test for Tiny
        if (-64 <= n <= 63)
            {
            out.writeByte((n << 1 | 0x01).toByteArray()[7]);
            return;
            }

        // test for Small and Medium
        Int bitCount = 65 - n.maxOf(~n).leadingZeroCount;
        if (1 << bitCount & 0x3E3E00 != 0)              // test against bits 9-13 and 17-21
            {
            if (bitCount <= 13)
                {
                n = 0b010_00000000                      // 0x2 marker at 0..2 in byte #1
                        | (n & 0x1F00 << 3)             // bits 8..12 at 3..7 in byte #1
                        | (n & 0x00FF);                 // bits 0..7  at 0..7 in byte #2
                out.writeBytes(n.toByteArray(), 6, 2);
                }
            else
                {
                n = 0b110_00000000_00000000             // 0x6 marker  at 0..2 in byte #1
                        | (n & 0x1F0000 << 3)           // bits 16..20 at 3..7 in byte #1
                        | (n & 0x00FFFF);               // bits 8..15  at 0..7 in byte #2
                                                        // bits 0..7   at 0..7 in byte #3
                out.writeBytes(n.toByteArray(), 5, 3);
                }
            return;
            }

        Int byteCount = bitCount + 7 >>> 3;
        out.writeByte((byteCount - 1 << 2).toByteArray()[7]);
        out.writeBytes(n.toByteArray(), 8 - byteCount, byteCount);
        }

    /**
     * Write an integer to the passed stream, encoding it using the packed integer format.
     *
     * The packed integer format represents a signed, 2's-complement integer of 1-512 bytes in size.
     * See the notes on `writePackedInt()`.
     *
     * @param out  the DataOutput stream to write to
     * @param n    the IntN value to write to the stream
     */
    static void writePackedIntN(DataOutput out, IntN n)
        {
        if (Int.minvalue <= n <= Int.maxvalue)
            {
            writePackedInt(out, n.toInt());
            }

        Byte[] bytes     = n.toByteArray();
        Int    byteCount = bytes.size;
        assert:bounds 8 < byteCount <= 64;

        // write out using large format
        out.writeByte(byteCount-1 << 2);
        out.writeBytes(bytes);
        }

    /**
     * Write a Char to the stream using the UTF-8 format.
     *
     * @param out  the DataOutput stream to write to
     * @param ch   the character to write to the stream in UTF-8 format
     *
     * @throws IllegalUTF if the data cannot be written as valid UTF data
     */
    static void writeUTF8Char(DataOutput out, Char ch)
        {
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
    static void writeUTF8Codepoint(DataOutput out, UInt32 codepoint)
        {
        if (codepoint & ~0x7F == 0)
            {
            // ASCII - single byte 0xxxxxxx format
            out.writeByte(codepoint.toByte());
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
        switch (codepoint.leftmostBit)
            {
            case 0b00000000000000000000000010000000:
            case 0b00000000000000000000000100000000:
            case 0b00000000000000000000001000000000:
            case 0b00000000000000000000010000000000:
                out.writeByte(0b11000000 | (codepoint >>> 6).toByte());
                trail = 1;
                break;

            case 0b00000000000000000000100000000000:
            case 0b00000000000000000001000000000000:
            case 0b00000000000000000010000000000000:
            case 0b00000000000000000100000000000000:
            case 0b00000000000000001000000000000000:
                out.writeByte(0b11100000 | (codepoint >>> 12).toByte());
                trail = 2;
                break;

            case 0b00000000000000010000000000000000:
            case 0b00000000000000100000000000000000:
            case 0b00000000000001000000000000000000:
            case 0b00000000000010000000000000000000:
            case 0b00000000000100000000000000000000:
                out.writeByte(0b11110000 | (codepoint >>> 18).toByte());
                trail = 3;
                break;

            case 0b00000000001000000000000000000000:
            case 0b00000000010000000000000000000000:
            case 0b00000000100000000000000000000000:
            case 0b00000001000000000000000000000000:
            case 0b00000010000000000000000000000000:
                out.writeByte(0b11111000 | (codepoint >>> 24).toByte());
                trail = 4;
                break;

            case 0b00000100000000000000000000000000:
            case 0b00001000000000000000000000000000:
            case 0b00010000000000000000000000000000:
            case 0b00100000000000000000000000000000:
            case 0b01000000000000000000000000000000:
                out.writeByte(0b11111100 | (codepoint >>> 30).toByte());
                trail = 5;
                break;

            default: throw new IllegalUTF($"illegal character codepoint: {codepoint}");
            }

        // write out trailing bytes; each has the same "10xxxxxx" format with 6
        // bits of data
        while (trail > 0)
            {
            out.writeByte(0b10_000000 | (codepoint >>> --trail * 6 & 0b00_111111));
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
    static void writeUTF16Char(DataOutput out, Char ch)
        {
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
    static void writeUTF16Codepoint(DataOutput out, UInt32 codepoint)
        {
        if (codepoint > 0xFFFF)
            {
            // the high ten bits (in the range 0x000–0x3FF) are encoded in the range 0xD800–0xDBFF;
            // the low ten bits (in the range 0x000–0x3FF) are encoded in the range 0xDC00–0xDFFF
            codepoint -= 0x10000;
            UInt32 nHiLo = 0xD800DC00
                         | (codepoint & 0xFFC << 6)
                         | (codepoint & 0x3FF     );
            out.writeBytes(nHiLo.toByteArray());
            }
        else
            {
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
    static void writeUTF32Char(DataOutput out, Char ch)
        {
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
    static void writeUTF32Codepoint(DataOutput out, UInt32 codepoint)
        {
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
    static void writeAsciiStringZ(DataOutput out, String s, function Byte(Char) convToAscii = _ -> '?'.toByte())
        {
        for (Char ch : s)
            {
            UInt32 codepoint = ch.codepoint;
            Byte   byte      = 0 < codepoint <= 0x7F ? codepoint : convToAscii(ch);
            out.writeByte(byte);
            if (byte == 0)
                {
                return;
                }
            }
        out.writeByte(0);
        }
    }