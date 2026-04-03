/**
 * Describes a protobuf message definition.
 *
 * Contains the fields, oneofs, nested messages, nested enums, reserved ranges, and options for a
 * single message type.
 */
const MessageDescriptor {

    /**
     * A reserved field number range. The end value is exclusive.
     */
    static const ReservedRange(Int start, Int endExclusive) {
        @Override
        String toString() = start == endExclusive - 1
                ? start.toString()
                : $"{start} to {endExclusive - 1}";
    }

    construct(String               name,
              FieldDescriptor[]    fields          = [],
              OneofDescriptor[]    oneofs          = [],
              EnumDescriptor[]     enums           = [],
              MessageDescriptor[]  nestedMessages  = [],
              ReservedRange[]      reservedRanges  = [],
              String[]             reservedNames   = [],
              Map<String, String>  options         = Map:[]) {
        this.name            = name;
        this.fields          = fields;
        this.oneofs          = oneofs;
        this.enums           = enums;
        this.nestedMessages  = nestedMessages;
        this.reservedRanges  = reservedRanges;
        this.reservedNames   = reservedNames;
        this.options         = options;
    }

    /**
     * The message type name.
     */
    String name;

    /**
     * The fields in declaration order.
     */
    FieldDescriptor[] fields;

    /**
     * The oneof groups in declaration order.
     */
    OneofDescriptor[] oneofs;

    /**
     * Nested enum definitions.
     */
    EnumDescriptor[] enums;

    /**
     * Nested message definitions.
     */
    MessageDescriptor[] nestedMessages;

    /**
     * Reserved field number ranges.
     */
    ReservedRange[] reservedRanges;

    /**
     * Reserved field names.
     */
    String[] reservedNames;

    /**
     * Message-level options.
     */
    Map<String, String> options;

    /**
     * Look up a field by name.
     *
     * @param fieldName  the field name
     *
     * @return True and the matching FieldDescriptor if found
     */
    conditional FieldDescriptor fieldByName(String fieldName) {
        for (FieldDescriptor field : fields) {
            if (field.name == fieldName) {
                return True, field;
            }
        }
        return False;
    }

    /**
     * Look up a field by number.
     *
     * @param fieldNumber  the field number
     *
     * @return True and the matching FieldDescriptor if found
     */
    conditional FieldDescriptor fieldByNumber(Int fieldNumber) {
        for (FieldDescriptor field : fields) {
            if (field.number == fieldNumber) {
                return True, field;
            }
        }
        return False;
    }

    /**
     * Look up a nested enum by name.
     *
     * @param enumName  the enum type name
     *
     * @return True and the matching EnumDescriptor if found
     */
    conditional EnumDescriptor enumByName(String enumName) {
        for (EnumDescriptor e : enums) {
            if (e.name == enumName) {
                return True, e;
            }
        }
        return False;
    }

    /**
     * Look up a nested message by name.
     *
     * @param messageName  the message type name
     *
     * @return True and the matching MessageDescriptor if found
     */
    conditional MessageDescriptor nestedMessageByName(String messageName) {
        for (MessageDescriptor nested : nestedMessages) {
            if (nested.name == messageName) {
                return True, nested;
            }
        }
        return False;
    }

    @Override
    String toString() = $"message {name}";
}
