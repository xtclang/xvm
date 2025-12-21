package org.xvm.compiler.ast;


import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Unit tests for AST node behavior: forEachChild, withChildren, copy, adopt.
 * These tests verify current behavior before refactoring to ensure nothing breaks.
 */
public class AstNodeTest {

    // ---- Test Utilities ----

    /**
     * Create a token for testing. Tokens are immutable and can be shared.
     */
    private static Token token(Id id) {
        return new Token(0, 0, id);
    }

    /**
     * Create an integer literal token.
     */
    private static Token intLiteral(long value) {
        return new Token(0, 0, Id.LIT_INT, value);
    }

    /**
     * Create a LiteralExpression for testing.
     */
    private static LiteralExpression literal(long value) {
        return new LiteralExpression(intLiteral(value));
    }

    // ---- forEachChild tests ----

    @Test
    void testForEachChild_literalExpression_hasNoChildren() {
        LiteralExpression expr = literal(42);

        List<AstNode> children = new ArrayList<>();
        expr.forEachChild(child -> {
            children.add(child);
            return null;
        });

        assertTrue(children.isEmpty(), "LiteralExpression should have no children");
    }

    @Test
    void testForEachChild_biExpression_visitsLeftAndRight() {
        LiteralExpression left = literal(1);
        LiteralExpression right = literal(2);
        CmpExpression expr = new CmpExpression(left, token(Id.COMP_EQ), right);

        List<AstNode> children = new ArrayList<>();
        expr.forEachChild(child -> {
            children.add(child);
            return null;
        });

        assertEquals(2, children.size());
        assertSame(left, children.get(0));
        assertSame(right, children.get(1));
    }

    @Test
    void testForEachChild_earlyExit_stopsIteration() {
        LiteralExpression left = literal(1);
        LiteralExpression right = literal(2);
        CmpExpression expr = new CmpExpression(left, token(Id.COMP_EQ), right);

        List<AstNode> visited = new ArrayList<>();
        Object result = expr.forEachChild(child -> {
            visited.add(child);
            return "STOP";  // Non-null stops iteration
        });

        assertEquals(1, visited.size(), "Should stop after first child");
        assertEquals("STOP", result);
    }

    // ---- withChildren tests ----

    @Test
    void testWithChildren_cmpExpression_createsNewInstance() {
        LiteralExpression left = literal(1);
        LiteralExpression right = literal(2);
        CmpExpression original = new CmpExpression(left, token(Id.COMP_EQ), right);

        LiteralExpression newLeft = literal(99);
        AstNode replaced = original.withChildren(List.of(newLeft, right));

        assertNotSame(original, replaced);
        assertTrue(replaced instanceof CmpExpression);

        CmpExpression cmp = (CmpExpression) replaced;
        assertSame(newLeft, cmp.getExpression1());
        assertSame(right, cmp.getExpression2());
    }

    @Test
    void testWithChildren_preservesNonChildFields() {
        LiteralExpression left = literal(1);
        LiteralExpression right = literal(2);
        Token operator = token(Id.COMP_LT);
        CmpExpression original = new CmpExpression(left, operator, right);

        AstNode replaced = original.withChildren(List.of(literal(10), literal(20)));

        CmpExpression cmp = (CmpExpression) replaced;
        // Operator token should be preserved (it's not a child)
        assertSame(operator, cmp.getOperator());
    }

    // ---- copy tests ----

    @Test
    void testCopy_literalExpression_createsNewInstance() {
        LiteralExpression original = literal(42);

        LiteralExpression copy = (LiteralExpression) original.copy();

        assertNotSame(original, copy);
        // Value should be equal (same literal token)
        assertEquals(original.getLiteral().getValue(), copy.getLiteral().getValue());
    }

    @Test
    void testCopy_cmpExpression_deepCopiesChildren() {
        LiteralExpression left = literal(1);
        LiteralExpression right = literal(2);
        CmpExpression original = new CmpExpression(left, token(Id.COMP_EQ), right);

        CmpExpression copy = (CmpExpression) original.copy();

        assertNotSame(original, copy);
        assertNotSame(original.getExpression1(), copy.getExpression1());
        assertNotSame(original.getExpression2(), copy.getExpression2());
    }

    @Test
    void testCopy_nestedExpression_deepCopiesAllLevels() {
        // Create: (1 == 2) == (3 == 4)
        CmpExpression innerLeft = new CmpExpression(literal(1), token(Id.COMP_EQ), literal(2));
        CmpExpression innerRight = new CmpExpression(literal(3), token(Id.COMP_EQ), literal(4));
        CmpExpression outer = new CmpExpression(innerLeft, token(Id.COMP_EQ), innerRight);

        CmpExpression copy = (CmpExpression) outer.copy();

        // All levels should be different instances
        assertNotSame(outer, copy);
        assertNotSame(innerLeft, copy.getExpression1());
        assertNotSame(innerRight, copy.getExpression2());

        // Nested children should also be copied
        CmpExpression copiedInnerLeft = (CmpExpression) copy.getExpression1();
        assertNotSame(innerLeft.getExpression1(), copiedInnerLeft.getExpression1());
    }

    // ---- adopt / parent tests ----

    @Test
    void testAdopt_setsParentReference() {
        LiteralExpression left = literal(1);
        LiteralExpression right = literal(2);
        CmpExpression parent = new CmpExpression(left, token(Id.COMP_EQ), right);

        // Before introduceParentage, children have no parent
        assertNull(left.getParent());
        assertNull(right.getParent());

        parent.introduceParentage();

        // After introduceParentage, parent is set
        assertSame(parent, left.getParent());
        assertSame(parent, right.getParent());
    }

    @Test
    void testIntroduceParentage_setsParentsRecursively() {
        // Create nested: outer( inner(leaf1, leaf2), leaf3 )
        LiteralExpression leaf1 = literal(1);
        LiteralExpression leaf2 = literal(2);
        LiteralExpression leaf3 = literal(3);
        CmpExpression inner = new CmpExpression(leaf1, token(Id.COMP_EQ), leaf2);
        CmpExpression outer = new CmpExpression(inner, token(Id.COMP_EQ), leaf3);

        outer.introduceParentage();

        // Check all parent references
        assertSame(outer, inner.getParent());
        assertSame(outer, leaf3.getParent());
        assertSame(inner, leaf1.getParent());
        assertSame(inner, leaf2.getParent());
    }

    @Test
    void testCopy_setsParentOnCopiedChildren() {
        LiteralExpression left = literal(1);
        LiteralExpression right = literal(2);
        CmpExpression original = new CmpExpression(left, token(Id.COMP_EQ), right);
        original.introduceParentage();

        CmpExpression copy = (CmpExpression) original.copy();

        // Copy's children should have copy as parent
        assertSame(copy, copy.getExpression1().getParent());
        assertSame(copy, copy.getExpression2().getParent());

        // Original's children should still have original as parent
        assertSame(original, original.getExpression1().getParent());
        assertSame(original, original.getExpression2().getParent());
    }

    // ---- replaceChild tests ----

    @Test
    void testReplaceChild_updatesChildReference() {
        LiteralExpression left = literal(1);
        LiteralExpression right = literal(2);
        CmpExpression expr = new CmpExpression(left, token(Id.COMP_EQ), right);
        expr.introduceParentage();

        LiteralExpression newLeft = literal(99);
        expr.replaceChild(left, newLeft);

        assertSame(newLeft, expr.getExpression1());
        assertSame(right, expr.getExpression2());
    }

    @Test
    void testReplaceChild_throwsIfChildNotFound() {
        LiteralExpression left = literal(1);
        LiteralExpression right = literal(2);
        CmpExpression expr = new CmpExpression(left, token(Id.COMP_EQ), right);

        LiteralExpression notAChild = literal(999);

        assertThrows(IllegalStateException.class, () -> {
            expr.replaceChild(notAChild, literal(0));
        });
    }

    // ---- ChildList helper tests ----

    @Test
    void testChildList_extractsChildrenInOrder() {
        // This tests the ChildList helper used in withChildren implementations
        LiteralExpression child1 = literal(1);
        LiteralExpression child2 = literal(2);
        List<AstNode> children = List.of(child1, child2);

        AstNode.ChildList c = new AstNode.ChildList(children);

        assertSame(child1, c.next());
        assertSame(child2, c.next());
    }

    @Test
    void testChildList_nextList_extractsList() {
        LiteralExpression child1 = literal(1);
        LiteralExpression child2 = literal(2);
        LiteralExpression child3 = literal(3);
        List<AstNode> children = List.of(child1, child2, child3);

        AstNode.ChildList c = new AstNode.ChildList(children);
        c.next();  // Skip first

        List<Expression> remaining = c.nextList(2);

        assertEquals(2, remaining.size());
        assertSame(child2, remaining.get(0));
        assertSame(child3, remaining.get(1));
    }
}
