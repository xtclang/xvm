package org.xvm.asm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * A structure could reach through its parent and redirect the diagnostics of a whole
     * containment tree it did not own, because setErrorListener was inherited from XvmStructure
     * and delegated the mutation upwards. Only the file itself decides now.
     */
    @Test
    public void onlyTheFileItselfCanDirectItsDiagnostics() {
        assertFalse(java.util.Arrays.stream(XvmStructure.class.getMethods())
                        .anyMatch(m -> m.getName().equals("setErrorListener")),
                "XvmStructure must not offer a setter that mutates its parent");
    }

    /**
     * A collector reports resolution diagnostics somewhere its caller chose. The interface used to
     * default that to a silent listener, so a collector that had not thought about diagnostics and
     * one that had decided against them were the same collector.
     */
    @Test
    public void aResolutionCollectorMustSayWhereItsDiagnosticsGo() throws NoSuchMethodException {
        assertFalse(ComponentResolver.ResolutionCollector.class
                        .getMethod("getErrorListener").isDefault(),
                "the collector must supply a listener rather than inherit silence");
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
