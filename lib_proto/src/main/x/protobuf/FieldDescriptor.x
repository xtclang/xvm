/**
 * Describes a single field within a protobuf message definition.
 *
 * For scalar fields, `type` indicates the encoding and `typeName` is empty. For message and enum
 * references, `type` is `Message` or `Enum` and `typeName` holds the (possibly qualified) type
 * name from the `.proto` file.
 *
 * Map fields are detected by the parser and represented with `isMapField` set to True, along with
 * the key and value type information. On the wire, maps are encoded as repeated sub-messages.
 */
const FieldDescriptor {

    /**
     * The label applied to a field in a `.proto` file.
     */
    enum Label {
        Optional,
        Required,
        Repeated
    }

    construct(String    name,
              Int       number,
              FieldType type,
              Label     label            = Optional,
              String    typeName         = "",
              Int       oneofIndex       = -1,
              String    defaultValue     = "",
              Boolean   isMapField       = False,
              FieldType mapKeyType       = FieldType.I32,
              FieldType mapValueType     = FieldType.I32,
              String    mapValueTypeName = "",
              String    jsonName         = "",
              Map<String, String> options = Map:[]) {
        this.name             = name;
        this.number           = number;
        this.type             = type;
        this.label            = label;
        this.typeName         = typeName;
        this.oneofIndex       = oneofIndex;
        this.defaultValue     = defaultValue;
        this.isMapField       = isMapField;
        this.mapKeyType       = mapKeyType;
        this.mapValueType     = mapValueType;
        this.mapValueTypeName = mapValueTypeName;
        this.jsonName         = jsonName.size == 0 ? name : jsonName;
        this.options          = options;
    }

    /**
     * The field name from the `.proto` file.
     */
    String name;

    /**
     * The field number.
     */
    Int number;

    /**
     * The field type. For composite types, this is `Message` or `Enum`.
     */
    FieldType type;

    /**
     * The field label: optional, required, or repeated.
     */
    Label label;

    /**
     * The type name for message or enum references. Empty for scalar types.
     */
    String typeName;

    /**
     * The index into the parent message's oneof list, or -1 if not in a oneof.
     */
    Int oneofIndex;

    /**
     * The default value as a string, or empty if none.
     */
    String defaultValue;

    /**
     * True if this field represents a `map<K, V>` field.
     */
    Boolean isMapField;

    /**
     * For map fields, the key type.
     */
    FieldType mapKeyType;

    /**
     * For map fields, the value type.
     */
    FieldType mapValueType;

    /**
     * For map fields with a message or enum value type, the value type name.
     */
    String mapValueTypeName;

    /**
     * The JSON name for this field.
     */
    String jsonName;

    /**
     * Any options declared on this field (e.g. `packed`, `json_name`).
     */
    Map<String, String> options;

    /**
     * @return True if this field is repeated (including map fields)
     */
    Boolean isRepeated.get() = label == Repeated;

    /**
     * @return True if this field is required (proto2 only)
     */
    Boolean isRequired.get() = label == Required;

    /**
     * @return True if this field references a message type
     */
    Boolean isMessage.get() = type == FieldType.Msg;

    /**
     * @return True if this field references an enum type
     */
    Boolean isEnum.get() = type == FieldType.Enm;

    /**
     * @return True if this field can use packed encoding
     */
    Boolean isPackable.get() = type.packable && label == Repeated;

    @Override
    String toString() = $"{label.name.toLowercase()} {type.protoName} {name} = {number}";
}
