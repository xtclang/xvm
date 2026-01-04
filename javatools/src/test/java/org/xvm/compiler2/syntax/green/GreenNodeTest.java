package org.xvm.compiler2.syntax.green;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.xvm.compiler2.syntax.SyntaxKind;
import org.xvm.compiler2.syntax.red.SyntaxNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the green node foundation of compiler2.
 */
class GreenNodeTest {

    @BeforeEach
    void setUp() {
        // Clear intern cache between tests for isolation
        GreenNode.clearInternCache();
    }

    // -------------------------------------------------------------------------
    // Token tests
    // -------------------------------------------------------------------------

    @Test
    void token_create_hasCorrectProperties() {
        GreenToken token = GreenToken.create(SyntaxKind.PLUS, "+");

        assertEquals(SyntaxKind.PLUS, token.getKind());
        assertEquals("+", token.getText());
        assertEquals(1, token.getFullWidth());
        assertTrue(token.isToken());
        assertEquals(0, token.getChildCount());
    }

    @Test
    void token_intLiteral_hasValue() {
        GreenToken token = GreenToken.intLiteral(42);

        assertEquals(SyntaxKind.INT_LITERAL, token.getKind());
        assertEquals("42", token.getText());
        assertEquals(Long.valueOf(42), token.getIntValue());
    }

    @Test
    void token_withTrivia_includesTriviaInWidth() {
        GreenToken token = GreenToken.identifier("foo", " ", "  ");

        assertEquals("foo", token.getText());
        assertEquals(3, token.getTextWidth());
        assertEquals(6, token.getFullWidth()); // " foo  "
    }

    @Test
    void token_areInterned() {
        GreenToken a = GreenToken.intLiteral(42);
        GreenToken b = GreenToken.intLiteral(42);

        assertSame(a, b, "Same value should return same object");
    }

    // -------------------------------------------------------------------------
    // Expression tests
    // -------------------------------------------------------------------------

    @Test
    void literalExpr_create_wrapsToken() {
        GreenLiteralExpr expr = GreenLiteralExpr.intLiteral(123);

        assertEquals(SyntaxKind.LITERAL_EXPRESSION, expr.getKind());
        assertEquals(Long.valueOf(123), expr.getValue());
        assertEquals(1, expr.getChildCount());
        assertEquals(3, expr.getFullWidth()); // "123"
    }

    @Test
    void nameExpr_create_hasIdentifier() {
        GreenNameExpr expr = GreenNameExpr.create("x");

        assertEquals(SyntaxKind.NAME_EXPRESSION, expr.getKind());
        assertEquals("x", expr.getName());
    }

    @Test
    void binaryExpr_create_hasThreeChildren() {
        GreenExpression left = GreenLiteralExpr.intLiteral(1);
        GreenToken op = GreenToken.create(SyntaxKind.PLUS, "+");
        GreenExpression right = GreenLiteralExpr.intLiteral(2);

        GreenBinaryExpr expr = GreenBinaryExpr.create(left, op, right);

        assertEquals(SyntaxKind.BINARY_EXPRESSION, expr.getKind());
        assertEquals(3, expr.getChildCount());
        assertSame(left, expr.getLeft());
        assertSame(op, expr.getOperator());
        assertSame(right, expr.getRight());
        assertEquals(3, expr.getFullWidth()); // "1+2"
    }

    // -------------------------------------------------------------------------
    // Structural sharing tests
    // -------------------------------------------------------------------------

    @Test
    void binaryExpr_withChild_returnsSameIfUnchanged() {
        GreenExpression left = GreenLiteralExpr.intLiteral(1);
        GreenToken op = GreenToken.create(SyntaxKind.PLUS, "+");
        GreenExpression right = GreenLiteralExpr.intLiteral(2);
        GreenBinaryExpr expr = GreenBinaryExpr.create(left, op, right);

        // Replace with same child - should return same node
        GreenNode result = expr.withChild(0, left);

        assertSame(expr, result, "withChild should return same node if child unchanged");
    }

    @Test
    void binaryExpr_withChild_createsNewIfChanged() {
        GreenExpression left = GreenLiteralExpr.intLiteral(1);
        GreenToken op = GreenToken.create(SyntaxKind.PLUS, "+");
        GreenExpression right = GreenLiteralExpr.intLiteral(2);
        GreenBinaryExpr expr = GreenBinaryExpr.create(left, op, right);

        GreenExpression newLeft = GreenLiteralExpr.intLiteral(99);
        GreenBinaryExpr modified = (GreenBinaryExpr) expr.withChild(0, newLeft);

        assertNotSame(expr, modified, "withChild should create new node if changed");
        assertSame(newLeft, modified.getLeft(), "New left should be the replacement");
        assertSame(op, modified.getOperator(), "Operator should be shared");
        assertSame(right, modified.getRight(), "Right should be shared");
    }

    @Test
    void structuralSharing_onEdit() {
        // Build: (1 + 2) * 3
        GreenExpression one = GreenLiteralExpr.intLiteral(1);
        GreenExpression two = GreenLiteralExpr.intLiteral(2);
        GreenExpression three = GreenLiteralExpr.intLiteral(3);
        GreenToken plus = GreenToken.create(SyntaxKind.PLUS, "+");
        GreenToken star = GreenToken.create(SyntaxKind.STAR, "*");

        GreenBinaryExpr add = GreenBinaryExpr.create(one, plus, two);
        GreenBinaryExpr mul = GreenBinaryExpr.create(add, star, three);

        // Edit: change 2 to 5 -> (1 + 5) * 3
        GreenExpression five = GreenLiteralExpr.intLiteral(5);
        GreenBinaryExpr newAdd = (GreenBinaryExpr) add.withChild(2, five);
        GreenBinaryExpr newMul = (GreenBinaryExpr) mul.withChild(0, newAdd);

        // Verify structural sharing
        assertNotSame(mul, newMul);
        assertNotSame(add, newAdd);
        assertSame(one, newAdd.getLeft(), "1 should be shared");
        assertSame(plus, newAdd.getOperator(), "+ should be shared");
        assertSame(star, newMul.getOperator(), "* should be shared");
        assertSame(three, newMul.getRight(), "3 should be shared");
    }

    // -------------------------------------------------------------------------
    // Red tree tests
    // -------------------------------------------------------------------------

    @Test
    void syntaxNode_wrapsGreen() {
        GreenBinaryExpr green = GreenBinaryExpr.create(
                GreenLiteralExpr.intLiteral(1),
                GreenToken.create(SyntaxKind.PLUS, "+"),
                GreenLiteralExpr.intLiteral(2));

        SyntaxNode red = SyntaxNode.createRoot(green);

        assertEquals(SyntaxKind.BINARY_EXPRESSION, red.getKind());
        assertSame(green, red.getGreen());
        assertEquals(0, red.getPosition());
        assertEquals(3, red.getFullWidth());
    }

    @Test
    void syntaxNode_providesParentNavigation() {
        GreenBinaryExpr green = GreenBinaryExpr.create(
                GreenLiteralExpr.intLiteral(1),
                GreenToken.create(SyntaxKind.PLUS, "+"),
                GreenLiteralExpr.intLiteral(2));

        SyntaxNode root = SyntaxNode.createRoot(green);
        SyntaxNode leftExpr = root.getChild(0);
        SyntaxNode leftToken = leftExpr.getChild(0);

        assertSame(root, leftExpr.getParent());
        assertSame(leftExpr, leftToken.getParent());
    }

    @Test
    void syntaxNode_computesPositions() {
        // "1+2" with trivia " " before the +
        GreenExpression left = GreenLiteralExpr.intLiteral(1);
        GreenToken plus = GreenToken.create(SyntaxKind.PLUS, "+", " ", "");
        GreenExpression right = GreenLiteralExpr.intLiteral(2);
        GreenBinaryExpr green = GreenBinaryExpr.create(left, plus, right);

        SyntaxNode root = SyntaxNode.createRoot(green);

        assertEquals(0, root.getPosition());
        assertEquals(0, root.getChild(0).getPosition()); // "1" at 0
        assertEquals(1, root.getChild(1).getPosition()); // " +" at 1
        assertEquals(3, root.getChild(2).getPosition()); // "2" at 3
    }

    @Test
    void syntaxNode_findNode_findsDeepest() {
        GreenBinaryExpr green = GreenBinaryExpr.create(
                GreenLiteralExpr.intLiteral(1),
                GreenToken.create(SyntaxKind.PLUS, "+"),
                GreenLiteralExpr.intLiteral(2));

        SyntaxNode root = SyntaxNode.createRoot(green);

        // Position 0 should find the "1" literal token
        SyntaxNode found = root.findNode(0);
        assertTrue(found.isToken());
        assertEquals(SyntaxKind.INT_LITERAL, found.getKind());

        // Position 1 should find the "+" token
        found = root.findNode(1);
        assertTrue(found.isToken());
        assertEquals(SyntaxKind.PLUS, found.getKind());
    }

    // -------------------------------------------------------------------------
    // Width calculation tests
    // -------------------------------------------------------------------------

    @Test
    void complexExpression_hasCorrectWidth() {
        // Build: a + b * c
        // With spaces: "a + b * c" = 9 characters
        GreenExpression a = GreenNameExpr.create(GreenToken.identifier("a", "", " "));
        GreenExpression b = GreenNameExpr.create(GreenToken.identifier("b", "", " "));
        GreenExpression c = GreenNameExpr.create(GreenToken.identifier("c"));
        GreenToken plus = GreenToken.create(SyntaxKind.PLUS, "+", "", " ");
        GreenToken star = GreenToken.create(SyntaxKind.STAR, "*", "", " ");

        GreenBinaryExpr mul = GreenBinaryExpr.create(b, star, c);
        GreenBinaryExpr add = GreenBinaryExpr.create(a, plus, mul);

        assertEquals(9, add.getFullWidth());
    }
}
