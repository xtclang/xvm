package org.xvm.compiler2.bridge;

import java.util.EnumMap;
import java.util.Map;

import org.xvm.compiler.Token;
import org.xvm.compiler2.syntax.SyntaxKind;
import org.xvm.compiler2.syntax.green.GreenToken;

/**
 * Converts XVM Token objects to GreenToken nodes.
 */
public final class TokenConverter {

    private TokenConverter() {}

    /**
     * Mapping from Token.Id to SyntaxKind.
     */
    private static final Map<Token.Id, SyntaxKind> TOKEN_MAP = new EnumMap<>(Token.Id.class);

    static {
        // Operators - Arithmetic
        TOKEN_MAP.put(Token.Id.ADD, SyntaxKind.PLUS);
        TOKEN_MAP.put(Token.Id.SUB, SyntaxKind.MINUS);
        TOKEN_MAP.put(Token.Id.MUL, SyntaxKind.STAR);
        TOKEN_MAP.put(Token.Id.DIV, SyntaxKind.SLASH);
        TOKEN_MAP.put(Token.Id.MOD, SyntaxKind.PERCENT);

        // Operators - Comparison
        TOKEN_MAP.put(Token.Id.COMP_EQ, SyntaxKind.EQ);
        TOKEN_MAP.put(Token.Id.COMP_NEQ, SyntaxKind.NEQ);
        TOKEN_MAP.put(Token.Id.COMP_LT, SyntaxKind.LT);
        TOKEN_MAP.put(Token.Id.COMP_GT, SyntaxKind.GT);
        TOKEN_MAP.put(Token.Id.COMP_LTEQ, SyntaxKind.LTEQ);
        TOKEN_MAP.put(Token.Id.COMP_GTEQ, SyntaxKind.GTEQ);
        TOKEN_MAP.put(Token.Id.COMP_ORD, SyntaxKind.SPACESHIP);

        // Operators - Logical
        TOKEN_MAP.put(Token.Id.COND_AND, SyntaxKind.AND);
        TOKEN_MAP.put(Token.Id.COND_OR, SyntaxKind.OR);
        TOKEN_MAP.put(Token.Id.NOT, SyntaxKind.NOT);

        // Operators - Bitwise
        TOKEN_MAP.put(Token.Id.BIT_AND, SyntaxKind.BIT_AND);
        TOKEN_MAP.put(Token.Id.BIT_OR, SyntaxKind.BIT_OR);
        TOKEN_MAP.put(Token.Id.BIT_XOR, SyntaxKind.BIT_XOR);
        TOKEN_MAP.put(Token.Id.BIT_NOT, SyntaxKind.BIT_NOT);
        TOKEN_MAP.put(Token.Id.SHL, SyntaxKind.SHL);
        TOKEN_MAP.put(Token.Id.SHR, SyntaxKind.SHR);
        TOKEN_MAP.put(Token.Id.USHR, SyntaxKind.USHR);

        // Operators - Assignment
        TOKEN_MAP.put(Token.Id.ASN, SyntaxKind.ASSIGN);
        TOKEN_MAP.put(Token.Id.ADD_ASN, SyntaxKind.ADD_ASSIGN);
        TOKEN_MAP.put(Token.Id.SUB_ASN, SyntaxKind.SUB_ASSIGN);
        TOKEN_MAP.put(Token.Id.MUL_ASN, SyntaxKind.MUL_ASSIGN);
        TOKEN_MAP.put(Token.Id.DIV_ASN, SyntaxKind.DIV_ASSIGN);
        TOKEN_MAP.put(Token.Id.MOD_ASN, SyntaxKind.MOD_ASSIGN);
        TOKEN_MAP.put(Token.Id.BIT_AND_ASN, SyntaxKind.AND_ASSIGN);
        TOKEN_MAP.put(Token.Id.BIT_OR_ASN, SyntaxKind.OR_ASSIGN);
        TOKEN_MAP.put(Token.Id.BIT_XOR_ASN, SyntaxKind.XOR_ASSIGN);
        TOKEN_MAP.put(Token.Id.SHL_ASN, SyntaxKind.SHL_ASSIGN);
        TOKEN_MAP.put(Token.Id.SHR_ASN, SyntaxKind.SHR_ASSIGN);
        TOKEN_MAP.put(Token.Id.USHR_ASN, SyntaxKind.USHR_ASSIGN);
        TOKEN_MAP.put(Token.Id.COND_NN_ASN, SyntaxKind.COND_ASSIGN);
        TOKEN_MAP.put(Token.Id.COND_ELSE_ASN, SyntaxKind.ELVIS_ASSIGN);

        // Operators - Other
        TOKEN_MAP.put(Token.Id.COND, SyntaxKind.COND);
        TOKEN_MAP.put(Token.Id.COND_ELSE, SyntaxKind.ELVIS);
        TOKEN_MAP.put(Token.Id.DOT, SyntaxKind.DOT);
        TOKEN_MAP.put(Token.Id.I_RANGE_I, SyntaxKind.DOTDOT);
        TOKEN_MAP.put(Token.Id.LAMBDA, SyntaxKind.ARROW);

        // Delimiters
        TOKEN_MAP.put(Token.Id.L_PAREN, SyntaxKind.LPAREN);
        TOKEN_MAP.put(Token.Id.R_PAREN, SyntaxKind.RPAREN);
        TOKEN_MAP.put(Token.Id.L_CURLY, SyntaxKind.LBRACE);
        TOKEN_MAP.put(Token.Id.R_CURLY, SyntaxKind.RBRACE);
        TOKEN_MAP.put(Token.Id.L_SQUARE, SyntaxKind.LBRACKET);
        TOKEN_MAP.put(Token.Id.R_SQUARE, SyntaxKind.RBRACKET);
        TOKEN_MAP.put(Token.Id.COMMA, SyntaxKind.COMMA);
        TOKEN_MAP.put(Token.Id.SEMICOLON, SyntaxKind.SEMICOLON);

        // Keywords - declarations
        TOKEN_MAP.put(Token.Id.MODULE, SyntaxKind.KW_MODULE);
        TOKEN_MAP.put(Token.Id.PACKAGE, SyntaxKind.KW_PACKAGE);
        TOKEN_MAP.put(Token.Id.CLASS, SyntaxKind.KW_CLASS);
        TOKEN_MAP.put(Token.Id.INTERFACE, SyntaxKind.KW_INTERFACE);
        TOKEN_MAP.put(Token.Id.MIXIN, SyntaxKind.KW_MIXIN);
        TOKEN_MAP.put(Token.Id.SERVICE, SyntaxKind.KW_SERVICE);
        TOKEN_MAP.put(Token.Id.CONST, SyntaxKind.KW_CONST);
        TOKEN_MAP.put(Token.Id.ENUM, SyntaxKind.KW_ENUM);

        // Keywords - modifiers
        TOKEN_MAP.put(Token.Id.PUBLIC, SyntaxKind.KW_PUBLIC);
        TOKEN_MAP.put(Token.Id.PROTECTED, SyntaxKind.KW_PROTECTED);
        TOKEN_MAP.put(Token.Id.PRIVATE, SyntaxKind.KW_PRIVATE);
        TOKEN_MAP.put(Token.Id.STATIC, SyntaxKind.KW_STATIC);

        // Keywords - statements
        TOKEN_MAP.put(Token.Id.IF, SyntaxKind.KW_IF);
        TOKEN_MAP.put(Token.Id.ELSE, SyntaxKind.KW_ELSE);
        TOKEN_MAP.put(Token.Id.SWITCH, SyntaxKind.KW_SWITCH);
        TOKEN_MAP.put(Token.Id.CASE, SyntaxKind.KW_CASE);
        TOKEN_MAP.put(Token.Id.DEFAULT, SyntaxKind.KW_DEFAULT);
        TOKEN_MAP.put(Token.Id.WHILE, SyntaxKind.KW_WHILE);
        TOKEN_MAP.put(Token.Id.DO, SyntaxKind.KW_DO);
        TOKEN_MAP.put(Token.Id.FOR, SyntaxKind.KW_FOR);
        TOKEN_MAP.put(Token.Id.RETURN, SyntaxKind.KW_RETURN);
        TOKEN_MAP.put(Token.Id.BREAK, SyntaxKind.KW_BREAK);
        TOKEN_MAP.put(Token.Id.CONTINUE, SyntaxKind.KW_CONTINUE);
        TOKEN_MAP.put(Token.Id.THROW, SyntaxKind.KW_THROW);
        TOKEN_MAP.put(Token.Id.TRY, SyntaxKind.KW_TRY);
        TOKEN_MAP.put(Token.Id.CATCH, SyntaxKind.KW_CATCH);
        TOKEN_MAP.put(Token.Id.FINALLY, SyntaxKind.KW_FINALLY);
        TOKEN_MAP.put(Token.Id.USING, SyntaxKind.KW_USING);
        TOKEN_MAP.put(Token.Id.ASSERT, SyntaxKind.KW_ASSERT);

        // Keywords - expressions
        TOKEN_MAP.put(Token.Id.NEW, SyntaxKind.KW_NEW);
        TOKEN_MAP.put(Token.Id.THIS, SyntaxKind.KW_THIS);
        TOKEN_MAP.put(Token.Id.SUPER, SyntaxKind.KW_SUPER);
        TOKEN_MAP.put(Token.Id.IS, SyntaxKind.KW_IS);
        TOKEN_MAP.put(Token.Id.AS, SyntaxKind.KW_AS);

        // Keywords - types
        TOKEN_MAP.put(Token.Id.VOID, SyntaxKind.KW_VOID);
        TOKEN_MAP.put(Token.Id.VAR, SyntaxKind.KW_VAR);
        TOKEN_MAP.put(Token.Id.VAL, SyntaxKind.KW_VAL);
        TOKEN_MAP.put(Token.Id.IMMUTABLE, SyntaxKind.KW_IMMUTABLE);
        TOKEN_MAP.put(Token.Id.CONDITIONAL, SyntaxKind.KW_CONDITIONAL);

        // Literals
        TOKEN_MAP.put(Token.Id.LIT_INT, SyntaxKind.INT_LITERAL);
        TOKEN_MAP.put(Token.Id.LIT_DEC, SyntaxKind.FP_LITERAL);
        TOKEN_MAP.put(Token.Id.LIT_FLOAT, SyntaxKind.FP_LITERAL);
        TOKEN_MAP.put(Token.Id.LIT_CHAR, SyntaxKind.CHAR_LITERAL);
        TOKEN_MAP.put(Token.Id.LIT_STRING, SyntaxKind.STRING_LITERAL);
        TOKEN_MAP.put(Token.Id.LIT_BINSTR, SyntaxKind.BINARY_LITERAL);

        // Identifier
        TOKEN_MAP.put(Token.Id.IDENTIFIER, SyntaxKind.IDENTIFIER);
    }

    /**
     * Convert a Token.Id to SyntaxKind.
     *
     * @param id the Token.Id
     * @return the corresponding SyntaxKind, or null if unmapped
     */
    public static SyntaxKind toSyntaxKind(Token.Id id) {
        return TOKEN_MAP.get(id);
    }

    /**
     * Convert an XVM Token to a GreenToken.
     *
     * @param token the XVM token
     * @return the green token
     * @throws IllegalArgumentException if the token type is not supported
     */
    public static GreenToken convert(Token token) {
        Token.Id id = token.getId();
        SyntaxKind kind = TOKEN_MAP.get(id);

        if (kind == null) {
            // For unmapped tokens, try to derive from text
            String text = id.TEXT;
            if (text != null) {
                // It's a keyword or operator with fixed text
                kind = SyntaxKind.BAD_TOKEN;
            } else {
                kind = SyntaxKind.BAD_TOKEN;
            }
        }

        String text = getTokenText(token);
        String leadingTrivia = token.hasLeadingWhitespace() ? " " : "";
        String trailingTrivia = token.hasTrailingWhitespace() ? " " : "";

        Object value = token.getValue();

        // Handle special literal cases
        if (kind == SyntaxKind.INT_LITERAL && value instanceof Number n) {
            return GreenToken.intLiteral(n.longValue(), leadingTrivia, trailingTrivia);
        }

        if (kind == SyntaxKind.FP_LITERAL && value instanceof Number n) {
            return GreenToken.create(kind, text, leadingTrivia, trailingTrivia);
        }

        if (kind == SyntaxKind.IDENTIFIER) {
            return GreenToken.identifier(text, leadingTrivia, trailingTrivia);
        }

        return GreenToken.create(kind, text, leadingTrivia, trailingTrivia);
    }

    /**
     * Get the text representation of a token.
     */
    private static String getTokenText(Token token) {
        Token.Id id = token.getId();

        // Fixed text tokens
        if (id.TEXT != null) {
            return id.TEXT;
        }

        // Value-based tokens
        Object value = token.getValue();
        if (value != null) {
            if (id == Token.Id.LIT_STRING) {
                return "\"" + value + "\"";
            }
            if (id == Token.Id.LIT_CHAR) {
                return "'" + value + "'";
            }
            return value.toString();
        }

        return "";
    }
}
