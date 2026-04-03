import ecstasy.maps.ListMap;

import FieldDescriptor.Label;

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

    // ----- public interface -----------------------------------------------------------------------

    /**
     * Generate Ecstasy source files from a parsed proto file descriptor.
     *
     * @param file  the parsed FileDescriptor
     *
     * @return a map from file name (e.g. `"Person.x"`) to source text
     */
    Map<String, String> generate(FileDescriptor file) {
        ListMap<String, String> result = new ListMap();

        for (EnumDescriptor e : file.enums) {
            result.put($"{e.name}.x", generateTopLevelEnum(e, file));
        }

        for (MessageDescriptor msg : file.messages) {
            result.put($"{msg.name}.x", generateTopLevelMessage(msg, file));
        }

        for (ServiceDescriptor svc : file.services) {
            result.put($"{svc.name}.x", generateTopLevelService(svc, file));
        }

        return result.freeze(inPlace=True);
    }

    // ----- top-level generators -------------------------------------------------------------------

    /**
     * Generate a top-level enum file.
     */
    private String generateTopLevelEnum(EnumDescriptor e, FileDescriptor file) {
        StringBuffer buf = new StringBuffer();
        appendImport(buf, "protobuf.ProtoEnum");
        buf.add('\n');
        generateEnum(buf, e, 0);
        return buf.toString();
    }

    /**
     * Generate a top-level message file.
     */
    private String generateTopLevelMessage(MessageDescriptor msg, FileDescriptor file) {
        StringBuffer buf = new StringBuffer();

        // collect imports
        Array<String> imports = collectImports(msg);
        for (String imp : imports) {
            appendImport(buf, imp);
        }
        if (!imports.empty) {
            buf.add('\n');
        }

        generateMessage(buf, msg, 0, False);
        return buf.toString();
    }

    /**
     * Generate a top-level service interface file.
     */
    private String generateTopLevelService(ServiceDescriptor svc, FileDescriptor file) {
        StringBuffer buf = new StringBuffer();
        generateService(buf, svc, 0);
        return buf.toString();
    }

    // ----- enum generation ------------------------------------------------------------------------

    /**
     * Generate an enum declaration.
     */
    private void generateEnum(StringBuffer buf, EnumDescriptor e, Int indent) {
        String pad = spaces(indent);

        buf.addAll(pad)
           .addAll("enum ")
           .addAll(e.name)
           .add('\n');
        buf.addAll(pad)
           .addAll("        implements ProtoEnum {\n");

        // enum values
        for (Int i = 0; i < e.values.size; i++) {
            EnumValueDescriptor v = e.values[i];
            buf.addAll(pad)
               .addAll("    ")
               .addAll(toPascalCase(v.name))
               .add('(')
               .addAll(v.number.toString())
               .add(')');
            if (i < e.values.size - 1) {
                buf.add(',');
            } else {
                buf.add(';');
            }
            buf.add('\n');
        }

        buf.add('\n');
        buf.addAll(pad)
           .addAll("    construct(Int protoValue) {\n");
        buf.addAll(pad)
           .addAll("        this.protoValue = protoValue;\n");
        buf.addAll(pad)
           .addAll("    }\n");
        buf.add('\n');
        buf.addAll(pad)
           .addAll("    @Override\n");
        buf.addAll(pad)
           .addAll("    Int protoValue;\n");
        buf.addAll(pad)
           .addAll("}\n");
    }

    // ----- message generation ---------------------------------------------------------------------

    /**
     * Generate a message class declaration.
     *
     * @param buf      the output buffer
     * @param msg      the message descriptor
     * @param indent   the indentation level
     * @param nested   True if this is a nested (inner static) class
     */
    private void generateMessage(StringBuffer buf, MessageDescriptor msg, Int indent,
                                 Boolean nested) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String pad2 = spaces(indent + 2);

        // class declaration
        if (nested) {
            buf.addAll(pad).addAll("static class ").addAll(msg.name).add('\n');
        } else {
            buf.addAll(pad).addAll("class ").addAll(msg.name).add('\n');
        }
        buf.addAll(pad).addAll("        extends AbstractMessage {\n");
        buf.add('\n');

        // collect non-oneof fields and oneof descriptors
        Array<FieldDescriptor> regularFields = new Array();
        Array<FieldDescriptor> oneofFields   = new Array();
        for (FieldDescriptor field : msg.fields) {
            if (field.oneofIndex >= 0) {
                oneofFields.add(field);
            } else {
                regularFields.add(field);
            }
        }

        // inline typedefs for message/enum references
        generateTypedefs(buf, msg, indent + 1);

        // default constructor
        buf.addAll(pad1).addAll("construct() {\n");
        buf.addAll(pad2).addAll("construct AbstractMessage();\n");
        buf.addAll(pad1).addAll("}\n");
        buf.add('\n');

        // copy constructor
        generateCopyConstructor(buf, msg, regularFields, indent + 1);
        buf.add('\n');

        // field declarations
        generateFieldDeclarations(buf, regularFields, msg.oneofs, indent + 1);
        buf.add('\n');

        // oneof accessors
        if (!msg.oneofs.empty) {
            buf.addAll(pad1).addAll(separator("oneof accessors", indent + 1)).add('\n');
            buf.add('\n');
            for (Int oi = 0; oi < msg.oneofs.size; oi++) {
                generateOneofAccessors(buf, msg.oneofs[oi], oneofFields, oi, indent + 1);
            }
        }

        // nested enums
        for (EnumDescriptor e : msg.enums) {
            buf.addAll(pad1).addAll(separator("enum " + e.name, indent + 1)).add('\n');
            buf.add('\n');
            generateEnum(buf, e, indent + 1);
            buf.add('\n');
        }

        // nested messages
        for (MessageDescriptor nested2 : msg.nestedMessages) {
            buf.addAll(pad1).addAll(separator("message " + nested2.name, indent + 1)).add('\n');
            buf.add('\n');
            generateMessage(buf, nested2, indent + 1, True);
            buf.add('\n');
        }

        // serialization section
        buf.addAll(pad1).addAll(separator("serialization", indent + 1)).add('\n');
        buf.add('\n');

        // parseField
        generateParseField(buf, msg, regularFields, oneofFields, indent + 1);
        buf.add('\n');

        // writeKnownFields
        generateWriteKnownFields(buf, msg, regularFields, oneofFields, indent + 1);
        buf.add('\n');

        // knownFieldsSize
        generateKnownFieldsSize(buf, msg, regularFields, oneofFields, indent + 1);
        buf.add('\n');

        // freeze
        generateFreeze(buf, msg, regularFields, indent + 1);

        buf.addAll(pad).addAll("}\n");
    }

    // ----- typedef generation ---------------------------------------------------------------------

    /**
     * Generate inline typedefs for message and enum field references.
     */
    private void generateTypedefs(StringBuffer buf, MessageDescriptor msg, Int indent) {
        String pad = spaces(indent);
        Boolean emitted = False;

        // collect unique type names that need Maybe typedefs
        Array<String> seen = new Array();
        for (FieldDescriptor field : msg.fields) {
            if (field.oneofIndex >= 0) {
                continue;  // oneof fields use Oneof class, not Maybe types
            }
            if (field.label == Repeated) {
                continue;  // repeated fields use arrays, not Maybe types
            }
            if (field.isMapField) {
                continue;
            }
            String tn = field.typeName;
            if (tn.size > 0 && !seen.contains(tn)) {
                seen.add(tn);
                buf.addAll(pad)
                   .addAll("typedef Presence | ")
                   .addAll(tn)
                   .addAll(" as Maybe")
                   .addAll(sanitizeTypedefName(tn))
                   .addAll(";\n");
                emitted = True;
            }
        }
        if (emitted) {
            buf.add('\n');
        }
    }

    // ----- field declaration generation ------------------------------------------------------------

    /**
     * Generate field declarations for regular (non-oneof) fields and oneof instances.
     */
    private void generateFieldDeclarations(StringBuffer buf,
                                           Array<FieldDescriptor> regularFields,
                                           OneofDescriptor[] oneofs, Int indent) {
        String pad = spaces(indent);

        for (FieldDescriptor field : regularFields) {
            buf.addAll(pad)
               .addAll(fieldTypeName(field))
               .add(' ')
               .addAll(fieldName(field.name))
               .addAll(" = ")
               .addAll(fieldDefault(field))
               .addAll(";\n");
        }

        for (OneofDescriptor oneof : oneofs) {
            buf.addAll(pad)
               .addAll("Oneof ")
               .addAll(fieldName(oneof.name))
               .addAll(" = new Oneof();\n");
        }
    }

    // ----- copy constructor generation ------------------------------------------------------------

    /**
     * Generate the Duplicable copy constructor.
     */
    private void generateCopyConstructor(StringBuffer buf, MessageDescriptor msg,
                                         Array<FieldDescriptor> regularFields, Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);

        buf.addAll(pad).addAll("@Override\n");
        buf.addAll(pad).addAll("construct(").addAll(msg.name).addAll(" other) {\n");
        buf.addAll(pad1).addAll("construct AbstractMessage(other);\n");

        for (FieldDescriptor field : regularFields) {
            String fname = fieldName(field.name);
            if (field.label == Repeated || field.isMapField) {
                buf.addAll(pad1)
                   .addAll(fname)
                   .addAll(" = other.")
                   .addAll(fname)
                   .addAll(".duplicate();\n");
            } else {
                buf.addAll(pad1)
                   .addAll(fname)
                   .addAll(" = other.")
                   .addAll(fname)
                   .addAll(";\n");
            }
        }

        for (OneofDescriptor oneof : msg.oneofs) {
            String oname = fieldName(oneof.name);
            buf.addAll(pad1)
               .addAll(oname)
               .addAll(" = other.")
               .addAll(oname)
               .addAll(".duplicate();\n");
        }

        buf.addAll(pad).addAll("}\n");
    }

    // ----- oneof accessor generation --------------------------------------------------------------

    /**
     * Generate typed getters and setters for oneof fields.
     */
    private void generateOneofAccessors(StringBuffer buf, OneofDescriptor oneof,
                                        Array<FieldDescriptor> oneofFields, Int oneofIndex,
                                        Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String oname = fieldName(oneof.name);

        for (FieldDescriptor field : oneofFields) {
            if (field.oneofIndex != oneofIndex) {
                continue;
            }
            String fname    = toPascalCase(field.name);
            String ecType   = ecstasyScalarType(field);
            Int    fieldNum = field.number;

            // getter
            buf.addAll(pad)
               .addAll("conditional ")
               .addAll(ecType)
               .addAll(" get")
               .addAll(fname)
               .addAll("() {\n");
            buf.addAll(pad1)
               .addAll("if (Object v := ")
               .addAll(oname)
               .addAll(".get(")
               .addAll(fieldNum.toString())
               .addAll(")) {\n");
            buf.addAll(spaces(indent + 2))
               .addAll("return True, v.as(")
               .addAll(ecType)
               .addAll(");\n");
            buf.addAll(pad1)
               .addAll("}\n");
            buf.addAll(pad1)
               .addAll("return False;\n");
            buf.addAll(pad)
               .addAll("}\n");
            buf.add('\n');

            // setter
            buf.addAll(pad)
               .addAll("void set")
               .addAll(fname)
               .addAll("(")
               .addAll(ecType)
               .addAll(" value) {\n");
            buf.addAll(pad1)
               .addAll(oname)
               .addAll(".set(")
               .addAll(fieldNum.toString())
               .addAll(", value);\n");
            buf.addAll(pad)
               .addAll("}\n");
            buf.add('\n');
        }
    }

    // ----- parseField generation ------------------------------------------------------------------

    /**
     * Generate the parseField override.
     */
    private void generateParseField(StringBuffer buf, MessageDescriptor msg,
                                    Array<FieldDescriptor> regularFields,
                                    Array<FieldDescriptor> oneofFields, Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String pad2 = spaces(indent + 2);
        String pad3 = spaces(indent + 3);

        buf.addAll(pad).addAll("@Override\n");
        buf.addAll(pad).addAll("Boolean parseField(CodedInput input, Int tag) {\n");

        if (regularFields.empty && oneofFields.empty) {
            // no known fields — delegate to superclass for unknown field handling
            buf.addAll(pad1).addAll("return False;\n");
        } else {
            buf.addAll(pad1).addAll("switch (WireType.getFieldNumber(tag)) {\n");

            // regular fields
            for (FieldDescriptor field : regularFields) {
                String fname = fieldName(field.name);
                buf.addAll(pad1).addAll("case ").addAll(field.number.toString()).addAll(":\n");

                if (field.isMapField) {
                    generateParseMapField(buf, field, fname, indent + 2);
                } else if (field.label == Repeated) {
                    generateParseRepeatedField(buf, field, fname, indent + 2);
                } else if (field.type == FieldType.Msg) {
                    generateParseMessageField(buf, field, fname, indent + 2);
                } else {
                    buf.addAll(pad2)
                       .addAll(fname)
                       .addAll(" = input.")
                       .addAll(readMethod(field))
                       .addAll(";\n");
                }
                buf.addAll(pad2).addAll("return True;\n");
            }

            // oneof fields
            for (FieldDescriptor field : oneofFields) {
                String oname = fieldName(msg.oneofs[field.oneofIndex].name);
                buf.addAll(pad1).addAll("case ").addAll(field.number.toString()).addAll(":\n");
                buf.addAll(pad2)
                   .addAll(oname)
                   .addAll(".set(")
                   .addAll(field.number.toString())
                   .addAll(", input.")
                   .addAll(readMethod(field))
                   .addAll(");\n");
                buf.addAll(pad2).addAll("return True;\n");
            }

            buf.addAll(pad1).addAll("}\n");
            buf.addAll(pad1).addAll("return False;\n");
        }

        buf.addAll(pad).addAll("}\n");
    }

    /**
     * Generate parse logic for a repeated field.
     */
    private void generateParseRepeatedField(StringBuffer buf, FieldDescriptor field,
                                            String fname, Int indent) {
        String pad = spaces(indent);
        if (field.type.packable) {
            // handle both packed (LEN) and unpacked wire types
            String pad1 = spaces(indent + 1);
            buf.addAll(pad)
               .addAll("if (WireType.getWireType(tag) == WireType.LEN) {\n");
            buf.addAll(pad1)
               .addAll(fname)
               .addAll(".addAll(input.")
               .addAll(readPackedMethod(field))
               .addAll(");\n");
            buf.addAll(pad)
               .addAll("} else {\n");
            buf.addAll(pad1)
               .addAll(fname)
               .addAll(".add(input.")
               .addAll(readMethod(field))
               .addAll(");\n");
            buf.addAll(pad)
               .addAll("}\n");
        } else if (field.type == FieldType.Msg) {
            // repeated message
            String pad1 = spaces(indent + 1);
            buf.addAll(pad)
               .addAll(field.typeName)
               .addAll(" elem = new ")
               .addAll(field.typeName)
               .addAll("();\n");
            buf.addAll(pad)
               .addAll("Int len = input.readVarint().toInt();\n");
            buf.addAll(pad)
               .addAll("Int oldLimit = input.pushLimit(len);\n");
            buf.addAll(pad)
               .addAll("elem.mergeFrom(input);\n");
            buf.addAll(pad)
               .addAll("input.popLimit(oldLimit);\n");
            buf.addAll(pad)
               .addAll(fname)
               .addAll(".add(elem);\n");
        } else {
            // repeated non-packable scalar (string, bytes)
            buf.addAll(pad)
               .addAll(fname)
               .addAll(".add(input.")
               .addAll(readMethod(field))
               .addAll(");\n");
        }
    }

    /**
     * Generate parse logic for a singular message field.
     */
    private void generateParseMessageField(StringBuffer buf, FieldDescriptor field,
                                           String fname, Int indent) {
        String pad = spaces(indent);
        buf.addAll(pad)
           .addAll(field.typeName)
           .addAll(" msg = new ")
           .addAll(field.typeName)
           .addAll("();\n");
        buf.addAll(pad)
           .addAll("Int len = input.readVarint().toInt();\n");
        buf.addAll(pad)
           .addAll("Int oldLimit = input.pushLimit(len);\n");
        buf.addAll(pad)
           .addAll("msg.mergeFrom(input);\n");
        buf.addAll(pad)
           .addAll("input.popLimit(oldLimit);\n");
        buf.addAll(pad)
           .addAll(fname)
           .addAll(" = msg;\n");
    }

    /**
     * Generate parse logic for a map field.
     */
    private void generateParseMapField(StringBuffer buf, FieldDescriptor field,
                                       String fname, Int indent) {
        String pad  = spaces(indent);
        String readMapMethod = mapReadMethod(field);
        if (readMapMethod.size > 0) {
            buf.addAll(pad)
               .addAll("(")
               .addAll(ecstasyType(field.mapKeyType))
               .addAll(" k, ")
               .addAll(ecstasyType(field.mapValueType))
               .addAll(" v) = input.")
               .addAll(readMapMethod)
               .addAll(";\n");
            buf.addAll(pad)
               .addAll(fname)
               .addAll(".put(k, v);\n");
        } else {
            // fallback: inline sub-message parsing
            buf.addAll(pad).addAll("// TODO: unsupported map type combination\n");
        }
    }

    // ----- writeKnownFields generation ------------------------------------------------------------

    /**
     * Generate the writeKnownFields override.
     */
    private void generateWriteKnownFields(StringBuffer buf, MessageDescriptor msg,
                                          Array<FieldDescriptor> regularFields,
                                          Array<FieldDescriptor> oneofFields, Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String pad2 = spaces(indent + 2);

        buf.addAll(pad).addAll("@Override\n");
        buf.addAll(pad).addAll("void writeKnownFields(CodedOutput out) {\n");

        // regular fields
        for (FieldDescriptor field : regularFields) {
            String fname = fieldName(field.name);

            if (field.isMapField) {
                generateWriteMapField(buf, field, fname, indent + 1);
            } else if (field.label == Repeated) {
                generateWriteRepeatedField(buf, field, fname, indent + 1);
            } else if (field.type == FieldType.Msg) {
                String ecType = field.typeName;
                buf.addAll(pad1)
                   .addAll("if (")
                   .addAll(fname)
                   .addAll(".is(")
                   .addAll(ecType)
                   .addAll(")) {\n");
                buf.addAll(pad2)
                   .addAll("out.writeMessage(")
                   .addAll(field.number.toString())
                   .addAll(", ")
                   .addAll(fname)
                   .addAll(");\n");
                buf.addAll(pad1).addAll("}\n");
            } else {
                String ecType = ecstasyScalarType(field);
                buf.addAll(pad1)
                   .addAll("if (")
                   .addAll(fname)
                   .addAll(".is(")
                   .addAll(ecType)
                   .addAll(")) {\n");
                buf.addAll(pad2)
                   .addAll("out.")
                   .addAll(writeMethod(field))
                   .addAll("(")
                   .addAll(field.number.toString())
                   .addAll(", ")
                   .addAll(fname)
                   .addAll(");\n");
                buf.addAll(pad1).addAll("}\n");
            }
        }

        // oneof fields
        for (OneofDescriptor oneof : msg.oneofs) {
            generateWriteOneof(buf, oneof, oneofFields, indent + 1);
        }

        buf.addAll(pad).addAll("}\n");
    }

    /**
     * Generate write logic for a repeated field.
     */
    private void generateWriteRepeatedField(StringBuffer buf, FieldDescriptor field,
                                            String fname, Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        if (field.type.packable) {
            buf.addAll(pad)
               .addAll("if (!")
               .addAll(fname)
               .addAll(".empty) {\n");
            buf.addAll(pad1)
               .addAll("out.")
               .addAll(writePackedMethod(field))
               .addAll("(")
               .addAll(field.number.toString())
               .addAll(", ")
               .addAll(fname)
               .addAll(");\n");
            buf.addAll(pad).addAll("}\n");
        } else {
            String ecType = field.type == FieldType.Msg ? field.typeName
                                                        : ecstasyScalarType(field);
            buf.addAll(pad)
               .addAll("for (")
               .addAll(ecType)
               .addAll(" v : ")
               .addAll(fname)
               .addAll(") {\n");
            if (field.type == FieldType.Msg) {
                buf.addAll(pad1)
                   .addAll("out.writeMessage(")
                   .addAll(field.number.toString())
                   .addAll(", v);\n");
            } else {
                buf.addAll(pad1)
                   .addAll("out.")
                   .addAll(writeMethod(field))
                   .addAll("(")
                   .addAll(field.number.toString())
                   .addAll(", v);\n");
            }
            buf.addAll(pad).addAll("}\n");
        }
    }

    /**
     * Generate write logic for a map field.
     */
    private void generateWriteMapField(StringBuffer buf, FieldDescriptor field,
                                       String fname, Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String writeMapMethod = mapWriteMethod(field);
        if (writeMapMethod.size > 0) {
            buf.addAll(pad)
               .addAll("for ((")
               .addAll(ecstasyType(field.mapKeyType))
               .addAll(" k, ")
               .addAll(ecstasyType(field.mapValueType))
               .addAll(" v) : ")
               .addAll(fname)
               .addAll(") {\n");
            buf.addAll(pad1)
               .addAll("out.")
               .addAll(writeMapMethod)
               .addAll("(")
               .addAll(field.number.toString())
               .addAll(", k, v);\n");
            buf.addAll(pad).addAll("}\n");
        }
    }

    /**
     * Generate write logic for a oneof group.
     */
    private void generateWriteOneof(StringBuffer buf, OneofDescriptor oneof,
                                    Array<FieldDescriptor> oneofFields, Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String pad2 = spaces(indent + 2);
        String oname = fieldName(oneof.name);

        buf.addAll(pad)
           .addAll("switch (")
           .addAll(oname)
           .addAll(".activeFieldNumber) {\n");

        for (FieldDescriptor field : oneofFields) {
            if (!Oneof.contains(field.number, oneof.fieldNumbers)) {
                continue;
            }
            String fname   = toPascalCase(field.name);
            String ecType  = ecstasyScalarType(field);

            buf.addAll(pad)
               .addAll("case ")
               .addAll(field.number.toString())
               .addAll(":\n");
            buf.addAll(pad1)
               .addAll("if (")
               .addAll(ecType)
               .addAll(" ")
               .addAll(fieldName(field.name))
               .addAll(" := get")
               .addAll(fname)
               .addAll("()) {\n");
            buf.addAll(pad2)
               .addAll("out.")
               .addAll(writeMethod(field))
               .addAll("(")
               .addAll(field.number.toString())
               .addAll(", ")
               .addAll(fieldName(field.name))
               .addAll(");\n");
            buf.addAll(pad1)
               .addAll("}\n");
            buf.addAll(pad1).addAll("break;\n");
        }

        buf.addAll(pad).addAll("}\n");
    }

    // ----- knownFieldsSize generation -------------------------------------------------------------

    /**
     * Generate the knownFieldsSize override.
     */
    private void generateKnownFieldsSize(StringBuffer buf, MessageDescriptor msg,
                                         Array<FieldDescriptor> regularFields,
                                         Array<FieldDescriptor> oneofFields, Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String pad2 = spaces(indent + 2);

        buf.addAll(pad).addAll("@Override\n");
        buf.addAll(pad).addAll("Int knownFieldsSize() {\n");
        buf.addAll(pad1).addAll("Int size = 0;\n");

        // regular fields
        for (FieldDescriptor field : regularFields) {
            String fname = fieldName(field.name);

            if (field.isMapField) {
                generateSizeMapField(buf, field, fname, indent + 1);
            } else if (field.label == Repeated) {
                generateSizeRepeatedField(buf, field, fname, indent + 1);
            } else if (field.type == FieldType.Msg) {
                String ecType = field.typeName;
                buf.addAll(pad1)
                   .addAll("if (")
                   .addAll(fname)
                   .addAll(".is(")
                   .addAll(ecType)
                   .addAll(")) {\n");
                buf.addAll(pad2)
                   .addAll("size += CodedOutput.computeMessageSize(")
                   .addAll(field.number.toString())
                   .addAll(", ")
                   .addAll(fname)
                   .addAll(");\n");
                buf.addAll(pad1).addAll("}\n");
            } else {
                String ecType = ecstasyScalarType(field);
                buf.addAll(pad1)
                   .addAll("if (")
                   .addAll(fname)
                   .addAll(".is(")
                   .addAll(ecType)
                   .addAll(")) {\n");
                buf.addAll(pad2)
                   .addAll("size += CodedOutput.")
                   .addAll(computeSizeMethod(field))
                   .addAll(";\n");
                buf.addAll(pad1).addAll("}\n");
            }
        }

        // oneof fields
        for (OneofDescriptor oneof : msg.oneofs) {
            generateSizeOneof(buf, oneof, oneofFields, indent + 1);
        }

        buf.addAll(pad1).addAll("return size;\n");
        buf.addAll(pad).addAll("}\n");
    }

    /**
     * Generate size logic for a repeated field.
     */
    private void generateSizeRepeatedField(StringBuffer buf, FieldDescriptor field,
                                           String fname, Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        if (field.type.packable) {
            buf.addAll(pad)
               .addAll("if (!")
               .addAll(fname)
               .addAll(".empty) {\n");
            buf.addAll(pad1)
               .addAll("size += CodedOutput.")
               .addAll(computePackedSizeMethod(field))
               .addAll(";\n");
            buf.addAll(pad).addAll("}\n");
        } else {
            String ecType = field.type == FieldType.Msg ? field.typeName
                                                        : ecstasyScalarType(field);
            buf.addAll(pad)
               .addAll("for (")
               .addAll(ecType)
               .addAll(" v : ")
               .addAll(fname)
               .addAll(") {\n");
            if (field.type == FieldType.Msg) {
                buf.addAll(pad1)
                   .addAll("size += CodedOutput.computeMessageSize(")
                   .addAll(field.number.toString())
                   .addAll(", v);\n");
            } else {
                buf.addAll(pad1)
                   .addAll("size += CodedOutput.")
                   .addAll(computeSizeMethodForElement(field))
                   .addAll(";\n");
            }
            buf.addAll(pad).addAll("}\n");
        }
    }

    /**
     * Generate size logic for a map field.
     */
    private void generateSizeMapField(StringBuffer buf, FieldDescriptor field,
                                      String fname, Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String computeMapMethod = mapComputeSizeMethod(field);
        if (computeMapMethod.size > 0) {
            buf.addAll(pad)
               .addAll("for ((")
               .addAll(ecstasyType(field.mapKeyType))
               .addAll(" k, ")
               .addAll(ecstasyType(field.mapValueType))
               .addAll(" v) : ")
               .addAll(fname)
               .addAll(") {\n");
            buf.addAll(pad1)
               .addAll("size += CodedOutput.")
               .addAll(computeMapMethod)
               .addAll("(")
               .addAll(field.number.toString())
               .addAll(", k, v);\n");
            buf.addAll(pad).addAll("}\n");
        }
    }

    /**
     * Generate size logic for a oneof group.
     */
    private void generateSizeOneof(StringBuffer buf, OneofDescriptor oneof,
                                   Array<FieldDescriptor> oneofFields, Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);
        String pad2 = spaces(indent + 2);
        String oname = fieldName(oneof.name);

        buf.addAll(pad)
           .addAll("switch (")
           .addAll(oname)
           .addAll(".activeFieldNumber) {\n");

        for (FieldDescriptor field : oneofFields) {
            if (!Oneof.contains(field.number, oneof.fieldNumbers)) {
                continue;
            }
            String fname  = toPascalCase(field.name);
            String ecType = ecstasyScalarType(field);

            buf.addAll(pad)
               .addAll("case ")
               .addAll(field.number.toString())
               .addAll(":\n");
            buf.addAll(pad1)
               .addAll("if (")
               .addAll(ecType)
               .addAll(" ")
               .addAll(fieldName(field.name))
               .addAll(" := get")
               .addAll(fname)
               .addAll("()) {\n");
            buf.addAll(pad2)
               .addAll("size += CodedOutput.")
               .addAll(computeSizeMethodForOneof(field))
               .addAll(";\n");
            buf.addAll(pad1)
               .addAll("}\n");
            buf.addAll(pad1).addAll("break;\n");
        }

        buf.addAll(pad).addAll("}\n");
    }

    // ----- freeze generation ----------------------------------------------------------------------

    /**
     * Generate the freeze override.
     */
    private void generateFreeze(StringBuffer buf, MessageDescriptor msg,
                                Array<FieldDescriptor> regularFields, Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);

        buf.addAll(pad).addAll("@Override\n");
        buf.addAll(pad)
           .addAll("immutable ")
           .addAll(msg.name)
           .addAll(" freeze(Boolean inPlace = False) {\n");
        buf.addAll(pad1)
           .addAll("if (this.is(immutable ")
           .addAll(msg.name)
           .addAll(")) {\n");
        buf.addAll(spaces(indent + 2)).addAll("return this;\n");
        buf.addAll(pad1).addAll("}\n");

        // freeze mutable collections
        buf.addAll(pad1).addAll("unknownFields.freeze(inPlace);\n");
        for (FieldDescriptor field : regularFields) {
            if (field.label == Repeated || field.isMapField) {
                buf.addAll(pad1)
                   .addAll(fieldName(field.name))
                   .addAll(".freeze(inPlace);\n");
            }
        }

        buf.addAll(pad1).addAll("return this.makeImmutable();\n");
        buf.addAll(pad).addAll("}\n");
    }

    // ----- service generation ---------------------------------------------------------------------

    /**
     * Generate a service interface.
     */
    private void generateService(StringBuffer buf, ServiceDescriptor svc, Int indent) {
        String pad  = spaces(indent);
        String pad1 = spaces(indent + 1);

        buf.addAll(pad).addAll("interface ").addAll(svc.name).addAll(" {\n");

        for (MethodDescriptor method : svc.methods) {
            buf.add('\n');
            String methodName = toCamelCase(method.name);

            if (method.clientStreaming || method.serverStreaming) {
                buf.addAll(pad1)
                   .addAll("// TODO: streaming RPC\n");
                buf.addAll(pad1)
                   .addAll("// ")
                   .addAll(method.name)
                   .addAll("(")
                   .addAll(method.clientStreaming ? "stream " : "")
                   .addAll(method.inputType)
                   .addAll(") returns (")
                   .addAll(method.serverStreaming ? "stream " : "")
                   .addAll(method.outputType)
                   .addAll(")\n");
            } else {
                buf.addAll(pad1)
                   .addAll(method.outputType)
                   .addAll(" ")
                   .addAll(methodName)
                   .addAll("(")
                   .addAll(method.inputType)
                   .addAll(" request);\n");
            }
        }

        buf.addAll(pad).addAll("}\n");
    }

    // ----- import collection ---------------------------------------------------------------------

    /**
     * Collect the import statements needed for a message.
     */
    private Array<String> collectImports(MessageDescriptor msg) {
        Array<String> imports = new Array();
        imports.add("protobuf.AbstractMessage");
        imports.add("protobuf.CodedInput");
        imports.add("protobuf.CodedOutput");
        imports.add("protobuf.WireType");

        Boolean hasMap   = False;
        Boolean hasOneof = False;
        Boolean hasEnum  = msg.enums.size > 0;

        for (FieldDescriptor field : msg.fields) {
            if (field.isMapField) {
                hasMap = True;
            }
            if (field.oneofIndex >= 0) {
                hasOneof = True;
            }
            if (field.type == FieldType.Enm) {
                hasEnum = True;
            }
        }

        if (hasMap) {
            imports.add("ecstasy.maps.ListMap");
        }
        if (hasOneof) {
            imports.add("protobuf.Oneof");
        }
        if (hasEnum) {
            imports.add("protobuf.ProtoEnum");
        }

        return imports;
    }

    // ----- type mapping helpers -------------------------------------------------------------------

    /**
     * @return the Ecstasy type name for a field declaration (including Maybe wrappers)
     */
    private String fieldTypeName(FieldDescriptor field) {
        if (field.isMapField) {
            return $"ListMap<{ecstasyType(field.mapKeyType)}, {ecstasyType(field.mapValueType)}>";
        }
        if (field.label == Repeated) {
            if (field.type == FieldType.Msg) {
                return $"Array<{field.typeName}>";
            }
            return $"Array<{ecstasyScalarType(field)}>";
        }
        if (field.type == FieldType.Msg) {
            return $"Maybe{sanitizeTypedefName(field.typeName)}";
        }
        return maybeType(field);
    }

    /**
     * @return the default value literal for a field
     */
    private String fieldDefault(FieldDescriptor field) {
        if (field.isMapField) {
            return "new ListMap()";
        }
        if (field.label == Repeated) {
            return "new Array()";
        }
        return "Unset";
    }

    /**
     * @return the Maybe* type name for a scalar field
     */
    private String maybeType(FieldDescriptor field) {
        return switch (field.type) {
            case I32, SI32, SFIX32: "MaybeInt32";
            case I64, SI64, SFIX64: "MaybeInt64";
            case UI32, FIX32:       "MaybeUInt32";
            case UI64, FIX64:       "MaybeUInt64";
            case Bool:              "MaybeBoolean";
            case Str:               "MaybeString";
            case Bytes:             "MaybeByteString";
            case Dbl:               "MaybeFloat64";
            case Flt:               "MaybeFloat32";
            case Enm:               $"Maybe{sanitizeTypedefName(field.typeName)}";
            default:                "Object";
        };
    }

    /**
     * @return the raw Ecstasy type name for a scalar FieldType (no Maybe wrapper)
     */
    private String ecstasyScalarType(FieldDescriptor field) {
        if (field.type == FieldType.Msg) {
            return field.typeName;
        }
        if (field.type == FieldType.Enm) {
            return field.typeName;
        }
        return ecstasyType(field.type);
    }

    /**
     * @return the Ecstasy type name for a FieldType
     */
    private String ecstasyType(FieldType type) {
        return switch (type) {
            case Dbl:               "Float64";
            case Flt:               "Float32";
            case I64, SI64, SFIX64: "Int64";
            case UI64, FIX64:       "UInt64";
            case I32, SI32, SFIX32: "Int32";
            case UI32, FIX32:       "UInt32";
            case Bool:              "Boolean";
            case Str:               "String";
            case Bytes:             "Byte[]";
            default:                "Object";
        };
    }

    // ----- read/write method helpers --------------------------------------------------------------

    /**
     * @return the CodedInput read method call for a field (without "input." prefix)
     */
    private String readMethod(FieldDescriptor field) {
        return switch (field.type) {
            case Dbl:    "readDouble()";
            case Flt:    "readFloat()";
            case I64:    "readInt64()";
            case UI64:   "readUInt64()";
            case I32:    "readInt32()";
            case FIX64:  "readFixed64()";
            case FIX32:  "readFixed32()";
            case Bool:   "readBool()";
            case Str:    "readString()";
            case Bytes:  "readBytes()";
            case UI32:   "readUInt32()";
            case Enm:    "readEnum()";
            case SFIX32: "readSFixed32()";
            case SFIX64: "readSFixed64()";
            case SI32:   "readSInt32()";
            case SI64:   "readSInt64()";
            default:     "readVarint()";
        };
    }

    /**
     * @return the CodedInput packed read method call for a repeated field
     */
    private String readPackedMethod(FieldDescriptor field) {
        return switch (field.type) {
            case Dbl:               "readPackedDoubles()";
            case Flt:               "readPackedFloats()";
            case I64, SI64:         "readPackedVarints()";
            case UI64:              "readPackedVarints()";
            case I32, SI32:         "readPackedVarints()";
            case FIX64, SFIX64:     "readPackedFixed64s()";
            case FIX32, SFIX32:     "readPackedFixed32s()";
            case Bool:              "readPackedBools()";
            case UI32:              "readPackedVarints()";
            case Enm:               "readPackedVarints()";
            default:                "readPackedVarints()";
        };
    }

    /**
     * @return the CodedOutput write method name for a field (without "out." prefix)
     */
    private String writeMethod(FieldDescriptor field) {
        return switch (field.type) {
            case Dbl:    "writeDouble";
            case Flt:    "writeFloat";
            case I64:    "writeInt64";
            case UI64:   "writeUInt64";
            case I32:    "writeInt32";
            case FIX64:  "writeFixed64";
            case FIX32:  "writeFixed32";
            case Bool:   "writeBool";
            case Str:    "writeString";
            case Bytes:  "writeBytes";
            case UI32:   "writeUInt32";
            case Enm:    "writeEnum";
            case SFIX32: "writeSFixed32";
            case SFIX64: "writeSFixed64";
            case SI32:   "writeSInt32";
            case SI64:   "writeSInt64";
            default:     "writeVarint";
        };
    }

    /**
     * @return the CodedOutput packed write method name for a repeated field
     */
    private String writePackedMethod(FieldDescriptor field) {
        return switch (field.type) {
            case Dbl:               "writePackedDoubles";
            case Flt:               "writePackedFloats";
            case I64, SI64:         "writePackedVarints";
            case UI64:              "writePackedVarints";
            case I32, SI32:         "writePackedVarints";
            case FIX64, SFIX64:     "writePackedFixed64s";
            case FIX32, SFIX32:     "writePackedFixed32s";
            case Bool:              "writePackedBools";
            case UI32:              "writePackedVarints";
            case Enm:               "writePackedVarints";
            default:                "writePackedVarints";
        };
    }

    // ----- compute size helpers -------------------------------------------------------------------

    /**
     * @return the CodedOutput.compute*Size call for a singular field (using fname as the value)
     */
    private String computeSizeMethod(FieldDescriptor field) {
        String fn = field.number.toString();
        String fname = fieldName(field.name);
        return switch (field.type) {
            case Dbl:    $"computeFixed64Size({fn})";
            case Flt:    $"computeFixed32Size({fn})";
            case I64:    $"computeInt64Size({fn}, {fname})";
            case UI64:   $"computeUInt64Size({fn}, {fname})";
            case I32:    $"computeInt32Size({fn}, {fname})";
            case FIX64:  $"computeFixed64Size({fn})";
            case FIX32:  $"computeFixed32Size({fn})";
            case Bool:   $"computeBoolSize({fn})";
            case Str:    $"computeStringSize({fn}, {fname})";
            case Bytes:  $"computeBytesSize({fn}, {fname})";
            case UI32:   $"computeUInt32Size({fn}, {fname})";
            case Enm:    $"computeEnumSize({fn}, {fname})";
            case SFIX32: $"computeFixed32Size({fn})";
            case SFIX64: $"computeFixed64Size({fn})";
            case SI32:   $"computeSInt32Size({fn}, {fname})";
            case SI64:   $"computeSInt64Size({fn}, {fname})";
            default:     $"computeVarintSize({fname})";
        };
    }

    /**
     * @return the compute size call for a repeated element (uses "v" as variable name)
     */
    private String computeSizeMethodForElement(FieldDescriptor field) {
        String fn = field.number.toString();
        return switch (field.type) {
            case Str:   $"computeStringSize({fn}, v)";
            case Bytes: $"computeBytesSize({fn}, v)";
            default:    $"computeStringSize({fn}, v)";
        };
    }

    /**
     * @return the compute packed size call for a repeated packable field
     */
    private String computePackedSizeMethod(FieldDescriptor field) {
        String fn = field.number.toString();
        String fname = fieldName(field.name);
        return switch (field.type) {
            case Dbl:               $"computePackedDoublesSize({fn}, {fname})";
            case Flt:               $"computePackedFloatsSize({fn}, {fname})";
            case I64, SI64:         $"computePackedVarintsSize({fn}, {fname})";
            case UI64:              $"computePackedVarintsSize({fn}, {fname})";
            case I32, SI32:         $"computePackedVarintsSize({fn}, {fname})";
            case FIX64, SFIX64:     $"computePackedFixed64sSize({fn}, {fname})";
            case FIX32, SFIX32:     $"computePackedFixed32sSize({fn}, {fname})";
            case Bool:              $"computePackedBoolsSize({fn}, {fname})";
            case UI32:              $"computePackedVarintsSize({fn}, {fname})";
            case Enm:               $"computePackedVarintsSize({fn}, {fname})";
            default:                $"computePackedVarintsSize({fn}, {fname})";
        };
    }

    /**
     * @return the compute size call for a oneof field (uses the local variable name)
     */
    private String computeSizeMethodForOneof(FieldDescriptor field) {
        String fn = field.number.toString();
        String fname = fieldName(field.name);
        return switch (field.type) {
            case Dbl:    $"computeFixed64Size({fn})";
            case Flt:    $"computeFixed32Size({fn})";
            case I64:    $"computeInt64Size({fn}, {fname})";
            case UI64:   $"computeUInt64Size({fn}, {fname})";
            case I32:    $"computeInt32Size({fn}, {fname})";
            case FIX64:  $"computeFixed64Size({fn})";
            case FIX32:  $"computeFixed32Size({fn})";
            case Bool:   $"computeBoolSize({fn})";
            case Str:    $"computeStringSize({fn}, {fname})";
            case Bytes:  $"computeBytesSize({fn}, {fname})";
            case UI32:   $"computeUInt32Size({fn}, {fname})";
            case Enm:    $"computeEnumSize({fn}, {fname})";
            case SFIX32: $"computeFixed32Size({fn})";
            case SFIX64: $"computeFixed64Size({fn})";
            case SI32:   $"computeSInt32Size({fn}, {fname})";
            case SI64:   $"computeSInt64Size({fn}, {fname})";
            default:     $"computeVarintSize({fname})";
        };
    }

    // ----- map method helpers --------------------------------------------------------------------

    /**
     * @return the CodedInput read method for a map field, or "" if unsupported
     */
    private String mapReadMethod(FieldDescriptor field) {
        String key = field.mapKeyType.protoName;
        String val = field.mapValueType.protoName;
        return switch (key, val) {
            case ("string", "string"): "readMapStringString()";
            case ("string", "int32"):  "readMapStringInt32()";
            case ("string", "int64"):  "readMapStringInt64()";
            case ("string", "bytes"):  "readMapStringBytes()";
            case ("int32",  "string"): "readMapInt32String()";
            case ("int32",  "int32"):  "readMapInt32Int32()";
            case ("int64",  "string"): "readMapInt64String()";
            case ("int64",  "int64"):  "readMapInt64Int64()";
            case ("bool",   "string"): "readMapBoolString()";
            default: "";
        };
    }

    /**
     * @return the CodedOutput write method for a map field, or "" if unsupported
     */
    private String mapWriteMethod(FieldDescriptor field) {
        String key = field.mapKeyType.protoName;
        String val = field.mapValueType.protoName;
        return switch (key, val) {
            case ("string", "string"): "writeMapStringString";
            case ("string", "int32"):  "writeMapStringInt32";
            case ("string", "int64"):  "writeMapStringInt64";
            case ("string", "bytes"):  "writeMapStringBytes";
            case ("int32",  "string"): "writeMapInt32String";
            case ("int32",  "int32"):  "writeMapInt32Int32";
            case ("int64",  "string"): "writeMapInt64String";
            case ("int64",  "int64"):  "writeMapInt64Int64";
            case ("bool",   "string"): "writeMapBoolString";
            default: "";
        };
    }

    /**
     * @return the CodedOutput compute size method for a map field, or "" if unsupported
     */
    private String mapComputeSizeMethod(FieldDescriptor field) {
        String key = field.mapKeyType.protoName;
        String val = field.mapValueType.protoName;
        return switch (key, val) {
            case ("string", "string"): "computeMapStringStringSize";
            case ("string", "int32"):  "computeMapStringInt32Size";
            case ("string", "int64"):  "computeMapStringInt64Size";
            case ("string", "bytes"):  "computeMapStringBytesSize";
            case ("int32",  "string"): "computeMapInt32StringSize";
            case ("int32",  "int32"):  "computeMapInt32Int32Size";
            case ("int64",  "string"): "computeMapInt64StringSize";
            case ("int64",  "int64"):  "computeMapInt64Int64Size";
            case ("bool",   "string"): "computeMapBoolStringSize";
            default: "";
        };
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

    /**
     * Sanitize a type name for use in a typedef alias (convert dots to underscores).
     */
    private String sanitizeTypedefName(String typeName) {
        if (!typeName.indexOf('.')) {
            return typeName;
        }
        StringBuffer buf = new StringBuffer(typeName.size);
        for (Int i = 0; i < typeName.size; i++) {
            Char ch = typeName[i];
            buf.add(ch == '.' ? '_' : ch);
        }
        return buf.toString();
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

    /**
     * Append an import statement.
     */
    private void appendImport(StringBuffer buf, String imp) {
        buf.addAll("import ").addAll(imp).addAll(";\n");
    }
}
