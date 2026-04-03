/**
 * Describes a protobuf enum definition.
 *
 * @param name        the enum type name
 * @param values      the enum values in declaration order
 * @param options     any options declared on this enum
 * @param allowAlias  True if the `allow_alias` option is set
 */
const EnumDescriptor(String                name,
                     EnumValueDescriptor[] values,
                     Map<String, String>   options    = Map:[],
                     Boolean               allowAlias = False) {

    /**
     * Look up an enum value by name.
     *
     * @param valueName  the enum value name
     *
     * @return True and the matching EnumValueDescriptor if found
     */
    conditional EnumValueDescriptor byName(String valueName) {
        for (EnumValueDescriptor value : values) {
            if (value.name == valueName) {
                return True, value;
            }
        }
        return False;
    }

    /**
     * Look up an enum value by number.
     *
     * @param number  the enum value number
     *
     * @return True and the matching EnumValueDescriptor if found
     */
    conditional EnumValueDescriptor byNumber(Int number) {
        for (EnumValueDescriptor value : values) {
            if (value.number == number) {
                return True, value;
            }
        }
        return False;
    }

    @Override
    String toString() = $"enum {name}";
}
