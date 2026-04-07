import ecstasy.io.Reader;
import ecstasy.maps.ListMap;

import ProtoLexer.Token;
import ProtoLexer.TokenType;

import FieldDescriptor.Label;

import MessageDescriptor.ReservedRange;

/**
 * A recursive-descent parser for Protocol Buffers `.proto` files.
 *
 * Produces a [FileDescriptor] from the source text, representing all message, enum, and service
 * definitions along with file-level metadata.
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

    // ----- public interface -----------------------------------------------------------------------

    /**
     * Parse the source text and return a [FileDescriptor].
     *
     * @param fileName  the file name to record in the descriptor
     *
     * @return the parsed file descriptor
     */
    FileDescriptor parseFile(String fileName) {
        String                    syntax       = "proto3";
        String                    packageName  = "";
        Array<String>             deps         = new Array();
        Array<String>             publicDeps   = new Array();
        Array<MessageDescriptor>  messages     = new Array();
        Array<EnumDescriptor>     enums        = new Array();
        Array<ServiceDescriptor>  services     = new Array();
        ListMap<String, String>   options      = new ListMap();

        while (current.type != Eof) {
            String text = current.text;
            switch (text) {
            case "syntax":
                syntax = parseSyntax();
                break;

            case "package":
                packageName = parsePackage();
                break;

            case "import":
                (String path, Boolean isPublic) = parseImport();
                deps.add(path);
                if (isPublic) {
                    publicDeps.add(path);
                }
                break;

            case "option":
                (String name, String value) = parseOption();
                options.put(name, value);
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

        return new FileDescriptor(fileName, syntax, packageName,
                deps.freeze(inPlace=True), publicDeps.freeze(inPlace=True),
                messages.freeze(inPlace=True), enums.freeze(inPlace=True),
                services.freeze(inPlace=True), options.freeze(inPlace=True));
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
    private MessageDescriptor parseMessage() {
        expectIdentifier("message");
        String name = expect(Identifier).text;
        expect(LeftBrace);

        Array<FieldDescriptor>    fields         = new Array();
        Array<OneofDescriptor>    oneofs         = new Array();
        Array<EnumDescriptor>     enums          = new Array();
        Array<MessageDescriptor>  nestedMessages = new Array();
        Array<ReservedRange>      reservedRanges = new Array();
        Array<String>             reservedNames  = new Array();
        ListMap<String, String>   options        = new ListMap();

        while (current.type != RightBrace && current.type != Eof) {
            String text = current.text;
            switch (text) {
            case "message":
                nestedMessages.add(parseMessage());
                break;

            case "enum":
                enums.add(parseEnum());
                break;

            case "oneof":
                Int oneofIndex = oneofs.size;
                (OneofDescriptor oneof, Array<FieldDescriptor> oneofFields) =
                        parseOneof(oneofIndex);
                oneofs.add(oneof);
                fields.addAll(oneofFields);
                break;

            case "map":
                fields.add(parseMapField());
                break;

            case "reserved":
                (Array<ReservedRange> ranges, Array<String> names) = parseReserved();
                reservedRanges.addAll(ranges);
                reservedNames.addAll(names);
                break;

            case "extensions":
                parseExtensions();
                break;

            case "option":
                (String optName, String optValue) = parseOption();
                options.put(optName, optValue);
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

        return new MessageDescriptor(name,
                fields.freeze(inPlace=True), oneofs.freeze(inPlace=True),
                enums.freeze(inPlace=True), nestedMessages.freeze(inPlace=True),
                reservedRanges.freeze(inPlace=True), reservedNames.freeze(inPlace=True),
                options.freeze(inPlace=True));
    }

    /**
     * Parse a field: `type name = number [options];`
     *
     * The label keyword (`optional`, `required`, `repeated`) has already been consumed.
     */
    private FieldDescriptor parseField(String labelText) {
        Label label = switch (labelText) {
            case "required": Required;
            case "repeated": Repeated;
            default: Optional;
        };

        String    typeName   = parseTypeName();
        FieldType type;
        String    typeRef    = "";
        if (FieldType scalar := FieldType.byProtoName(typeName)) {
            type = scalar;
        } else {
            type    = TypeMessage;
            typeRef = typeName;
        }

        String fieldName = expect(Identifier).text;
        expect(Equals);
        Int fieldNumber = parseIntValue();

        ListMap<String, String> options  = new ListMap();
        String                  jsonName = "";
        if (current.type == LeftBracket) {
            options = parseFieldOptions();
            if (String jn := options.get("json_name")) {
                jsonName = jn;
            }
        }
        expect(Semicolon);

        return new FieldDescriptor(fieldName, fieldNumber, type, label, typeRef,
                options=options.freeze(inPlace=True), jsonName=jsonName);
    }

    /**
     * Parse a `oneof name { ... }` block.
     */
    private (OneofDescriptor, Array<FieldDescriptor>) parseOneof(Int oneofIndex) {
        expectIdentifier("oneof");
        String name = expect(Identifier).text;
        expect(LeftBrace);

        Array<FieldDescriptor> fields       = new Array();
        Array<Int>             fieldNumbers = new Array();

        while (current.type != RightBrace && current.type != Eof) {
            if (checkIdentifier("option")) {
                parseOption();
                continue;
            }

            // oneof fields have no label
            String    typeName = parseTypeName();
            FieldType type;
            String    typeRef  = "";
            if (FieldType scalar := FieldType.byProtoName(typeName)) {
                type = scalar;
            } else {
                type    = TypeMessage;
                typeRef = typeName;
            }

            String fieldName   = expect(Identifier).text;
            expect(Equals);
            Int    fieldNumber = parseIntValue();

            ListMap<String, String> options = new ListMap();
            if (current.type == LeftBracket) {
                options = parseFieldOptions();
            }
            expect(Semicolon);

            fields.add(new FieldDescriptor(fieldName, fieldNumber, type, Optional, typeRef,
                    oneofIndex, options=options.freeze(inPlace=True)));
            fieldNumbers.add(fieldNumber);
        }
        expect(RightBrace);

        return (new OneofDescriptor(name, fieldNumbers.freeze(inPlace=True)), fields);
    }

    /**
     * Parse a `map<KeyType, ValueType> name = number [options];` field.
     */
    private FieldDescriptor parseMapField() {
        expectIdentifier("map");
        expect(LessThan);
        String keyTypeName = expect(Identifier).text;
        expect(Comma);
        String valueTypeName = parseTypeName();
        expect(GreaterThan);

        String fieldName = expect(Identifier).text;
        expect(Equals);
        Int fieldNumber = parseIntValue();

        ListMap<String, String> options = new ListMap();
        if (current.type == LeftBracket) {
            options = parseFieldOptions();
        }
        expect(Semicolon);

        // resolve key type (must be a scalar)
        assert FieldType keyType := FieldType.byProtoName(keyTypeName)
                as $"Invalid map key type: {keyTypeName}";

        // resolve value type
        FieldType valueType;
        String    valueTypeRef = "";
        if (FieldType scalar := FieldType.byProtoName(valueTypeName)) {
            valueType = scalar;
        } else {
            valueType    = TypeMessage;
            valueTypeRef = valueTypeName;
        }

        return new FieldDescriptor(fieldName, fieldNumber, TypeMessage, Repeated,
                isMapField=True, mapKeyType=keyType, mapValueType=valueType,
                mapValueTypeName=valueTypeRef, options=options.freeze(inPlace=True));
    }

    /**
     * Parse a `reserved` statement.
     *
     * Handles both numeric ranges (`reserved 2, 15, 9 to 11;`) and field names
     * (`reserved "foo", "bar";`).
     */
    private (Array<ReservedRange>, Array<String>) parseReserved() {
        expectIdentifier("reserved");

        Array<ReservedRange> ranges = new Array();
        Array<String>        names  = new Array();

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
                    ranges.add(new ReservedRange(start, end + 1));
                } else {
                    ranges.add(new ReservedRange(start, start + 1));
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
    private EnumDescriptor parseEnum() {
        expectIdentifier("enum");
        String name = expect(Identifier).text;
        expect(LeftBrace);

        Array<EnumValueDescriptor> values     = new Array();
        ListMap<String, String>    options     = new ListMap();
        Boolean                    allowAlias = False;

        while (current.type != RightBrace && current.type != Eof) {
            if (checkIdentifier("option")) {
                (String optName, String optValue) = parseOption();
                options.put(optName, optValue);
                if (optName == "allow_alias" && optValue == "true") {
                    allowAlias = True;
                }
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

            ListMap<String, String> valueOptions = new ListMap();
            if (current.type == LeftBracket) {
                valueOptions = parseFieldOptions();
            }
            expect(Semicolon);

            values.add(new EnumValueDescriptor(valueName, number,
                    valueOptions.freeze(inPlace=True)));
        }
        expect(RightBrace);

        return new EnumDescriptor(name, values.freeze(inPlace=True),
                options.freeze(inPlace=True), allowAlias);
    }

    // ----- service parsing ------------------------------------------------------------------------

    /**
     * Parse a `service Name { ... }` definition.
     */
    private ServiceDescriptor parseService() {
        expectIdentifier("service");
        String name = expect(Identifier).text;
        expect(LeftBrace);

        Array<MethodDescriptor> methods = new Array();
        ListMap<String, String> options = new ListMap();

        while (current.type != RightBrace && current.type != Eof) {
            if (checkIdentifier("option")) {
                (String optName, String optValue) = parseOption();
                options.put(optName, optValue);
            } else if (checkIdentifier("rpc")) {
                methods.add(parseRpc());
            } else {
                assert as $|Unexpected token {current.text.quoted()} in service at \
                            |{current.line}:{current.column}
                            ;
            }
        }
        expect(RightBrace);

        return new ServiceDescriptor(name, methods.freeze(inPlace=True),
                options.freeze(inPlace=True));
    }

    /**
     * Parse an `rpc MethodName (RequestType) returns (ResponseType) [{ ... }|;]` method.
     */
    private MethodDescriptor parseRpc() {
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
        ListMap<String, String> options = new ListMap();
        if (current.type == LeftBrace) {
            advance();
            while (current.type != RightBrace && current.type != Eof) {
                if (checkIdentifier("option")) {
                    (String optName, String optValue) = parseOption();
                    options.put(optName, optValue);
                } else {
                    advance();  // skip unknown tokens in rpc body
                }
            }
            expect(RightBrace);
        } else {
            expect(Semicolon);
        }

        return new MethodDescriptor(name, inputType, outputType,
                clientStreaming, serverStreaming, options.freeze(inPlace=True));
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
}
