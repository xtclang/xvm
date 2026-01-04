package org.xvm.compiler2.parser;

import org.xvm.compiler2.CompilationContext;
import org.xvm.compiler2.syntax.SyntaxKind;
import org.xvm.compiler2.syntax.green.GreenToken;

/**
 * An immutable lexer for the green tree architecture.
 * <p>
 * Design principles:
 * <ul>
 *   <li>No shared mutable state - each method returns new immutable objects</li>
 *   <li>Position tracking via immutable snapshots</li>
 *   <li>Direct emission of GreenToken nodes</li>
 *   <li>Suitable for incremental/LSP use</li>
 *   <li>Integrated with CompilationContext for error reporting</li>
 * </ul>
 * <p>
 * The lexer maintains its position as part of its immutable state.
 * To advance, you create a new lexer at the new position.
 */
public final class GreenLexer {

    private final SourceText source;
    private final CompilationContext context;
    private final int position;
    private final int length;

    /**
     * Create a lexer with a CompilationContext.
     *
     * @param context the compilation context
     */
    public GreenLexer(CompilationContext context) {
        this(context.getSource(), context, 0);
    }

    /**
     * Create a lexer for the given source text without a context.
     * <p>
     * Note: Prefer using the CompilationContext constructor for proper error reporting.
     *
     * @param source the source text
     */
    public GreenLexer(SourceText source) {
        this(source, null, 0);
    }

    /**
     * Create a lexer for a simple string (for testing).
     *
     * @param code the source code string
     */
    public GreenLexer(String code) {
        this(new SourceText(code), null, 0);
    }

    /**
     * Private constructor for creating lexer at specific position.
     */
    private GreenLexer(SourceText source, CompilationContext context, int position) {
        this.source = source;
        this.context = context;
        this.position = position;
        this.length = source.getLength();
    }

    /**
     * @return true if there are more tokens
     */
    public boolean hasNext() {
        return position < length;
    }

    /**
     * @return the current position in the source
     */
    public int getPosition() {
        return position;
    }

    /**
     * @return the compilation context (may be null for testing)
     */
    public CompilationContext getContext() {
        return context;
    }

    /**
     * Report an error at the given position.
     *
     * @param code    the error code
     * @param message the error message
     * @param offset  the source offset
     * @param length  the length
     */
    private void reportError(String code, String message, int offset, int length) {
        if (context != null) {
            context.reportError(code, message, offset, length);
        }
    }

    /**
     * Scan the next token.
     *
     * @return a TokenResult containing the token and the lexer positioned after it
     */
    public TokenResult nextToken() {
        if (!hasNext()) {
            GreenToken eof = GreenToken.create(SyntaxKind.EOF, "");
            return new TokenResult(eof, this);
        }

        // Skip whitespace and track it
        int start = position;
        int pos = skipWhitespace(position);
        String leadingTrivia = getText(start, pos);

        if (pos >= length) {
            GreenToken eof = GreenToken.create(SyntaxKind.EOF, "", leadingTrivia, "");
            return new TokenResult(eof, at(pos));
        }

        // Scan the actual token
        char c = charAt(pos);
        int tokenStart = pos;

        // Identifiers and keywords
        if (isIdentifierStart(c)) {
            return scanIdentifier(tokenStart, leadingTrivia);
        }

        // Numbers
        if (isDigit(c)) {
            return scanNumber(tokenStart, leadingTrivia);
        }

        // String literals
        if (c == '"') {
            return scanString(tokenStart, leadingTrivia);
        }

        // Character literals
        if (c == '\'') {
            return scanChar(tokenStart, leadingTrivia);
        }

        // Operators and punctuation
        return scanOperator(tokenStart, leadingTrivia);
    }

    // -------------------------------------------------------------------------
    // Scanning methods
    // -------------------------------------------------------------------------

    private TokenResult scanIdentifier(int start, String leadingTrivia) {
        int pos = start;
        while (pos < length && isIdentifierPart(charAt(pos))) {
            pos++;
        }

        String text = getText(start, pos);
        String trailingTrivia = scanTrailingTrivia(pos);
        int newPos = pos + trailingTrivia.length();

        // Check for keywords
        SyntaxKind kind = getKeywordKind(text);
        if (kind == null) {
            kind = SyntaxKind.IDENTIFIER;
        }

        GreenToken token = GreenToken.identifier(text, leadingTrivia, trailingTrivia);
        if (kind != SyntaxKind.IDENTIFIER) {
            token = GreenToken.create(kind, text, leadingTrivia, trailingTrivia);
        }

        return new TokenResult(token, at(newPos));
    }

    private TokenResult scanNumber(int start, String leadingTrivia) {
        int pos = start;

        // Scan integer part
        while (pos < length && isDigit(charAt(pos))) {
            pos++;
        }

        // Check for decimal point
        boolean isFloat = false;
        if (pos < length && charAt(pos) == '.' &&
            pos + 1 < length && isDigit(charAt(pos + 1))) {
            isFloat = true;
            pos++; // skip '.'
            while (pos < length && isDigit(charAt(pos))) {
                pos++;
            }
        }

        String text = getText(start, pos);
        String trailingTrivia = scanTrailingTrivia(pos);
        int newPos = pos + trailingTrivia.length();

        GreenToken token;
        if (isFloat) {
            double value = Double.parseDouble(text);
            token = GreenToken.fpLiteral(value);
        } else {
            long value = Long.parseLong(text);
            token = GreenToken.intLiteral(value, leadingTrivia, trailingTrivia);
        }

        return new TokenResult(token, at(newPos));
    }

    private TokenResult scanString(int start, String leadingTrivia) {
        int pos = start + 1; // skip opening "
        StringBuilder value = new StringBuilder();

        while (pos < length && charAt(pos) != '"') {
            char c = charAt(pos);
            if (c == '\\' && pos + 1 < length) {
                pos++;
                c = charAt(pos);
                switch (c) {
                    case 'n' -> value.append('\n');
                    case 't' -> value.append('\t');
                    case 'r' -> value.append('\r');
                    case '\\' -> value.append('\\');
                    case '"' -> value.append('"');
                    default -> value.append(c);
                }
            } else {
                value.append(c);
            }
            pos++;
        }

        if (pos < length) {
            pos++; // skip closing "
        } else {
            // Unterminated string
            reportError("XC0001", "Unterminated string literal", start, pos - start);
        }

        String text = getText(start, pos);
        String trailingTrivia = scanTrailingTrivia(pos);
        int newPos = pos + trailingTrivia.length();

        GreenToken token = GreenToken.stringLiteral(text, value.toString());
        return new TokenResult(token, at(newPos));
    }

    private TokenResult scanChar(int start, String leadingTrivia) {
        int pos = start + 1; // skip opening '
        char value = 0;

        if (pos < length) {
            char c = charAt(pos);
            if (c == '\\' && pos + 1 < length) {
                pos++;
                c = charAt(pos);
                value = switch (c) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    case '\\' -> '\\';
                    case '\'' -> '\'';
                    default -> c;
                };
            } else {
                value = c;
            }
            pos++;
        }

        if (pos < length && charAt(pos) == '\'') {
            pos++; // skip closing '
        } else {
            // Unterminated character literal
            reportError("XC0002", "Unterminated character literal", start, pos - start);
        }

        String text = getText(start, pos);
        String trailingTrivia = scanTrailingTrivia(pos);
        int newPos = pos + trailingTrivia.length();

        GreenToken token = GreenToken.create(SyntaxKind.CHAR_LITERAL, text, leadingTrivia, trailingTrivia);
        return new TokenResult(token, at(newPos));
    }

    private TokenResult scanOperator(int start, String leadingTrivia) {
        char c = charAt(start);
        int pos = start;
        SyntaxKind kind;

        switch (c) {
            case '+' -> {
                pos++;
                if (pos < length && charAt(pos) == '=') {
                    pos++;
                    kind = SyntaxKind.ADD_ASSIGN;
                } else {
                    kind = SyntaxKind.PLUS;
                }
            }
            case '-' -> {
                pos++;
                if (pos < length && charAt(pos) == '=') {
                    pos++;
                    kind = SyntaxKind.SUB_ASSIGN;
                } else if (pos < length && charAt(pos) == '>') {
                    pos++;
                    kind = SyntaxKind.ARROW;
                } else {
                    kind = SyntaxKind.MINUS;
                }
            }
            case '*' -> {
                pos++;
                if (pos < length && charAt(pos) == '=') {
                    pos++;
                    kind = SyntaxKind.MUL_ASSIGN;
                } else {
                    kind = SyntaxKind.STAR;
                }
            }
            case '/' -> {
                pos++;
                if (pos < length && charAt(pos) == '=') {
                    pos++;
                    kind = SyntaxKind.DIV_ASSIGN;
                } else if (pos < length && charAt(pos) == '/') {
                    // Line comment - skip to end of line
                    while (pos < length && charAt(pos) != '\n') {
                        pos++;
                    }
                    // Recurse to get next real token
                    return at(pos).nextToken();
                } else {
                    kind = SyntaxKind.SLASH;
                }
            }
            case '%' -> {
                pos++;
                if (pos < length && charAt(pos) == '=') {
                    pos++;
                    kind = SyntaxKind.MOD_ASSIGN;
                } else {
                    kind = SyntaxKind.PERCENT;
                }
            }
            case '=' -> {
                pos++;
                if (pos < length && charAt(pos) == '=') {
                    pos++;
                    kind = SyntaxKind.EQ;
                } else {
                    kind = SyntaxKind.ASSIGN;
                }
            }
            case '!' -> {
                pos++;
                if (pos < length && charAt(pos) == '=') {
                    pos++;
                    kind = SyntaxKind.NEQ;
                } else {
                    kind = SyntaxKind.NOT;
                }
            }
            case '<' -> {
                pos++;
                if (pos < length && charAt(pos) == '=') {
                    pos++;
                    if (pos < length && charAt(pos) == '>') {
                        pos++;
                        kind = SyntaxKind.SPACESHIP;
                    } else {
                        kind = SyntaxKind.LTEQ;
                    }
                } else if (pos < length && charAt(pos) == '<') {
                    pos++;
                    kind = SyntaxKind.SHL;
                } else {
                    kind = SyntaxKind.LT;
                }
            }
            case '>' -> {
                pos++;
                if (pos < length && charAt(pos) == '=') {
                    pos++;
                    kind = SyntaxKind.GTEQ;
                } else if (pos < length && charAt(pos) == '>') {
                    pos++;
                    if (pos < length && charAt(pos) == '>') {
                        pos++;
                        kind = SyntaxKind.USHR;
                    } else {
                        kind = SyntaxKind.SHR;
                    }
                } else {
                    kind = SyntaxKind.GT;
                }
            }
            case '&' -> {
                pos++;
                if (pos < length && charAt(pos) == '&') {
                    pos++;
                    kind = SyntaxKind.AND;
                } else {
                    kind = SyntaxKind.BIT_AND;
                }
            }
            case '|' -> {
                pos++;
                if (pos < length && charAt(pos) == '|') {
                    pos++;
                    kind = SyntaxKind.OR;
                } else {
                    kind = SyntaxKind.BIT_OR;
                }
            }
            case '^' -> {
                pos++;
                kind = SyntaxKind.BIT_XOR;
            }
            case '~' -> {
                pos++;
                kind = SyntaxKind.BIT_NOT;
            }
            case '?' -> {
                pos++;
                if (pos < length && charAt(pos) == ':') {
                    pos++;
                    kind = SyntaxKind.ELVIS;
                } else {
                    kind = SyntaxKind.COND;
                }
            }
            case ':' -> {
                pos++;
                kind = SyntaxKind.COLON;
            }
            case '.' -> {
                pos++;
                if (pos < length && charAt(pos) == '.') {
                    pos++;
                    kind = SyntaxKind.DOTDOT;
                } else {
                    kind = SyntaxKind.DOT;
                }
            }
            case '(' -> {
                pos++;
                kind = SyntaxKind.LPAREN;
            }
            case ')' -> {
                pos++;
                kind = SyntaxKind.RPAREN;
            }
            case '{' -> {
                pos++;
                kind = SyntaxKind.LBRACE;
            }
            case '}' -> {
                pos++;
                kind = SyntaxKind.RBRACE;
            }
            case '[' -> {
                pos++;
                kind = SyntaxKind.LBRACKET;
            }
            case ']' -> {
                pos++;
                kind = SyntaxKind.RBRACKET;
            }
            case ',' -> {
                pos++;
                kind = SyntaxKind.COMMA;
            }
            case ';' -> {
                pos++;
                kind = SyntaxKind.SEMICOLON;
            }
            default -> {
                pos++;
                kind = SyntaxKind.BAD_TOKEN;
                reportError("XC0003", "Unexpected character: " + c, start, 1);
            }
        }

        String text = getText(start, pos);
        String trailingTrivia = scanTrailingTrivia(pos);
        int newPos = pos + trailingTrivia.length();

        GreenToken token = GreenToken.create(kind, text, leadingTrivia, trailingTrivia);
        return new TokenResult(token, at(newPos));
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private int skipWhitespace(int pos) {
        while (pos < length) {
            char c = charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
        return pos;
    }

    private String scanTrailingTrivia(int pos) {
        int start = pos;
        while (pos < length) {
            char c = charAt(pos);
            if (c == ' ' || c == '\t') {
                pos++;
            } else {
                break;
            }
        }
        return getText(start, pos);
    }

    private GreenLexer at(int newPosition) {
        return new GreenLexer(source, context, newPosition);
    }

    /**
     * Get a character at the given position.
     */
    private char charAt(int pos) {
        return source.charAt(pos);
    }

    /**
     * Get text between two positions.
     */
    private String getText(int start, int end) {
        return source.substring(start, end);
    }

    private static boolean isIdentifierStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isIdentifierPart(char c) {
        return isIdentifierStart(c) || isDigit(c);
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static SyntaxKind getKeywordKind(String text) {
        return switch (text) {
            case "if" -> SyntaxKind.KW_IF;
            case "else" -> SyntaxKind.KW_ELSE;
            case "while" -> SyntaxKind.KW_WHILE;
            case "for" -> SyntaxKind.KW_FOR;
            case "return" -> SyntaxKind.KW_RETURN;
            case "break" -> SyntaxKind.KW_BREAK;
            case "continue" -> SyntaxKind.KW_CONTINUE;
            case "true" -> SyntaxKind.KW_TRUE;
            case "false" -> SyntaxKind.KW_FALSE;
            case "null" -> SyntaxKind.KW_NULL;
            case "new" -> SyntaxKind.KW_NEW;
            case "this" -> SyntaxKind.KW_THIS;
            case "super" -> SyntaxKind.KW_SUPER;
            case "class" -> SyntaxKind.KW_CLASS;
            case "interface" -> SyntaxKind.KW_INTERFACE;
            case "module" -> SyntaxKind.KW_MODULE;
            case "package" -> SyntaxKind.KW_PACKAGE;
            case "public" -> SyntaxKind.KW_PUBLIC;
            case "private" -> SyntaxKind.KW_PRIVATE;
            case "protected" -> SyntaxKind.KW_PROTECTED;
            case "static" -> SyntaxKind.KW_STATIC;
            case "void" -> SyntaxKind.KW_VOID;
            case "var" -> SyntaxKind.KW_VAR;
            case "val" -> SyntaxKind.KW_VAL;
            case "is" -> SyntaxKind.KW_IS;
            case "as" -> SyntaxKind.KW_AS;
            case "try" -> SyntaxKind.KW_TRY;
            case "catch" -> SyntaxKind.KW_CATCH;
            case "finally" -> SyntaxKind.KW_FINALLY;
            case "throw" -> SyntaxKind.KW_THROW;
            case "assert" -> SyntaxKind.KW_ASSERT;
            case "switch" -> SyntaxKind.KW_SWITCH;
            case "case" -> SyntaxKind.KW_CASE;
            case "default" -> SyntaxKind.KW_DEFAULT;
            case "do" -> SyntaxKind.KW_DO;
            case "const" -> SyntaxKind.KW_CONST;
            case "enum" -> SyntaxKind.KW_ENUM;
            case "mixin" -> SyntaxKind.KW_MIXIN;
            case "service" -> SyntaxKind.KW_SERVICE;
            case "immutable" -> SyntaxKind.KW_IMMUTABLE;
            case "conditional" -> SyntaxKind.KW_CONDITIONAL;
            default -> null;
        };
    }

    // -------------------------------------------------------------------------
    // Result type
    // -------------------------------------------------------------------------

    /**
     * Immutable result of scanning a token.
     * Contains both the token and the lexer positioned after it.
     */
    public record TokenResult(GreenToken token, GreenLexer next) {}
}
