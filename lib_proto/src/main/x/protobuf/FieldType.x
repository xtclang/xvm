/**
 * Represents the scalar field types defined in the Protocol Buffers specification.
 *
 * Each value carries its `.proto` file name and the wire type used to encode it. The `Msg` and
 * `Enumeration` entries represent composite types whose wire encoding depends on the referenced
 * type. Some value names differ from the proto type names to avoid conflicts with Ecstasy built-in
 * types (e.g. `Str` for `"string"`, `Msg` for `"message"`).
 */
enum FieldType(String protoName, WireType wireType) {
    Dbl     ("double",   WireType.I64),
    Flt     ("float",    WireType.I32),
    I64     ("int64",    WireType.VARINT),
    UI64    ("uint64",   WireType.VARINT),
    I32     ("int32",    WireType.VARINT),
    FIX64   ("fixed64",  WireType.I64),
    FIX32   ("fixed32",  WireType.I32),
    Bool    ("bool",     WireType.VARINT),
    Str     ("string",   WireType.LEN),
    Grp     ("group",    WireType.SGROUP),
    Msg     ("message",  WireType.LEN),
    Bytes   ("bytes",    WireType.LEN),
    UI32    ("uint32",   WireType.VARINT),
    Enm     ("enum",     WireType.VARINT),
    SFIX32  ("sfixed32", WireType.I32),
    SFIX64  ("sfixed64", WireType.I64),
    SI32    ("sint32",   WireType.VARINT),
    SI64    ("sint64",   WireType.VARINT);

    /**
     * Look up a `FieldType` by its `.proto` file name.
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
            case Dbl, Flt, I64, UI64, I32, FIX64, FIX32,
                 Bool, UI32, Enm, SFIX32, SFIX64, SI32, SI64:
                True;
            default:
                False;
        };
    }
}
