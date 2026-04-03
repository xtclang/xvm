/**
 * Describes a single value within a protobuf enum definition.
 *
 * @param name     the enum value name (e.g. `"UNKNOWN"`)
 * @param number   the integer value assigned in the `.proto` file
 * @param options  any options declared on this enum value
 */
const EnumValueDescriptor(String name, Int number, Map<String, String> options = Map:[]) {

    @Override
    String toString() = $"{name} = {number}";
}
