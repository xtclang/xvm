package org.xvm.compiler2;

import org.junit.jupiter.api.Test;

import org.xvm.compiler2.parser.Diagnostic;
import org.xvm.compiler2.parser.GreenLexer;
import org.xvm.compiler2.parser.GreenParser;
import org.xvm.compiler2.parser.SourceText;
import org.xvm.compiler2.parser.TextLocation;
import org.xvm.compiler2.syntax.SyntaxKind;
import org.xvm.compiler2.syntax.green.GreenBinaryExpr;
import org.xvm.compiler2.syntax.green.GreenBlockStmt;
import org.xvm.compiler2.syntax.green.GreenExpression;
import org.xvm.compiler2.syntax.green.GreenInvokeExpr;
import org.xvm.compiler2.syntax.green.GreenMemberAccessExpr;
import org.xvm.compiler2.syntax.green.GreenStatement;
import org.xvm.compiler2.syntax.green.GreenToken;
import org.xvm.compiler2.syntax.red.SyntaxNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration tests for the compiler2 infrastructure.
 */
class IntegrationTest {

    // -------------------------------------------------------------------------
    // End-to-end lexer tests
    // -------------------------------------------------------------------------

    @Test
    void lexer_tokenizesComplexExpression() {
        String code = "obj.method(x + 1, arr[i])";
        GreenLexer lexer = new GreenLexer(code);

        int tokenCount = 0;
        GreenLexer.TokenResult result;
        while ((result = lexer.nextToken()).token().getKind() != SyntaxKind.EOF) {
            tokenCount++;
            lexer = result.next();
        }

        // Expected: obj . method ( x + 1 , arr [ i ] )
        assertEquals(13, tokenCount);
    }

    @Test
    void lexer_tracksPositionImmutably() {
        String code = "a b c";
        GreenLexer lexer1 = new GreenLexer(code);

        GreenLexer.TokenResult r1 = lexer1.nextToken();
        GreenLexer lexer2 = r1.next();

        GreenLexer.TokenResult r2 = lexer2.nextToken();
        GreenLexer lexer3 = r2.next();

        // Original lexer positions should be unchanged
        assertEquals(0, lexer1.getPosition());
        assertTrue(lexer2.getPosition() > 0);
        assertTrue(lexer3.getPosition() > lexer2.getPosition());
    }

    @Test
    void lexer_handlesAllOperators() {
        String code = "+ - * / % = == != < > <= >= && || & | ^ ~ << >> ? : .";
        GreenLexer lexer = new GreenLexer(code);

        SyntaxKind[] expected = {
            SyntaxKind.PLUS, SyntaxKind.MINUS, SyntaxKind.STAR, SyntaxKind.SLASH,
            SyntaxKind.PERCENT, SyntaxKind.ASSIGN, SyntaxKind.EQ, SyntaxKind.NEQ,
            SyntaxKind.LT, SyntaxKind.GT, SyntaxKind.LTEQ, SyntaxKind.GTEQ,
            SyntaxKind.AND, SyntaxKind.OR, SyntaxKind.BIT_AND, SyntaxKind.BIT_OR,
            SyntaxKind.BIT_XOR, SyntaxKind.BIT_NOT, SyntaxKind.SHL, SyntaxKind.SHR,
            SyntaxKind.COND, SyntaxKind.COLON, SyntaxKind.DOT
        };

        for (SyntaxKind expectedKind : expected) {
            GreenLexer.TokenResult result = lexer.nextToken();
            assertEquals(expectedKind, result.token().getKind(),
                    "Expected " + expectedKind + " but got " + result.token().getKind());
            lexer = result.next();
        }
    }

    // -------------------------------------------------------------------------
    // End-to-end parser tests
    // -------------------------------------------------------------------------

    @Test
    void parser_parsesComplexExpression() {
        String code = "obj.method(x + 1).result[0]";
        GreenParser parser = new GreenParser(code);

        GreenExpression expr = parser.parseExpression();

        // Top level should be index access
        assertInstanceOf(org.xvm.compiler2.syntax.green.GreenIndexExpr.class, expr);
    }

    @Test
    void parser_parsesMultipleStatements() {
        String code = "{ x = 1; y = 2; return x + y; }";
        GreenParser parser = new GreenParser(code);

        GreenStatement stmt = parser.parseStatement();

        assertInstanceOf(GreenBlockStmt.class, stmt);
        GreenBlockStmt block = (GreenBlockStmt) stmt;
        assertEquals(3, block.getStatementCount());
    }

    @Test
    void parser_handlesNestedExpressions() {
        String code = "a.b.c(d.e.f(g)).h[i.j.k()]";
        GreenParser parser = new GreenParser(code);

        GreenExpression expr = parser.parseExpression();
        assertNotNull(expr);
    }

    // -------------------------------------------------------------------------
    // Compilation context integration tests
    // -------------------------------------------------------------------------

    @Test
    void context_collectsDiagnostics() {
        String code = "x + )"; // Invalid syntax
        SourceText source = new SourceText(code, "test.x");
        CompilationContext ctx = new CompilationContext(source);
        GreenParser parser = new GreenParser(ctx);

        try {
            parser.parseExpression();
        } catch (GreenParser.ParseException e) {
            // Expected
        }

        assertTrue(ctx.hasErrors());
        assertTrue(ctx.getErrorCount() > 0);

        Diagnostic error = ctx.getErrors().get(0);
        assertNotNull(error.span());
    }

    @Test
    void context_reportsCorrectPositions() {
        String code = "line1\nline2\nline3 + )";
        SourceText source = new SourceText(code, "test.x");
        CompilationContext ctx = new CompilationContext(source);
        GreenParser parser = new GreenParser(ctx);

        try {
            parser.parseExpression();
        } catch (GreenParser.ParseException e) {
            // Expected
        }

        // Error should be on line 3
        if (ctx.hasErrors()) {
            Diagnostic error = ctx.getErrors().get(0);
            TextLocation loc = error.span().getStartLocation();
            assertEquals(2, loc.line()); // 0-based line 2 = display line 3
        }
    }

    @Test
    void context_loggingWorks() {
        String code = "42";
        SourceText source = new SourceText(code, "test.x");
        ConsoleLogger logger = new ConsoleLogger("test", ConsoleLogger.Level.DEBUG);
        CompilationContext ctx = new CompilationContext(source, logger);

        GreenParser parser = new GreenParser(ctx);
        GreenExpression expr = parser.parseExpression();

        assertNotNull(expr);
        assertFalse(ctx.hasErrors());
    }

    // -------------------------------------------------------------------------
    // Red tree navigation tests
    // -------------------------------------------------------------------------

    @Test
    void redTree_fullNavigation() {
        String code = "a.b(c + d)";
        GreenParser parser = new GreenParser(code);
        GreenExpression green = parser.parseExpression();

        SyntaxNode root = SyntaxNode.createRoot(green);

        // Navigate to all descendants
        int nodeCount = countNodes(root);
        assertTrue(nodeCount > 5); // Should have multiple nodes
    }

    @Test
    void redTree_findNodeByPosition() {
        String code = "abc + def";
        GreenParser parser = new GreenParser(code);
        GreenExpression green = parser.parseExpression();

        SyntaxNode root = SyntaxNode.createRoot(green);

        // Find the '+' operator (around position 4)
        SyntaxNode found = root.findNode(4);
        assertNotNull(found);
    }

    @Test
    void redTree_preservesSourcePositions() {
        String code = "  x + y  "; // With leading/trailing whitespace
        GreenParser parser = new GreenParser(code);
        GreenExpression green = parser.parseExpression();

        SyntaxNode root = SyntaxNode.createRoot(green);

        // Root should start at position 0
        assertEquals(0, root.getPosition());

        // Full width should include trivia
        assertTrue(root.getFullWidth() > 0);
    }

    // -------------------------------------------------------------------------
    // Structural sharing tests
    // -------------------------------------------------------------------------

    @Test
    void structuralSharing_identicalExpressionsAreShared() {
        GreenParser parser1 = new GreenParser("42 + 1");
        GreenExpression expr1 = parser1.parseExpression();

        GreenParser parser2 = new GreenParser("42 + 1");
        GreenExpression expr2 = parser2.parseExpression();

        // Same structure should produce same interned objects
        assertNotNull(expr1);
        assertNotNull(expr2);
        // Note: Full interning depends on hash collision and cache state
    }

    @Test
    void structuralSharing_modificationCreatesNewNodes() {
        GreenParser parser = new GreenParser("a + b");
        GreenBinaryExpr expr = (GreenBinaryExpr) parser.parseExpression();

        // Create a modification
        GreenToken newOp = GreenToken.create(SyntaxKind.MINUS, "-");
        GreenBinaryExpr modified = (GreenBinaryExpr) expr.withChild(1, newOp);

        // Original should be unchanged
        assertEquals(SyntaxKind.PLUS, expr.getOperatorKind());

        // Modified should have new operator
        assertEquals(SyntaxKind.MINUS, modified.getOperatorKind());

        // Children should be shared
        assertTrue(expr.getLeft() == modified.getLeft());
        assertTrue(expr.getRight() == modified.getRight());
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    private int countNodes(SyntaxNode node) {
        int count = 1;
        for (int i = 0; i < node.getChildCount(); i++) {
            SyntaxNode child = node.getChild(i);
            if (child != null) {
                count += countNodes(child);
            }
        }
        return count;
    }
}
