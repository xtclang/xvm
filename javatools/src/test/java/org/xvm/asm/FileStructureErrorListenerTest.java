package org.xvm.asm;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.TypeConstant;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A structure must not be a place diagnostics are routed through.
 *
 * <p>It used to be. A {@code TypeConstant} asked to build a {@code TypeInfo} without being given a
 * listener had no caller to ask, so it walked up to the containing {@code FileStructure} and
 * reported to whatever that file was last told - and failing that, to a listener that prints to
 * stdout. Three things were wrong with it, and this class is what keeps them from coming back.</p>
 *
 * <p>It answered for the wrong request: "whatever this file was last told" is not "the caller's".
 * It consulted {@code ConstantPool.getCurrentPool()}, a thread-local that is null on any thread
 * that has not had a pool pushed onto it - which is every thread driving the compiler from
 * ordinary Java code - and dereferenced it, so the accessor meant to report problems threw an NPE.
 * And it was writable through a setter inherited from {@code XvmStructure}, so a structure could
 * redirect the diagnostics of a whole containment tree it did not own.</p>
 *
 * <p>All of it is gone. {@code ensureTypeInfo()} says its own silence, which is what the file was
 * being parked on anyway, and the two thirds of its callers that run after compilation is over no
 * longer reach a listener that prints.</p>
 */
public class FileStructureErrorListenerTest {
    /**
     * No structure offers a listener, so nothing can route through one.
     */
    @Test
    public void aStructureDoesNotAnswerForDiagnostics() {
        List.<Class<?>>of(XvmStructure.class, FileStructure.class, Component.class, TypeConstant.class).forEach(clz -> {
            assertFalse(Arrays.stream(clz.getMethods())
                            .anyMatch(m -> m.getName().equals("getErrorListener")),
                    clz.getSimpleName() + " must not offer a listener to route through");
        });
    }

    /**
     * Nor can one be set on a structure, for itself or - as was once possible by inheritance -
     * for its parent.
     */
    @Test
    public void aStructuresReportingCannotBeRedirected() {
        List.<Class<?>>of(XvmStructure.class, FileStructure.class).forEach(clz -> {
            assertFalse(Arrays.stream(clz.getMethods())
                            .anyMatch(m -> m.getName().equals("setErrorListener")
                                        || m.getName().equals("reportingTo")),
                    clz.getSimpleName() + " must not offer a way to redirect reporting");
        });
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
}
