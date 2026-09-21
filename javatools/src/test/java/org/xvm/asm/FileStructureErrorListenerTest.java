package org.xvm.asm;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
     * and delegated the mutation upwards. Only the file itself decides now, and only for as long
     * as it holds the scope open.
     */
    @Test
    public void onlyTheFileItselfCanDirectItsDiagnostics() {
        assertFalse(Arrays.stream(XvmStructure.class.getMethods())
                        .anyMatch(m -> m.getName().equals("setErrorListener")
                                    || m.getName().equals("reportingTo")),
                "XvmStructure must not offer a way to mutate its parent's reporting");
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
    public void getErrorListenerPrefersTheListenerOfTheOpenScope() {
        var file = new FileStructure("test");
        var mine = new ErrorList();

        try (var reporting = file.reportingTo(mine)) {
            assertSame(mine, file.getErrorListener(),
                    "an explicitly supplied listener must win over any fallback");
        }
    }

    /**
     * The direction lasts as long as the work that asked for it, and no longer. It used to be a
     * setting cleared by whoever remembered, which left a file that had been compiled once
     * answering for a listener belonging to a request that had finished - and, when the clearing
     * was skipped on a failed compile, permanently silenced.
     */
    @Test
    public void theDirectionEndsWithTheScope() {
        var file = new FileStructure("test");

        try (var reporting = file.reportingTo(new ErrorList())) {
            assertNotSame(ErrorListener.RUNTIME, file.getErrorListener());
        }

        assertSame(ErrorListener.RUNTIME, file.getErrorListener(),
                "the scope closed, so the file answers for nobody in particular again");
    }

    /**
     * Scopes nest: an inner stretch of work can narrow the reporting and give it back.
     */
    @Test
    public void scopesNest() {
        var file  = new FileStructure("test");
        var outer = new ErrorList();
        var inner = new ErrorList();

        try (var a = file.reportingTo(outer)) {
            try (var b = file.reportingTo(inner)) {
                assertSame(inner, file.getErrorListener());
            }
            assertSame(outer, file.getErrorListener());
        }
    }
}
