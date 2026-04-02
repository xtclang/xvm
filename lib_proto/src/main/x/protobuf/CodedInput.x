/**
 * A reader for the Protocol Buffers binary wire format.
 *
 * CodedInput wraps a BinaryInput and provides methods to read protobuf-encoded values including
 * varints, fixed-width integers, length-delimited fields, and ZigZag-encoded signed integers.
 */
class CodedInput {

    construct(BinaryInput in) {
        this.in    = in;
        this.limit = Int.MaxValue;
    }

    /**
     * The underlying binary input stream.
     */
    private BinaryInput in;

    /**
     * The current byte limit for reading (used for sub-message parsing).
     */
    private Int limit;

    /**
     * The number of bytes read so far.
     */
    private Int bytesRead = 0;

    // ----- tag reading ---------------------------------------------------------------------------

    /**
     * Read the next field tag from the stream. Returns 0 if the end of the stream or the current
     * limit has been reached.
     *
     * @return the tag value, or 0 at end of input
     */
    Int readTag() {
        if (isAtEnd()) {
            return 0;
        }
        return readVarint().toInt();
    }

    /**
     * @return True if the stream is at the end or the current limit has been reached
     */
    Boolean isAtEnd() = bytesRead >= limit || in.eof;

    // ----- varint reading ------------------------------------------------------------------------

    /**
     * Read a raw varint (up to 64 bits) from the stream.
     *
     * Each byte contributes 7 bits of payload. The MSB is a continuation bit: 1 means more bytes
     * follow, 0 means this is the last byte. The 7-bit groups are in little-endian order.
     *
     * @return the decoded varint as an Int64
     */
    Int64 readVarint() {
        Int64 result = 0;
        Int   shift  = 0;
        while (shift < 64) {
            Byte b = readRawByte();
            result |= (b & 0x7F).toInt64() << shift;
            if (b & 0x80 == 0) {
                return result;
            }
            shift += 7;
        }
        throw new IllegalState("varint too long");
    }

    // ----- integer field readers -----------------------------------------------------------------

    /**
     * Read a protobuf `int32` field value (varint-encoded, two's complement for negatives).
     */
    Int32 readInt32() = readVarint().toInt32();

    /**
     * Read a protobuf `int64` field value (varint-encoded, two's complement for negatives).
     */
    Int64 readInt64() = readVarint();

    /**
     * Read a protobuf `uint32` field value (varint-encoded, unsigned).
     */
    UInt32 readUInt32() = readVarint().toUInt32();

    /**
     * Read a protobuf `uint64` field value (varint-encoded, unsigned).
     */
    UInt64 readUInt64() = readVarint().toUInt64();

    /**
     * Read a protobuf `sint32` field value (ZigZag-encoded).
     *
     * ZigZag encoding maps signed integers to unsigned integers so that numbers with a small
     * absolute value have a small varint encoding: 0 -> 0, -1 -> 1, 1 -> 2, -2 -> 3, ...
     *
     * Decode: `(n >>> 1) ^ -(n & 1)`
     */
    Int32 readSInt32() {
        Int32 n = readVarint().toInt32();
        return (n >>> 1) ^ -(n & 1);
    }

    /**
     * Read a protobuf `sint64` field value (ZigZag-encoded).
     */
    Int64 readSInt64() {
        Int64 n = readVarint();
        return (n >>> 1) ^ -(n & 1);
    }

    // ----- fixed-width readers -------------------------------------------------------------------

    /**
     * Read a protobuf `fixed32` field value (4 bytes, little-endian, unsigned).
     */
    UInt32 readFixed32() {
        return  readRawByte().toUInt32()
             | (readRawByte().toUInt32() <<  8)
             | (readRawByte().toUInt32() << 16)
             | (readRawByte().toUInt32() << 24);
    }

    /**
     * Read a protobuf `fixed64` field value (8 bytes, little-endian, unsigned).
     */
    UInt64 readFixed64() {
        return  readRawByte().toUInt64()
             | (readRawByte().toUInt64() <<  8)
             | (readRawByte().toUInt64() << 16)
             | (readRawByte().toUInt64() << 24)
             | (readRawByte().toUInt64() << 32)
             | (readRawByte().toUInt64() << 40)
             | (readRawByte().toUInt64() << 48)
             | (readRawByte().toUInt64() << 56);
    }

    /**
     * Read a protobuf `sfixed32` field value (4 bytes, little-endian, signed).
     */
    Int32 readSFixed32() = readFixed32().toInt32();

    /**
     * Read a protobuf `sfixed64` field value (8 bytes, little-endian, signed).
     */
    Int64 readSFixed64() = readFixed64().toInt64();

    // ----- floating-point readers ----------------------------------------------------------------

    /**
     * Read a protobuf `float` field value (4 bytes, IEEE 754 single-precision).
     */
    Float32 readFloat() {
        // Wire format is little-endian; Float32 constructor expects big-endian
        immutable Byte[] leBytes = readRawBytes(4);
        return new Float32(leBytes.reversed());
    }

    /**
     * Read a protobuf `double` field value (8 bytes, IEEE 754 double-precision).
     */
    Float64 readDouble() {
        // Wire format is little-endian; Float64 constructor expects big-endian
        immutable Byte[] leBytes = readRawBytes(8);
        return new Float64(leBytes.reversed());
    }

    // ----- bool and enum readers -----------------------------------------------------------------

    /**
     * Read a protobuf `bool` field value (varint, non-zero is True).
     */
    Boolean readBool() = readVarint() != 0;

    // ----- enum reader ---------------------------------------------------------------------------

    /**
     * Read a protobuf enum field value as a raw integer (varint-encoded).
     *
     * The caller is responsible for mapping the integer to the appropriate enum type using
     * [ProtoEnum.byProtoValue] or similar.
     *
     * @return the raw enum integer value
     */
    Int32 readEnum() = readInt32();

    // ----- length-delimited readers --------------------------------------------------------------

    /**
     * Read a protobuf `bytes` field value (length-prefixed raw bytes).
     *
     * @return the bytes read from the stream
     */
    immutable Byte[] readBytes() {
        Int length = readVarint().toInt();
        assert:arg length >= 0 as "Negative length in protobuf bytes field";
        return readRawBytes(length);
    }

    /**
     * Read a protobuf `string` field value (length-prefixed UTF-8).
     *
     * @return the decoded string
     */
    String readString() {
        Int length = readVarint().toInt();
        assert:arg length >= 0 as "Negative length in protobuf string field";
        if (length == 0) {
            return "";
        }
        return decodeUtf8(readRawBytes(length));
    }

    /**
     * Read a protobuf `bytes` field value as a ByteString.
     *
     * @return a ByteString containing the bytes read from the stream
     */
    ByteString readByteString() = new ByteString(readBytes());

    // ----- map entry readers ---------------------------------------------------------------------

    /**
     * Read a map<string, string> entry.
     *
     * A map entry is a length-delimited sub-message with field 1 = key and field 2 = value.
     *
     * @return the key and value
     */
    (String, String) readMapStringString() {
        String key   = "";
        String value = "";
        Int length   = readVarint().toInt();
        Int oldLimit = pushLimit(length);
        while (!isAtEnd()) {
            Int      tag         = readTag();
            if (tag == 0) {
                break;
            }
            Int      fieldNumber = WireType.getFieldNumber(tag);
            WireType wireType    = WireType.getWireType(tag);
            switch (fieldNumber) {
            case 1:
                key = readString();
                break;
            case 2:
                value = readString();
                break;
            default:
                skipField(wireType);
                break;
            }
        }
        popLimit(oldLimit);
        return (key, value);
    }

    /**
     * Read a map<string, int32> entry.
     *
     * @return the key and value
     */
    (String, Int32) readMapStringInt32() {
        String key   = "";
        Int32  value = 0;
        Int length   = readVarint().toInt();
        Int oldLimit = pushLimit(length);
        while (!isAtEnd()) {
            Int      tag         = readTag();
            if (tag == 0) {
                break;
            }
            Int      fieldNumber = WireType.getFieldNumber(tag);
            WireType wireType    = WireType.getWireType(tag);
            switch (fieldNumber) {
            case 1:
                key = readString();
                break;
            case 2:
                value = readInt32();
                break;
            default:
                skipField(wireType);
                break;
            }
        }
        popLimit(oldLimit);
        return (key, value);
    }

    /**
     * Read a map<string, int64> entry.
     *
     * @return the key and value
     */
    (String, Int64) readMapStringInt64() {
        String key   = "";
        Int64  value = 0;
        Int length   = readVarint().toInt();
        Int oldLimit = pushLimit(length);
        while (!isAtEnd()) {
            Int      tag         = readTag();
            if (tag == 0) {
                break;
            }
            Int      fieldNumber = WireType.getFieldNumber(tag);
            WireType wireType    = WireType.getWireType(tag);
            switch (fieldNumber) {
            case 1:
                key = readString();
                break;
            case 2:
                value = readInt64();
                break;
            default:
                skipField(wireType);
                break;
            }
        }
        popLimit(oldLimit);
        return (key, value);
    }

    /**
     * Read a map<string, bytes> entry.
     *
     * @return the key and value
     */
    (String, immutable Byte[]) readMapStringBytes() {
        String           key   = "";
        immutable Byte[] value = [];
        Int length   = readVarint().toInt();
        Int oldLimit = pushLimit(length);
        while (!isAtEnd()) {
            Int      tag         = readTag();
            if (tag == 0) {
                break;
            }
            Int      fieldNumber = WireType.getFieldNumber(tag);
            WireType wireType    = WireType.getWireType(tag);
            switch (fieldNumber) {
            case 1:
                key = readString();
                break;
            case 2:
                value = readBytes();
                break;
            default:
                skipField(wireType);
                break;
            }
        }
        popLimit(oldLimit);
        return (key, value);
    }

    /**
     * Read a map<int32, string> entry.
     *
     * @return the key and value
     */
    (Int32, String) readMapInt32String() {
        Int32  key   = 0;
        String value = "";
        Int length   = readVarint().toInt();
        Int oldLimit = pushLimit(length);
        while (!isAtEnd()) {
            Int      tag         = readTag();
            if (tag == 0) {
                break;
            }
            Int      fieldNumber = WireType.getFieldNumber(tag);
            WireType wireType    = WireType.getWireType(tag);
            switch (fieldNumber) {
            case 1:
                key = readInt32();
                break;
            case 2:
                value = readString();
                break;
            default:
                skipField(wireType);
                break;
            }
        }
        popLimit(oldLimit);
        return (key, value);
    }

    /**
     * Read a map<int32, int32> entry.
     *
     * @return the key and value
     */
    (Int32, Int32) readMapInt32Int32() {
        Int32 key   = 0;
        Int32 value = 0;
        Int length   = readVarint().toInt();
        Int oldLimit = pushLimit(length);
        while (!isAtEnd()) {
            Int      tag         = readTag();
            if (tag == 0) {
                break;
            }
            Int      fieldNumber = WireType.getFieldNumber(tag);
            WireType wireType    = WireType.getWireType(tag);
            switch (fieldNumber) {
            case 1:
                key = readInt32();
                break;
            case 2:
                value = readInt32();
                break;
            default:
                skipField(wireType);
                break;
            }
        }
        popLimit(oldLimit);
        return (key, value);
    }

    /**
     * Read a map<int64, string> entry.
     *
     * @return the key and value
     */
    (Int64, String) readMapInt64String() {
        Int64  key   = 0;
        String value = "";
        Int length   = readVarint().toInt();
        Int oldLimit = pushLimit(length);
        while (!isAtEnd()) {
            Int      tag         = readTag();
            if (tag == 0) {
                break;
            }
            Int      fieldNumber = WireType.getFieldNumber(tag);
            WireType wireType    = WireType.getWireType(tag);
            switch (fieldNumber) {
            case 1:
                key = readInt64();
                break;
            case 2:
                value = readString();
                break;
            default:
                skipField(wireType);
                break;
            }
        }
        popLimit(oldLimit);
        return (key, value);
    }

    /**
     * Read a map<int64, int64> entry.
     *
     * @return the key and value
     */
    (Int64, Int64) readMapInt64Int64() {
        Int64 key   = 0;
        Int64 value = 0;
        Int length   = readVarint().toInt();
        Int oldLimit = pushLimit(length);
        while (!isAtEnd()) {
            Int      tag         = readTag();
            if (tag == 0) {
                break;
            }
            Int      fieldNumber = WireType.getFieldNumber(tag);
            WireType wireType    = WireType.getWireType(tag);
            switch (fieldNumber) {
            case 1:
                key = readInt64();
                break;
            case 2:
                value = readInt64();
                break;
            default:
                skipField(wireType);
                break;
            }
        }
        popLimit(oldLimit);
        return (key, value);
    }

    /**
     * Read a map<bool, string> entry.
     *
     * @return the key and value
     */
    (Boolean, String) readMapBoolString() {
        Boolean key   = False;
        String  value = "";
        Int length   = readVarint().toInt();
        Int oldLimit = pushLimit(length);
        while (!isAtEnd()) {
            Int      tag         = readTag();
            if (tag == 0) {
                break;
            }
            Int      fieldNumber = WireType.getFieldNumber(tag);
            WireType wireType    = WireType.getWireType(tag);
            switch (fieldNumber) {
            case 1:
                key = readBool();
                break;
            case 2:
                value = readString();
                break;
            default:
                skipField(wireType);
                break;
            }
        }
        popLimit(oldLimit);
        return (key, value);
    }

    // ----- packed repeated field readers ---------------------------------------------------------

    /**
     * Read a packed repeated varint field. Reads the length prefix, then reads varints until the
     * sub-limit is reached.
     *
     * @return the list of decoded varint values
     */
    List<Int64> readPackedVarints() {
        Int length   = readVarint().toInt();
        Int oldLimit = pushLimit(length);
        List<Int64> result = new Array();
        while (!isAtEnd()) {
            result.add(readVarint());
        }
        popLimit(oldLimit);
        return result;
    }

    /**
     * Read a packed repeated int32 field.
     *
     * @return the list of decoded int32 values
     */
    List<Int32> readPackedInt32s() {
        Int length   = readVarint().toInt();
        Int oldLimit = pushLimit(length);
        List<Int32> result = new Array();
        while (!isAtEnd()) {
            result.add(readInt32());
        }
        popLimit(oldLimit);
        return result;
    }

    /**
     * Read a packed repeated sint32 field (ZigZag-encoded).
     *
     * @return the list of decoded sint32 values
     */
    List<Int32> readPackedSInt32s() {
        Int length   = readVarint().toInt();
        Int oldLimit = pushLimit(length);
        List<Int32> result = new Array();
        while (!isAtEnd()) {
            result.add(readSInt32());
        }
        popLimit(oldLimit);
        return result;
    }

    /**
     * Read a packed repeated sint64 field (ZigZag-encoded).
     *
     * @return the list of decoded sint64 values
     */
    List<Int64> readPackedSInt64s() {
        Int length   = readVarint().toInt();
        Int oldLimit = pushLimit(length);
        List<Int64> result = new Array();
        while (!isAtEnd()) {
            result.add(readSInt64());
        }
        popLimit(oldLimit);
        return result;
    }

    /**
     * Read a packed repeated fixed32 field.
     *
     * @return the list of decoded fixed32 values
     */
    List<UInt32> readPackedFixed32s() {
        Int length   = readVarint().toInt();
        Int oldLimit = pushLimit(length);
        List<UInt32> result = new Array();
        while (!isAtEnd()) {
            result.add(readFixed32());
        }
        popLimit(oldLimit);
        return result;
    }

    /**
     * Read a packed repeated fixed64 field.
     *
     * @return the list of decoded fixed64 values
     */
    List<UInt64> readPackedFixed64s() {
        Int length   = readVarint().toInt();
        Int oldLimit = pushLimit(length);
        List<UInt64> result = new Array();
        while (!isAtEnd()) {
            result.add(readFixed64());
        }
        popLimit(oldLimit);
        return result;
    }

    /**
     * Read a packed repeated float field.
     *
     * @return the list of decoded float values
     */
    List<Float32> readPackedFloats() {
        Int length   = readVarint().toInt();
        Int oldLimit = pushLimit(length);
        List<Float32> result = new Array();
        while (!isAtEnd()) {
            result.add(readFloat());
        }
        popLimit(oldLimit);
        return result;
    }

    /**
     * Read a packed repeated double field.
     *
     * @return the list of decoded double values
     */
    List<Float64> readPackedDoubles() {
        Int length   = readVarint().toInt();
        Int oldLimit = pushLimit(length);
        List<Float64> result = new Array();
        while (!isAtEnd()) {
            result.add(readDouble());
        }
        popLimit(oldLimit);
        return result;
    }

    /**
     * Read a packed repeated bool field.
     *
     * @return the list of decoded boolean values
     */
    List<Boolean> readPackedBools() {
        Int length   = readVarint().toInt();
        Int oldLimit = pushLimit(length);
        List<Boolean> result = new Array();
        while (!isAtEnd()) {
            result.add(readBool());
        }
        popLimit(oldLimit);
        return result;
    }

    // ----- sub-message support -------------------------------------------------------------------

    /**
     * Push a new byte limit for reading a sub-message. The limit is relative to the current
     * position. Call [popLimit] when done reading the sub-message.
     *
     * @param byteLimit  the number of bytes allowed for the sub-message
     *
     * @return the previous limit, to be passed to [popLimit]
     */
    Int pushLimit(Int byteLimit) {
        assert:arg byteLimit >= 0 as "Negative size limit";
        Int oldLimit = limit;
        limit = bytesRead + byteLimit;
        return oldLimit;
    }

    /**
     * Restore the previous byte limit after reading a sub-message.
     *
     * @param oldLimit  the value returned by the corresponding [pushLimit] call
     */
    void popLimit(Int oldLimit) {
        limit = oldLimit;
    }

    // ----- skip support --------------------------------------------------------------------------

    /**
     * Skip a field with the given wire type. This is used to skip unknown fields during
     * deserialization.
     *
     * @param wireType  the wire type of the field to skip
     */
    void skipField(WireType wireType) {
        switch (wireType) {
        case VARINT:
            readVarint();
            break;

        case I64:
            skipRawBytes(8);
            break;

        case LEN:
            Int length = readVarint().toInt();
            skipRawBytes(length);
            break;

        case SGROUP:
            // Skip fields until we find the matching EGROUP
            while (True) {
                Int      tag       = readTag();
                WireType innerType = WireType.getWireType(tag);
                if (innerType == EGROUP) {
                    break;
                }
                skipField(innerType);
            }
            break;

        case EGROUP:
            // Nothing to skip
            break;

        case I32:
            skipRawBytes(4);
            break;
        }
    }

    // ----- raw byte access -----------------------------------------------------------------------

    /**
     * Read a single raw byte from the underlying stream and update the bytes-read counter.
     *
     * @return the byte read
     */
    Byte readRawByte() {
        bytesRead++;
        return in.readByte();
    }

    /**
     * Read the specified number of raw bytes from the underlying stream.
     *
     * @param count  the number of bytes to read
     *
     * @return an immutable byte array
     */
    immutable Byte[] readRawBytes(Int count) {
        bytesRead += count;
        return in.readBytes(count);
    }

    /**
     * Skip the specified number of bytes in the underlying stream.
     *
     * @param count  the number of bytes to skip
     */
    void skipRawBytes(Int count) {
        for (Int i = 0; i < count; i++) {
            readRawByte();
        }
    }

    // ----- UTF-8 decoding ------------------------------------------------------------------------

    /**
     * Decode a UTF-8 encoded byte array into a String.
     *
     * @param bytes  the UTF-8 encoded bytes
     *
     * @return the decoded string
     */
    static String decodeUtf8(Byte[] bytes) {
        if (bytes.empty) {
            return "";
        }
        StringBuffer buf   = new StringBuffer(bytes.size);
        Int          index = 0;
        while (index < bytes.size) {
            Byte   b  = bytes[index++];
            UInt32 cp;
            if (b & 0x80 == 0) {
                cp = b.toUInt32();
            } else if (b & 0xE0 == 0xC0) {
                cp =  ((b & 0x1F).toUInt32() << 6)
                   | (bytes[index++] & 0x3F).toUInt32();
            } else if (b & 0xF0 == 0xE0) {
                cp =  ((b & 0x0F).toUInt32() << 12)
                   | ((bytes[index++] & 0x3F).toUInt32() << 6)
                   | (bytes[index++] & 0x3F).toUInt32();
            } else {
                cp =  ((b & 0x07).toUInt32() << 18)
                   | ((bytes[index++] & 0x3F).toUInt32() << 12)
                   | ((bytes[index++] & 0x3F).toUInt32() << 6)
                   | (bytes[index++] & 0x3F).toUInt32();
            }
            buf.add(cp.toChar());
        }
        return buf.toString();
    }
}
