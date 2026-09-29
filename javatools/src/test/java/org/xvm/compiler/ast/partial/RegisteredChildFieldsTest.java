package org.xvm.compiler.ast.partial;

import java.lang.reflect.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;
import org.xvm.compiler.ast.AstNode;
import org.xvm.compiler.ast.Context;
import org.xvm.compiler.ast.Expression;
import org.xvm.compiler.ast.NameExpression;
import org.xvm.compiler.ast.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercise actual AST operations on private registered fields outside the base AST package. */
public class RegisteredChildFieldsTest {
    @Test
    public void traversalAndDumpIncludeInheritedAndDeclaredFieldsOnly() {
        var node = new Child(name("receiver"), List.of(name("argument")));
        var children = snapshot(node);

        assertEquals(List.of("receiver", "argument"), children.stream().map(Object::toString).toList());
        children.forEach(child -> assertSame(node, child.getParent()));
        assertEquals(children, snapshot(node));
        assertEquals(List.of("receiver", "arguments"), List.copyOf(node.getDumpChildren().keySet()));
        assertTrue(node.toDumpString().contains("receiver"));
        assertTrue(node.toDumpString().contains("arguments"));
        assertFalse(node.toDumpString().contains("unregistered"));
    }

    @Test
    public void cloneOwnsIndependentChildrenAndMutableLists() {
        var node = new Child(name("receiver"), List.of(name("argument")));
        var copy = (Child) node.clone();
        var original = snapshot(node);
        var cloned = snapshot(copy);

        assertNotSame(original.getFirst(), cloned.getFirst());
        assertNotSame(original.getLast(), cloned.getLast());
        cloned.forEach(child -> assertSame(copy, child.getParent()));
        original.forEach(child -> assertSame(node, child.getParent()));
        assertEquals(node.toDumpString(), copy.toDumpString());

        var replacement = name("replacement");
        copy.replaceChild(cloned.getLast(), replacement);
        assertSame(copy, replacement.getParent());
        assertEquals(original, snapshot(node));
        assertEquals(List.of(cloned.getFirst(), replacement), snapshot(copy));
    }

    @Test
    public void replacementAndRemovalWorkForInheritedScalarAndDeclaredList() {
        var node = new Child(name("receiver"), List.of(name("first"), name("last")));
        var original = snapshot(node);
        var receiver = name("newReceiver");
        var argument = name("newArgument");
        node.replaceChild(original.getFirst(), receiver);
        node.replaceChild(original.get(1), argument);
        assertSame(node, receiver.getParent());
        assertSame(node, argument.getParent());

        var cursor = node.children();
        assertSame(receiver, cursor.next());
        cursor.remove();
        assertSame(argument, cursor.next());
        cursor.remove();
        assertSame(original.getLast(), cursor.next());
        assertFalse(cursor.hasNext());
        assertEquals(List.of(original.getLast()), snapshot(node));
    }

    private static List<AstNode> snapshot(AstNode node) {
        return StreamSupport.stream(node.childNodes().spliterator(), false).toList();
    }

    private static NameExpression name(String name) {
        return new NameExpression(new Token(0, name.length(), Id.IDENTIFIER, name));
    }

    private abstract static class Parent extends Statement {
        Parent(Expression receiver) {
            this.receiver = adopt(receiver);
        }

        @Override
        public long getStartPosition() {
            return 0;
        }

        @Override
        public long getEndPosition() {
            return 1;
        }

        @Override
        protected Statement validateImpl(Context ctx, ErrorListener errs) {
            return this;
        }

        @Override
        protected boolean emit(Context ctx, boolean reachable, Code code, ErrorListener errs) {
            throw new UnsupportedOperationException("Traversal fixture cannot emit code");
        }

        @Override
        public String toString() {
            return "registered children";
        }

        private Expression receiver;
    }

    private static final class Child extends Parent {
        Child(Expression receiver, List<Expression> arguments) {
            super(receiver);
            this.arguments = new ArrayList<>(arguments);
            adopt(this.arguments);
        }

        @Override
        protected Field[] getChildFields() {
            return CHILD_FIELDS;
        }

        private List<Expression> arguments;
        private final Expression unregistered = name("unregistered");

        private static final Field[] CHILD_FIELDS = fieldsForNames(Child.class, "receiver", "arguments");
    }
}
