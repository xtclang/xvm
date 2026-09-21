package org.xvm.asm;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Component.Format;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.constants.TypeConstant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The pool a piece of work should use, when the thread has not been told which.
 *
 * <p>{@link ConstantPool#getCurrentPool()} is a thread-local, bound by
 * {@link ConstantPool#withPool} around stretches of compilation and by the runtime container.
 * Outside those it is null, which is the ordinary state of any thread driving the compiler from
 * Java - a build tool, an embedding host, a test, a debugger evaluating a watch. Code that
 * dereferenced it threw there, and two such NullPointerExceptions were found by accident in
 * unrelated code while this work was going on.</p>
 *
 * <p>The ambient pool is preferred rather than ignored: {@code withPool} exists because the
 * compiler works across pools, so a constant's own pool is not always the right one and answering
 * from the wrong pool would be worse than answering from none.</p>
 */
public class ConstantPoolAmbientTest {
    @Test
    public void withNoPoolBoundTheFallbackIsUsed() {
        assertNull(ConstantPool.getCurrentPool(), "the premise: a test thread has never had a pool bound");

        ConstantPool pool = new FileStructure("test").getConstantPool();

        assertSame(pool, ConstantPool.currentOr(pool), "nothing bound, so the fallback answers");
    }

    @Test
    public void aBoundPoolWinsOverTheFallback() {
        ConstantPool bound    = new FileStructure("bound").getConstantPool();
        ConstantPool fallback = new FileStructure("fallback").getConstantPool();

        try (var scope = ConstantPool.withPool(bound)) {
            assertSame(bound, ConstantPool.currentOr(fallback),
                    "a pool bound to this thread is the one the compiler meant");
        }

        assertSame(fallback, ConstantPool.currentOr(fallback), "and the binding ends with the scope");
    }

    /**
     * A constant asked to resolve another one outside a compilation answers from its own pool
     * rather than throwing. This is what the unguarded readers used to do.
     */
    @Test
    public void aConstantResolvesFromItsOwnPoolWithNothingBound() {
        assertNull(ConstantPool.getCurrentPool(), "the premise");

        FileStructure  file = new FileStructure("test");
        ClassStructure clz  = file.getModule().createClass(Access.PUBLIC, Format.CLASS, "Test", null);
        TypeConstant   type = clz.getCanonicalType();

        assertNotNull(type.getConstantPool());
        assertSame(file.getConstantPool(), ConstantPool.currentOr(type.getConstantPool()));
    }
}
