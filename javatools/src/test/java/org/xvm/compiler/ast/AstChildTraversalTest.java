package org.xvm.compiler.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Repeatable child views must preserve the compiler's existing one-shot mutation cursor. */
public class AstChildTraversalTest {
    @Test
    public void viewSupportsRepeatedAndInterleavedTraversals() {
        var first = statement("first");
        var last  = statement("last");
        var block = new StatementBlock(new ArrayList<>(List.of(first, last)));
        var view  = block.childNodes();
        var left  = view.iterator();
        var right = view.iterator();

        assertSame(first, left.next());
        assertSame(first, right.next());
        assertSame(last, right.next());
        assertSame(last, left.next());
        assertFalse(left.hasNext());
        assertFalse(right.hasNext());
        assertEquals(List.of(first, last), snapshot(view));
        assertEquals(List.of(first, last), snapshot(view));
    }

    @Test
    public void laterTraversalSeesEditsButAnEarlierSnapshotDoesNot() {
        var original    = statement("original");
        var replacement = statement("replacement");
        var block       = new StatementBlock(new ArrayList<>(List.of(original)));
        var view        = block.childNodes();
        var before      = snapshot(view);

        block.replaceChild(original, replacement);

        assertEquals(List.of(original), before);
        assertEquals(List.of(replacement), snapshot(view));
        assertSame(block, replacement.getParent());
    }

    @Test
    public void legacyEnhancedForRetainsTheCursorUsedForReplacement() {
        var original    = statement("original");
        var untouched   = statement("untouched");
        var replacement = statement("replacement");
        var block       = new StatementBlock(new ArrayList<>(List.of(original, untouched)));
        var cursor      = block.children();

        assertSame(cursor, cursor.iterator());
        for (AstNode child : cursor) {
            if (child == original) {
                cursor.replaceWith(replacement);
            }
        }

        assertFalse(cursor.iterator().hasNext());
        assertEquals(List.of(replacement, untouched), block.getStatements());
        assertEquals(List.of(replacement, untouched), snapshot(block.childNodes()));
    }

    @Test
    public void legacyListCursorCanReplaceAndRemoveWithoutSkippingSiblings() {
        var first       = statement("first");
        var last        = statement("last");
        var replacement = statement("replacement");
        var block       = new StatementBlock(new ArrayList<>(List.of(first, last)));
        var cursor      = block.children();

        assertSame(first, cursor.next());
        cursor.replaceWith(replacement);
        assertSame(last, cursor.next());
        cursor.remove();

        assertFalse(cursor.hasNext());
        assertEquals(List.of(replacement), block.getStatements());
    }

    @Test
    public void legacyScalarCursorCanReplaceAndRemoveItsField() {
        var statement   = statement("original");
        var replacement = name("replacement");
        var cursor      = statement.children();

        assertSame(statement.getExpression(), cursor.next());
        cursor.replaceWith(replacement);
        assertSame(replacement, statement.getExpression());
        assertEquals(List.of(replacement), snapshot(statement.childNodes()));
        cursor.remove();

        assertFalse(cursor.hasNext());
        assertEquals(List.of(), snapshot(statement.childNodes()));
    }

    @Test
    public void emptyViewAndLegacyCursorPreserveExhaustion() {
        var leaf = name("leaf");
        var view = leaf.childNodes();

        assertEquals(List.of(), snapshot(view));
        assertEquals(List.of(), snapshot(view));
        assertThrows(NoSuchElementException.class, () -> view.iterator().next());
        assertThrows(NoSuchElementException.class, () -> leaf.children().next());
        assertThrows(IllegalStateException.class, () -> leaf.children().replaceWith(name("other")));
    }

    private static List<AstNode> snapshot(Iterable<AstNode> children) {
        return StreamSupport.stream(children.spliterator(), false).toList();
    }

    private static ExpressionStatement statement(String name) {
        return new ExpressionStatement(name(name));
    }

    private static NameExpression name(String name) {
        return new NameExpression(new Token(0, name.length(), Id.IDENTIFIER, name));
    }
}
