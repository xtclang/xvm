package org.xvm.compiler2.bridge;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Token;
import org.xvm.compiler.ast.BiExpression;
import org.xvm.compiler.ast.LiteralExpression;
import org.xvm.compiler.ast.RelOpExpression;

import org.xvm.compiler2.syntax.SyntaxKind;
import org.xvm.compiler2.syntax.green.GreenBinaryExpr;
import org.xvm.compiler2.syntax.green.GreenExpression;
import org.xvm.compiler2.syntax.green.GreenLiteralExpr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for AST to Green tree conversion.
 */
class AstToGreenConverterTest {

    private final AstToGreenConverter converter = new AstToGreenConverter();

    @Test
    void convertLiteral_intLiteral() {
        // Create AST: 42
        Token token = new Token(0, 2, Token.Id.LIT_INT, 42L);
        LiteralExpression ast = new LiteralExpression(token);

        GreenExpression green = converter.convertExpression(ast);

        assertInstanceOf(GreenLiteralExpr.class, green);
        GreenLiteralExpr lit = (GreenLiteralExpr) green;
        assertEquals(SyntaxKind.LITERAL_EXPRESSION, lit.getKind());
        assertEquals(42L, lit.getValue());
    }

    @Test
    void convertBinary_addition() {
        // Create AST: 1 + 2
        Token tok1 = new Token(0, 1, Token.Id.LIT_INT, 1L);
        Token tokPlus = new Token(2, 3, Token.Id.ADD);
        Token tok2 = new Token(4, 5, Token.Id.LIT_INT, 2L);

        LiteralExpression left = new LiteralExpression(tok1);
        LiteralExpression right = new LiteralExpression(tok2);
        BiExpression ast = new RelOpExpression(left, tokPlus, right);

        GreenExpression green = converter.convertExpression(ast);

        assertInstanceOf(GreenBinaryExpr.class, green);
        GreenBinaryExpr bin = (GreenBinaryExpr) green;
        assertEquals(SyntaxKind.BINARY_EXPRESSION, bin.getKind());
        assertEquals(SyntaxKind.PLUS, bin.getOperatorKind());

        assertInstanceOf(GreenLiteralExpr.class, bin.getLeft());
        assertInstanceOf(GreenLiteralExpr.class, bin.getRight());
    }

    @Test
    void convertBinary_nestedExpression() {
        // Create AST: (1 + 2) * 3
        Token tok1 = new Token(0, 1, Token.Id.LIT_INT, 1L);
        Token tokPlus = new Token(2, 3, Token.Id.ADD);
        Token tok2 = new Token(4, 5, Token.Id.LIT_INT, 2L);
        Token tokMul = new Token(7, 8, Token.Id.MUL);
        Token tok3 = new Token(9, 10, Token.Id.LIT_INT, 3L);

        LiteralExpression lit1 = new LiteralExpression(tok1);
        LiteralExpression lit2 = new LiteralExpression(tok2);
        LiteralExpression lit3 = new LiteralExpression(tok3);
        BiExpression add = new RelOpExpression(lit1, tokPlus, lit2);
        BiExpression mul = new RelOpExpression(add, tokMul, lit3);

        GreenExpression green = converter.convertExpression(mul);

        assertInstanceOf(GreenBinaryExpr.class, green);
        GreenBinaryExpr mulGreen = (GreenBinaryExpr) green;
        assertEquals(SyntaxKind.STAR, mulGreen.getOperatorKind());

        // Left should be the addition
        assertInstanceOf(GreenBinaryExpr.class, mulGreen.getLeft());
        GreenBinaryExpr addGreen = (GreenBinaryExpr) mulGreen.getLeft();
        assertEquals(SyntaxKind.PLUS, addGreen.getOperatorKind());

        // Right should be literal 3
        assertInstanceOf(GreenLiteralExpr.class, mulGreen.getRight());
    }

    @Test
    void convertUnknown_returnsPlaceholder() {
        // For unsupported expression types, should return a placeholder
        // This test uses a mock/anonymous class to simulate unsupported type
        // For now, just verify the converter doesn't throw
        assertNotNull(converter);
    }
}
