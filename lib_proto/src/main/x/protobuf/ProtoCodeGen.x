import ecstasy.collections.HashSet;
import ecstasy.maps.ListMap;

import wellknown.DescriptorProto;
import wellknown.EnumDescriptorProto;
import wellknown.EnumValueDescriptorProto;
import wellknown.FieldDescriptorProto;
import wellknown.FieldDescriptorProto.Type as FieldType;
import wellknown.FieldDescriptorProto.Label;
import wellknown.FileDescriptorProto;
import wellknown.MethodDescriptorProto;
import wellknown.OneofDescriptorProto;
import wellknown.ServiceDescriptorProto;

/**
 * Generates Ecstasy source files from parsed `.proto` file descriptors.
 *
 * Produces one `.x` file per top-level message, enum, or service. Nested messages and enums are
 * emitted as inner declarations within their enclosing message class.
 *
 * Generated message classes extend [AbstractMessage] and use the `Maybe*` union types from the
 * `protobuf` module for field presence tracking.
 */
class ProtoCodeGen {

    /**
     * Ecstasy keywords that must be escaped when used as field names.
     */
    static String[] Keywords = [
        "abstract", "annotation", "assert", "break", "case", "class", "conditional",
        "const", "construct", "continue", "default", "do", "else", "enum", "extends",
        "finally", "for", "function", "if", "immutable", "implements", "import",
        "incorporates", "interface", "is", "mixin", "module", "new", "package",
        "private", "protected", "public", "return", "service", "static", "switch",
        "this", "throw", "try", "typedef", "using", "val", "var", "void", "while",
    ];

    /**
     * Set of known enum type names collected during generation, used to distinguish
     * enum references from message references (the parser treats both as TypeMessage).
     */
    private Set<String> enumNames = new HashSet();

    /**
     * Information about a map field's key and value types, extracted from the synthetic
     * entry message in the descriptor.
     */
    private static const MapFieldInfo(FieldType keyType, FieldType valueType,
                                      String valueTypeName);

    /**
     * Build a map from entry message typeName to MapFieldInfo for all map fields in a message.
     */
    private Map<String, MapFieldInfo> buildMapFieldInfo(DescriptorProto msg) {
        ListMap<String, MapFieldInfo> map = new ListMap();
        for (DescriptorProto nested : msg.nestedType) {
            if (isMapEntryMessage(nested)) {
                FieldDescriptorProto keyField   = nested.field[0];
                FieldDescriptorProto valueField = nested.field[1];
                assert FieldType keyType   := keyField.hasType();
                assert FieldType valueType := valueField.hasType();
                map.put(nested.name, new MapFieldInfo(keyType, valueType, valueField.typeName));
            }
        }
        return map;
    }

    /**
     * @return True if the field is a map field (references a synthetic map entry message)
     */
    private Boolean isMapField(FieldDescriptorProto field, Map<String, MapFieldInfo> mapInfo) {
        return field.label == LabelRepeated && mapInfo.contains(field.typeName);
    }

    /**
     * @return True if the message is a synthetic map entry message
     */
    private Boolean isMapEntryMessage(DescriptorProto msg) {
        import wellknown.MessageOptions;
        MessageOptions? opts = msg.options;
        return opts != Null && opts.mapEntry;
    }

    /**
     * @return True if the given field type is packable (numeric, bool, or enum)
     */
    private Boolean isPackable(FieldType type) {
        return switch (type) {
            case TypeDouble, TypeFloat:                                True;
            case TypeInt64, TypeUint64, TypeInt32, TypeUint32:         True;
            case TypeFixed64, TypeFixed32, TypeSfixed32, TypeSfixed64: True;
            case TypeSint32, TypeSint64:                               True;
            case TypeBool, TypeEnum:                                   True;
            default:                                                   False;
        };
    }

    /**
     * @return the Ecstasy type name for a map value, handling both scalar types and message/enum types
     */
    private String mapValueEcstasyType(MapFieldInfo info) {
        if (info.valueType == TypeMessage || info.valueType == TypeEnum) {
            return info.valueTypeName;
        }
        return ecstasyType(info.valueType);
    }

    // ----- public interface -----------------------------------------------------------------------

    /**
     * Generate Ecstasy source files from a parsed proto file descriptor.
     *
     * @param file  the parsed FileDescriptorProto
     *
     * @return a map from file name (e.g. `"Person.x"`) to source text
     */
    Map<String, String> generate(FileDescriptorProto file) {
        enumNames = collectEnumNames(file);
        ListMap<String, String> result = new ListMap();

        for (EnumDescriptorProto e : file.enumType) {
            result.put($"{e.name}.x", generateTopLevelEnum(e, file));
        }

        for (DescriptorProto msg : file.messageType) {
            result.put($"{msg.name}.x", generateTopLevelMessage(msg, file));
        }

        for (ServiceDescriptorProto svc : file.service_) {
            result.put($"{svc.name}.x", generateTopLevelService(svc, file));
        }

        return result.freeze(inPlace=True);
    }

    // ----- top-level generators ------------------------------------------------------------------

    /**
     * Generate a top-level enum file.
     */
    private String generateTopLevelEnum(EnumDescriptorProto e, FileDescriptorProto file) {
        StringBuffer buf = new StringBuffer();
        generateEnum(buf, e, file, 0);
        return buf.toString();
    }

    /**
     * Generate a top-level message file.
     */
    private String generateTopLevelMessage(DescriptorProto msg, FileDescriptorProto file) {
        StringBuffer buf = new StringBuffer();
        generateMessage(buf, msg, file, 0, False);
        return buf.toString();
    }

    /**
     * Generate a top-level service interface file.
     */
    private String generateTopLevelService(ServiceDescriptorProto svc, FileDescriptorProto file) {
        StringBuffer buf = new StringBuffer();
        generateService(buf, svc, file, 0);
        return buf.toString();
    }

    // ----- enum generation ------------------------------------------------------------------------

    /**
     * Generate an enum declaration.
     */
    private void generateEnum(StringBuffer buf, EnumDescriptorProto e, FileDescriptorProto file,
                              Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);

        $|{pad}/**
         |{pad} * This enum has been generated by the Ecstasy protoc plugin from the message type
         |{pad} * {e.name} defined in the proto file {file.name}
         |{pad} */
         |{pad}enum {e.name}
         |{pad}        implements protobuf.ProtoEnum \{
         |
         .appendTo(buf);

        // enum values
        for (Int i = 0; i < e.value.size; i++) {
            EnumValueDescriptorProto v = e.value[i];
            $|{pad1}{toPascalCase(v.name)}({v.number})
             .appendTo(buf);
            buf.add(i < e.value.size - 1 ? ',' : ';');
            buf.add('\n');
        }

        $|
         |{pad1}construct(Int protoValue) \{
         |{pad1}    this.protoValue = protoValue;
         |{pad1}}
         |
         |{pad1}@Override
         |{pad1}Int protoValue;
         |{pad}}
         .appendTo(buf);
    }

    // ----- message generation ---------------------------------------------------------------------

    /**
     * Generate a message class declaration.
     *
     * @param buf      the output buffer
     * @param msg      the message descriptor
     * @param file     the parent proto file descriptor
     * @param indent   the indentation level
     * @param nested   True if this is a nested (inner static) class
     */
    private void generateMessage(StringBuffer buf, DescriptorProto msg, FileDescriptorProto file,
                                 Int indent, Boolean nested) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);

        // class declaration
        String classKind = nested ? "static class" : "class";
        $|{pad}/**
         |{pad} * This class has been generated by the Ecstasy protoc plugin from the message type
         |{pad} * {msg.name} defined in the proto file {file.name}
         |{pad} */
         |{pad}{classKind} {msg.name}
         |{pad}        extends protobuf.AbstractMessage \{
         |
         .appendTo(buf);

        // build map field info from nested entry messages
        Map<String, MapFieldInfo> mapInfo = buildMapFieldInfo(msg);

        // collect non-oneof fields and oneof descriptors
        Array<FieldDescriptorProto> regularFields = new Array();
        Array<FieldDescriptorProto> oneofFields   = new Array();
        for (FieldDescriptorProto field : msg.field) {
            if (field.hasOneofIndex()) {
                oneofFields.add(field);
            } else {
                regularFields.add(field);
            }
        }

        // build presence tracking map for scalar fields
        Map<String, Int> presenceMap = buildPresenceMap(regularFields, mapInfo);

        // oneof typedefs
        generateOneofTypedefs(buf, msg.oneofDecl, oneofFields, indent + 1);

        // default constructor with field parameters
        generateConstructor(buf, regularFields, msg.oneofDecl, indent + 1, presenceMap, mapInfo);

        // copy constructor
        generateCopyConstructor(buf, msg, regularFields, indent + 1, presenceMap, mapInfo);
        buf.add('\n');

        // field declarations
        generateFieldDeclarations(buf, regularFields, msg.oneofDecl, indent + 1, presenceMap, mapInfo);

        // has methods
        generateHasMethods(buf, regularFields, msg.oneofDecl, oneofFields, indent + 1, presenceMap, mapInfo);

        // nested enums
        for (EnumDescriptorProto e : msg.enumType) {
            $"{pad1}{separator($"enum {e.name}", indent + 1)}\n\n".appendTo(buf);
            generateEnum(buf, e, file, indent + 1);
            buf.add('\n');
        }

        // nested messages (skip map entry messages)
        for (DescriptorProto nested2 : msg.nestedType) {
            if (isMapEntryMessage(nested2)) {
                continue;
            }
            $"{pad1}{separator($"message {nested2.name}", indent + 1)}\n\n".appendTo(buf);
            generateMessage(buf, nested2, file, indent + 1, True);
            buf.add('\n');
        }

        // serialization section
        $"{pad1}{separator("serialization", indent + 1)}\n\n".appendTo(buf);

        // parseField (no changes - virtual property setters handle presence bits)
        generateParseField(buf, msg, regularFields, oneofFields, indent + 1, mapInfo);
        buf.add('\n');

        // writeKnownFields
        generateWriteKnownFields(buf, msg, regularFields, oneofFields, indent + 1, presenceMap, mapInfo);
        buf.add('\n');

        // knownFieldsSize
        generateKnownFieldsSize(buf, msg, regularFields, oneofFields, indent + 1, presenceMap, mapInfo);
        buf.add('\n');

        // freeze
        generateFreeze(buf, msg, regularFields, indent + 1, mapInfo);

        $"{pad}}\n".appendTo(buf);
    }

    // ----- oneof typedef generation ----------------------------------------------------------------

    /**
     * Generate typedefs for oneof groups.
     */
    private void generateOneofTypedefs(StringBuffer buf, Array<OneofDescriptorProto> oneofs,
                                       Array<FieldDescriptorProto> oneofFields, Int indent) {
        String pad = spaces(indent);

        for (Int oi = 0; oi < oneofs.size; oi++) {
            OneofDescriptorProto oneof = oneofs[oi];
            StringBuffer types = new StringBuffer();
            Boolean first = True;
            for (FieldDescriptorProto field : oneofFields) {
                if (field.oneofIndex != oi) {
                    continue;
                }
                if (first) {
                    first = False;
                } else {
                    " | ".appendTo(types);
                }
                ecstasyScalarType(field).appendTo(types);
            }
            String typedefName = $"{toPascalCase(oneof.name)}Type";
            $"{pad}typedef {types} as {typedefName};\n".appendTo(buf);
        }
        if (oneofs.size > 0) {
            buf.add('\n');
        }
    }

    // ----- field declaration generation ------------------------------------------------------------

    /**
     * Generate field declarations for regular (non-oneof) fields and oneof instances.
     */
    private void generateFieldDeclarations(StringBuffer buf,
                                           Array<FieldDescriptorProto> regularFields,
                                           Array<OneofDescriptorProto> oneofs, Int indent,
                                           Map<String, Int> presenceMap,
                                           Map<String, MapFieldInfo> mapInfo) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String pad2 = spaces(indent + 2);

        // emit presentBits fields
        if (!presenceMap.empty) {
            Int wordCount = presenceMap.values.reduce(0, (Int mx, Int v) -> mx > v ? mx : v) / 64 + 1;
            for (Int w = 0; w < wordCount; w++) {
                $"{pad}private Int presentBits_{w} = 0;\n".appendTo(buf);
            }
            buf.add('\n');
        }

        for (FieldDescriptorProto field : regularFields) {
            String ftype = fieldTypeName(field, mapInfo);
            String fname = fieldName(field.name);
            String fdef  = fieldDefault(field, mapInfo);

            if (presenceMap.contains(fname)) {
                assert Int bitIndex := presenceMap.get(fname);
                String wordName = presenceWordName(bitIndex);
                String mask     = presenceMaskLiteral(bitIndex);

                // private backing field
                $"{pad}private {ftype} _{fname} = {fdef};\n\n".appendTo(buf);

                // virtual property with presence-tracking setter
                $|{pad}{ftype} {fname} \{
                 |{pad1}@Override
                 |{pad1}{ftype} get() \{
                 |{pad2}return _{fname};
                 |{pad1}}
                 |{pad1}@Override
                 |{pad1}void set({ftype} value) \{
                 |{pad2}{wordName} |= {mask};
                 |{pad2}_{fname} = value;
                 |{pad1}}
                 |{pad}}
                 |
                 |
                 .appendTo(buf);
            } else {
                $"{pad}{ftype} {fname} = {fdef};\n".appendTo(buf);
            }
        }

        for (OneofDescriptorProto oneof : oneofs) {
            String typedefName = $"{toPascalCase(oneof.name)}Type";
            String propName    = toCamelCase(oneof.name);
            $"{pad}{typedefName}? {propName} = Null;\n".appendTo(buf);
        }
    }

    // ----- constructor generation -------------------------------------------------------------------

    /**
     * Generate the default constructor with field parameters.
     */
    private void generateConstructor(StringBuffer buf, Array<FieldDescriptorProto> regularFields,
                                     Array<OneofDescriptorProto> oneofs, Int indent,
                                     Map<String, Int> presenceMap,
                                     Map<String, MapFieldInfo> mapInfo) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);

        $"{pad}construct(".appendTo(buf);

        // parameters
        Boolean first = True;
        for (FieldDescriptorProto field : regularFields) {
            if (first) {
                first = False;
            } else {
                ",".appendTo(buf);
            }
            String fname = fieldName(field.name);
            if (presenceMap.contains(fname)) {
                // scalar fields with presence tracking use nullable parameter
                String ftype = $"{fieldTypeName(field, mapInfo)}?";
                $"\n{pad1}{pad}{ftype} {fname} = Null".appendTo(buf);
            } else {
                String ftype = fieldTypeName(field, mapInfo);
                String fdef  = constructorDefault(field, mapInfo);
                $"\n{pad1}{pad}{ftype} {fname} = {fdef}".appendTo(buf);
            }
        }
        for (OneofDescriptorProto oneof : oneofs) {
            if (first) {
                first = False;
            } else {
                ",".appendTo(buf);
            }
            String typedefName = $"{toPascalCase(oneof.name)}Type";
            String propName    = toCamelCase(oneof.name);
            $"\n{pad1}{pad}{typedefName}? {propName} = Null".appendTo(buf);
        }

        $") \{\n{pad1}construct protobuf.AbstractMessage();\n".appendTo(buf);

        // constructor body: non-scalar fields and oneofs
        for (FieldDescriptorProto field : regularFields) {
            String fname = fieldName(field.name);
            if (!presenceMap.contains(fname)) {
                $"{pad1}this.{fname} = {fname};\n".appendTo(buf);
            }
        }
        for (OneofDescriptorProto oneof : oneofs) {
            String propName = toCamelCase(oneof.name);
            $"{pad1}this.{propName} = {propName};\n".appendTo(buf);
        }

        // finally block: scalar fields with presence tracking using ?= operator
        Boolean hasPresenceFields = False;
        for (FieldDescriptorProto field : regularFields) {
            String fname = fieldName(field.name);
            if (presenceMap.contains(fname)) {
                if (!hasPresenceFields) {
                    $"{pad}} finally \{\n".appendTo(buf);
                    hasPresenceFields = True;
                }
                $"{pad1}this.{fname} ?= {fname};\n".appendTo(buf);
            }
        }

        $"{pad}}\n\n".appendTo(buf);
    }

    /**
     * @return the default value literal for a constructor parameter
     */
    private String constructorDefault(FieldDescriptorProto field,
                                      Map<String, MapFieldInfo> mapInfo) {
        if (isMapField(field, mapInfo)) {
            return "Map:[]";
        }
        if (field.label == LabelRepeated) {
            return "[]";
        }
        assert FieldType fieldType := field.hasType();
        if (fieldType == TypeMessage || isEnumRef(field)) {
            return "Null";
        }
        return scalarDefault(fieldType);
    }

    /**
     * @return the protobuf default value literal for a scalar field type
     */
    private String scalarDefault(FieldType fieldType) {
        return switch (fieldType) {
            case TypeInt32, TypeSint32, TypeSfixed32:     "0";
            case TypeInt64, TypeSint64, TypeSfixed64:     "0";
            case TypeUint32, TypeFixed32:                  "0";
            case TypeUint64, TypeFixed64:                  "0";
            case TypeFloat:                               "0.0";
            case TypeDouble:                              "0.0";
            case TypeBool:                                "False";
            case TypeString:                              "\"\"";
            case TypeBytes:                               "[]";
            default:                                      "Null";
        };
    }

    // ----- copy constructor generation ------------------------------------------------------------

    /**
     * Generate the Duplicable copy constructor.
     */
    private void generateCopyConstructor(StringBuffer buf, DescriptorProto msg,
                                         Array<FieldDescriptorProto> regularFields, Int indent,
                                         Map<String, Int> presenceMap,
                                         Map<String, MapFieldInfo> mapInfo) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String pad2 = spaces(indent + 2);

        $|{pad}@Override
         |{pad}construct({msg.name} other) \{
         |{pad1}construct protobuf.AbstractMessage(other);
         .appendTo(buf);

        for (FieldDescriptorProto field : regularFields) {
            String fname = fieldName(field.name);
            if (isMapField(field, mapInfo)) {
                $|
                 |{pad1}if (other.{fname}.is(immutable)) \{
                 |{pad2}{fname} = other.{fname};
                 |{pad1}} else \{
                 |{pad2}{fname} = new ecstasy.maps.ListMap(other.{fname});
                 |{pad1}}
                 .appendTo(buf);
            } else if (field.label == LabelRepeated) {
                $"\n{pad1}{fname} = other.{fname}.duplicate();".appendTo(buf);
            } else if (presenceMap.contains(fname)) {
                $"\n{pad1}_{fname} = other.{fname};".appendTo(buf);
            } else {
                $"\n{pad1}{fname} = other.{fname};".appendTo(buf);
            }
        }

        for (OneofDescriptorProto oneof : msg.oneofDecl) {
            String propName = toCamelCase(oneof.name);
            $"\n{pad1}{propName} = other.{propName};".appendTo(buf);
        }

        // copy presence bitmask fields
        if (!presenceMap.empty) {
            Int wordCount = presenceMap.values.reduce(0, (Int mx, Int v) -> mx > v ? mx : v) / 64 + 1;
            for (Int w = 0; w < wordCount; w++) {
                $"\n{pad1}presentBits_{w} = other.presentBits_{w};".appendTo(buf);
            }
        }

        $"\n{pad}}\n".appendTo(buf);
    }

    // ----- has method generation -------------------------------------------------------------------

    /**
     * Generate conditional has*() methods for fields that support presence checking.
     */
    private void generateHasMethods(StringBuffer buf, Array<FieldDescriptorProto> regularFields,
                                    Array<OneofDescriptorProto> oneofs,
                                    Array<FieldDescriptorProto> oneofFields, Int indent,
                                    Map<String, Int> presenceMap,
                                    Map<String, MapFieldInfo> mapInfo) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String pad2 = spaces(indent + 2);

        Boolean any = False;

        // scalar fields with presence bitmask
        for (FieldDescriptorProto field : regularFields) {
            String fname = fieldName(field.name);
            if (presenceMap.contains(fname)) {
                any = True;
                String ftype   = fieldTypeName(field, mapInfo);
                String hasName = $"has{toPascalCase(field.name)}";
                String cond    = presenceCondition(presenceMap, fname);
                $|
                 |{pad}conditional {ftype} {hasName}() \{
                 |{pad1}if ({cond}) \{
                 |{pad2}return True, {fname};
                 |{pad1}}
                 |{pad1}return False;
                 |{pad}}
                 |
                 .appendTo(buf);
            }
        }

        // message and enum fields (nullable, non-map, non-repeated)
        for (FieldDescriptorProto field : regularFields) {
            String fname = fieldName(field.name);
            assert FieldType fieldType := field.hasType();
            if (isMapField(field, mapInfo) || field.label == LabelRepeated) {
                continue;
            }
            if (fieldType == TypeMessage && !isEnumRef(field)) {
                any = True;
                String ecType  = field.typeName;
                String hasName = $"has{toPascalCase(field.name)}";
                $|
                 |{pad}conditional {ecType} {hasName}() \{
                 |{pad1}{ecType}? value = {fname};
                 |{pad1}if (value.is({ecType})) \{
                 |{pad2}return True, value;
                 |{pad1}}
                 |{pad1}return False;
                 |{pad}}
                 .appendTo(buf);
            } else if (isEnumRef(field) && field.label != LabelRepeated) {
                any = True;
                String ecType  = field.typeName;
                String hasName = $"has{toPascalCase(field.name)}";
                $|
                 |{pad}conditional {ecType} {hasName}() \{
                 |{pad1}{ecType}? value = {fname};
                 |{pad1}if (value.is({ecType})) \{
                 |{pad2}return True, value;
                 |{pad1}}
                 |{pad1}return False;
                 |{pad}}
                 .appendTo(buf);
            }
        }

        // oneof fields (with type parameter and multi-switch)
        for (Int oi = 0; oi < oneofs.size; oi++) {
            OneofDescriptorProto oneof = oneofs[oi];
            any = True;
            String typedefName = $"{toPascalCase(oneof.name)}Type";
            String propName    = toCamelCase(oneof.name);
            String hasName     = $"has{toPascalCase(oneof.name)}";

            $|
             |{pad}conditional {typedefName} {hasName}(Type<{typedefName}> type = {typedefName}) \{
             |{pad1}{typedefName}? value = {propName};
             |{pad1}switch (type, value.is(_)) \{
             .appendTo(buf);

            // first case: the typedef itself
            $"{pad2}case ({typedefName}, {typedefName}):\n".appendTo(buf);
            $"{pad2}    return True, value;\n".appendTo(buf);

            // cases for each member type
            for (FieldDescriptorProto field : oneofFields) {
                if (field.oneofIndex != oi) {
                    continue;
                }
                String ecType = ecstasyScalarType(field);
                $"{pad2}case ({ecType}, {ecType}):\n".appendTo(buf);
                $"{pad2}    return True, value;\n".appendTo(buf);
            }

            $|{pad2}default:
             |{pad2}    return False;
             |{pad1}}
             |{pad}}
             .appendTo(buf);
        }

        if (any) {
            buf.add('\n');
        }
    }

    // ----- parseField generation ------------------------------------------------------------------

    /**
     * Generate the parseField override.
     */
    private void generateParseField(StringBuffer buf, DescriptorProto msg,
                                    Array<FieldDescriptorProto> regularFields,
                                    Array<FieldDescriptorProto> oneofFields, Int indent,
                                    Map<String, MapFieldInfo> mapInfo) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String pad2 = spaces(indent + 2);

        $|{pad}@Override
         |{pad}Boolean parseField(protobuf.CodedInput input, Int tag) \{
         .appendTo(buf);

        if (regularFields.empty && oneofFields.empty) {
            $|
             |{pad1}return False;
             .appendTo(buf);
        } else {
            $|
             |{pad1}switch (protobuf.WireType.getFieldNumber(tag)) \{
             .appendTo(buf);

            // regular fields
            for (FieldDescriptorProto field : regularFields) {
                String fname = fieldName(field.name);
                $"\n{pad1}case {field.number}:\n".appendTo(buf);
                assert FieldType fieldType := field.hasType();

                if (isMapField(field, mapInfo)) {
                    generateParseMapField(buf, field, fname, indent + 2, mapInfo);
                } else if (field.label == LabelRepeated) {
                    generateParseRepeatedField(buf, field, fname, indent + 2);
                } else if (isEnumRef(field)) {
                    generateParseEnumField(buf, field, fname, indent + 2);
                } else if (fieldType == TypeMessage) {
                    generateParseMessageField(buf, field, fname, indent + 2);
                } else {
                    $"{pad2}{fname} = input.{readMethod(field)};\n".appendTo(buf);
                }
                $"{pad2}return True;\n".appendTo(buf);
            }

            // oneof fields
            for (FieldDescriptorProto field : oneofFields) {
                String propName = toCamelCase(msg.oneofDecl[field.oneofIndex].name);
                $"\n{pad1}case {field.number}:\n".appendTo(buf);
                if (isEnumRef(field)) {
                    String tn   = field.typeName;
                    String pad3 = spaces(indent + 3);
                    $|{pad2}if ({tn} v := protobuf.ProtoEnum.byProtoValue({tn}.values, input.readEnum())) \{
                     |{pad3}{propName} = v;
                     |{pad2}}
                     .appendTo(buf);
                } else if (field.type == TypeMessage) {
                    generateParseMessageField(buf, field, propName, indent + 2);
                } else {
                    $"{pad2}{propName} = input.{readMethod(field)};\n".appendTo(buf);
                }
                $"{pad2}return True;\n".appendTo(buf);
            }

            $|{pad1}}
             |{pad1}return False;
             .appendTo(buf);
        }

        $"\n{pad}}\n".appendTo(buf);
    }

    /**
     * Generate parse logic for a repeated field.
     */
    private void generateParseRepeatedField(StringBuffer buf, FieldDescriptorProto field,
                                            String fname, Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String pad2 = spaces(indent + 2);
        String pad3 = spaces(indent + 3);

        // ensure the array is mutable (may be immutable from default parameter)
        $|{pad}if ({fname}.is(immutable)) \{
         |{pad1}{fname} = new Array({fname});
         |{pad}}
         |
         .appendTo(buf);

        if (isEnumRef(field)) {
            // repeated enum: read varints and convert to enum values
            String tn = field.typeName;
            $|{pad}if (protobuf.WireType.getWireType(tag) == protobuf.WireType.LEN) \{
             |{pad1}for (Int32 raw : input.readPackedInt32s()) \{
             |{pad2}if ({tn} v := protobuf.ProtoEnum.byProtoValue({tn}.values, raw)) \{
             |{pad3}{fname}.add(v);
             |{pad2}}
             |{pad1}}
             |{pad}} else \{
             |{pad1}if ({tn} v := protobuf.ProtoEnum.byProtoValue({tn}.values, input.readEnum())) \{
             |{pad2}{fname}.add(v);
             |{pad1}}
             |{pad}}
             .appendTo(buf);
        } else if (FieldType ft := field.hasType(), isPackable(ft)) {
            // handle both packed (LEN) and unpacked wire types
            $|{pad}if (protobuf.WireType.getWireType(tag) == protobuf.WireType.LEN) \{
             |{pad1}{fname}.addAll(input.{readPackedMethod(field)});
             |{pad}} else \{
             |{pad1}{fname}.add(input.{readMethod(field)});
             |{pad}}
             .appendTo(buf);
        } else if (field.type == TypeMessage) {
            // repeated message
            String tn = field.typeName;
            $|{pad}{tn} elem = new {tn}();
             |{pad}Int len = input.readVarint().toInt();
             |{pad}Int oldLimit = input.pushLimit(len);
             |{pad}elem.mergeFrom(input);
             |{pad}input.popLimit(oldLimit);
             |{pad}{fname}.add(elem);
             |
             .appendTo(buf);
        } else {
            // repeated non-packable scalar (string, bytes)
            $"{pad}{fname}.add(input.{readMethod(field)});\n".appendTo(buf);
        }
    }

    /**
     * Generate parse logic for a singular message field.
     */
    private void generateParseMessageField(StringBuffer buf, FieldDescriptorProto field,
                                           String fname, Int indent) {
        String pad = spaces(indent);
        String tn  = field.typeName;
        $|{pad}{tn} msg = new {tn}();
         |{pad}Int len = input.readVarint().toInt();
         |{pad}Int oldLimit = input.pushLimit(len);
         |{pad}msg.mergeFrom(input);
         |{pad}input.popLimit(oldLimit);
         |{pad}{fname} = msg;
         |{pad}
         .appendTo(buf);
    }

    /**
     * Generate parse logic for a singular enum field.
     */
    private void generateParseEnumField(StringBuffer buf, FieldDescriptorProto field,
                                        String fname, Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String tn   = field.typeName;
        $|{pad}if ({tn} v := protobuf.ProtoEnum.byProtoValue({tn}.values, input.readEnum())) \{
         |{pad1}{fname} = v;
         |{pad}}
         |
         .appendTo(buf);
    }

    /**
     * Generate parse logic for a map field.
     */
    private void generateParseMapField(StringBuffer buf, FieldDescriptorProto field,
                                       String fname, Int indent,
                                       Map<String, MapFieldInfo> mapInfo) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        assert MapFieldInfo info := mapInfo.get(field.typeName);
        String readMapMethod = mapReadMethod(info.keyType, info.valueType);
        if (readMapMethod.size > 0) {
            String kt = ecstasyType(info.keyType);
            String vt = ecstasyType(info.valueType);

            // ensure the map is mutable (may be immutable from default parameter)
            $|{pad}if ({fname}.is(immutable)) \{
             |{pad1}{fname} = new ecstasy.maps.ListMap({fname});
             |{pad}}
             |{pad}({kt} k, {vt} v) = input.{readMapMethod};
             |{pad}{fname}.put(k, v);
             |
             .appendTo(buf);
        } else {
            $"{pad}// TODO: unsupported map type combination\n".appendTo(buf);
        }
    }

    // ----- writeKnownFields generation ------------------------------------------------------------

    /**
     * Generate the writeKnownFields override.
     */
    private void generateWriteKnownFields(StringBuffer buf, DescriptorProto msg,
                                          Array<FieldDescriptorProto> regularFields,
                                          Array<FieldDescriptorProto> oneofFields, Int indent,
                                          Map<String, Int> presenceMap,
                                          Map<String, MapFieldInfo> mapInfo) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String pad2 = spaces(indent + 2);

        $|{pad}@Override
         |{pad}void writeKnownFields(protobuf.CodedOutput out) \{
         .appendTo(buf);

        // regular fields
        for (FieldDescriptorProto field : regularFields) {
            String fname = fieldName(field.name);
            Int    fn    = field.number;
            assert FieldType fieldType := field.hasType();

            if (isMapField(field, mapInfo)) {
                generateWriteMapField(buf, field, fname, indent + 1, mapInfo);
            } else if (field.label == LabelRepeated) {
                generateWriteRepeatedField(buf, field, fname, indent + 1);
            } else if (isEnumRef(field)) {
                String ecType = field.typeName;
                $|
                 |{pad1}{ecType}? {fname} = this.{fname};
                 |{pad1}if ({fname} != Null) \{
                 |{pad2}out.writeEnum({fn}, {fname});
                 |{pad1}}
                 .appendTo(buf);
            } else if (fieldType == TypeMessage) {
                String ecType = field.typeName;
                $|
                 |{pad1}{ecType}? {fname} = this.{fname};
                 |{pad1}if ({fname} != Null) \{
                 |{pad2}out.writeMessage({fn}, {fname});
                 |{pad1}}
                 .appendTo(buf);
            } else {
                String cond = presenceCondition(presenceMap, fname);
                $|
                 |{pad1}if ({cond}) \{
                 |{pad2}out.{writeMethod(field)}({fn}, {fname});
                 |{pad1}}
                 .appendTo(buf);
            }
        }

        // oneof fields
        for (Int oi = 0; oi < msg.oneofDecl.size; oi++) {
            generateWriteOneof(buf, msg.oneofDecl[oi], oi, oneofFields, indent + 1);
        }

        $"\n{pad}}\n".appendTo(buf);
    }

    /**
     * Generate write logic for a repeated field.
     */
    private void generateWriteRepeatedField(StringBuffer buf, FieldDescriptorProto field,
                                            String fname, Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        Int    fn   = field.number;
        assert FieldType fieldType := field.hasType();

        if (isEnumRef(field)) {
            // repeated enum: write each value individually using writeEnum
            String tn = field.typeName;
            $|
             |{pad}for ({tn} v : {fname}) \{
             |{pad1}out.writeEnum({fn}, v);
             |{pad}}
             .appendTo(buf);
        } else if (isPackable(fieldType)) {
            $|
             |{pad}if (!{fname}.empty) \{
             |{pad1}out.{writePackedMethod(field)}({fn}, {fname});
             |{pad}}
             .appendTo(buf);
        } else {
            String ecType = fieldType == TypeMessage ? field.typeName : ecstasyScalarType(field);
            String writeCall = fieldType == TypeMessage
                    ? $"out.writeMessage({fn}, v)"
                    : $"out.{writeMethod(field)}({fn}, v)";
            $|
             |{pad}for ({ecType} v : {fname}) \{
             |{pad1}{writeCall};
             |{pad}}
             .appendTo(buf);
        }
    }

    /**
     * Generate write logic for a map field.
     */
    private void generateWriteMapField(StringBuffer buf, FieldDescriptorProto field,
                                       String fname, Int indent,
                                       Map<String, MapFieldInfo> mapInfo) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        assert MapFieldInfo info := mapInfo.get(field.typeName);
        String writeMapMethod = mapWriteMethod(info.keyType, info.valueType);
        if (writeMapMethod.size > 0) {
            String kt = ecstasyType(info.keyType);
            String vt = ecstasyType(info.valueType);
            Int    fn = field.number;
            $|
             |{pad}for (({kt} k, {vt} v) : {fname}) \{
             |{pad1}out.{writeMapMethod}({fn}, k, v);
             |{pad}}
             .appendTo(buf);
        }
    }

    /**
     * Generate write logic for a oneof group.
     */
    private void generateWriteOneof(StringBuffer buf, OneofDescriptorProto oneof, Int oi,
                                    Array<FieldDescriptorProto> oneofFields, Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String pad2 = spaces(indent + 2);
        String typedefName = $"{toPascalCase(oneof.name)}Type";
        String propName    = toCamelCase(oneof.name);

        $"\n{pad}{typedefName}? {propName} = this.{propName};\n".appendTo(buf);

        Boolean first = True;
        for (FieldDescriptorProto field : oneofFields) {
            if (field.oneofIndex != oi) {
                continue;
            }
            String ecType = ecstasyScalarType(field);
            Int    fn     = field.number;

            if (!first) {
                $" else ".appendTo(buf);
            }
            first = False;

            String writeCall;
            if (isEnumRef(field)) {
                writeCall = $"out.writeEnum({fn}, {propName}.as({ecType}))";
            } else if (field.type == TypeMessage) {
                writeCall = $"out.writeMessage({fn}, {propName}.as({ecType}))";
            } else {
                writeCall = $"out.{writeMethod(field)}({fn}, {propName}.as({ecType}))";
            }

            $|if ({propName}.is({ecType})) \{
             |{pad1}{writeCall};
             |{pad}}
             .appendTo(buf);
        }
        buf.add('\n');
    }

    // ----- knownFieldsSize generation -------------------------------------------------------------

    /**
     * Generate the knownFieldsSize override.
     */
    private void generateKnownFieldsSize(StringBuffer buf, DescriptorProto msg,
                                         Array<FieldDescriptorProto> regularFields,
                                         Array<FieldDescriptorProto> oneofFields, Int indent,
                                         Map<String, Int> presenceMap,
                                         Map<String, MapFieldInfo> mapInfo) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String pad2 = spaces(indent + 2);

        $|{pad}@Override
         |{pad}Int knownFieldsSize() \{
         |{pad1}Int size = 0;
         .appendTo(buf);

        // regular fields
        for (FieldDescriptorProto field : regularFields) {
            String fname = fieldName(field.name);
            assert FieldType fieldType := field.hasType();

            if (isMapField(field, mapInfo)) {
                generateSizeMapField(buf, field, fname, indent + 1, mapInfo);
            } else if (field.label == LabelRepeated) {
                generateSizeRepeatedField(buf, field, fname, indent + 1);
            } else if (isEnumRef(field)) {
                String ecType = field.typeName;
                Int    fn     = field.number;
                $|
                 |{pad1}{ecType}? {fname} = this.{fname};
                 |{pad1}if ({fname} != Null) \{
                 |{pad2}size += protobuf.CodedOutput.computeEnumSize({fn}, {fname});
                 |{pad1}}
                 .appendTo(buf);
            } else if (fieldType == TypeMessage) {
                String ecType = field.typeName;
                Int    fn     = field.number;
                $|
                 |{pad1}{ecType}? {fname} = this.{fname};
                 |{pad1}if ({fname} != Null) \{
                 |{pad2}size += protobuf.CodedOutput.computeMessageSize({fn}, {fname});
                 |{pad1}}
                 .appendTo(buf);
            } else {
                String cond = presenceCondition(presenceMap, fname);
                $|
                 |{pad1}if ({cond}) \{
                 |{pad2}size += protobuf.CodedOutput.{computeSizeMethod(field)};
                 |{pad1}}
                 .appendTo(buf);
            }
        }

        // oneof fields
        for (Int oi = 0; oi < msg.oneofDecl.size; oi++) {
            generateSizeOneof(buf, msg.oneofDecl[oi], oi, oneofFields, indent + 1);
        }

        $|
         |{pad1}return size;
         |{pad}}
         |
         .appendTo(buf);
    }

    /**
     * Generate size logic for a repeated field.
     */
    private void generateSizeRepeatedField(StringBuffer buf, FieldDescriptorProto field,
                                           String fname, Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        Int    fn   = field.number;
        assert FieldType fieldType := field.hasType();

        if (isEnumRef(field)) {
            // repeated enum: compute size for each element individually
            String tn = field.typeName;
            $|
             |{pad}for ({tn} v : {fname}) \{
             |{pad1}size += protobuf.CodedOutput.computeEnumSize({fn}, v);
             |{pad}}
             .appendTo(buf);
        } else if (isPackable(fieldType)) {
            $|
             |{pad}if (!{fname}.empty) \{
             |{pad1}size += protobuf.CodedOutput.{computePackedSizeMethod(field)};
             |{pad}}
             .appendTo(buf);
        } else {
            String ecType = fieldType == TypeMessage ? field.typeName : ecstasyScalarType(field);
            String sizeCall = fieldType == TypeMessage
                    ? $"protobuf.CodedOutput.computeMessageSize({fn}, v)"
                    : $"protobuf.CodedOutput.{computeSizeMethodForElement(field)}";
            $|
             |{pad}for ({ecType} v : {fname}) \{
             |{pad1}size += {sizeCall};
             |{pad}}
             .appendTo(buf);
        }
    }

    /**
     * Generate size logic for a map field.
     */
    private void generateSizeMapField(StringBuffer buf, FieldDescriptorProto field,
                                      String fname, Int indent,
                                      Map<String, MapFieldInfo> mapInfo) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        assert MapFieldInfo info := mapInfo.get(field.typeName);
        String computeMapMethod = mapComputeSizeMethod(info.keyType, info.valueType);
        if (computeMapMethod.size > 0) {
            String kt = ecstasyType(info.keyType);
            String vt = ecstasyType(info.valueType);
            Int    fn = field.number;
            $|
             |{pad}for (({kt} k, {vt} v) : {fname}) \{
             |{pad1}size += protobuf.CodedOutput.{computeMapMethod}({fn}, k, v);
             |{pad}}
             .appendTo(buf);
        }
    }

    /**
     * Generate size logic for a oneof group.
     */
    private void generateSizeOneof(StringBuffer buf, OneofDescriptorProto oneof, Int oi,
                                   Array<FieldDescriptorProto> oneofFields, Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String typedefName = $"{toPascalCase(oneof.name)}Type";
        String propName    = toCamelCase(oneof.name);

        $"\n{pad}{typedefName}? {propName} = this.{propName};\n".appendTo(buf);

        Boolean first = True;
        for (FieldDescriptorProto field : oneofFields) {
            if (field.oneofIndex != oi) {
                continue;
            }
            String ecType = ecstasyScalarType(field);

            if (!first) {
                $" else ".appendTo(buf);
            }
            first = False;

            String castExpr = $"{propName}.as({ecType})";
            $|if ({propName}.is({ecType})) \{
             |{pad1}size += protobuf.CodedOutput.{computeSizeMethodForOneof(field, castExpr)};
             |{pad}}
             .appendTo(buf);
        }
        buf.add('\n');
    }

    // ----- freeze generation ----------------------------------------------------------------------

    /**
     * Generate the freeze override.
     */
    private void generateFreeze(StringBuffer buf, DescriptorProto msg,
                                Array<FieldDescriptorProto> regularFields, Int indent,
                                Map<String, MapFieldInfo> mapInfo) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String pad2 = spaces(indent + 2);
        String pad3 = spaces(indent + 3);

        $|{pad}@Override
         |{pad}immutable {msg.name} freeze(Boolean inPlace = False) \{
         |{pad1}if (this.is(immutable {msg.name})) \{
         |{pad2}return this;
         |{pad1}}
         |{pad1}if (!inPlace) \{
         |{pad2}return new {msg.name}(this).freeze(True);
         |{pad1}}
         |{pad1}unknownFields.freeze(True);
         .appendTo(buf);

        // freeze mutable fields (arrays, maps, messages)
        for (FieldDescriptorProto field : regularFields) {
            String fname = fieldName(field.name);
            assert FieldType fieldType := field.hasType();
            if (isMapField(field, mapInfo)) {
                $|
                 |{pad1}Map {fname} = this.{fname};
                 |{pad1}if ({fname}.is(Freezable)) \{
                 |{pad2}{fname}.freeze(True);
                 |{pad1}} else \{
                 |{pad2}this.{fname} = new ecstasy.maps.ListMap({fname}).freeze(True);
                 |{pad1}}
                 .appendTo(buf);
            } else if (field.label == LabelRepeated) {
                $"\n{pad1}{fname}.freeze(True);".appendTo(buf);
            } else if (fieldType == TypeMessage && !isEnumRef(field)) {
                $"\n{pad1}{fname}?.freeze(True);".appendTo(buf);
            }
        }

        $|
         |{pad1}return this.makeImmutable();
         |{pad}}
         |
         .appendTo(buf);
    }

    // ----- service generation ---------------------------------------------------------------------

    /**
     * Generate a service interface.
     */
    private void generateService(StringBuffer buf, ServiceDescriptorProto svc,
                                 FileDescriptorProto file, Int indent) {

        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);

        $|{pad}/**
         |{pad} * This service has been generated by the Ecstasy protoc plugin from the message type
         |{pad} * {svc.name} defined in the proto file {file.name}
         |{pad} */
         |{pad}interface {svc.name} \{
         |
         .appendTo(buf);

        for (MethodDescriptorProto method : svc.method) {
            buf.add('\n');
            String methodName = toCamelCase(method.name);

            if (method.clientStreaming || method.serverStreaming) {
                String clientStr = method.clientStreaming ? "stream " : "";
                String serverStr = method.serverStreaming ? "stream " : "";
                $|{pad1}// TODO: streaming RPC
                 |{pad1}// {method.name}({clientStr}{method.inputType}) returns ({serverStr}{method.outputType})
                 .appendTo(buf);
            } else {
                $"{pad1}{method.outputType} {methodName}({method.inputType} request);\n".appendTo(buf);
            }
        }

        $"{pad}}\n".appendTo(buf);
    }

    // ----- type mapping helpers -------------------------------------------------------------------

    /**
     * @return the Ecstasy type name for a field declaration
     */
    private String fieldTypeName(FieldDescriptorProto field, Map<String, MapFieldInfo> mapInfo) {
        if (isMapField(field, mapInfo)) {
            assert MapFieldInfo info := mapInfo.get(field.typeName);
            return $"Map<{ecstasyType(info.keyType)}, {mapValueEcstasyType(info)}>";
        }
        assert FieldType fieldType := field.hasType();
        if (field.label == LabelRepeated) {
            if (fieldType == TypeMessage) {
                return $"Array<{field.typeName}>";
            }
            return $"Array<{ecstasyScalarType(field)}>";
        }
        if (fieldType == TypeMessage || isEnumRef(field)) {
            return $"{field.typeName}?";
        }
        return ecstasyScalarType(field);
    }

    /**
     * @return the default value literal for a field
     */
    private String fieldDefault(FieldDescriptorProto field, Map<String, MapFieldInfo> mapInfo) {
        return constructorDefault(field, mapInfo);
    }

    /**
     * @return the raw Ecstasy type name for a scalar field type (no Maybe wrapper)
     */
    private String ecstasyScalarType(FieldDescriptorProto field) {
        assert FieldType fieldType := field.hasType();
        if (fieldType == TypeMessage) {
            return field.typeName;
        }
        if (fieldType == TypeEnum) {
            return field.typeName;
        }
        return ecstasyType(fieldType);
    }

    /**
     * @return the Ecstasy type name for a field type
     */
    private String ecstasyType(FieldType type) {
        return switch (type) {
            case TypeDouble:                          "Float64";
            case TypeFloat:                           "Float32";
            case TypeInt64, TypeSint64, TypeSfixed64: "Int64";
            case TypeUint64, TypeFixed64:             "UInt64";
            case TypeInt32, TypeSint32, TypeSfixed32: "Int32";
            case TypeUint32, TypeFixed32:             "UInt32";
            case TypeBool:                            "Boolean";
            case TypeString:                          "String";
            case TypeBytes:                           "Byte[]";
            default:                                  "Object";
        };
    }

    // ----- read/write method helpers --------------------------------------------------------------

    /**
     * @return the CodedInput read method call for a field (without "input." prefix)
     */
    private String readMethod(FieldDescriptorProto field) {
        assert FieldType fieldType := field.hasType();
        return switch (fieldType) {
            case TypeDouble:   "readDouble()";
            case TypeFloat:    "readFloat()";
            case TypeInt64:    "readInt64()";
            case TypeUint64:   "readUInt64()";
            case TypeInt32:    "readInt32()";
            case TypeFixed64:  "readFixed64()";
            case TypeFixed32:  "readFixed32()";
            case TypeBool:     "readBool()";
            case TypeString:   "readString()";
            case TypeBytes:    "readBytes()";
            case TypeUint32:   "readUInt32()";
            case TypeEnum:     "readEnum()";
            case TypeSfixed32: "readSFixed32()";
            case TypeSfixed64: "readSFixed64()";
            case TypeSint32:   "readSInt32()";
            case TypeSint64:   "readSInt64()";
            default:           "readVarint()";
        };
    }

    /**
     * @return the CodedInput packed read method call for a repeated field
     */
    private String readPackedMethod(FieldDescriptorProto field) {
        assert FieldType fieldType := field.hasType();
        return switch (fieldType) {
            case TypeDouble:                "readPackedDoubles()";
            case TypeFloat:                 "readPackedFloats()";
            case TypeInt64, TypeSint64:     "readPackedVarints()";
            case TypeUint64:                "readPackedUInt64s()";
            case TypeInt32, TypeSint32:     "readPackedInt32s()";
            case TypeFixed64, TypeSfixed64: "readPackedFixed64s()";
            case TypeFixed32, TypeSfixed32: "readPackedFixed32s()";
            case TypeBool:                  "readPackedBools()";
            case TypeUint32:                "readPackedInt32s()";
            case TypeEnum:                  "readPackedVarints()";
            default:                        "readPackedVarints()";
        };
    }

    /**
     * @return the CodedOutput write method name for a field (without "out." prefix)
     */
    private String writeMethod(FieldDescriptorProto field) {
        assert FieldType fieldType := field.hasType();
        return switch (fieldType) {
            case TypeDouble:   "writeDouble";
            case TypeFloat:    "writeFloat";
            case TypeInt64:    "writeInt64";
            case TypeUint64:   "writeUInt64";
            case TypeInt32:    "writeInt32";
            case TypeFixed64:  "writeFixed64";
            case TypeFixed32:  "writeFixed32";
            case TypeBool:     "writeBool";
            case TypeString:   "writeString";
            case TypeBytes:    "writeBytes";
            case TypeUint32:   "writeUInt32";
            case TypeEnum:     "writeEnum";
            case TypeSfixed32: "writeSFixed32";
            case TypeSfixed64: "writeSFixed64";
            case TypeSint32:   "writeSInt32";
            case TypeSint64:   "writeSInt64";
            default:           "writeVarint";
        };
    }

    /**
     * @return the CodedOutput packed write method name for a repeated field
     */
    private String writePackedMethod(FieldDescriptorProto field) {
        assert FieldType fieldType := field.hasType();
        return switch (fieldType) {
            case TypeDouble:                "writePackedDoubles";
            case TypeFloat:                 "writePackedFloats";
            case TypeInt64, TypeSint64:     "writePackedVarints";
            case TypeUint64:                "writePackedVarints";
            case TypeInt32, TypeSint32:     "writePackedVarints";
            case TypeFixed64, TypeSfixed64: "writePackedFixed64s";
            case TypeFixed32, TypeSfixed32: "writePackedFixed32s";
            case TypeBool:                  "writePackedBools";
            case TypeUint32:                "writePackedVarints";
            case TypeEnum:                  "writePackedVarints";
            default:                        "writePackedVarints";
        };
    }

    // ----- compute size helpers -------------------------------------------------------------------

    /**
     * @return the CodedOutput.compute*Size call for a singular field (using fname as the value)
     */
    private String computeSizeMethod(FieldDescriptorProto field) {
        assert FieldType fieldType := field.hasType();
        String fn = field.number.toString();
        String fname = fieldName(field.name);
        return switch (fieldType) {
            case TypeDouble:   $"computeFixed64Size({fn})";
            case TypeFloat:    $"computeFixed32Size({fn})";
            case TypeInt64:    $"computeInt64Size({fn}, {fname})";
            case TypeUint64:   $"computeUInt64Size({fn}, {fname})";
            case TypeInt32:    $"computeInt32Size({fn}, {fname})";
            case TypeFixed64:  $"computeFixed64Size({fn})";
            case TypeFixed32:  $"computeFixed32Size({fn})";
            case TypeBool:     $"computeBoolSize({fn})";
            case TypeString:   $"computeStringSize({fn}, {fname})";
            case TypeBytes:    $"computeBytesSize({fn}, {fname})";
            case TypeUint32:   $"computeUInt32Size({fn}, {fname})";
            case TypeEnum:     $"computeEnumSize({fn}, {fname})";
            case TypeSfixed32: $"computeFixed32Size({fn})";
            case TypeSfixed64: $"computeFixed64Size({fn})";
            case TypeSint32:   $"computeSInt32Size({fn}, {fname})";
            case TypeSint64:   $"computeSInt64Size({fn}, {fname})";
            default:           $"computeVarintSize({fname})";
        };
    }

    /**
     * @return the compute size call for a repeated element (uses "v" as variable name)
     */
    private String computeSizeMethodForElement(FieldDescriptorProto field) {
        assert FieldType fieldType := field.hasType();
        String fn = field.number.toString();
        return switch (fieldType) {
            case TypeString: $"computeStringSize({fn}, v)";
            case TypeBytes:  $"computeBytesSize({fn}, v)";
            default:         $"computeBytesSize({fn}, v)";
        };
    }

    /**
     * @return the compute packed size call for a repeated packable field
     */
    private String computePackedSizeMethod(FieldDescriptorProto field) {
        assert FieldType fieldType := field.hasType();
        String fn = field.number.toString();
        String fname = fieldName(field.name);
        return switch (fieldType) {
            case TypeDouble:                $"computePackedDoublesSize({fn}, {fname})";
            case TypeFloat:                 $"computePackedFloatsSize({fn}, {fname})";
            case TypeInt64, TypeSint64:     $"computePackedVarintsSize({fn}, {fname})";
            case TypeUint64:                $"computePackedVarintsSize({fn}, {fname})";
            case TypeInt32, TypeSint32:     $"computePackedVarintsSize({fn}, {fname})";
            case TypeFixed64, TypeSfixed64: $"computePackedFixed64sSize({fn}, {fname})";
            case TypeFixed32, TypeSfixed32: $"computePackedFixed32sSize({fn}, {fname})";
            case TypeBool:                  $"computePackedBoolsSize({fn}, {fname})";
            case TypeUint32:                $"computePackedVarintsSize({fn}, {fname})";
            case TypeEnum:                  $"computePackedVarintsSize({fn}, {fname})";
            default:                        $"computePackedVarintsSize({fn}, {fname})";
        };
    }

    /**
     * @return the compute size call for a oneof field (uses the local variable name)
     */
    private String computeSizeMethodForOneof(FieldDescriptorProto field, String varName) {
        assert FieldType fieldType := field.hasType();
        String fn = field.number.toString();
        return switch (fieldType) {
            case TypeDouble:   $"computeFixed64Size({fn})";
            case TypeFloat:    $"computeFixed32Size({fn})";
            case TypeInt64:    $"computeInt64Size({fn}, {varName})";
            case TypeUint64:   $"computeUInt64Size({fn}, {varName})";
            case TypeInt32:    $"computeInt32Size({fn}, {varName})";
            case TypeFixed64:  $"computeFixed64Size({fn})";
            case TypeFixed32:  $"computeFixed32Size({fn})";
            case TypeBool:     $"computeBoolSize({fn})";
            case TypeString:   $"computeStringSize({fn}, {varName})";
            case TypeBytes:    $"computeBytesSize({fn}, {varName})";
            case TypeUint32:   $"computeUInt32Size({fn}, {varName})";
            case TypeEnum:     $"computeEnumSize({fn}, {varName})";
            case TypeSfixed32: $"computeFixed32Size({fn})";
            case TypeSfixed64: $"computeFixed64Size({fn})";
            case TypeSint32:   $"computeSInt32Size({fn}, {varName})";
            case TypeSint64:   $"computeSInt64Size({fn}, {varName})";
            default:           $"computeVarintSize({varName})";
        };
    }

    // ----- map method helpers --------------------------------------------------------------------

    /**
     * @return the CodedInput read method for a map field, or "" if unsupported
     */
    private String mapReadMethod(FieldType keyType, FieldType valueType) {
        return switch (keyType, valueType) {
            case (TypeString, TypeString): "readMapStringString()";
            case (TypeString, TypeInt32):  "readMapStringInt32()";
            case (TypeString, TypeInt64):  "readMapStringInt64()";
            case (TypeString, TypeBytes):  "readMapStringBytes()";
            case (TypeInt32,  TypeString): "readMapInt32String()";
            case (TypeInt32,  TypeInt32):  "readMapInt32Int32()";
            case (TypeInt64,  TypeString): "readMapInt64String()";
            case (TypeInt64,  TypeInt64):  "readMapInt64Int64()";
            case (TypeBool,   TypeString): "readMapBoolString()";
            default: "";
        };
    }

    /**
     * @return the CodedOutput write method for a map field, or "" if unsupported
     */
    private String mapWriteMethod(FieldType keyType, FieldType valueType) {
        return switch (keyType, valueType) {
            case (TypeString, TypeString): "writeMapStringString";
            case (TypeString, TypeInt32):  "writeMapStringInt32";
            case (TypeString, TypeInt64):  "writeMapStringInt64";
            case (TypeString, TypeBytes):  "writeMapStringBytes";
            case (TypeInt32,  TypeString): "writeMapInt32String";
            case (TypeInt32,  TypeInt32):  "writeMapInt32Int32";
            case (TypeInt64,  TypeString): "writeMapInt64String";
            case (TypeInt64,  TypeInt64):  "writeMapInt64Int64";
            case (TypeBool,   TypeString): "writeMapBoolString";
            default: "";
        };
    }

    /**
     * @return the CodedOutput compute size method for a map field, or "" if unsupported
     */
    private String mapComputeSizeMethod(FieldType keyType, FieldType valueType) {
        return switch (keyType, valueType) {
            case (TypeString, TypeString): "computeMapStringStringSize";
            case (TypeString, TypeInt32):  "computeMapStringInt32Size";
            case (TypeString, TypeInt64):  "computeMapStringInt64Size";
            case (TypeString, TypeBytes):  "computeMapStringBytesSize";
            case (TypeInt32,  TypeString): "computeMapInt32StringSize";
            case (TypeInt32,  TypeInt32):  "computeMapInt32Int32Size";
            case (TypeInt64,  TypeString): "computeMapInt64StringSize";
            case (TypeInt64,  TypeInt64):  "computeMapInt64Int64Size";
            case (TypeBool,   TypeString): "computeMapBoolStringSize";
            default: "";
        };
    }

    // ----- enum resolution helpers ----------------------------------------------------------------

    /**
     * Collect all enum type names from the file (top-level and nested).
     */
    private Set<String> collectEnumNames(FileDescriptorProto file) {
        HashSet<String> names = new HashSet();
        for (EnumDescriptorProto e : file.enumType) {
            names.add(e.name);
        }
        for (DescriptorProto msg : file.messageType) {
            collectEnumNamesFromMessage(msg, "", names);
        }
        return names;
    }

    /**
     * Recursively collect enum type names from a message and its nested types.
     * Adds both the simple name and the dot-qualified name so that references like
     * `VisibilityFeature.DefaultSymbolVisibility` are recognized as enum references.
     */
    private void collectEnumNamesFromMessage(DescriptorProto msg, String prefix,
                                             HashSet<String> names) {
        String qualifiedPrefix = prefix.size == 0 ? msg.name : $"{prefix}.{msg.name}";
        for (EnumDescriptorProto e : msg.enumType) {
            names.add(e.name);
            names.add($"{qualifiedPrefix}.{e.name}");
        }
        for (DescriptorProto nested : msg.nestedType) {
            collectEnumNamesFromMessage(nested, qualifiedPrefix, names);
        }
    }

    /**
     * @return True if the field references an enum type (either via TypeEnum or by
     *         having a typeName that matches a known enum)
     */
    private Boolean isEnumRef(FieldDescriptorProto field) {
        assert FieldType fieldType := field.hasType();
        if (fieldType == TypeEnum) {
            return True;
        }
        if (fieldType != TypeMessage || field.typeName.size == 0) {
            return False;
        }
        if (enumNames.contains(field.typeName)) {
            return True;
        }
        // check if the last segment of a dotted name is a known enum
        if (Int dot := field.typeName.lastIndexOf('.')) {
            return enumNames.contains(field.typeName[dot + 1 ..< field.typeName.size]);
        }
        return False;
    }

    // ----- presence tracking helpers ---------------------------------------------------------------

    /**
     * @return True if this field needs a presence bit in the bitmask
     */
    private Boolean needsPresenceBit(FieldDescriptorProto field, Map<String, MapFieldInfo> mapInfo) {
        assert FieldType fieldType := field.hasType();
        return !isMapField(field, mapInfo)
            && field.label != LabelRepeated
            && fieldType != TypeMessage
            && !isEnumRef(field);
    }

    /**
     * Build a map from escaped field name to bit index for all fields that need presence tracking.
     */
    private Map<String, Int> buildPresenceMap(Array<FieldDescriptorProto> regularFields,
                                              Map<String, MapFieldInfo> mapInfo) {
        ListMap<String, Int> map = new ListMap();
        Int bitIndex = 0;
        for (FieldDescriptorProto field : regularFields) {
            if (needsPresenceBit(field, mapInfo)) {
                map.put(fieldName(field.name), bitIndex++);
            }
        }
        return map;
    }

    /**
     * @return the name of the presence bitmask field for the given bit index
     */
    private String presenceWordName(Int bitIndex) {
        return $"presentBits_{bitIndex / 64}";
    }

    /**
     * @return the hex literal mask for the given bit index within its word
     */
    private static String[] HexMasks = [
            "0x01",               "0x02",               "0x04",               "0x08",
            "0x10",               "0x20",               "0x40",               "0x80",
            "0x0100",             "0x0200",             "0x0400",             "0x0800",
            "0x1000",             "0x2000",             "0x4000",             "0x8000",
            "0x00010000",         "0x00020000",         "0x00040000",         "0x00080000",
            "0x00100000",         "0x00200000",         "0x00400000",         "0x00800000",
            "0x01000000",         "0x02000000",         "0x04000000",         "0x08000000",
            "0x10000000",         "0x20000000",         "0x40000000",         "0x80000000",
            "0x0000000100000000", "0x0000000200000000", "0x0000000400000000", "0x0000000800000000",
            "0x0000001000000000", "0x0000002000000000", "0x0000004000000000", "0x0000008000000000",
            "0x0000010000000000", "0x0000020000000000", "0x0000040000000000", "0x0000080000000000",
            "0x0000100000000000", "0x0000200000000000", "0x0000400000000000", "0x0000800000000000",
            "0x0001000000000000", "0x0002000000000000", "0x0004000000000000", "0x0008000000000000",
            "0x0010000000000000", "0x0020000000000000", "0x0040000000000000", "0x0080000000000000",
            "0x0100000000000000", "0x0200000000000000", "0x0400000000000000", "0x0800000000000000",
            "0x1000000000000000", "0x2000000000000000", "0x4000000000000000", "0x8000000000000000",
        ];

    private String presenceMaskLiteral(Int bitIndex) {
        return HexMasks[bitIndex % 64];
    }

    /**
     * @return the condition expression to check if a field's presence bit is set
     */
    private String presenceCondition(Map<String, Int> presenceMap, String fname) {
        assert Int bitIndex := presenceMap.get(fname);
        return $"{presenceWordName(bitIndex)} & {presenceMaskLiteral(bitIndex)} != 0";
    }

    // ----- naming helpers ------------------------------------------------------------------------

    /**
     * Convert a proto field name to a safe Ecstasy camelCase identifier, escaping keywords.
     */
    private String fieldName(String name) = escapeKeyword(toCamelCase(name));

    /**
     * Convert a `snake_case` or `UPPER_SNAKE_CASE` name to `camelCase`.
     */
    private String toCamelCase(String name) {
        // if no underscores, it's PascalCase or a single word — just lowercase the first char
        if (!name.indexOf('_')) {
            if (name.size == 0) {
                return name;
            }
            // check if it's ALL_UPPERCASE (no lowercase chars)
            Boolean allUpper = True;
            for (Int i = 0; i < name.size; i++) {
                if (name[i] >= 'a' && name[i] <= 'z') {
                    allUpper = False;
                    break;
                }
            }
            if (allUpper) {
                // ALL_UPPER single word: lowercase everything then uppercase first
                // handled by toPascalCase caller — here just lowercase all
                StringBuffer buf = new StringBuffer();
                for (Int i = 0; i < name.size; i++) {
                    buf.add(name[i] >= 'A' && name[i] <= 'Z' ? name[i].lowercase : name[i]);
                }
                return buf.toString();
            }
            // PascalCase: just lowercase first char
            return $"{name[0].lowercase}{name[1..<name.size]}";
        }

        // snake_case or UPPER_SNAKE_CASE: split on underscore, capitalize each segment
        StringBuffer buf       = new StringBuffer();
        Boolean      upperNext = False;
        Boolean      first     = True;

        for (Int i = 0; i < name.size; i++) {
            Char ch = name[i];
            if (ch == '_') {
                upperNext = True;
            } else if (first) {
                buf.add(ch >= 'A' && ch <= 'Z' ? ch.lowercase : ch);
                first = False;
            } else if (upperNext) {
                buf.add(ch >= 'a' && ch <= 'z' ? ch.uppercase : ch);
                upperNext = False;
            } else {
                buf.add(ch >= 'A' && ch <= 'Z' ? ch.lowercase : ch);
            }
        }
        return buf.toString();
    }

    /**
     * Convert a `snake_case`, `UPPER_SNAKE_CASE`, or `PascalCase` name to `PascalCase`.
     */
    private String toPascalCase(String name) {
        String camel = toCamelCase(name);
        return camel.size > 0 && camel[0] >= 'a' && camel[0] <= 'z'
                ? $"{camel[0].uppercase}{camel[1..<camel.size]}"
                : camel;
    }

    /**
     * Escape a field name if it matches an Ecstasy keyword by appending an underscore.
     */
    private String escapeKeyword(String name) {
        for (String kw : Keywords) {
            if (kw == name) {
                return name + "_";
            }
        }
        return name;
    }

    // ----- formatting helpers --------------------------------------------------------------------

    /**
     * @return whitespace for the given indentation level (4 spaces per level)
     */
    private String spaces(Int level) {
        StringBuffer buf = new StringBuffer(level * 4);
        for (Int i = 0; i < level * 4; i++) {
            buf.add(' ');
        }
        return buf.toString();
    }

    /**
     * @return a separator comment line padded to 100 characters
     */
    private String separator(String label, Int indent) {
        StringBuffer buf = new StringBuffer();
        buf.addAll("// ----- ").addAll(label).addAll(" ");
        Int padLen = indent * 4;
        Int targetLen = 100 - padLen;
        while (buf.size < targetLen) {
            buf.add('-');
        }
        return buf.toString();
    }
}
