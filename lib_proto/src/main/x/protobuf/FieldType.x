/**
 * Represents the scalar field types defined in the Protocol Buffers specification.
 *
 * Each value carries its `.proto` file name and the wire type used to encode it. The `Msg` and
 * `Enumeration` entries represent composite types whose wire encoding depends on the referenced
 * type. Some value names differ from the proto type names to avoid conflicts with Ecstasy built-in
 * types (e.g. `Str` for `"string"`, `Msg` for `"message"`).
 */
enum FieldType(String protoName, WireType wireType) {
    TypeDouble    ("double",   WireType.I64),
    TypeFloat     ("float",    WireType.I32),
    TypeInt64     ("int64",    WireType.VARINT),
    TypeUint64    ("uint64",   WireType.VARINT),
    TypeInt32     ("int32",    WireType.VARINT),
    TypeFixed64   ("fixed64",  WireType.I64),
    TypeFixed32   ("fixed32",  WireType.I32),
    TypeBool      ("bool",     WireType.VARINT),
    TypeString    ("string",   WireType.LEN),
    TypeGroup     ("group",    WireType.SGROUP),
    TypeMessage   ("message",  WireType.LEN),
    TypeBytes     ("bytes",    WireType.LEN),
    TypeUint32    ("uint32",   WireType.VARINT),
    TypeEnum      ("enum",     WireType.VARINT),
    TypeSfixed32  ("sfixed32", WireType.I32),
    TypeSfixed64  ("sfixed64", WireType.I64),
    TypeSint32    ("sint32",   WireType.VARINT),
    TypeSint64    ("sint64",   WireType.VARINT);

    /**
     * Look up a field type by its `.proto` file name.
     *
     * @param name  the proto type name (e.g. `"int32"`, `"string"`)
     *
     * @return True and the matching FieldType if found
     */
    static conditional FieldType byProtoName(String name) {
        for (FieldType type : FieldType.values) {
            if (type.protoName == name) {
                return True, type;
            }
        }
        return False;
    }

    /**
     * @return True if this type can be used in a packed repeated field
     */
    Boolean packable.get() {
        return switch (this) {
            case TypeDouble, TypeFloat, TypeInt64, TypeUint64, TypeInt32, TypeFixed64, TypeFixed32,
                 TypeBool, TypeUint32, TypeEnum, TypeSfixed32, TypeSfixed64, TypeSint32, TypeSint64:
                True;
            default:
                False;
        };
    }
}
