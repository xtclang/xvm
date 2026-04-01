/**
 * Represents the wire types used in the Protocol Buffers binary encoding format.
 *
 * Each field in a protobuf message is encoded with a tag that includes a wire type,
 * which tells the parser how to determine the size of the following value.
 *
 * The tag is encoded as a varint: `(fieldNumber << 3) | wireType`.
 */
enum WireType(Byte id) {
    /**
     * Variable-length integer encoding.
     * Used for: int32, int64, uint32, uint64, sint32, sint64, bool, enum.
     */
    VARINT(0),

    /**
     * Fixed 8-byte encoding (little-endian).
     * Used for: fixed64, sfixed64, double.
     */
    I64(1),

    /**
     * Length-delimited encoding (varint length prefix followed by payload).
     * Used for: string, bytes, embedded messages, packed repeated fields.
     */
    LEN(2),

    /**
     * Start group (deprecated).
     */
    SGROUP(3),

    /**
     * End group (deprecated).
     */
    EGROUP(4),

    /**
     * Fixed 4-byte encoding (little-endian).
     * Used for: fixed32, sfixed32, float.
     */
    I32(5);

    /**
     * The number of bits used to encode the wire type within a tag.
     */
    static Int TagTypeBits = 3;

    /**
     * The bitmask for extracting the wire type from a tag.
     */
    static Int TagTypeMask = (1 << TagTypeBits) - 1;

    /**
     * Create a tag value from a field number and wire type.
     *
     * @param fieldNumber  the field number (must be > 0)
     * @param wireType     the wire type
     *
     * @return the encoded tag value
     */
    static Int makeTag(Int fieldNumber, WireType wireType) {
        return (fieldNumber << TagTypeBits) | wireType.id;
    }

    /**
     * Extract the field number from an encoded tag.
     *
     * @param tag  the encoded tag value
     *
     * @return the field number
     */
    static Int getFieldNumber(Int tag) {
        return tag >>> TagTypeBits;
    }

    /**
     * Extract the wire type from an encoded tag.
     *
     * @param tag  the encoded tag value
     *
     * @return the WireType
     */
    static WireType getWireType(Int tag) {
        return WireType.values[tag & TagTypeMask];
    }
}
