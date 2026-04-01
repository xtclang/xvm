/**
 * An interface for Ecstasy enums that represent protobuf enum types.
 *
 * Protobuf enums are encoded as int32 varints on the wire. The [protoValue] property
 * provides the mapping from the Ecstasy enum constant to its protobuf integer value.
 *
 * Protobuf enum values do not necessarily match Ecstasy ordinals — they can be sparse,
 * start at non-zero values, or have gaps. This interface provides an explicit mapping.
 *
 * Example usage:
 * ```
 * enum Status
 *         implements ProtoEnum {
 *     Unknown(0), Active(1), Inactive(2);
 *
 *     construct(Int protoValue) {
 *         this.protoValue = protoValue;
 *     }
 *
 *     @Override
 *     Int protoValue;
 * }
 * ```
 */
interface ProtoEnum {

    /**
     * The protobuf integer value for this enum constant.
     */
    @RO Int protoValue;

    /**
     * Look up an enum constant by its protobuf integer value from the given enum values array.
     *
     * @param values     the array of all enum constants (e.g., `Status.values`)
     * @param protoValue the protobuf integer value to look up
     *
     * @return True and the matching enum constant, or False if not found
     */
    static <EnumType extends ProtoEnum> conditional EnumType byProtoValue(
            EnumType[] values, Int protoValue) {
        for (EnumType e : values) {
            if (e.protoValue == protoValue) {
                return True, e;
            }
        }
        return False;
    }
}
