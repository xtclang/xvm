package org.xvm.asm.constants;

import org.junit.jupiter.api.Test;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.FileStructure;
import org.xvm.asm.MethodStructure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link MethodBody} must not depend on an ambient "current pool" being bound to the calling
 * thread.
 *
 * <p>It asked {@code ConstantPool.getCurrentPool()} for the pool to resolve {@code @Op} against,
 * and dereferenced the answer. That is a thread-local: it is null on any thread which has not had
 * a pool pushed onto it, which is every thread that is not inside a compilation - a debugger
 * evaluating a watch, a log line, a test. The two methods that reach it are {@link MethodBody#isOp()}
 * and {@code toString()}, so printing a MethodBody threw a NullPointerException out of the very
 * code meant to describe it, and an assertion failure mentioning one failed to report itself.</p>
 *
 * <p>This is the same fault as the one {@code FileStructure.getErrorListener()} had, in the same
 * shape, from the same thread-local. See the appendix of docs/errs.md.</p>
 */
public class MethodBodyAmbientPoolTest {
    @Test
    public void aMethodBodyDescribesItselfWithNoAmbientPoolBound() {
        MethodBody body = bodyOnAThreadWithNoPool();

        assertDoesNotThrow(() -> body.toString(), "printing it must not need an ambient pool");
        assertNotNull(body.toString());
    }

    @Test
    public void isOpAnswersWithNoAmbientPoolBound() {
        MethodBody body = bodyOnAThreadWithNoPool();

        assertDoesNotThrow(() -> body.isOp(), "asking whether it is an operator must not need one either");
    }

    /**
     * Build a MethodBody, having first checked the premise: this thread has no pool bound, which
     * is what makes the test meaningful rather than accidental.
     */
    private static MethodBody bodyOnAThreadWithNoPool() {
        assertNull(ConstantPool.getCurrentPool(), "the premise: a test thread has never had a pool pushed onto it");

        FileStructure   file   = new FileStructure("test");
        ClassStructure  clz    = file.getModule().createClass(Access.PUBLIC, Format.CLASS, "Test", null);
        MethodStructure method = clz.createMethod(false, Access.PUBLIC, null,
                org.xvm.asm.Parameter.NO_PARAMS, "go", org.xvm.asm.Parameter.NO_PARAMS, true, true);

        return new MethodBody(method.getIdentityConstant(), method.getIdentityConstant().getSignature(),
                MethodBody.Implementation.Explicit);
    }
}
