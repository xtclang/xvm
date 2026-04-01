/**
 * Represents a single protobuf field value at the wire format level.
 *
 * A FieldValue holds the raw data for one occurrence of a field, tagged with
 * the wire type that was used to encode it. Without a schema, this is the most
 * specific type information available from the wire format.
 */
interface FieldValue
        extends immutable Const {

    /**
     * The wire type of this field value.
     */
    @RO WireType wireType;

    /**
     * Compute the number of bytes this value occupies on the wire (excluding the tag).
     */
    @RO Int wireSize;

    /**
     * Write this value (excluding the tag) to the given output.
     *
     * @param out  the coded output to write to
     */
    void writeTo(CodedOutput out);

    // ----- concrete implementations ----------------------------------------------------------

    /**
     * A varint-encoded value.
     *
     * Used for: int32, int64, uint32, uint64, sint32, sint64, bool, enum.
     */
    static const VarintValue(Int64 value)
            implements FieldValue {

        @Override
        WireType wireType.get() = WireType.VARINT;

        @Override
        Int wireSize.get() = CodedOutput.computeVarintSize(value);

        @Override
        void writeTo(CodedOutput out) {
            out.writeVarint(value);
        }
    }

    /**
     * A fixed 4-byte value (little-endian).
     *
     * Used for: fixed32, sfixed32, float.
     */
    static const Fixed32Value(UInt32 value)
            implements FieldValue {

        @Override
        WireType wireType.get() = WireType.I32;

        @Override
        Int wireSize.get() = 4;

        @Override
        void writeTo(CodedOutput out) {
            out.writeRawByte((value       & 0xFF).toByte());
            out.writeRawByte((value >>  8 & 0xFF).toByte());
            out.writeRawByte((value >> 16 & 0xFF).toByte());
            out.writeRawByte((value >> 24 & 0xFF).toByte());
        }
    }

    /**
     * A fixed 8-byte value (little-endian).
     *
     * Used for: fixed64, sfixed64, double.
     */
    static const Fixed64Value(UInt64 value)
            implements FieldValue {

        @Override
        WireType wireType.get() = WireType.I64;

        @Override
        Int wireSize.get() = 8;

        @Override
        void writeTo(CodedOutput out) {
            out.writeRawByte((value       & 0xFF).toByte());
            out.writeRawByte((value >>  8 & 0xFF).toByte());
            out.writeRawByte((value >> 16 & 0xFF).toByte());
            out.writeRawByte((value >> 24 & 0xFF).toByte());
            out.writeRawByte((value >> 32 & 0xFF).toByte());
            out.writeRawByte((value >> 40 & 0xFF).toByte());
            out.writeRawByte((value >> 48 & 0xFF).toByte());
            out.writeRawByte((value >> 56 & 0xFF).toByte());
        }
    }

    /**
     * A length-delimited value.
     *
     * Used for: string, bytes, embedded messages, packed repeated fields.
     * The interpretation depends on the schema, which is not available at this level.
     */
    static const LengthValue(immutable Byte[] data)
            implements FieldValue {

        @Override
        WireType wireType.get() = WireType.LEN;

        @Override
        Int wireSize.get() = CodedOutput.computeVarintSize(data.size.toInt64()) + data.size;

        @Override
        void writeTo(CodedOutput out) {
            out.writeVarint(data.size.toInt64());
            for (Byte b : data) {
                out.writeRawByte(b);
            }
        }
    }
}
