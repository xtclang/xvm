import protobuf.ProtoLexer;
import protobuf.ProtoLexer.Token;
import protobuf.ProtoLexer.TokenType;

class ProtoLexerTest {

    // ----- basic tokens --------------------------------------------------------------------------

    @Test
    void shouldLexIdentifier() {
        ProtoLexer lexer = new ProtoLexer("message");
        Token tok = lexer.next();
        assert tok.type == Identifier;
        assert tok.text == "message";
    }

    @Test
    void shouldLexIntLiteral() {
        ProtoLexer lexer = new ProtoLexer("42");
        Token tok = lexer.next();
        assert tok.type == IntLiteral;
        assert tok.text == "42";
    }

    @Test
    void shouldLexHexLiteral() {
        ProtoLexer lexer = new ProtoLexer("0xFF");
        Token tok = lexer.next();
        assert tok.type == IntLiteral;
        assert tok.text == "0xFF";
    }

    @Test
    void shouldLexFloatLiteral() {
        ProtoLexer lexer = new ProtoLexer("3.14");
        Token tok = lexer.next();
        assert tok.type == FloatLiteral;
        assert tok.text == "3.14";
    }

    @Test
    void shouldLexStringLiteral() {
        ProtoLexer lexer = new ProtoLexer("\"hello world\"");
        Token tok = lexer.next();
        assert tok.type == StringLiteral;
        assert tok.text == "hello world";
    }

    @Test
    void shouldLexSingleQuotedString() {
        ProtoLexer lexer = new ProtoLexer("'single'");
        Token tok = lexer.next();
        assert tok.type == StringLiteral;
        assert tok.text == "single";
    }

    @Test
    void shouldLexStringEscapes() {
        ProtoLexer lexer = new ProtoLexer("\"line\\none\"");
        Token tok = lexer.next();
        assert tok.type == StringLiteral;
        assert tok.text == "line\none";
    }

    // ----- punctuation ---------------------------------------------------------------------------

    @Test
    void shouldLexPunctuation() {
        ProtoLexer lexer = new ProtoLexer("{ } [ ] ( ) ; , = . < >");
        assert lexer.next().type == LeftBrace;
        assert lexer.next().type == RightBrace;
        assert lexer.next().type == LeftBracket;
        assert lexer.next().type == RightBracket;
        assert lexer.next().type == LeftParen;
        assert lexer.next().type == RightParen;
        assert lexer.next().type == Semicolon;
        assert lexer.next().type == Comma;
        assert lexer.next().type == Equals;
        assert lexer.next().type == Dot;
        assert lexer.next().type == LessThan;
        assert lexer.next().type == GreaterThan;
        assert lexer.next().type == Eof;
    }

    // ----- comments ------------------------------------------------------------------------------

    @Test
    void shouldSkipLineComment() {
        ProtoLexer lexer = new ProtoLexer("// this is a comment\nmessage");
        Token tok = lexer.next();
        assert tok.type == Identifier;
        assert tok.text == "message";
    }

    @Test
    void shouldSkipBlockComment() {
        ProtoLexer lexer = new ProtoLexer("/* block\ncomment */message");
        Token tok = lexer.next();
        assert tok.type == Identifier;
        assert tok.text == "message";
    }

    // ----- line/column tracking ------------------------------------------------------------------

    @Test
    void shouldTrackLineNumbers() {
        ProtoLexer lexer = new ProtoLexer("a\nb\nc");
        Token a = lexer.next();
        assert a.line == 1;
        Token b = lexer.next();
        assert b.line == 2;
        Token c = lexer.next();
        assert c.line == 3;
    }

    // ----- eof -----------------------------------------------------------------------------------

    @Test
    void shouldReturnEofForEmptyInput() {
        ProtoLexer lexer = new ProtoLexer("");
        assert lexer.next().type == Eof;
    }

    @Test
    void shouldReturnEofAfterAllTokens() {
        ProtoLexer lexer = new ProtoLexer("x");
        lexer.next();
        assert lexer.next().type == Eof;
    }

    // ----- full token stream ---------------------------------------------------------------------

    @Test
    void shouldLexSyntaxStatement() {
        ProtoLexer lexer = new ProtoLexer("syntax = \"proto3\";");
        assert lexer.next().text == "syntax";
        assert lexer.next().type == Equals;
        Token str = lexer.next();
        assert str.type == StringLiteral;
        assert str.text == "proto3";
        assert lexer.next().type == Semicolon;
        assert lexer.next().type == Eof;
    }

    @Test
    void shouldLexFieldDefinition() {
        ProtoLexer lexer = new ProtoLexer("string name = 1;");
        assert lexer.next().text == "string";
        assert lexer.next().text == "name";
        assert lexer.next().type == Equals;
        Token num = lexer.next();
        assert num.type == IntLiteral;
        assert num.text == "1";
        assert lexer.next().type == Semicolon;
    }

    @Test
    void shouldLexMapField() {
        ProtoLexer lexer = new ProtoLexer("map<string, int32> labels = 4;");
        assert lexer.next().text == "map";
        assert lexer.next().type == LessThan;
        assert lexer.next().text == "string";
        assert lexer.next().type == Comma;
        assert lexer.next().text == "int32";
        assert lexer.next().type == GreaterThan;
        assert lexer.next().text == "labels";
        assert lexer.next().type == Equals;
        assert lexer.next().text == "4";
        assert lexer.next().type == Semicolon;
    }
}
