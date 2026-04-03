/**
 * Describes a `oneof` group within a protobuf message definition.
 *
 * @param name          the oneof group name
 * @param fieldNumbers  the field numbers belonging to this oneof group
 */
const OneofDescriptor(String name, Int[] fieldNumbers) {

    @Override
    String toString() = $"oneof {name}";
}
