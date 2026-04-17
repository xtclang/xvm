/**
 * A writer for the Protocol Buffers binary wire format.
 *
 * CodedOutput wraps a BinaryOutput and provides methods to write protobuf-encoded values including
 * varints, fixed-width integers, length-delimited fields, and ZigZag-encoded signed integers.
 */
class CodedOutput {

    construct(BinaryOutput out) {
        this.out = out;
    }

    /**
     * The underlying binary output stream.
     */
    private BinaryOutput out;

    // ----- tag writing ---------------------------------------------------------------------------

    /**
     * Write a field tag (field number + wire type) as a varint.
     *
     * @param fieldNumber  the field number
     * @param wireType     the wire type
     */
    void writeTag(Int fieldNumber, WireType wireType) {
        writeVarint(WireType.makeTag(fieldNumber, wireType));
    }

    // ----- varint writing ------------------------------------------------------------------------

    /**
     * Write a value as a raw varint. Each byte contributes 7 bits of payload with the MSB as a
     * continuation bit.
     *
     * @param value  the value to encode
     */
    void writeVarint(Int64 value) {
        // Treat the value as unsigned bits
        UInt64 bits = value.toUInt64();
        while (bits > 0x7F) {
            writeRawByte((bits & 0x7F | 0x80).toByte());
            bits >>>= 7;
        }
        writeRawByte(bits.toByte());
    }

    // ----- integer field writers -----------------------------------------------------------------

    /**
     * Write a protobuf `int32` field (tag + varint).
     *
     * @param fieldNumber  the field number
     * @param value        the int32 value
     */
    void writeInt32(Int fieldNumber, Int32 value) {
        writeTag(fieldNumber, WireType.VARINT);
        writeVarint(value.toInt64());
    }

    /**
     * Write a protobuf `int64` field (tag + varint).
     *
     * @param fieldNumber  the field number
     * @param value        the int64 value
     */
    void writeInt64(Int fieldNumber, Int64 value) {
        writeTag(fieldNumber, WireType.VARINT);
        writeVarint(value);
    }

    /**
     * Write a protobuf `uint32` field (tag + varint).
     *
     * @param fieldNumber  the field number
     * @param value        the uint32 value
     */
    void writeUInt32(Int fieldNumber, UInt32 value) {
        writeTag(fieldNumber, WireType.VARINT);
        writeVarint(value.toInt64());
    }

    /**
     * Write a protobuf `uint64` field (tag + varint).
     *
     * @param fieldNumber  the field number
     * @param value        the uint64 value
     */
    void writeUInt64(Int fieldNumber, UInt64 value) {
        writeTag(fieldNumber, WireType.VARINT);
        writeVarint(value.toInt64());
    }

    /**
     * Write a protobuf `sint32` field (tag + ZigZag-encoded varint).
     *
     * ZigZag encoding: `(n << 1) ^ (n >> 31)`
     *
     * @param fieldNumber  the field number
     * @param value        the sint32 value
     */
    void writeSInt32(Int fieldNumber, Int32 value) {
        writeTag(fieldNumber, WireType.VARINT);
        writeVarint(((value << 1) ^ (value >> 31)).toInt64());
    }

    /**
     * Write a protobuf `sint64` field (tag + ZigZag-encoded varint).
     *
     * ZigZag encoding: `(n << 1) ^ (n >> 63)`
     *
     * @param fieldNumber  the field number
     * @param value        the sint64 value
     */
    void writeSInt64(Int fieldNumber, Int64 value) {
        writeTag(fieldNumber, WireType.VARINT);
        writeVarint((value << 1) ^ (value >> 63));
    }

    // ----- fixed-width writers -------------------------------------------------------------------

    /**
     * Write a protobuf `fixed32` field (tag + 4-byte little-endian).
     *
     * @param fieldNumber  the field number
     * @param value        the fixed32 value
     */
    void writeFixed32(Int fieldNumber, UInt32 value) {
        writeTag(fieldNumber, WireType.I32);
        writeFixed32Value(value);
    }

    /**
     * Write a protobuf `fixed64` field (tag + 8-byte little-endian).
     *
     * @param fieldNumber  the field number
     * @param value        the fixed64 value
     */
    void writeFixed64(Int fieldNumber, UInt64 value) {
        writeTag(fieldNumber, WireType.I64);
        writeFixed64Value(value);
    }

    /**
     * Write a protobuf `sfixed32` field (tag + 4-byte little-endian).
     *
     * @param fieldNumber  the field number
     * @param value        the sfixed32 value
     */
    void writeSFixed32(Int fieldNumber, Int32 value) {
        writeTag(fieldNumber, WireType.I32);
        writeFixed32Value(value.toUInt32());
    }

    /**
     * Write a protobuf `sfixed64` field (tag + 8-byte little-endian).
     *
     * @param fieldNumber  the field number
     * @param value        the sfixed64 value
     */
    void writeSFixed64(Int fieldNumber, Int64 value) {
        writeTag(fieldNumber, WireType.I64);
        writeFixed64Value(value.toUInt64());
    }

    // ----- floating-point writers ----------------------------------------------------------------

    /**
     * Write a protobuf `float` field (tag + 4-byte IEEE 754).
     *
     * @param fieldNumber  the field number
     * @param value        the float value
     */
    void writeFloat(Int fieldNumber, Float32 value) {
        writeTag(fieldNumber, WireType.I32);
        // Float32.toByteArray() returns big-endian; protobuf needs little-endian
        Byte[] bytes = value.toByteArray();
        for (Int i = bytes.size - 1; i >= 0; i--) {
            writeRawByte(bytes[i]);
        }
    }

    /**
     * Write a protobuf `double` field (tag + 8-byte IEEE 754).
     *
     * @param fieldNumber  the field number
     * @param value        the double value
     */
    void writeDouble(Int fieldNumber, Float64 value) {
        writeTag(fieldNumber, WireType.I64);
        // Float64.toByteArray() returns big-endian; protobuf needs little-endian
        Byte[] bytes = value.toByteArray();
        for (Int i = bytes.size - 1; i >= 0; i--) {
            writeRawByte(bytes[i]);
        }
    }

    // ----- bool writer ---------------------------------------------------------------------------

    /**
     * Write a protobuf `bool` field (tag + varint 0 or 1).
     *
     * @param fieldNumber  the field number
     * @param value        the boolean value
     */
    void writeBool(Int fieldNumber, Boolean value) {
        writeTag(fieldNumber, WireType.VARINT);
        writeRawByte(value ? 1 : 0);
    }

    // ----- enum writer ---------------------------------------------------------------------------

    /**
     * Write a protobuf enum field (tag + varint).
     *
     * Protobuf enums are encoded identically to int32 on the wire.
     *
     * @param fieldNumber  the field number
     * @param value        the enum value
     */
    void writeEnum(Int fieldNumber, ProtoEnum value) {
        writeInt32(fieldNumber, value.protoValue.toInt32());
    }

    /**
     * Write a protobuf enum field using a raw integer value (tag + varint).
     *
     * This is useful when the enum value is not known at compile time or when preserving
     * unrecognized enum values.
     *
     * @param fieldNumber  the field number
     * @param value        the raw enum integer value
     */
    void writeEnumValue(Int fieldNumber, Int32 value) =
        writeInt32(fieldNumber, value);

    // ----- length-delimited writers --------------------------------------------------------------

    /**
     * Write a protobuf `bytes` field (tag + length varint + raw bytes).
     *
     * @param fieldNumber  the field number
     * @param value        the byte array
     */
    void writeBytes(Int fieldNumber, Byte[] value) {
        writeTag(fieldNumber, WireType.LEN);
        writeVarint(value.size.toInt64());
        out.writeBytes(value);
    }

    /**
     * Write a protobuf `string` field (tag + length varint + UTF-8 bytes).
     *
     * @param fieldNumber  the field number
     * @param value        the string value
     */
    void writeString(Int fieldNumber, String value) {
        writeTag(fieldNumber, WireType.LEN);
        immutable Byte[] utf8Bytes = value.utf8();
        writeVarint(utf8Bytes.size.toInt64());
        out.writeBytes(utf8Bytes);
    }

    /**
     * Write a protobuf embedded message field (tag + length varint + serialized message).
     *
     * The message's [serializedSize] is used to compute the length prefix, then the message is
     * serialized directly to this output stream.
     *
     * @param fieldNumber  the field number
     * @param value        the message to write
     */
    void writeMessage(Int fieldNumber, MessageLite value) {
        writeTag(fieldNumber, WireType.LEN);
        writeVarint(value.serializedSize().toInt64());
        value.writeTo(this);
    }

    // ----- map field writers ---------------------------------------------------------------------

    /**
     * Write a single map entry as a length-delimited sub-message containing key (field 1) and value
     * (field 2).
     *
     * Protobuf maps are encoded on the wire as:
     * ```
     * tag(fieldNumber, LEN) + length + tag(1, keyWireType) + key + tag(2, valueWireType) + value
     * ```
     *
     * @param fieldNumber  the map field number
     * @param entrySize    the pre-computed size of the entry content (key field + value field)
     * @param writer       a function that writes the key (field 1) and value (field 2) fields
     */
    void writeMapEntry(Int fieldNumber, Int entrySize, function void(CodedOutput) writer) {
        writeTag(fieldNumber, WireType.LEN);
        writeVarint(entrySize.toInt64());
        writer(this);
    }

    /**
     * Write a map<string, string> entry.
     */
    void writeMapStringString(Int fieldNumber, String key, String value) {
        Int entrySize = computeStringSize(1, key) + computeStringSize(2, value);
        writeMapEntry(fieldNumber, entrySize, o -> {
            o.writeString(1, key);
            o.writeString(2, value);
        });
    }

    /**
     * Write a map<string, int32> entry.
     */
    void writeMapStringInt32(Int fieldNumber, String key, Int32 value) {
        Int entrySize = computeStringSize(1, key) + computeInt32Size(2, value);
        writeMapEntry(fieldNumber, entrySize, o -> {
            o.writeString(1, key);
            o.writeInt32(2, value);
        });
    }

    /**
     * Write a map<string, int64> entry.
     */
    void writeMapStringInt64(Int fieldNumber, String key, Int64 value) {
        Int entrySize = computeStringSize(1, key) + computeInt64Size(2, value);
        writeMapEntry(fieldNumber, entrySize, o -> {
            o.writeString(1, key);
            o.writeInt64(2, value);
        });
    }

    /**
     * Write a map<string, bytes> entry.
     */
    void writeMapStringBytes(Int fieldNumber, String key, Byte[] value) {
        Int entrySize = computeStringSize(1, key) + computeBytesSize(2, value);
        writeMapEntry(fieldNumber, entrySize, o -> {
            o.writeString(1, key);
            o.writeBytes(2, value);
        });
    }

    /**
     * Write a map<string, MessageLite> entry.
     */
    void writeMapStringMessage(Int fieldNumber, String key, MessageLite value) {
        Int entrySize = computeStringSize(1, key) + computeMessageSize(2, value);
        writeMapEntry(fieldNumber, entrySize, o -> {
            o.writeString(1, key);
            o.writeMessage(2, value);
        });
    }

    /**
     * Write a map<int32, string> entry.
     */
    void writeMapInt32String(Int fieldNumber, Int32 key, String value) {
        Int entrySize = computeInt32Size(1, key) + computeStringSize(2, value);
        writeMapEntry(fieldNumber, entrySize, o -> {
            o.writeInt32(1, key);
            o.writeString(2, value);
        });
    }

    /**
     * Write a map<int32, int32> entry.
     */
    void writeMapInt32Int32(Int fieldNumber, Int32 key, Int32 value) {
        Int entrySize = computeInt32Size(1, key) + computeInt32Size(2, value);
        writeMapEntry(fieldNumber, entrySize, o -> {
            o.writeInt32(1, key);
            o.writeInt32(2, value);
        });
    }

    /**
     * Write a map<int64, string> entry.
     */
    void writeMapInt64String(Int fieldNumber, Int64 key, String value) {
        Int entrySize = computeInt64Size(1, key) + computeStringSize(2, value);
        writeMapEntry(fieldNumber, entrySize, o -> {
            o.writeInt64(1, key);
            o.writeString(2, value);
        });
    }

    /**
     * Write a map<int64, int64> entry.
     */
    void writeMapInt64Int64(Int fieldNumber, Int64 key, Int64 value) {
        Int entrySize = computeInt64Size(1, key) + computeInt64Size(2, value);
        writeMapEntry(fieldNumber, entrySize, o -> {
            o.writeInt64(1, key);
            o.writeInt64(2, value);
        });
    }

    /**
     * Write a map<bool, string> entry.
     */
    void writeMapBoolString(Int fieldNumber, Boolean key, String value) {
        Int entrySize = computeBoolSize(1) + computeStringSize(2, value);
        writeMapEntry(fieldNumber, entrySize, o -> {
            o.writeBool(1, key);
            o.writeString(2, value);
        });
    }

    // ----- packed repeated field writers ---------------------------------------------------------

    /**
     * Write a packed repeated varint field (tag + length + concatenated varints).
     *
     * @param fieldNumber  the field number
     * @param values       the varint values to pack
     */
    void writePackedVarints(Int fieldNumber, List<IntLiteral> values) {
        List<UInt64> uint64Values = values.map(v -> v.toUInt64()).toArray();
        writePackedVarints(fieldNumber, uint64Values);
    }

    /**
     * Write a packed repeated varint field (tag + length + concatenated varints).
     *
     * @param fieldNumber  the field number
     * @param values       the varint values to pack
     */
    void writePackedVarints(Int fieldNumber, List<IntNumber> values) {
        if (values.empty) {
            return;
        }
        writeTag(fieldNumber, WireType.LEN);
        Int dataSize = 0;
        for (IntNumber v : values) {
            dataSize += computeVarintSize(v);
        }
        writeVarint(dataSize);
        for (IntNumber v : values) {
            writeVarint(v.toInt64());
        }
    }

    /**
     * Write a packed repeated fixed32 field (tag + length + concatenated 4-byte values).
     *
     * @param fieldNumber  the field number
     * @param values       the fixed32 values to pack
     */
    void writePackedFixed32s(Int fieldNumber, List<UInt32> values) {
        if (values.empty) {
            return;
        }
        writeTag(fieldNumber, WireType.LEN);
        writeVarint((values.size * 4).toInt64());
        for (UInt32 v : values) {
            writeFixed32Value(v);
        }
    }

    /**
     * Write a packed repeated fixed64 field (tag + length + concatenated 8-byte values).
     *
     * @param fieldNumber  the field number
     * @param values       the fixed64 values to pack
     */
    void writePackedFixed64s(Int fieldNumber, List<UInt64> values) {
        if (values.empty) {
            return;
        }
        writeTag(fieldNumber, WireType.LEN);
        writeVarint((values.size * 8).toInt64());
        for (UInt64 v : values) {
            writeFixed64Value(v);
        }
    }

    /**
     * Write a packed repeated sint32 field (tag + length + concatenated ZigZag varints).
     *
     * @param fieldNumber  the field number
     * @param values       the sint32 values to pack
     */
    void writePackedSInt32s(Int fieldNumber, List<Int32> values) {
        if (values.empty) {
            return;
        }
        writeTag(fieldNumber, WireType.LEN);
        Int dataSize = 0;
        for (Int32 v : values) {
            dataSize += computeVarintSize(((v << 1) ^ (v >> 31)));
        }
        writeVarint(dataSize.toInt64());
        for (Int32 v : values) {
            writeVarint(((v << 1) ^ (v >> 31)).toInt64());
        }
    }

    /**
     * Write a packed repeated sint64 field (tag + length + concatenated ZigZag varints).
     *
     * @param fieldNumber  the field number
     * @param values       the sint64 values to pack
     */
    void writePackedSInt64s(Int fieldNumber, List<Int64> values) {
        if (values.empty) {
            return;
        }
        writeTag(fieldNumber, WireType.LEN);
        Int dataSize = 0;
        for (Int64 v : values) {
            dataSize += computeVarintSize((v << 1) ^ (v >> 63));
        }
        writeVarint(dataSize.toInt64());
        for (Int64 v : values) {
            writeVarint((v << 1) ^ (v >> 63));
        }
    }

    /**
     * Write a packed repeated float field (tag + length + concatenated 4-byte IEEE 754 values).
     *
     * @param fieldNumber  the field number
     * @param values       the float values to pack
     */
    void writePackedFloats(Int fieldNumber, List<Float32> values) {
        if (values.empty) {
            return;
        }
        writeTag(fieldNumber, WireType.LEN);
        writeVarint((values.size * 4).toInt64());
        for (Float32 v : values) {
            Byte[] bytes = v.toByteArray();
            for (Int i = bytes.size - 1; i >= 0; i--) {
                writeRawByte(bytes[i]);
            }
        }
    }

    /**
     * Write a packed repeated double field (tag + length + concatenated 8-byte IEEE 754 values).
     *
     * @param fieldNumber  the field number
     * @param values       the double values to pack
     */
    void writePackedDoubles(Int fieldNumber, List<Float64> values) {
        if (values.empty) {
            return;
        }
        writeTag(fieldNumber, WireType.LEN);
        writeVarint((values.size * 8).toInt64());
        for (Float64 v : values) {
            Byte[] bytes = v.toByteArray();
            for (Int i = bytes.size - 1; i >= 0; i--) {
                writeRawByte(bytes[i]);
            }
        }
    }

    /**
     * Write a packed repeated bool field (tag + length + concatenated varint 0/1 values).
     *
     * @param fieldNumber  the field number
     * @param values       the boolean values to pack
     */
    void writePackedBools(Int fieldNumber, List<Boolean> values) {
        if (values.empty) {
            return;
        }
        writeTag(fieldNumber, WireType.LEN);
        writeVarint(values.size.toInt64());
        for (Boolean v : values) {
            writeRawByte(v ? 1 : 0);
        }
    }

    // ----- size computation ----------------------------------------------------------------------

    /**
     * Compute the number of bytes needed to encode a varint value.
     *
     * @param value  the value to measure
     *
     * @return the number of bytes required
     */
    static Int computeVarintSize(IntLiteral value) {
        UInt64 bits = value.toUInt64();
        return computeVarintSize(bits);
    }

    /**
     * Compute the number of bytes needed to encode a varint value.
     *
     * @param value  the value to measure
     *
     * @return the number of bytes required
     */
    static Int computeVarintSize(IntNumber value) {
        UInt64 bits = value.toUInt64();
        Int size = 1;
        while (bits > 0x7F) {
            size++;
            bits >>>= 7;
        }
        return size;
    }

    /**
     * Compute the number of bytes needed to encode a tag.
     *
     * @param fieldNumber  the field number
     *
     * @return the number of bytes required for the tag
     */
    static Int computeTagSize(Int fieldNumber) =
        computeVarintSize(WireType.makeTag(fieldNumber, WireType.VARINT));

    /**
     * Compute the total bytes needed to encode an `int32` field (tag + varint).
     */
    static Int computeInt32Size(Int fieldNumber, Int32 value) =
        computeTagSize(fieldNumber) + computeVarintSize(value);

    /**
     * Compute the total bytes needed to encode an `int64` field (tag + varint).
     */
    static Int computeInt64Size(Int fieldNumber, Int64 value) =
        computeTagSize(fieldNumber) + computeVarintSize(value);

    /**
     * Compute the total bytes needed to encode a `uint32` field (tag + varint).
     */
    static Int computeUInt32Size(Int fieldNumber, UInt32 value) =
        computeTagSize(fieldNumber) + computeVarintSize(value);

    /**
     * Compute the total bytes needed to encode a `uint64` field (tag + varint).
     */
    static Int computeUInt64Size(Int fieldNumber, UInt64 value) =
        computeTagSize(fieldNumber) + computeVarintSize(value);

    /**
     * Compute the total bytes needed to encode a `sint32` field (tag + ZigZag varint).
     */
    static Int computeSInt32Size(Int fieldNumber, Int32 value) =
        computeTagSize(fieldNumber) + computeVarintSize(((value << 1) ^ (value >> 31)));

    /**
     * Compute the total bytes needed to encode a `sint64` field (tag + ZigZag varint).
     */
    static Int computeSInt64Size(Int fieldNumber, Int64 value) =
        computeTagSize(fieldNumber) + computeVarintSize((value << 1) ^ (value >> 63));

    /**
     * Compute the total bytes needed to encode a `fixed32` field (tag + 4 bytes).
     */
    static Int computeFixed32Size(Int fieldNumber) = computeTagSize(fieldNumber) + 4;

    /**
     * Compute the total bytes needed to encode a `fixed64` field (tag + 8 bytes).
     */
    static Int computeFixed64Size(Int fieldNumber) = computeTagSize(fieldNumber) + 8;

    /**
     * Compute the total bytes needed to encode a `bool` field (tag + 1 byte).
     */
    static Int computeBoolSize(Int fieldNumber) = computeTagSize(fieldNumber) + 1;

    /**
     * Compute the total bytes needed to encode an enum field (tag + varint).
     */
    static Int computeEnumSize(Int fieldNumber, ProtoEnum value) =
        computeInt32Size(fieldNumber, value.protoValue.toInt32());

    /**
     * Compute the total bytes needed to encode an enum field using a raw integer value.
     */
    static Int computeEnumValueSize(Int fieldNumber, Int32 value) =
        computeInt32Size(fieldNumber, value);

    /**
     * Compute the total bytes needed to encode a `string` field (tag + length varint + UTF-8
     * bytes).
     */
    static Int computeStringSize(Int fieldNumber, String value) {
        Int utf8Length = value.utf8().size;
        return computeTagSize(fieldNumber) + computeVarintSize(utf8Length) + utf8Length;
    }

    /**
     * Compute the total bytes needed to encode a `bytes` field (tag + length varint + raw bytes).
     */
    static Int computeBytesSize(Int fieldNumber, Byte[] value) =
        computeTagSize(fieldNumber) + computeVarintSize(value.size) + value.size;

    /**
     * Compute the total bytes needed to encode an embedded message field (tag + length varint +
     * serialized message).
     */
    static Int computeMessageSize(Int fieldNumber, MessageLite value) {
        Int messageSize = value.serializedSize();
        return computeTagSize(fieldNumber) + computeVarintSize(messageSize) + messageSize;
    }

    // ----- map size computation ------------------------------------------------------------------

    /**
     * Compute the size of a single map entry sub-message (tag + length varint + entry content).
     *
     * @param fieldNumber  the map field number
     * @param entrySize    the size of the entry content (key field + value field)
     *
     * @return the total bytes needed
     */
    static Int computeMapEntrySize(Int fieldNumber, Int entrySize) =
        computeTagSize(fieldNumber) + computeVarintSize(entrySize) + entrySize;

    /**
     * Compute the size of a map<string, string> entry.
     */
    static Int computeMapStringStringSize(Int fieldNumber, String key, String value) {
        Int entrySize = computeStringSize(1, key) + computeStringSize(2, value);
        return computeMapEntrySize(fieldNumber, entrySize);
    }

    /**
     * Compute the size of a map<string, int32> entry.
     */
    static Int computeMapStringInt32Size(Int fieldNumber, String key, Int32 value) {
        Int entrySize = computeStringSize(1, key) + computeInt32Size(2, value);
        return computeMapEntrySize(fieldNumber, entrySize);
    }

    /**
     * Compute the size of a map<string, int64> entry.
     */
    static Int computeMapStringInt64Size(Int fieldNumber, String key, Int64 value) {
        Int entrySize = computeStringSize(1, key) + computeInt64Size(2, value);
        return computeMapEntrySize(fieldNumber, entrySize);
    }

    /**
     * Compute the size of a map<string, bytes> entry.
     */
    static Int computeMapStringBytesSize(Int fieldNumber, String key, Byte[] value) {
        Int entrySize = computeStringSize(1, key) + computeBytesSize(2, value);
        return computeMapEntrySize(fieldNumber, entrySize);
    }

    /**
     * Compute the size of a map<string, MessageLite> entry.
     */
    static Int computeMapStringMessageSize(Int fieldNumber, String key, MessageLite value) {
        Int entrySize = computeStringSize(1, key) + computeMessageSize(2, value);
        return computeMapEntrySize(fieldNumber, entrySize);
    }

    /**
     * Compute the size of a map<int32, string> entry.
     */
    static Int computeMapInt32StringSize(Int fieldNumber, Int32 key, String value) {
        Int entrySize = computeInt32Size(1, key) + computeStringSize(2, value);
        return computeMapEntrySize(fieldNumber, entrySize);
    }

    /**
     * Compute the size of a map<int32, int32> entry.
     */
    static Int computeMapInt32Int32Size(Int fieldNumber, Int32 key, Int32 value) {
        Int entrySize = computeInt32Size(1, key) + computeInt32Size(2, value);
        return computeMapEntrySize(fieldNumber, entrySize);
    }

    /**
     * Compute the size of a map<int64, string> entry.
     */
    static Int computeMapInt64StringSize(Int fieldNumber, Int64 key, String value) {
        Int entrySize = computeInt64Size(1, key) + computeStringSize(2, value);
        return computeMapEntrySize(fieldNumber, entrySize);
    }

    /**
     * Compute the size of a map<int64, int64> entry.
     */
    static Int computeMapInt64Int64Size(Int fieldNumber, Int64 key, Int64 value) {
        Int entrySize = computeInt64Size(1, key) + computeInt64Size(2, value);
        return computeMapEntrySize(fieldNumber, entrySize);
    }

    /**
     * Compute the size of a map<bool, string> entry.
     */
    static Int computeMapBoolStringSize(Int fieldNumber, Boolean key, String value) {
        Int entrySize = computeBoolSize(1) + computeStringSize(2, value);
        return computeMapEntrySize(fieldNumber, entrySize);
    }

    // ----- packed size computation ---------------------------------------------------------------

    /**
     * Compute the total bytes needed to encode a packed repeated varint field.
     */
    static Int computePackedVarintsSize(Int fieldNumber, List<IntNumber> values) {
        if (values.empty) {
            return 0;
        }
        Int dataSize = 0;
        for (Int64 v : values) {
            dataSize += computeVarintSize(v);
        }
        return computeTagSize(fieldNumber) + computeVarintSize(dataSize) + dataSize;
    }

    /**
     * Compute the total bytes needed to encode a packed repeated fixed32 field.
     */
    static Int computePackedFixed32sSize(Int fieldNumber, List<UInt32> values) {
        if (values.empty) {
            return 0;
        }
        Int dataSize = values.size * 4;
        return computeTagSize(fieldNumber) + computeVarintSize(dataSize) + dataSize;
    }

    /**
     * Compute the total bytes needed to encode a packed repeated fixed64 field.
     */
    static Int computePackedFixed64sSize(Int fieldNumber, List<UInt64> values) {
        if (values.empty) {
            return 0;
        }
        Int dataSize = values.size * 8;
        return computeTagSize(fieldNumber) + computeVarintSize(dataSize) + dataSize;
    }

    /**
     * Compute the total bytes needed to encode a packed repeated sint32 field.
     */
    static Int computePackedSInt32sSize(Int fieldNumber, List<Int32> values) {
        if (values.empty) {
            return 0;
        }
        Int dataSize = 0;
        for (Int32 v : values) {
            dataSize += computeVarintSize(((v << 1) ^ (v >> 31)));
        }
        return computeTagSize(fieldNumber) + computeVarintSize(dataSize) + dataSize;
    }

    /**
     * Compute the total bytes needed to encode a packed repeated sint64 field.
     */
    static Int computePackedSInt64sSize(Int fieldNumber, List<Int64> values) {
        if (values.empty) {
            return 0;
        }
        Int dataSize = 0;
        for (Int64 v : values) {
            dataSize += computeVarintSize((v << 1) ^ (v >> 63));
        }
        return computeTagSize(fieldNumber) + computeVarintSize(dataSize) + dataSize;
    }

    /**
     * Compute the total bytes needed to encode a packed repeated float field.
     */
    static Int computePackedFloatsSize(Int fieldNumber, List<Float32> values) {
        if (values.empty) {
            return 0;
        }
        Int dataSize = values.size * 4;
        return computeTagSize(fieldNumber) + computeVarintSize(dataSize) + dataSize;
    }

    /**
     * Compute the total bytes needed to encode a packed repeated double field.
     */
    static Int computePackedDoublesSize(Int fieldNumber, List<Float64> values) {
        if (values.empty) {
            return 0;
        }
        Int dataSize = values.size * 8;
        return computeTagSize(fieldNumber) + computeVarintSize(dataSize) + dataSize;
    }

    /**
     * Compute the total bytes needed to encode a packed repeated bool field.
     */
    static Int computePackedBoolsSize(Int fieldNumber, List<Boolean> values) {
        if (values.empty) {
            return 0;
        }
        Int dataSize = values.size;
        return computeTagSize(fieldNumber) + computeVarintSize(dataSize) + dataSize;
    }

    // ----- raw byte access -----------------------------------------------------------------------

    /**
     * Write a single raw byte to the underlying stream.
     *
     * @param value  the byte to write
     */
    void writeRawByte(Byte value) = out.writeByte(value);

    /**
     * Write raw bytes to the underlying stream.
     *
     * @param bytes  the bytes to write
     */
    void writeRawBytes(Byte[] bytes) = out.writeBytes(bytes);

    /**
     * Write a 4-byte little-endian unsigned integer value (no tag).
     *
     * @param value  the uint32 value
     */
    private void writeFixed32Value(UInt32 value) {
        writeRawByte((value       & 0xFF).toByte());
        writeRawByte((value >>  8 & 0xFF).toByte());
        writeRawByte((value >> 16 & 0xFF).toByte());
        writeRawByte((value >> 24 & 0xFF).toByte());
    }

    /**
     * Write an 8-byte little-endian unsigned integer value (no tag).
     *
     * @param value  the uint64 value
     */
    private void writeFixed64Value(UInt64 value) {
        writeRawByte((value       & 0xFF).toByte());
        writeRawByte((value >>  8 & 0xFF).toByte());
        writeRawByte((value >> 16 & 0xFF).toByte());
        writeRawByte((value >> 24 & 0xFF).toByte());
        writeRawByte((value >> 32 & 0xFF).toByte());
        writeRawByte((value >> 40 & 0xFF).toByte());
        writeRawByte((value >> 48 & 0xFF).toByte());
        writeRawByte((value >> 56 & 0xFF).toByte());
    }
}
