import ecstasy.io.ByteArrayInputStream;
import ecstasy.io.Reader;
import ecstasy.io.UTF8Reader;

/**
 * A tokenizer for Protocol Buffers `.proto` files.
 *
 * Breaks input from a [Reader] into a stream of tokens, handling whitespace, single-line (`//`)
 * and block comments, string literals with escape sequences, numeric literals (decimal, hex,
 * octal, float), and identifier/keyword tokens.
 *
 * The lexer maintains a 2-character lookahead (`current` and `nextChar`) so it can detect
 * two-character sequences like `//`, `/*`, `0x`, and `.` followed by a digit.
 */
class ProtoLexer {

    /**
     * The type of a lexer token.
     */
    enum TokenType {
        Identifier,
        IntLiteral,
        FloatLiteral,
        StringLiteral,
        LeftBrace,
        RightBrace,
        LeftBracket,
        RightBracket,
        LeftParen,
        RightParen,
        Semicolon,
        Comma,
        Equals,
        Dot,
        Minus,
        LessThan,
        GreaterThan,
        Colon,
        Eof
    }

    /**
     * A single token from the lexer.
     *
     * @param type    the token type
     * @param text    the token text
     * @param line    the 1-based line number
     * @param column  the 1-based column number
     */
    static const Token(TokenType type, String text, Int line, Int column) {
        @Override
        String toString() = $"{type}({text.quoted()}) at {line}:{column}";
    }

    /**
     * Construct a lexer from a [Reader].
     */
    construct(Reader reader) {
        this.reader = reader;
        // prime the 2-character lookahead
        if (Char ch := reader.next()) {
            this.current = ch;
            this.eof     = False;
            if (Char ch2 := reader.next()) {
                this.nextChar    = ch2;
                this.hasNextChar = True;
            } else {
                this.nextChar    = '\0';
                this.hasNextChar = False;
            }
        } else {
            this.current     = '\0';
            this.eof         = True;
            this.nextChar    = '\0';
            this.hasNextChar = False;
        }
    }

    /**
     * Construct a lexer from a source string.
     */
    construct(String source) {
        construct ProtoLexer(new UTF8Reader(new ByteArrayInputStream(source.utf8())));
    }

    /**
     * The underlying character reader.
     */
    private Reader reader;

    /**
     * The current (first lookahead) character.
     */
    private Char current;

    /**
     * The second lookahead character.
     */
    private Char nextChar;

    /**
     * True when `nextChar` holds a valid character.
     */
    private Boolean hasNextChar;

    /**
     * True when the reader has been exhausted (no more `current` character).
     */
    private Boolean eof;

    /**
     * The current line number (1-based).
     */
    private Int line = 1;

    /**
     * The current column number (1-based).
     */
    private Int col = 1;

    // ----- public interface -----------------------------------------------------------------------

    /**
     * Read and return the next token.
     *
     * @return the next token, or an `Eof` token at the end of input
     */
    Token next() {
        skipWhitespaceAndComments();

        if (eof) {
            return new Token(Eof, "", line, col);
        }

        Int  startLine = line;
        Int  startCol  = col;
        Char ch        = current;

        // single-character tokens
        switch (ch) {
        case '{': advance(); return new Token(LeftBrace,    "{", startLine, startCol);
        case '}': advance(); return new Token(RightBrace,   "}", startLine, startCol);
        case '[': advance(); return new Token(LeftBracket,  "[", startLine, startCol);
        case ']': advance(); return new Token(RightBracket, "]", startLine, startCol);
        case '(': advance(); return new Token(LeftParen,    "(", startLine, startCol);
        case ')': advance(); return new Token(RightParen,   ")", startLine, startCol);
        case ';': advance(); return new Token(Semicolon,    ";", startLine, startCol);
        case ',': advance(); return new Token(Comma,        ",", startLine, startCol);
        case '=': advance(); return new Token(Equals,       "=", startLine, startCol);
        case '.': advance(); return new Token(Dot,          ".", startLine, startCol);
        case '<': advance(); return new Token(LessThan,     "<", startLine, startCol);
        case '>': advance(); return new Token(GreaterThan,  ">", startLine, startCol);
        case ':': advance(); return new Token(Colon,        ":", startLine, startCol);
        }

        // string literal
        if (ch == '"' || ch == '\'') {
            return readString(startLine, startCol);
        }

        // numeric literal
        if (ch.asciiDigit()) {
            return readNumber(startLine, startCol);
        }

        // minus sign
        if (ch == '-') {
            advance();
            return new Token(Minus, "-", startLine, startCol);
        }

        // identifier or keyword
        if (ch.asciiLetter() || ch == '_') {
            return readIdentifier(startLine, startCol);
        }

        assert as $"Unexpected character {ch.quoted()} at {startLine}:{startCol}";
    }

    // ----- whitespace and comment handling ---------------------------------------------------------

    /**
     * Skip whitespace, single-line comments (`//`), and block comments.
     */
    private void skipWhitespaceAndComments() {
        while (!eof) {
            Char ch = current;

            if (ch.isWhitespace()) {
                advance();
                continue;
            }

            if (ch == '/') {
                Char? peeked = peek();
                if (peeked == '/') {
                    skipLineComment();
                    continue;
                }
                if (peeked == '*') {
                    skipBlockComment();
                    continue;
                }
            }

            break;
        }
    }

    /**
     * Skip a single-line comment (`// ... \n`).
     */
    private void skipLineComment() {
        while (!eof && current != '\n') {
            advance();
        }
    }

    /**
     * Skip a block comment.
     */
    private void skipBlockComment() {
        Int startLine = line;
        Int startCol  = col;
        advance();  // '/'
        advance();  // '*'
        while (!eof) {
            if (current == '*') {
                if (peek() == '/') {
                    advance();  // '*'
                    advance();  // '/'
                    return;
                }
            }
            advance();
        }
        assert as $"Unterminated block comment starting at {startLine}:{startCol}";
    }

    // ----- token readers --------------------------------------------------------------------------

    /**
     * Read a string literal, handling escape sequences.
     */
    private Token readString(Int startLine, Int startCol) {
        Char         quote = current;
        StringBuffer buf   = new StringBuffer();
        advance();  // opening quote

        while (!eof) {
            Char ch = current;
            if (ch == quote) {
                advance();  // closing quote
                return new Token(StringLiteral, buf.toString(), startLine, startCol);
            }
            if (ch == '\n') {
                assert as $"Unterminated string literal at {startLine}:{startCol}";
            }
            if (ch == '\\') {
                advance();
                assert !eof as $"Unterminated escape sequence at {line}:{col}";
                Char esc = current;
                switch (esc) {
                case 'n':  buf.add('\n'); advance(); break;
                case 'r':  buf.add('\r'); advance(); break;
                case 't':  buf.add('\t'); advance(); break;
                case '\\': buf.add('\\'); advance(); break;
                case '\'': buf.add('\''); advance(); break;
                case '"':  buf.add('"');  advance(); break;
                case 'x', 'X':
                    advance();
                    buf.add(readHexEscape());
                    break;

                default:
                    if (esc >= '0' && esc <= '7') {
                        buf.add(readOctalEscape());
                    } else {
                        buf.add(esc);
                        advance();
                    }
                    break;
                }
            } else {
                buf.add(ch);
                advance();
            }
        }
        assert as $"Unterminated string literal at {startLine}:{startCol}";
    }

    /**
     * Read a hex escape sequence (1-2 hex digits after `\x`).
     */
    private Char readHexEscape() {
        Int value = 0;
        Int count = 0;
        while (!eof && count < 2) {
            if (Int digit := hexDigit(current)) {
                value = value * 16 + digit;
                advance();
                count++;
            } else {
                break;
            }
        }
        assert count > 0 as $"Expected hex digit at {line}:{col}";
        return value.toChar();
    }

    /**
     * Read an octal escape sequence (1-3 octal digits).
     */
    private Char readOctalEscape() {
        Int value = 0;
        Int count = 0;
        while (!eof && count < 3) {
            Char ch = current;
            if (ch >= '0' && ch <= '7') {
                value = value * 8 + (ch - '0');
                advance();
                count++;
            } else {
                break;
            }
        }
        return value.toChar();
    }

    /**
     * Read a numeric literal (decimal, hex, octal, or float).
     */
    private Token readNumber(Int startLine, Int startCol) {
        StringBuffer buf = new StringBuffer();

        // hex literal: 0x or 0X
        if (current == '0') {
            Char? next = peek();
            if (next == 'x' || next == 'X') {
                buf.add(current); advance();  // '0'
                buf.add(current); advance();  // 'x'
                while (!eof && hexDigit(current)) {
                    buf.add(current);
                    advance();
                }
                return new Token(IntLiteral, buf.toString(), startLine, startCol);
            }
        }

        // leading digits
        while (!eof && current.asciiDigit()) {
            buf.add(current);
            advance();
        }

        // check for float (decimal point or exponent)
        Boolean isFloat = False;
        if (!eof && current == '.') {
            Char? next = peek();
            if (next != Null && next.asciiDigit()) {
                isFloat = True;
                buf.add(current); advance();  // '.'
                while (!eof && current.asciiDigit()) {
                    buf.add(current);
                    advance();
                }
            }
        }
        if (!eof && (current == 'e' || current == 'E')) {
            isFloat = True;
            buf.add(current); advance();
            if (!eof && (current == '+' || current == '-')) {
                buf.add(current); advance();
            }
            while (!eof && current.asciiDigit()) {
                buf.add(current);
                advance();
            }
        }

        return new Token(isFloat ? FloatLiteral : IntLiteral, buf.toString(), startLine, startCol);
    }

    /**
     * Read an identifier token.
     */
    private Token readIdentifier(Int startLine, Int startCol) {
        StringBuffer buf = new StringBuffer();
        while (!eof) {
            Char ch = current;
            if (ch.asciiLetter() || ch.asciiDigit() || ch == '_') {
                buf.add(ch);
                advance();
            } else {
                break;
            }
        }
        return new Token(Identifier, buf.toString(), startLine, startCol);
    }

    // ----- character helpers ----------------------------------------------------------------------

    /**
     * Advance to the next character, shifting the 2-character lookahead buffer. Tracks line and
     * column numbers based on the character being consumed.
     */
    private void advance() {
        if (eof) {
            return;
        }
        if (current == '\n') {
            line++;
            col = 1;
        } else {
            col++;
        }
        if (hasNextChar) {
            current = nextChar;
            if (Char ch := reader.next()) {
                nextChar = ch;
            } else {
                nextChar    = '\0';
                hasNextChar = False;
            }
        } else {
            current = '\0';
            eof     = True;
        }
    }

    /**
     * Peek at the next character without consuming it.
     *
     * @return the next character, or `Null` if at the end of input
     */
    private Char? peek() = hasNextChar ? nextChar : Null;

    /**
     * @return True and the hex digit value if the character is a hex digit
     */
    private static conditional Int hexDigit(Char ch) {
        if (ch >= '0' && ch <= '9') {
            return True, ch - '0';
        }
        if (ch >= 'a' && ch <= 'f') {
            return True, ch - 'a' + 10;
        }
        if (ch >= 'A' && ch <= 'F') {
            return True, ch - 'A' + 10;
        }
        return False;
    }
}
