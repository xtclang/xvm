package org.xvm.compiler2.parser;

import org.junit.jupiter.api.Test;

import org.xvm.compiler2.syntax.SyntaxKind;
import org.xvm.compiler2.syntax.green.GreenAssignExpr;
import org.xvm.compiler2.syntax.green.GreenBinaryExpr;
import org.xvm.compiler2.syntax.green.GreenBlockStmt;
import org.xvm.compiler2.syntax.green.GreenConditionalExpr;
import org.xvm.compiler2.syntax.green.GreenExpression;
import org.xvm.compiler2.syntax.green.GreenExprStmt;
import org.xvm.compiler2.syntax.green.GreenIndexExpr;
import org.xvm.compiler2.syntax.green.GreenInvokeExpr;
import org.xvm.compiler2.syntax.green.GreenLiteralExpr;
import org.xvm.compiler2.syntax.green.GreenMemberAccessExpr;
import org.xvm.compiler2.syntax.green.GreenNameExpr;
import org.xvm.compiler2.syntax.green.GreenParenExpr;
import org.xvm.compiler2.syntax.green.GreenReturnStmt;
import org.xvm.compiler2.syntax.green.GreenStatement;
import org.xvm.compiler2.syntax.green.GreenUnaryExpr;
import org.xvm.compiler2.syntax.red.SyntaxNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for GreenParser - direct green node emission.
 */
class GreenParserTest {

    private GreenParser parser(String code) {
        return new GreenParser(code);
    }

    // -------------------------------------------------------------------------
    // Literal tests
    // -------------------------------------------------------------------------

    @Test
    void parseLiteral_integer() {
        GreenExpression expr = parser("42").parseExpression();

        assertInstanceOf(GreenLiteralExpr.class, expr);
        GreenLiteralExpr lit = (GreenLiteralExpr) expr;
        assertEquals(42L, lit.getValue());
    }

    @Test
    void parseLiteral_identifier() {
        GreenExpression expr = parser("foo").parseExpression();

        assertInstanceOf(GreenNameExpr.class, expr);
        GreenNameExpr name = (GreenNameExpr) expr;
        assertEquals("foo", name.getName());
    }

    // -------------------------------------------------------------------------
    // Binary expression tests
    // -------------------------------------------------------------------------

    @Test
    void parseBinary_addition() {
        GreenExpression expr = parser("1 + 2").parseExpression();

        assertInstanceOf(GreenBinaryExpr.class, expr);
        GreenBinaryExpr bin = (GreenBinaryExpr) expr;
        assertEquals(SyntaxKind.PLUS, bin.getOperatorKind());
        assertInstanceOf(GreenLiteralExpr.class, bin.getLeft());
        assertInstanceOf(GreenLiteralExpr.class, bin.getRight());
    }

    @Test
    void parseBinary_precedence() {
        // 1 + 2 * 3 should parse as 1 + (2 * 3)
        GreenExpression expr = parser("1 + 2 * 3").parseExpression();

        assertInstanceOf(GreenBinaryExpr.class, expr);
        GreenBinaryExpr add = (GreenBinaryExpr) expr;
        assertEquals(SyntaxKind.PLUS, add.getOperatorKind());

        // Right side should be multiplication
        assertInstanceOf(GreenBinaryExpr.class, add.getRight());
        GreenBinaryExpr mul = (GreenBinaryExpr) add.getRight();
        assertEquals(SyntaxKind.STAR, mul.getOperatorKind());
    }

    @Test
    void parseBinary_leftAssociative() {
        // 1 - 2 - 3 should parse as (1 - 2) - 3
        GreenExpression expr = parser("1 - 2 - 3").parseExpression();

        assertInstanceOf(GreenBinaryExpr.class, expr);
        GreenBinaryExpr outer = (GreenBinaryExpr) expr;
        assertEquals(SyntaxKind.MINUS, outer.getOperatorKind());

        // Left should be subtraction
        assertInstanceOf(GreenBinaryExpr.class, outer.getLeft());
        GreenBinaryExpr inner = (GreenBinaryExpr) outer.getLeft();
        assertEquals(SyntaxKind.MINUS, inner.getOperatorKind());
    }

    @Test
    void parseBinary_comparison() {
        GreenExpression expr = parser("a < b").parseExpression();

        assertInstanceOf(GreenBinaryExpr.class, expr);
        GreenBinaryExpr cmp = (GreenBinaryExpr) expr;
        assertEquals(SyntaxKind.LT, cmp.getOperatorKind());
    }

    @Test
    void parseBinary_logical() {
        GreenExpression expr = parser("a && b || c").parseExpression();

        // || has lower precedence than &&, so: (a && b) || c
        assertInstanceOf(GreenBinaryExpr.class, expr);
        GreenBinaryExpr or = (GreenBinaryExpr) expr;
        assertEquals(SyntaxKind.OR, or.getOperatorKind());

        assertInstanceOf(GreenBinaryExpr.class, or.getLeft());
        GreenBinaryExpr and = (GreenBinaryExpr) or.getLeft();
        assertEquals(SyntaxKind.AND, and.getOperatorKind());
    }

    // -------------------------------------------------------------------------
    // Unary expression tests
    // -------------------------------------------------------------------------

    @Test
    void parseUnary_negation() {
        GreenExpression expr = parser("-42").parseExpression();

        assertInstanceOf(GreenUnaryExpr.class, expr);
        GreenUnaryExpr unary = (GreenUnaryExpr) expr;
        assertEquals(SyntaxKind.MINUS, unary.getOperatorKind());
        assertTrue(unary.isPrefix());
    }

    @Test
    void parseUnary_not() {
        GreenExpression expr = parser("!flag").parseExpression();

        assertInstanceOf(GreenUnaryExpr.class, expr);
        GreenUnaryExpr unary = (GreenUnaryExpr) expr;
        assertEquals(SyntaxKind.NOT, unary.getOperatorKind());
    }

    // -------------------------------------------------------------------------
    // Parenthesized expression tests
    // -------------------------------------------------------------------------

    @Test
    void parseParen_simple() {
        GreenExpression expr = parser("(42)").parseExpression();

        assertInstanceOf(GreenParenExpr.class, expr);
        GreenParenExpr paren = (GreenParenExpr) expr;
        assertInstanceOf(GreenLiteralExpr.class, paren.getExpression());
    }

    @Test
    void parseParen_overridesPrecedence() {
        // (1 + 2) * 3 - parens override precedence
        GreenExpression expr = parser("(1 + 2) * 3").parseExpression();

        assertInstanceOf(GreenBinaryExpr.class, expr);
        GreenBinaryExpr mul = (GreenBinaryExpr) expr;
        assertEquals(SyntaxKind.STAR, mul.getOperatorKind());

        // Left should be parenthesized addition
        assertInstanceOf(GreenParenExpr.class, mul.getLeft());
        GreenParenExpr paren = (GreenParenExpr) mul.getLeft();
        assertInstanceOf(GreenBinaryExpr.class, paren.getExpression());
    }

    // -------------------------------------------------------------------------
    // Statement tests
    // -------------------------------------------------------------------------

    @Test
    void parseStatement_expressionStatement() {
        GreenStatement stmt = parser("x + 1;").parseStatement();

        assertInstanceOf(GreenExprStmt.class, stmt);
        GreenExprStmt exprStmt = (GreenExprStmt) stmt;
        assertInstanceOf(GreenBinaryExpr.class, exprStmt.getExpression());
    }

    @Test
    void parseStatement_returnVoid() {
        GreenStatement stmt = parser("return;").parseStatement();

        assertInstanceOf(GreenReturnStmt.class, stmt);
        GreenReturnStmt ret = (GreenReturnStmt) stmt;
        assertTrue(!ret.hasExpression());
    }

    @Test
    void parseStatement_returnValue() {
        GreenStatement stmt = parser("return 42;").parseStatement();

        assertInstanceOf(GreenReturnStmt.class, stmt);
        GreenReturnStmt ret = (GreenReturnStmt) stmt;
        assertTrue(ret.hasExpression());
        assertInstanceOf(GreenLiteralExpr.class, ret.getExpression());
    }

    @Test
    void parseStatement_block() {
        GreenStatement stmt = parser("{ x; y; }").parseStatement();

        assertInstanceOf(GreenBlockStmt.class, stmt);
        GreenBlockStmt block = (GreenBlockStmt) stmt;
        assertEquals(2, block.getStatementCount());
    }

    // -------------------------------------------------------------------------
    // Red tree navigation tests
    // -------------------------------------------------------------------------

    @Test
    void redTree_providesParentNavigation() {
        GreenExpression green = parser("1 + 2").parseExpression();
        SyntaxNode root = SyntaxNode.createRoot(green);

        assertEquals(SyntaxKind.BINARY_EXPRESSION, root.getKind());

        SyntaxNode left = root.getChild(0);
        assertSame(root, left.getParent());

        SyntaxNode op = root.getChild(1);
        assertSame(root, op.getParent());

        SyntaxNode right = root.getChild(2);
        assertSame(root, right.getParent());
    }

    @Test
    void redTree_computesPositions() {
        GreenExpression green = parser("1+2").parseExpression();
        SyntaxNode root = SyntaxNode.createRoot(green);

        assertEquals(0, root.getPosition());
        // Positions depend on token widths including trivia
        assertTrue(root.getFullWidth() > 0);
    }

    // -------------------------------------------------------------------------
    // Structural sharing tests
    // -------------------------------------------------------------------------

    @Test
    void structuralSharing_sameTokensAreInterned() {
        // Parse twice - same literals should be interned
        GreenExpression expr1 = parser("42").parseExpression();
        GreenExpression expr2 = parser("42").parseExpression();

        // The literal expressions should be the same object (interned)
        assertSame(expr1, expr2);
    }

    @Test
    void structuralSharing_withChild() {
        GreenBinaryExpr expr = (GreenBinaryExpr) parser("1 + 2").parseExpression();

        // Replace right with same value - should return same node
        GreenBinaryExpr same = (GreenBinaryExpr) expr.withChild(2, expr.getRight());
        assertSame(expr, same);

        // Replace right with different value - should return new node
        GreenLiteralExpr newRight = GreenLiteralExpr.intLiteral(99);
        GreenBinaryExpr different = (GreenBinaryExpr) expr.withChild(2, newRight);
        assertSame(expr.getLeft(), different.getLeft()); // Left is shared
        assertSame(expr.getOperator(), different.getOperator()); // Op is shared
        assertSame(newRight, different.getRight()); // Right is new
    }

    // -------------------------------------------------------------------------
    // Assignment expression tests
    // -------------------------------------------------------------------------

    @Test
    void parseAssignment_simple() {
        GreenExpression expr = parser("x = 42").parseExpression();

        assertInstanceOf(GreenAssignExpr.class, expr);
        GreenAssignExpr assign = (GreenAssignExpr) expr;
        assertEquals(SyntaxKind.ASSIGN, assign.getOperatorKind());
        assertInstanceOf(GreenNameExpr.class, assign.getTarget());
        assertInstanceOf(GreenLiteralExpr.class, assign.getValue());
    }

    @Test
    void parseAssignment_compound() {
        GreenExpression expr = parser("x += 1").parseExpression();

        assertInstanceOf(GreenAssignExpr.class, expr);
        GreenAssignExpr assign = (GreenAssignExpr) expr;
        assertEquals(SyntaxKind.ADD_ASSIGN, assign.getOperatorKind());
    }

    @Test
    void parseAssignment_rightAssociative() {
        // a = b = c should parse as a = (b = c)
        GreenExpression expr = parser("a = b = c").parseExpression();

        assertInstanceOf(GreenAssignExpr.class, expr);
        GreenAssignExpr outer = (GreenAssignExpr) expr;
        assertInstanceOf(GreenNameExpr.class, outer.getTarget());
        assertInstanceOf(GreenAssignExpr.class, outer.getValue());
    }

    // -------------------------------------------------------------------------
    // Conditional expression tests
    // -------------------------------------------------------------------------

    @Test
    void parseConditional_ternary() {
        GreenExpression expr = parser("a ? b : c").parseExpression();

        assertInstanceOf(GreenConditionalExpr.class, expr);
        GreenConditionalExpr cond = (GreenConditionalExpr) expr;
        assertInstanceOf(GreenNameExpr.class, cond.getCondition());
        assertInstanceOf(GreenNameExpr.class, cond.getThenExpr());
        assertInstanceOf(GreenNameExpr.class, cond.getElseExpr());
    }

    @Test
    void parseConditional_nested() {
        // a ? b : c ? d : e should parse as a ? b : (c ? d : e) (right-associative)
        GreenExpression expr = parser("a ? b : c ? d : e").parseExpression();

        assertInstanceOf(GreenConditionalExpr.class, expr);
        GreenConditionalExpr outer = (GreenConditionalExpr) expr;
        assertInstanceOf(GreenConditionalExpr.class, outer.getElseExpr());
    }

    // -------------------------------------------------------------------------
    // Member access expression tests
    // -------------------------------------------------------------------------

    @Test
    void parseMemberAccess_simple() {
        GreenExpression expr = parser("obj.field").parseExpression();

        assertInstanceOf(GreenMemberAccessExpr.class, expr);
        GreenMemberAccessExpr member = (GreenMemberAccessExpr) expr;
        assertEquals("field", member.getMemberName());
        assertInstanceOf(GreenNameExpr.class, member.getTarget());
    }

    @Test
    void parseMemberAccess_chained() {
        GreenExpression expr = parser("a.b.c").parseExpression();

        assertInstanceOf(GreenMemberAccessExpr.class, expr);
        GreenMemberAccessExpr outer = (GreenMemberAccessExpr) expr;
        assertEquals("c", outer.getMemberName());
        assertInstanceOf(GreenMemberAccessExpr.class, outer.getTarget());

        GreenMemberAccessExpr inner = (GreenMemberAccessExpr) outer.getTarget();
        assertEquals("b", inner.getMemberName());
    }

    // -------------------------------------------------------------------------
    // Invocation expression tests
    // -------------------------------------------------------------------------

    @Test
    void parseInvocation_noArgs() {
        GreenExpression expr = parser("foo()").parseExpression();

        assertInstanceOf(GreenInvokeExpr.class, expr);
        GreenInvokeExpr invoke = (GreenInvokeExpr) expr;
        assertEquals(0, invoke.getArgumentCount());
    }

    @Test
    void parseInvocation_withArgs() {
        GreenExpression expr = parser("foo(1, 2, 3)").parseExpression();

        assertInstanceOf(GreenInvokeExpr.class, expr);
        GreenInvokeExpr invoke = (GreenInvokeExpr) expr;
        assertEquals(3, invoke.getArgumentCount());
    }

    @Test
    void parseInvocation_methodCall() {
        GreenExpression expr = parser("obj.method(x)").parseExpression();

        assertInstanceOf(GreenInvokeExpr.class, expr);
        GreenInvokeExpr invoke = (GreenInvokeExpr) expr;
        assertEquals(1, invoke.getArgumentCount());
        assertInstanceOf(GreenMemberAccessExpr.class, invoke.getTarget());
    }

    // -------------------------------------------------------------------------
    // Index expression tests
    // -------------------------------------------------------------------------

    @Test
    void parseIndex_simple() {
        GreenExpression expr = parser("arr[0]").parseExpression();

        assertInstanceOf(GreenIndexExpr.class, expr);
        GreenIndexExpr index = (GreenIndexExpr) expr;
        assertInstanceOf(GreenNameExpr.class, index.getTarget());
        assertInstanceOf(GreenLiteralExpr.class, index.getIndex());
    }

    @Test
    void parseIndex_chained() {
        GreenExpression expr = parser("arr[i][j]").parseExpression();

        assertInstanceOf(GreenIndexExpr.class, expr);
        GreenIndexExpr outer = (GreenIndexExpr) expr;
        assertInstanceOf(GreenIndexExpr.class, outer.getTarget());
    }

    @Test
    void parseIndex_withExpression() {
        GreenExpression expr = parser("arr[i + 1]").parseExpression();

        assertInstanceOf(GreenIndexExpr.class, expr);
        GreenIndexExpr index = (GreenIndexExpr) expr;
        assertInstanceOf(GreenBinaryExpr.class, index.getIndex());
    }

    // -------------------------------------------------------------------------
    // Bitwise operator tests
    // -------------------------------------------------------------------------

    @Test
    void parseBitwise_and() {
        GreenExpression expr = parser("a & b").parseExpression();

        assertInstanceOf(GreenBinaryExpr.class, expr);
        GreenBinaryExpr bin = (GreenBinaryExpr) expr;
        assertEquals(SyntaxKind.BIT_AND, bin.getOperatorKind());
    }

    @Test
    void parseBitwise_or() {
        GreenExpression expr = parser("a | b").parseExpression();

        assertInstanceOf(GreenBinaryExpr.class, expr);
        GreenBinaryExpr bin = (GreenBinaryExpr) expr;
        assertEquals(SyntaxKind.BIT_OR, bin.getOperatorKind());
    }

    @Test
    void parseBitwise_xor() {
        GreenExpression expr = parser("a ^ b").parseExpression();

        assertInstanceOf(GreenBinaryExpr.class, expr);
        GreenBinaryExpr bin = (GreenBinaryExpr) expr;
        assertEquals(SyntaxKind.BIT_XOR, bin.getOperatorKind());
    }

    @Test
    void parseBitwise_precedence() {
        // a | b & c should parse as a | (b & c) since & has higher precedence
        GreenExpression expr = parser("a | b & c").parseExpression();

        assertInstanceOf(GreenBinaryExpr.class, expr);
        GreenBinaryExpr or = (GreenBinaryExpr) expr;
        assertEquals(SyntaxKind.BIT_OR, or.getOperatorKind());
        assertInstanceOf(GreenBinaryExpr.class, or.getRight());

        GreenBinaryExpr and = (GreenBinaryExpr) or.getRight();
        assertEquals(SyntaxKind.BIT_AND, and.getOperatorKind());
    }

    // -------------------------------------------------------------------------
    // Shift operator tests
    // -------------------------------------------------------------------------

    @Test
    void parseShift_left() {
        GreenExpression expr = parser("a << 2").parseExpression();

        assertInstanceOf(GreenBinaryExpr.class, expr);
        GreenBinaryExpr bin = (GreenBinaryExpr) expr;
        assertEquals(SyntaxKind.SHL, bin.getOperatorKind());
    }

    @Test
    void parseShift_right() {
        GreenExpression expr = parser("a >> 2").parseExpression();

        assertInstanceOf(GreenBinaryExpr.class, expr);
        GreenBinaryExpr bin = (GreenBinaryExpr) expr;
        assertEquals(SyntaxKind.SHR, bin.getOperatorKind());
    }

    // -------------------------------------------------------------------------
    // Complex expression tests
    // -------------------------------------------------------------------------

    @Test
    void parseComplex_methodChain() {
        GreenExpression expr = parser("a.b().c.d()").parseExpression();

        assertInstanceOf(GreenInvokeExpr.class, expr);
    }

    @Test
    void parseComplex_mixedOperations() {
        GreenExpression expr = parser("arr[i].field + obj.method(x, y)").parseExpression();

        assertInstanceOf(GreenBinaryExpr.class, expr);
        GreenBinaryExpr bin = (GreenBinaryExpr) expr;
        assertEquals(SyntaxKind.PLUS, bin.getOperatorKind());
    }
}
