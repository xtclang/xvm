package org.xvm.asm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link FileStructure#getErrorListener()} must not depend on an ambient "current pool" being bound
 * to the calling thread.
 *
 * <p>It consulted {@code ConstantPool.getCurrentPool()} - a thread-local - and dereferenced the
 * result unconditionally. That thread-local is simply {@code null} on any thread that has not had a
 * pool pushed onto it, which is every thread that drives the compiler or runtime from ordinary Java
 * code (an embedding host, a build tool, a test). The accessor is a DIAGNOSTIC accessor, so the
 * failure mode was a {@code NullPointerException} thrown from the very code meant to report
 * problems.</p>
 */
public class FileStructureErrorListenerTest {
    @Test
    public void getErrorListenerWorksWithNoAmbientPoolBound() {
        // a plain FileStructure with no explicit ErrorListener set; this test thread has never had a
        // pool bound, so getCurrentPool() returns null
        var file = new FileStructure("test");

        ErrorListener errs = file.getErrorListener();

        assertNotNull(errs, "a diagnostic accessor must never return null");
        assertSame(ErrorListener.RUNTIME, errs,
                "with no explicit listener and no ambient pool, the runtime listener is the answer");
    }

    @Test
    public void getErrorListenerPrefersAnExplicitlySetListener() {
        var file = new FileStructure("test");
        var mine = new ErrorList(10);

        file.setErrorListener(mine);

        assertSame(mine, file.getErrorListener(),
                "an explicitly supplied listener must win over any fallback");
    }
}
