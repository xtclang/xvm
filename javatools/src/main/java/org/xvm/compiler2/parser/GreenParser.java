package org.xvm.compiler2.parser;

import java.util.ArrayList;
import java.util.List;

import org.xvm.compiler2.CompilationContext;
import org.xvm.compiler2.syntax.SyntaxKind;
import org.xvm.compiler2.syntax.green.GreenAssignExpr;
import org.xvm.compiler2.syntax.green.GreenBinaryExpr;
import org.xvm.compiler2.syntax.green.GreenBlockStmt;
import org.xvm.compiler2.syntax.green.GreenConditionalExpr;
import org.xvm.compiler2.syntax.green.GreenExpression;
import org.xvm.compiler2.syntax.green.GreenExprStmt;
import org.xvm.compiler2.syntax.green.GreenIndexExpr;
import org.xvm.compiler2.syntax.green.GreenInvokeExpr;
import org.xvm.compiler2.syntax.green.GreenList;
import org.xvm.compiler2.syntax.green.GreenLiteralExpr;
import org.xvm.compiler2.syntax.green.GreenMemberAccessExpr;
import org.xvm.compiler2.syntax.green.GreenNameExpr;
import org.xvm.compiler2.syntax.green.GreenNode;
import org.xvm.compiler2.syntax.green.GreenParenExpr;
import org.xvm.compiler2.syntax.green.GreenReturnStmt;
import org.xvm.compiler2.syntax.green.GreenStatement;
import org.xvm.compiler2.syntax.green.GreenToken;
import org.xvm.compiler2.syntax.green.GreenUnaryExpr;

/**
 * A parser that directly emits immutable green tree nodes.
 * <p>
 * This parser uses the immutable GreenLexer and builds the green tree
 * without any mutable state sharing. This enables:
 * <ul>
 *   <li>Immutable syntax trees</li>
 *   <li>Structural sharing for incremental reparsing</li>
 *   <li>No reflection or mutable state</li>
 *   <li>Suitable for LSP/IDE use</li>
 *   <li>Integrated with CompilationContext for error reporting</li>
 * </ul>
 */
public class GreenParser {

    private final CompilationContext context;
    private GreenLexer lexer;
    private GreenToken current;
    private GreenToken previous;
    private int currentPosition;

    /**
     * Create a parser with a CompilationContext.
     *
     * @param context the compilation context
     */
    public GreenParser(CompilationContext context) {
        this.context = context;
        this.lexer = new GreenLexer(context);
        this.currentPosition = 0;
        advance(); // Prime the pump
    }

    /**
     * Create a parser for the given source code (for testing).
     *
     * @param source the source code
     */
    public GreenParser(String source) {
        this.context = null;
        this.lexer = new GreenLexer(source);
        this.currentPosition = 0;
        advance(); // Prime the pump
    }

    /**
     * @return the compilation context (may be null for testing)
     */
    public CompilationContext getContext() {
        return context;
    }

    // -------------------------------------------------------------------------
    // Token consumption
    // -------------------------------------------------------------------------

    /**
     * Advance to the next token.
     */
    private GreenToken advance() {
        previous = current;
        if (current != null) {
            currentPosition += current.getFullWidth();
        }
        GreenLexer.TokenResult result = lexer.nextToken();
        current = result.token();
        lexer = result.next();
        return previous;
    }

    /**
     * Report an error at the current position.
     */
    private void reportError(String code, String message) {
        if (context != null) {
            int length = current != null ? current.getFullWidth() : 1;
            context.reportError(code, message, currentPosition, length);
        }
    }

    /**
     * Check if the current token matches the given kind.
     */
    private boolean check(SyntaxKind kind) {
        return current != null && current.getKind() == kind;
    }

    /**
     * Check if at end of input.
     */
    private boolean isAtEnd() {
        return current == null || current.getKind() == SyntaxKind.EOF;
    }

    /**
     * Consume the current token if it matches, advance, and return true.
     * Otherwise return false without advancing.
     */
    private boolean match(SyntaxKind kind) {
        if (check(kind)) {
            advance();
            return true;
        }
        return false;
    }

    /**
     * Consume the current token if it matches, or report an error.
     */
    private GreenToken consume(SyntaxKind kind, String message) {
        if (check(kind)) {
            return advance();
        }
        reportError("XP0001", message);
        throw new ParseException(message + " at " + current);
    }

    /**
     * Consume the current token and return it.
     */
    private GreenToken consumeToken() {
        return advance();
    }

    // -------------------------------------------------------------------------
    // Expression parsing (Pratt parser / precedence climbing)
    // -------------------------------------------------------------------------
    //
    // Precedence (lowest to highest):
    // 1. Assignment (=, +=, -=, etc.) - right associative
    // 2. Conditional (?:) - right associative
    // 3. Or (||)
    // 4. And (&&)
    // 5. Bitwise Or (|)
    // 6. Bitwise Xor (^)
    // 7. Bitwise And (&)
    // 8. Equality (==, !=)
    // 9. Comparison (<, <=, >, >=)
    // 10. Shift (<<, >>, >>>)
    // 11. Term (+, -)
    // 12. Factor (*, /, %)
    // 13. Unary (!, -, ~, ++, --)
    // 14. Postfix (a.b, a(), a[i], ++, --)
    // 15. Primary (literals, identifiers, parens)

    /**
     * Parse an expression.
     */
    public GreenExpression parseExpression() {
        return parseAssignment();
    }

    private GreenExpression parseAssignment() {
        GreenExpression left = parseConditional();

        // Check for assignment operators (right-associative)
        if (isAssignmentOperator()) {
            GreenToken op = consumeToken();
            GreenExpression right = parseAssignment(); // Right-associative
            return GreenAssignExpr.create(left, op, right);
        }

        return left;
    }

    private boolean isAssignmentOperator() {
        return check(SyntaxKind.ASSIGN) ||
               check(SyntaxKind.ADD_ASSIGN) ||
               check(SyntaxKind.SUB_ASSIGN) ||
               check(SyntaxKind.MUL_ASSIGN) ||
               check(SyntaxKind.DIV_ASSIGN) ||
               check(SyntaxKind.MOD_ASSIGN);
    }

    private GreenExpression parseConditional() {
        GreenExpression condition = parseOr();

        if (check(SyntaxKind.COND)) {
            GreenToken question = consumeToken();
            GreenExpression thenExpr = parseExpression();
            GreenToken colon = consume(SyntaxKind.COLON, "Expected ':' in conditional expression");
            GreenExpression elseExpr = parseConditional(); // Right-associative
            return GreenConditionalExpr.create(condition, question, thenExpr, colon, elseExpr);
        }

        return condition;
    }

    private GreenExpression parseOr() {
        GreenExpression left = parseAnd();
        while (check(SyntaxKind.OR)) {
            GreenToken op = consumeToken();
            GreenExpression right = parseAnd();
            left = GreenBinaryExpr.create(left, op, right);
        }
        return left;
    }

    private GreenExpression parseAnd() {
        GreenExpression left = parseBitOr();
        while (check(SyntaxKind.AND)) {
            GreenToken op = consumeToken();
            GreenExpression right = parseBitOr();
            left = GreenBinaryExpr.create(left, op, right);
        }
        return left;
    }

    private GreenExpression parseBitOr() {
        GreenExpression left = parseBitXor();
        while (check(SyntaxKind.BIT_OR)) {
            GreenToken op = consumeToken();
            GreenExpression right = parseBitXor();
            left = GreenBinaryExpr.create(left, op, right);
        }
        return left;
    }

    private GreenExpression parseBitXor() {
        GreenExpression left = parseBitAnd();
        while (check(SyntaxKind.BIT_XOR)) {
            GreenToken op = consumeToken();
            GreenExpression right = parseBitAnd();
            left = GreenBinaryExpr.create(left, op, right);
        }
        return left;
    }

    private GreenExpression parseBitAnd() {
        GreenExpression left = parseEquality();
        while (check(SyntaxKind.BIT_AND)) {
            GreenToken op = consumeToken();
            GreenExpression right = parseEquality();
            left = GreenBinaryExpr.create(left, op, right);
        }
        return left;
    }

    private GreenExpression parseEquality() {
        GreenExpression left = parseComparison();
        while (check(SyntaxKind.EQ) || check(SyntaxKind.NEQ)) {
            GreenToken op = consumeToken();
            GreenExpression right = parseComparison();
            left = GreenBinaryExpr.create(left, op, right);
        }
        return left;
    }

    private GreenExpression parseComparison() {
        GreenExpression left = parseShift();
        while (check(SyntaxKind.LT) || check(SyntaxKind.GT) ||
               check(SyntaxKind.LTEQ) || check(SyntaxKind.GTEQ)) {
            GreenToken op = consumeToken();
            GreenExpression right = parseShift();
            left = GreenBinaryExpr.create(left, op, right);
        }
        return left;
    }

    private GreenExpression parseShift() {
        GreenExpression left = parseTerm();
        while (check(SyntaxKind.SHL) || check(SyntaxKind.SHR) || check(SyntaxKind.USHR)) {
            GreenToken op = consumeToken();
            GreenExpression right = parseTerm();
            left = GreenBinaryExpr.create(left, op, right);
        }
        return left;
    }

    private GreenExpression parseTerm() {
        GreenExpression left = parseFactor();
        while (check(SyntaxKind.PLUS) || check(SyntaxKind.MINUS)) {
            GreenToken op = consumeToken();
            GreenExpression right = parseFactor();
            left = GreenBinaryExpr.create(left, op, right);
        }
        return left;
    }

    private GreenExpression parseFactor() {
        GreenExpression left = parseUnary();
        while (check(SyntaxKind.STAR) || check(SyntaxKind.SLASH) || check(SyntaxKind.PERCENT)) {
            GreenToken op = consumeToken();
            GreenExpression right = parseUnary();
            left = GreenBinaryExpr.create(left, op, right);
        }
        return left;
    }

    private GreenExpression parseUnary() {
        if (check(SyntaxKind.NOT) || check(SyntaxKind.MINUS) || check(SyntaxKind.BIT_NOT)) {
            GreenToken op = consumeToken();
            GreenExpression operand = parseUnary();
            return GreenUnaryExpr.prefix(op, operand);
        }
        return parsePostfix();
    }

    private GreenExpression parsePostfix() {
        GreenExpression expr = parsePrimary();

        // Handle postfix operators in a loop
        while (true) {
            if (check(SyntaxKind.DOT)) {
                // Member access: expr.member
                GreenToken dot = consumeToken();
                GreenToken member = consume(SyntaxKind.IDENTIFIER, "Expected member name after '.'");
                expr = GreenMemberAccessExpr.create(expr, dot, member);
            } else if (check(SyntaxKind.LPAREN)) {
                // Invocation: expr(args)
                expr = parseInvocation(expr);
            } else if (check(SyntaxKind.LBRACKET)) {
                // Index access: expr[index]
                GreenToken open = consumeToken();
                GreenExpression index = parseExpression();
                GreenToken close = consume(SyntaxKind.RBRACKET, "Expected ']' after index");
                expr = GreenIndexExpr.create(expr, open, index, close);
            } else {
                break;
            }
        }

        return expr;
    }

    private GreenExpression parseInvocation(GreenExpression target) {
        GreenToken open = consumeToken(); // consume '('

        List<GreenNode> args = new ArrayList<>();
        if (!check(SyntaxKind.RPAREN)) {
            do {
                args.add(parseExpression());
            } while (match(SyntaxKind.COMMA));
        }

        GreenToken close = consume(SyntaxKind.RPAREN, "Expected ')' after arguments");

        GreenList argList = GreenList.create(SyntaxKind.ARGUMENT, args.toArray(new GreenNode[0]));
        return GreenInvokeExpr.create(target, open, argList, close);
    }

    private GreenExpression parsePrimary() {
        // Literals
        if (check(SyntaxKind.INT_LITERAL) || check(SyntaxKind.FP_LITERAL) ||
            check(SyntaxKind.STRING_LITERAL) || check(SyntaxKind.CHAR_LITERAL)) {
            GreenToken lit = consumeToken();
            return GreenLiteralExpr.create(lit);
        }

        // Boolean literals
        if (check(SyntaxKind.KW_TRUE) || check(SyntaxKind.KW_FALSE)) {
            GreenToken lit = consumeToken();
            return GreenLiteralExpr.create(lit);
        }

        // Null literal
        if (check(SyntaxKind.KW_NULL)) {
            GreenToken lit = consumeToken();
            return GreenLiteralExpr.create(lit);
        }

        // Identifier
        if (check(SyntaxKind.IDENTIFIER)) {
            GreenToken name = consumeToken();
            return GreenNameExpr.create(name);
        }

        // Parenthesized expression
        if (check(SyntaxKind.LPAREN)) {
            GreenToken open = consumeToken();
            GreenExpression inner = parseExpression();
            GreenToken close = consume(SyntaxKind.RPAREN, "Expected ')' after expression");
            return GreenParenExpr.create(open, inner, close);
        }

        // Error case
        reportError("XP0002", "Unexpected token: " + (current != null ? current.getKind() : "EOF"));
        throw new ParseException("Unexpected token: " + current);
    }

    // -------------------------------------------------------------------------
    // Statement parsing
    // -------------------------------------------------------------------------

    /**
     * Parse a statement.
     */
    public GreenStatement parseStatement() {
        if (check(SyntaxKind.KW_RETURN)) {
            return parseReturn();
        }
        if (check(SyntaxKind.LBRACE)) {
            return parseBlock();
        }

        // Expression statement
        GreenExpression expr = parseExpression();
        GreenToken semi = consume(SyntaxKind.SEMICOLON, "Expected ';' after expression");
        return GreenExprStmt.create(expr, semi);
    }

    private GreenReturnStmt parseReturn() {
        GreenToken keyword = consumeToken(); // consume 'return'

        if (check(SyntaxKind.SEMICOLON)) {
            GreenToken semi = consumeToken();
            return GreenReturnStmt.create(keyword, null, semi);
        }

        GreenExpression value = parseExpression();
        GreenToken semi = consume(SyntaxKind.SEMICOLON, "Expected ';' after return value");
        return GreenReturnStmt.create(keyword, value, semi);
    }

    private GreenBlockStmt parseBlock() {
        GreenToken open = consumeToken(); // consume '{'

        List<GreenStatement> stmts = new ArrayList<>();
        while (!check(SyntaxKind.RBRACE) && !isAtEnd()) {
            stmts.add(parseStatement());
        }

        GreenToken close = consume(SyntaxKind.RBRACE, "Expected '}' after block");

        return GreenBlockStmt.create(open,
                GreenList.create(
                        SyntaxKind.BLOCK_STATEMENT,
                        stmts.toArray(new GreenStatement[0])),
                close);
    }

    // -------------------------------------------------------------------------
    // Exception
    // -------------------------------------------------------------------------

    /**
     * Parse exception for error handling.
     */
    public static class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }
    }
}
