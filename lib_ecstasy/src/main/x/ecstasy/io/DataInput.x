import io.IllegalUTF;

/**
 * The DataInput interface represents a stream of values of various fundamental Ecstasy types. It
 * provides default implementations for some methods, but does not prescribe an underlying data
 * format. For example, integers could be fixed length or compressed, and characters could be
 * encoded as UTF-8, UTF-16, UTF-32, or even ASCII.
 */
interface DataInput
        extends BinaryInput {
    /**
     * @return  a value of type Boolean read from the stream
     */
    Boolean readBoolean() = readByte() != 0;

    /**
     * @return  a value of type Char read from the stream
     */
    Char readChar() = new Char(readUInt32());

    /**
     * @return  a value of type String read from the stream
     */
    String readString() {
        Int length = this.readInt(); // TODO CP
        if (length == 0) {
            return "";
        }

        assert length > 0;
        Char[] chars = new Char[length];
        for (Int i : 0 ..< length) {
            chars[i] = readChar();
        }
        return new String(chars);
    }

    /**
     * @return  a value of the specified enumeration type read from the stream
     */
    <EnumType extends Enum> EnumType readEnum(Enumeration<EnumType> enumeration) {
        Int n = readUInt16();
        assert 0 <= n < enumeration.count;
        return enumeration.values[n];
    }

    /**
     * @return  a value of type Int8 read from the stream
     */
    Int8 readInt8() = new Int8(readBytes(1));

    /**
     * @return  a value of type Int16 read from the stream
     */
    Int16 readInt16() = new Int16(readBytes(2));

    /**
     * @return  a value of type Int32 read from the stream
     */
    Int32 readInt32() = new Int32(readBytes(4));

    /**
     * @return  a value of type Int64 (aka "Int") read from the stream
     */
    Int64 readInt64() = new Int64(readBytes(8));

    /**
     * @return  a value of type Int128 read from the stream
     */
    Int128 readInt128() = new Int128(readBytes(16));

    /**
     * @return  a value of type IntN read from the stream
     */
    IntN readIntN() = new IntN(readBytes(readInt()));

    /**
     * @return  a value of type UInt8 read from the stream
     */
    UInt8 readUInt8() = readByte();

    /**
     * @return  a value of type UInt16 read from the stream
     */
    UInt16 readUInt16() = new UInt16(readBytes(2));

    /**
     * @return  a value of type UInt32 read from the stream
     */
    UInt32 readUInt32() = new UInt32(readBytes(4));

    /**
     * @return  a value of type UInt64 (aka "UInt") read from the stream
     */
    UInt64 readUInt64() = new UInt64(readBytes(8));

    /**
     * @return  a value of type UInt128 read from the stream
     */
    UInt128 readUInt128() = new UInt128(readBytes(16));

    /**
     * @return  a value of type UIntN read from the stream
     */
    UIntN readUIntN() = new UIntN(readBytes(readInt()));

    /**
     * @return  a value of type Dec64 read from the stream
     */
    Dec32 readDec32() = new Dec32(readBytes(4));

    /**
     * @return  a value of type Dec64 read from the stream
     */
    Dec64 readDec64() = new Dec64(readBytes(8));

    /**
     * @return  a value of type Dec128 read from the stream
     */
    Dec128 readDec128() = new Dec128(readBytes(16));

    /**
     * @return  a value of type DecN read from the stream
     */
    DecN readDecN() = new DecN(readBytes(readInt()));

    /**
     * @return  a value of type Float8e4 read from the stream
     */
    Float8e4 readFloat8e4() = new Float8e4(readByte().bits);

    /**
     * @return  a value of type Float8e5 read from the stream
     */
    Float8e5 readFloat8e5() = new Float8e5(readByte().bits);

    /**
     * @return  a value of type BFloat16 read from the stream
     */
    BFloat16 readBFloat16() = new BFloat16(readBytes(2));

    /**
     * @return  a value of type Float16 read from the stream
     */
    Float16 readFloat16() = new Float16(readBytes(2));

    /**
     * @return  a value of type Float32 read from the stream
     */
    Float32 readFloat32() = new Float32(readBytes(4));

    /**
     * @return  a value of type Float64 read from the stream
     */
    Float64 readFloat64() = new Float64(readBytes(8));

    /**
     * @return  a value of type Float128 read from the stream
     */
    Float128 readFloat128()= new Float128(readBytes(16));

    /**
     * @return  a value of type FloatN read from the stream
     */
    FloatN readFloatN() = new FloatN(readBytes(readInt()));

    /**
     * @return  a value of type Date read from the stream
     */
    Date readDate() = new Date(readInt32());

    /**
     * @return  a value of type TimeOfDay read from the stream
     */
    TimeOfDay readTime() = new TimeOfDay(readInt());

    /**
     * @return  a value of type Time read from the stream
     */
    Time readTime() = new Time(readInt128(), readTimeZone());

    /**
     * @return  a value of type TimeZone read from the stream
     */
    TimeZone readTimeZone() {
        switch (Byte b = readByte()) {
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
    Duration readDuration() {
        return new Duration(readInt128());
    }


    // ----- aliases -------------------------------------------------------------------------------

    /**
     * Alias for the `readInt64()` method, since the `Int64` type is aliased as `Int`.
     */
    static Method<DataInput, <>, <Int>> readInt = readInt64;

    /**
     * Alias for the `readUInt64()` method, since the `UInt64` type is aliased as `UInt`.
     */
    static Method<DataInput, <>, <UInt>> readUInt = readUInt64;


    // ----- helper functions ----------------------------------------------------------------------

    static Int8 Huge = Byte:0b111111_00.toInt8();

    /**
     * Read an integer from the passed stream, which is encoded in the packed integer format. The format (XVM Integer
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
     * @param in  the stream to read from
     *
     * @return the Int value from the stream
     *
     * @throws OutOfBounds  if the packed integer value can not fit into an Int
     */
    static Int readPackedInt(DataInput in) {
        // small format: 1 byte value -64..127
        Byte b = in.readByte();
        if (b & 0xC0 != 0x80) {
            // convert the Byte to a signed Int8 to obtain automatic sign extension to the Int64
            return b.toInt8();
        }

        if (b & 0x20 == 0) {
            // medium format: 13 bit int, combines 5 bits + next byte (and sign extend)
            return b.toInt64() << 8 | in.readByte() << 51 >> 51;
        }

        // large format: trail mode: next x+1 (2-32) bytes
        Int byteCount = 1 + (b & 0x1F);
        assert:bounds 2 <= byteCount <= 8;   // note: no huge format support for an Int result (avoids recursion)

        Int n = in.readByte().toInt8();
        while (--byteCount > 0) {
            n = n << 8 | in.readByte();
        }
        return n;
    }

    /**
     * Read an integer value that is formatted using the packed integer format, and return it as
     * a `IntN`.
     *
     * @see readPackedInt
     *
     * @param in  the DataInput stream containing the packed integer
     *
     * @return the resulting IntN value
     */
    static IntN readPackedIntN(DataInput in) {
        // small format: 1 byte value -64..127
        Byte b = in.readByte();
        if (b & 0xC0 != 0x80) {
            // convert the Byte to a signed Int8 to obtain automatic sign extension to the Int64
            return b.toInt8();
        }

        if (b & 0x20 == 0) {
            // medium format: 13 bit int, combines 5 bits + next byte (and sign extend)
            return b.toInt64() << 8 | in.readByte() << 51 >> 51;
        }

        // large format: trail mode: next x+1 (2-32) bytes
        Int byteCount = 1 + (b & 0x1F);
        if (byteCount == 1) {
            // huge format: the actual byte length comes next in the stream
            byteCount = readPackedInt(in);
        }
        assert:bounds byteCount >= 2;
        return new IntN(in.readBytes(byteCount));
    }
}