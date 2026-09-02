package org.xvm.compiler.ast;

import java.lang.reflect.Field;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the two defects that {@code AstNode.fieldsForNames} carried.
 *
 * <p>The method builds the reflective child-field model the AST walks (65 call sites), and it is
 * supposed to fail loudly on a bad field name or an unsupported field type. It did neither.</p>
 *
 * <p><b>Defect A - the type guard was dead.</b> It read
 * {@code !field.getType().isInstance(AstNode.class) && field.getType().isInstance(List.class)}.
 * {@code Class.isInstance(x)} asks whether the OBJECT {@code x} is an instance of the receiver, so
 * passing {@code AstNode.class} asked whether the {@code Class} object itself was an instance of
 * the field's type - false for every realistic field type. Both conjuncts were therefore false and
 * the guard never fired, silently accepting any field type at all.</p>
 *
 * <p><b>Defect B - the not-found path tested the wrong variable.</b> After walking to the
 * superclass it checked {@code if (clz == null)}, but {@code clz} is the method parameter and is
 * never null there; the intended variable was {@code clzTry}. So when a named field existed nowhere
 * in the hierarchy, the loop simply exited and left {@code fields[i]} <b>null</b>, discarding the
 * saved {@code NoSuchFieldException} that named the missing field. A renamed or deleted child field
 * surfaced later as an opaque failure where the null slot was dereferenced, instead of immediately
 * as "no such field &lt;name&gt;".</p>
 */
public class AstNodeFieldModelTest {
    /**
     * A stand-in for a real AST node. It does NOT extend {@link AstNode} - that class is sealed,
     * and {@code fieldsForNames} takes a raw {@code Class} anyway, so only the FIELD TYPES matter
     * here: one child node, one child list, and one field of a type the child model does not
     * support.
     */
    @SuppressWarnings("unused")
    private static class Fixture {
        AstNode       child;
        List<AstNode> children;
        int           notANode;
    }

    /** Declares no fields of its own, so "child" must be found on {@link Fixture}. */
    private static class Inherited
            extends Fixture {
    }

    /**
     * Defect B. A name that exists nowhere in the hierarchy must fail loudly and name the field,
     * rather than leaving a null hole in the returned array for someone else to trip over.
     */
    @Test
    public void missingFieldFailsLoudlyInsteadOfReturningANullHole() {
        var e = assertThrows(IllegalStateException.class,
                () -> AstNode.fieldsForNames(Fixture.class, "noSuchFieldAnywhere"),
                "a field name that exists nowhere must throw, not yield fields[i] == null");

        assertNotNull(e.getCause(), "the saved NoSuchFieldException must be preserved as the cause");
        assertTrue(e.getCause().toString().contains("noSuchFieldAnywhere"),
                () -> "the failure must name the missing field, but was: " + e.getCause());
    }

    /**
     * Defect A. A field whose type is neither an {@link AstNode} nor a {@link List} is not part of
     * the child model and must be rejected.
     */
    @Test
    public void unsupportedFieldTypeIsRejected() {
        var e = assertThrows(IllegalStateException.class,
                () -> AstNode.fieldsForNames(Fixture.class, "notANode"),
                "an int field is not a child node or a child list and must be rejected");

        assertTrue(e.getMessage().contains("notANode"),
                () -> "the failure must name the offending field, but was: " + e.getMessage());
    }

    /**
     * The guard must still accept what the child model is actually made of, in both shapes: a
     * single child node, and a list of them.
     */
    @Test
    public void childNodeAndChildListFieldsAreAccepted() {
        Field[] fields = AstNode.fieldsForNames(Fixture.class, "child", "children");

        assertEquals(2, fields.length);
        assertNotNull(fields[0], "a direct AstNode child field must be accepted");
        assertNotNull(fields[1], "a List child field must be accepted");
        assertEquals("child", fields[0].getName());
        assertEquals("children", fields[1].getName());
    }

    /**
     * A field declared on a SUPERCLASS must still resolve - that walk is the reason the not-found
     * path exists at all, so it must keep working after the fix to it.
     */
    @Test
    public void inheritedFieldsStillResolve() {
        Field[] fields = AstNode.fieldsForNames(Inherited.class, "child");

        assertEquals(1, fields.length);
        assertNotNull(fields[0], "a field declared on a superclass must resolve");
        assertEquals(Fixture.class, fields[0].getDeclaringClass());
    }

}
