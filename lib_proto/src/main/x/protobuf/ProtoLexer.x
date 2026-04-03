/**
 * A tokenizer for Protocol Buffers `.proto` files.
 *
 * Breaks the source text into a stream of tokens, handling whitespace, single-line (`//`) and
 * block (`/* ... * /`) comments, string literals with escape sequences, numeric literals (decimal,
 * hex, octal, float), and identifier/keyword tokens.
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

    construct(String source) {
        this.source = source;
        this.len    = source.size;
    }

    /**
     * The source text.
     */
    private String source;

    /**
     * The total length of the source text.
     */
    private Int len;

    /**
     * The current character position.
     */
    private Int pos = 0;

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

        if (pos >= len) {
            return new Token(Eof, "", line, col);
        }

        Int  startLine = line;
        Int  startCol  = col;
        Char ch        = source[pos];

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

        // numeric literal (including negative via parser, but handle leading digits and 0x)
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
        while (pos < len) {
            Char ch = source[pos];

            // whitespace
            if (ch.isWhitespace()) {
                advance();
                continue;
            }

            // comments
            if (ch == '/' && pos + 1 < len) {
                Char next = source[pos + 1];
                if (next == '/') {
                    skipLineComment();
                    continue;
                }
                if (next == '*') {
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
        while (pos < len && source[pos] != '\n') {
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
        while (pos < len) {
            if (source[pos] == '*' && pos + 1 < len && source[pos + 1] == '/') {
                advance();  // '*'
                advance();  // '/'
                return;
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
        Char        quote = source[pos];
        StringBuffer buf   = new StringBuffer();
        advance();  // opening quote

        while (pos < len) {
            Char ch = source[pos];
            if (ch == quote) {
                advance();  // closing quote
                return new Token(StringLiteral, buf.toString(), startLine, startCol);
            }
            if (ch == '\n') {
                assert as $"Unterminated string literal at {startLine}:{startCol}";
            }
            if (ch == '\\') {
                advance();
                assert pos < len as $"Unterminated escape sequence at {line}:{col}";
                Char esc = source[pos];
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
        while (pos < len && count < 2) {
            Char ch = source[pos];
            if (Int digit := hexDigit(ch)) {
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
        while (pos < len && count < 3) {
            Char ch = source[pos];
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
        Int start = pos;

        // hex literal
        if (source[pos] == '0' && pos + 1 < len
                && (source[pos + 1] == 'x' || source[pos + 1] == 'X')) {
            advance();  // '0'
            advance();  // 'x'
            while (pos < len && hexDigit(source[pos])) {
                advance();
            }
            return new Token(IntLiteral, source[start ..< pos], startLine, startCol);
        }

        // leading digits
        while (pos < len && source[pos].asciiDigit()) {
            advance();
        }

        // check for float (decimal point or exponent)
        Boolean isFloat = False;
        if (pos < len && source[pos] == '.' && pos + 1 < len && source[pos + 1].asciiDigit()) {
            isFloat = True;
            advance();  // '.'
            while (pos < len && source[pos].asciiDigit()) {
                advance();
            }
        }
        if (pos < len && (source[pos] == 'e' || source[pos] == 'E')) {
            isFloat = True;
            advance();
            if (pos < len && (source[pos] == '+' || source[pos] == '-')) {
                advance();
            }
            while (pos < len && source[pos].asciiDigit()) {
                advance();
            }
        }

        String text = source[start ..< pos];
        return new Token(isFloat ? FloatLiteral : IntLiteral, text, startLine, startCol);
    }

    /**
     * Read an identifier token.
     */
    private Token readIdentifier(Int startLine, Int startCol) {
        Int start = pos;
        while (pos < len) {
            Char ch = source[pos];
            if (ch.asciiLetter() || ch.asciiDigit() || ch == '_') {
                advance();
            } else {
                break;
            }
        }
        return new Token(Identifier, source[start ..< pos], startLine, startCol);
    }

    // ----- character helpers ----------------------------------------------------------------------

    /**
     * Advance one character, tracking line and column numbers.
     */
    private void advance() {
        if (pos < len) {
            if (source[pos] == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
            pos++;
        }
    }

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
