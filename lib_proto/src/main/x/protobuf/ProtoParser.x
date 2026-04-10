import ecstasy.io.Reader;
import ecstasy.maps.ListMap;

import ProtoLexer.Token;
import ProtoLexer.TokenType;

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
 * A recursive-descent parser for Protocol Buffers `.proto` files.
 *
 * Produces a [FileDescriptorProto] from the source text, representing all message, enum, and
 * service definitions along with file-level metadata.
 */
class ProtoParser {

    /**
     * Construct a parser from a source string.
     */
    construct(String source) {
        construct ProtoParser(new ProtoLexer(source));
    }

    /**
     * Construct a parser from a [Reader].
     */
    construct(Reader reader) {
        construct ProtoParser(new ProtoLexer(reader));
    }

    /**
     * Construct a parser from a [ProtoLexer].
     */
    construct(ProtoLexer lexer) {
        this.lexer   = lexer;
        this.current = lexer.next();
    }

    /**
     * The lexer producing tokens.
     */
    private ProtoLexer lexer;

    /**
     * The current unconsumed token.
     */
    private Token current;

    /**
     * Map from proto type name to FieldType enum value.
     */
    private static Map<String, FieldType> ProtoNameToType = Map:[
        "double"   = TypeDouble,
        "float"    = TypeFloat,
        "int64"    = TypeInt64,
        "uint64"   = TypeUint64,
        "int32"    = TypeInt32,
        "fixed64"  = TypeFixed64,
        "fixed32"  = TypeFixed32,
        "bool"     = TypeBool,
        "string"   = TypeString,
        "group"    = TypeGroup,
        "bytes"    = TypeBytes,
        "uint32"   = TypeUint32,
        "sfixed32" = TypeSfixed32,
        "sfixed64" = TypeSfixed64,
        "sint32"   = TypeSint32,
        "sint64"   = TypeSint64,
    ];

    // ----- public interface -----------------------------------------------------------------------

    /**
     * Parse the source text and return a [FileDescriptorProto].
     *
     * @param fileName  the file name to record in the descriptor
     *
     * @return the parsed file descriptor
     */
    FileDescriptorProto parseFile(String fileName) {
        String                          syntax     = "proto3";
        String                          pkg        = "";
        Array<String>                   deps       = new Array();
        Array<Int32>                    publicDeps = new Array();
        Array<DescriptorProto>          messages   = new Array();
        Array<EnumDescriptorProto>      enums      = new Array();
        Array<ServiceDescriptorProto>   services   = new Array();

        while (current.type != Eof) {
            String text = current.text;
            switch (text) {
            case "syntax":
                syntax = parseSyntax();
                break;

            case "package":
                pkg = parsePackage();
                break;

            case "import":
                (String path, Boolean isPublic) = parseImport();
                if (isPublic) {
                    publicDeps.add(deps.size.toInt32());
                }
                deps.add(path);
                break;

            case "option":
                parseOption();
                break;

            case "message":
                messages.add(parseMessage());
                break;

            case "enum":
                enums.add(parseEnum());
                break;

            case "service":
                services.add(parseService());
                break;

            default:
                assert as $|Unexpected token {current.text.quoted()} at \
                            |{current.line}:{current.column}
                            ;
            }
        }

        FileDescriptorProto file = new FileDescriptorProto();
        file.name             = fileName;
        file.syntax           = syntax;
        file.package_         = pkg;
        file.dependency       = deps;
        file.publicDependency = publicDeps;
        file.messageType      = messages;
        file.enumType         = enums;
        file.service_         = services;
        return file;
    }

    // ----- top-level statement parsers -------------------------------------------------------------

    /**
     * Parse a `syntax = "proto3";` statement.
     */
    private String parseSyntax() {
        expectIdentifier("syntax");
        expect(Equals);
        String syntax = expect(StringLiteral).text;
        assert syntax == "proto2" || syntax == "proto3"
                as $"Invalid syntax version: {syntax}";
        expect(Semicolon);
        return syntax;
    }

    /**
     * Parse a `package foo.bar;` statement.
     */
    private String parsePackage() {
        expectIdentifier("package");
        String name = parseFullIdent();
        expect(Semicolon);
        return name;
    }

    /**
     * Parse an `import [public|weak] "path.proto";` statement.
     */
    private (String path, Boolean isPublic) parseImport() {
        expectIdentifier("import");
        Boolean isPublic = False;
        if (checkIdentifier("public")) {
            advance();
            isPublic = True;
        } else if (checkIdentifier("weak")) {
            advance();
        }
        String path = expect(StringLiteral).text;
        expect(Semicolon);
        return (path, isPublic);
    }

    /**
     * Parse an `option name = value;` statement.
     */
    private (String name, String value) parseOption() {
        expectIdentifier("option");
        String name  = parseOptionName();
        expect(Equals);
        String value = parseConstant();
        expect(Semicolon);
        return (name, value);
    }

    // ----- message parsing ------------------------------------------------------------------------

    /**
     * Parse a `message Name { ... }` definition.
     */
    private DescriptorProto parseMessage() {
        expectIdentifier("message");
        String name = expect(Identifier).text;
        expect(LeftBrace);

        Array<FieldDescriptorProto>    fields         = new Array();
        Array<OneofDescriptorProto>    oneofs         = new Array();
        Array<EnumDescriptorProto>     enums          = new Array();
        Array<DescriptorProto>         nestedTypes    = new Array();
        Array<DescriptorProto.ReservedRange> reservedRanges = new Array();
        Array<String>                  reservedNames  = new Array();

        while (current.type != RightBrace && current.type != Eof) {
            String text = current.text;
            switch (text) {
            case "message":
                nestedTypes.add(parseMessage());
                break;

            case "enum":
                enums.add(parseEnum());
                break;

            case "oneof":
                Int oneofIndex = oneofs.size;
                (OneofDescriptorProto oneof, Array<FieldDescriptorProto> oneofFields) =
                        parseOneof(oneofIndex);
                oneofs.add(oneof);
                fields.addAll(oneofFields);
                break;

            case "map":
                (FieldDescriptorProto mapField, DescriptorProto entryMsg) = parseMapField();
                fields.add(mapField);
                nestedTypes.add(entryMsg);
                break;

            case "reserved":
                (Array<DescriptorProto.ReservedRange> ranges, Array<String> names) = parseReserved();
                reservedRanges.addAll(ranges);
                reservedNames.addAll(names);
                break;

            case "extensions":
                parseExtensions();
                break;

            case "option":
                parseOption();
                break;

            case "optional", "required", "repeated":
                advance();
                fields.add(parseField(text));
                break;

            default:
                // proto3 implicit optional field
                fields.add(parseField("optional"));
                break;
            }
        }
        expect(RightBrace);

        DescriptorProto msg = new DescriptorProto();
        msg.name         = name;
        msg.field        = fields;
        msg.oneofDecl    = oneofs;
        msg.enumType     = enums;
        msg.nestedType   = nestedTypes;
        msg.reservedRange = reservedRanges;
        msg.reservedName = reservedNames;
        return msg;
    }

    /**
     * Parse a field: `type name = number [options];`
     *
     * The label keyword (`optional`, `required`, `repeated`) has already been consumed.
     */
    private FieldDescriptorProto parseField(String labelText) {
        Label label = switch (labelText) {
            case "required": LabelRequired;
            case "repeated": LabelRepeated;
            default: LabelOptional;
        };

        String   typeName = parseTypeName();
        FieldType type;
        String    typeRef  = "";
        if (FieldType scalar := ProtoNameToType.get(typeName)) {
            type = scalar;
        } else {
            type    = TypeMessage;
            typeRef = typeName;
        }

        String fieldName = expect(Identifier).text;
        expect(Equals);
        Int fieldNumber = parseIntValue();

        String jsonName = "";
        if (current.type == LeftBracket) {
            ListMap<String, String> options = parseFieldOptions();
            if (String jn := options.get("json_name")) {
                jsonName = jn;
            }
        }
        expect(Semicolon);

        FieldDescriptorProto field = new FieldDescriptorProto();
        field.name    = fieldName;
        field.number  = fieldNumber.toInt32();
        field.type    = type;
        field.label   = label;
        if (typeRef.size > 0) {
            field.typeName = typeRef;
        }
        if (jsonName.size > 0) {
            field.jsonName = jsonName;
        }
        return field;
    }

    /**
     * Parse a `oneof name { ... }` block.
     */
    private (OneofDescriptorProto, Array<FieldDescriptorProto>) parseOneof(Int oneofIndex) {
        expectIdentifier("oneof");
        String name = expect(Identifier).text;
        expect(LeftBrace);

        Array<FieldDescriptorProto> fields = new Array();

        while (current.type != RightBrace && current.type != Eof) {
            if (checkIdentifier("option")) {
                parseOption();
                continue;
            }

            // oneof fields have no label
            String    typeName = parseTypeName();
            FieldType type;
            String    typeRef  = "";
            if (FieldType scalar := ProtoNameToType.get(typeName)) {
                type = scalar;
            } else {
                type    = TypeMessage;
                typeRef = typeName;
            }

            String fieldName   = expect(Identifier).text;
            expect(Equals);
            Int    fieldNumber = parseIntValue();

            if (current.type == LeftBracket) {
                parseFieldOptions();
            }
            expect(Semicolon);

            FieldDescriptorProto field = new FieldDescriptorProto();
            field.name       = fieldName;
            field.number     = fieldNumber.toInt32();
            field.type       = type;
            field.label      = LabelOptional;
            field.oneofIndex = oneofIndex.toInt32();
            if (typeRef.size > 0) {
                field.typeName = typeRef;
            }
            fields.add(field);
        }
        expect(RightBrace);

        OneofDescriptorProto oneof = new OneofDescriptorProto();
        oneof.name = name;
        return (oneof, fields);
    }

    /**
     * Parse a `map<KeyType, ValueType> name = number [options];` field.
     *
     * Returns the field descriptor and a synthetic entry message for the map type.
     */
    private (FieldDescriptorProto, DescriptorProto) parseMapField() {
        expectIdentifier("map");
        expect(LessThan);
        String keyTypeName = expect(Identifier).text;
        expect(Comma);
        String valueTypeName = parseTypeName();
        expect(GreaterThan);

        String fieldName = expect(Identifier).text;
        expect(Equals);
        Int fieldNumber = parseIntValue();

        if (current.type == LeftBracket) {
            parseFieldOptions();
        }
        expect(Semicolon);

        // resolve key type (must be a scalar)
        assert FieldType keyType := ProtoNameToType.get(keyTypeName)
                as $"Invalid map key type: {keyTypeName}";

        // resolve value type
        FieldType valueType;
        String    valueTypeRef = "";
        if (FieldType scalar := ProtoNameToType.get(valueTypeName)) {
            valueType = scalar;
        } else {
            valueType    = TypeMessage;
            valueTypeRef = valueTypeName;
        }

        // create synthetic entry message (e.g., LabelsEntry)
        String entryName = $"{toPascalCase(fieldName)}Entry";

        FieldDescriptorProto keyField = new FieldDescriptorProto();
        keyField.name   = "key";
        keyField.number = 1;
        keyField.type   = keyType;
        keyField.label  = LabelOptional;

        FieldDescriptorProto valueField = new FieldDescriptorProto();
        valueField.name   = "value";
        valueField.number = 2;
        valueField.type   = valueType;
        valueField.label  = LabelOptional;
        if (valueTypeRef.size > 0) {
            valueField.typeName = valueTypeRef;
        }

        wellknown.MessageOptions entryOptions = new wellknown.MessageOptions();
        entryOptions.mapEntry = True;

        DescriptorProto entryMsg = new DescriptorProto();
        entryMsg.name    = entryName;
        entryMsg.field   = [keyField, valueField];
        entryMsg.options = entryOptions;

        // the field itself is repeated with typeName pointing to the entry message
        FieldDescriptorProto field = new FieldDescriptorProto();
        field.name     = fieldName;
        field.number   = fieldNumber.toInt32();
        field.type     = TypeMessage;
        field.label    = LabelRepeated;
        field.typeName = entryName;
        return (field, entryMsg);
    }

    /**
     * Parse a `reserved` statement.
     *
     * Handles both numeric ranges (`reserved 2, 15, 9 to 11;`) and field names
     * (`reserved "foo", "bar";`).
     */
    private (Array<DescriptorProto.ReservedRange>, Array<String>) parseReserved() {
        expectIdentifier("reserved");

        Array<DescriptorProto.ReservedRange> ranges = new Array();
        Array<String>                        names  = new Array();

        if (current.type == StringLiteral) {
            // reserved field names
            do {
                names.add(expect(StringLiteral).text);
            } while (matchToken(Comma));
        } else {
            // reserved field numbers
            do {
                Int start = parseIntValue();
                if (checkIdentifier("to")) {
                    advance();
                    Int end;
                    if (checkIdentifier("max")) {
                        advance();
                        end = 536870911;  // protobuf max field number
                    } else {
                        end = parseIntValue();
                    }
                    DescriptorProto.ReservedRange range = new DescriptorProto.ReservedRange();
                    range.start = start.toInt32();
                    range.end   = (end + 1).toInt32();
                    ranges.add(range);
                } else {
                    DescriptorProto.ReservedRange range = new DescriptorProto.ReservedRange();
                    range.start = start.toInt32();
                    range.end   = (start + 1).toInt32();
                    ranges.add(range);
                }
            } while (matchToken(Comma));
        }
        expect(Semicolon);
        return (ranges, names);
    }

    /**
     * Parse an `extensions` statement and discard it.
     */
    private void parseExtensions() {
        expectIdentifier("extensions");
        // skip until semicolon
        while (current.type != Semicolon && current.type != Eof) {
            advance();
        }
        expect(Semicolon);
    }

    /**
     * Parse field options: `[name = value, ...]`.
     */
    private ListMap<String, String> parseFieldOptions() {
        expect(LeftBracket);
        ListMap<String, String> options = new ListMap();
        do {
            String name  = parseOptionName();
            expect(Equals);
            String value = parseConstant();
            options.put(name, value);
        } while (matchToken(Comma));
        expect(RightBracket);
        return options;
    }

    // ----- enum parsing ---------------------------------------------------------------------------

    /**
     * Parse an `enum Name { ... }` definition.
     */
    private EnumDescriptorProto parseEnum() {
        expectIdentifier("enum");
        String name = expect(Identifier).text;
        expect(LeftBrace);

        Array<EnumValueDescriptorProto> values = new Array();

        while (current.type != RightBrace && current.type != Eof) {
            if (checkIdentifier("option")) {
                parseOption();
                continue;
            }
            if (checkIdentifier("reserved")) {
                parseReserved();  // consume and discard enum reserved
                continue;
            }

            // enum value
            String valueName = expect(Identifier).text;
            expect(Equals);

            // handle optional negative sign
            Boolean negative = False;
            if (current.type == Minus) {
                advance();
                negative = True;
            }
            Int number = parseIntValue();
            if (negative) {
                number = -number;
            }

            if (current.type == LeftBracket) {
                parseFieldOptions();
            }
            expect(Semicolon);

            EnumValueDescriptorProto v = new EnumValueDescriptorProto();
            v.name   = valueName;
            v.number = number.toInt32();
            values.add(v);
        }
        expect(RightBrace);

        EnumDescriptorProto e = new EnumDescriptorProto();
        e.name  = name;
        e.value = values;
        return e;
    }

    // ----- service parsing ------------------------------------------------------------------------

    /**
     * Parse a `service Name { ... }` definition.
     */
    private ServiceDescriptorProto parseService() {
        expectIdentifier("service");
        String name = expect(Identifier).text;
        expect(LeftBrace);

        Array<MethodDescriptorProto> methods = new Array();

        while (current.type != RightBrace && current.type != Eof) {
            if (checkIdentifier("option")) {
                parseOption();
            } else if (checkIdentifier("rpc")) {
                methods.add(parseRpc());
            } else {
                assert as $|Unexpected token {current.text.quoted()} in service at \
                            |{current.line}:{current.column}
                            ;
            }
        }
        expect(RightBrace);

        ServiceDescriptorProto svc = new ServiceDescriptorProto();
        svc.name   = name;
        svc.method = methods;
        return svc;
    }

    /**
     * Parse an `rpc MethodName (RequestType) returns (ResponseType) [{ ... }|;]` method.
     */
    private MethodDescriptorProto parseRpc() {
        expectIdentifier("rpc");
        String name = expect(Identifier).text;

        // input type
        expect(LeftParen);
        Boolean clientStreaming = False;
        if (checkIdentifier("stream")) {
            advance();
            clientStreaming = True;
        }
        String inputType = parseTypeName();
        expect(RightParen);

        // returns
        expectIdentifier("returns");

        // output type
        expect(LeftParen);
        Boolean serverStreaming = False;
        if (checkIdentifier("stream")) {
            advance();
            serverStreaming = True;
        }
        String outputType = parseTypeName();
        expect(RightParen);

        // method body or semicolon
        if (current.type == LeftBrace) {
            advance();
            while (current.type != RightBrace && current.type != Eof) {
                if (checkIdentifier("option")) {
                    parseOption();
                } else {
                    advance();  // skip unknown tokens in rpc body
                }
            }
            expect(RightBrace);
        } else {
            expect(Semicolon);
        }

        MethodDescriptorProto method = new MethodDescriptorProto();
        method.name             = name;
        method.inputType        = inputType;
        method.outputType       = outputType;
        method.clientStreaming  = clientStreaming;
        method.serverStreaming  = serverStreaming;
        return method;
    }

    // ----- helper methods -------------------------------------------------------------------------

    /**
     * Advance to the next token and return the previous one.
     */
    private Token advance() {
        Token prev = current;
        current = lexer.next();
        return prev;
    }

    /**
     * Consume the current token, asserting it has the expected type.
     */
    private Token expect(TokenType type) {
        assert current.type == type
                as $|Expected {type} but got {current.type}({current.text.quoted()}) at \
                    |{current.line}:{current.column}
                    ;
        return advance();
    }

    /**
     * Consume the current token, asserting it is an identifier with the expected text.
     */
    private Token expectIdentifier(String text) {
        assert current.type == Identifier && current.text == text
                as $|Expected {text.quoted()} but got \
                    |{current.type}({current.text.quoted()}) at \
                    |{current.line}:{current.column}
                    ;
        return advance();
    }

    /**
     * @return True if the current token has the given type
     */
    private Boolean check(TokenType type) = current.type == type;

    /**
     * @return True if the current token is an identifier with the given text
     */
    private Boolean checkIdentifier(String text) =
        current.type == Identifier && current.text == text;

    /**
     * If the current token matches the given type, consume it and return True.
     */
    private Boolean matchToken(TokenType type) {
        if (current.type == type) {
            advance();
            return True;
        }
        return False;
    }

    /**
     * Parse a possibly-dotted identifier (e.g. `foo.bar.Baz`).
     */
    private String parseFullIdent() {
        StringBuffer buf = new StringBuffer();

        // optional leading dot for absolute references
        if (current.type == Dot) {
            buf.add('.');
            advance();
        }

        buf.addAll(expect(Identifier).text);
        while (current.type == Dot) {
            advance();
            buf.add('.');
            buf.addAll(expect(Identifier).text);
        }
        return buf.toString();
    }

    /**
     * Parse a type name (scalar name or dotted message/enum reference).
     */
    private String parseTypeName() = parseFullIdent();

    /**
     * Parse an option name, which may be a simple identifier, a dotted identifier, or a
     * parenthesized extension name.
     */
    private String parseOptionName() {
        StringBuffer buf = new StringBuffer();

        if (current.type == LeftParen) {
            advance();
            buf.add('(');
            buf.addAll(parseFullIdent());
            expect(RightParen);
            buf.add(')');
        } else {
            buf.addAll(expect(Identifier).text);
        }

        while (current.type == Dot) {
            advance();
            buf.add('.');
            buf.addAll(expect(Identifier).text);
        }
        return buf.toString();
    }

    /**
     * Parse an integer literal and return its value.
     */
    private Int parseIntValue() {
        Token  token = expect(IntLiteral);
        String text  = token.text;
        if (text.startsWith("0x") || text.startsWith("0X")) {
            // hex literal
            Int value = 0;
            for (Int i = 2; i < text.size; i++) {
                Char ch = text[i];
                if (ch >= '0' && ch <= '9') {
                    value = value * 16 + (ch - '0');
                } else if (ch >= 'a' && ch <= 'f') {
                    value = value * 16 + (ch - 'a' + 10);
                } else if (ch >= 'A' && ch <= 'F') {
                    value = value * 16 + (ch - 'A' + 10);
                }
            }
            return value;
        }
        if (text.size > 1 && text[0] == '0') {
            // octal literal
            Int value = 0;
            for (Int i = 1; i < text.size; i++) {
                value = value * 8 + (text[i] - '0');
            }
            return value;
        }
        // decimal literal
        Int value = 0;
        for (Int i = 0; i < text.size; i++) {
            value = value * 10 + (text[i] - '0');
        }
        return value;
    }

    /**
     * Parse a constant value (string, number, boolean, or identifier).
     */
    private String parseConstant() {
        switch (current.type) {
        case StringLiteral:
            return advance().text;

        case IntLiteral:
        case FloatLiteral:
            return advance().text;

        case Minus:
            advance();
            return $"-{advance().text}";

        case Identifier:
            String text = current.text;
            if (text == "true" || text == "false") {
                advance();
                return text;
            }
            if (text == "inf" || text == "nan") {
                advance();
                return text;
            }
            return parseFullIdent();

        case LeftBrace:
            return parseAggregateValue();

        default:
            assert as $|Unexpected token {current.text.quoted()} in constant at \
                        |{current.line}:{current.column}
                        ;
        }
    }

    /**
     * Parse an aggregate option value: `{ key: value, ... }`.
     */
    private String parseAggregateValue() {
        StringBuffer buf   = new StringBuffer();
        Int          depth = 0;
        do {
            if (current.type == LeftBrace) {
                depth++;
            } else if (current.type == RightBrace) {
                depth--;
                if (depth < 0) {
                    break;
                }
            }
            buf.addAll(current.text);
            buf.add(' ');
            advance();
        } while (current.type != Eof && depth > 0);

        return buf.toString().trim();
    }

    /**
     * Convert a snake_case field name to PascalCase for map entry message names.
     */
    private String toPascalCase(String name) {
        StringBuffer buf       = new StringBuffer();
        Boolean      upperNext = True;

        for (Int i = 0; i < name.size; i++) {
            Char ch = name[i];
            if (ch == '_') {
                upperNext = True;
            } else if (upperNext) {
                buf.add(ch >= 'a' && ch <= 'z' ? ch.uppercase : ch);
                upperNext = False;
            } else {
                buf.add(ch);
            }
        }
        return buf.toString();
    }
}
