package org.xvm.asm;

import org.junit.jupiter.api.Test;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Diagnostic sinks belong to individual operations, never files or ambient pools.
 */
class FileStructureErrorListenerTest {
    @Test
    void diagnosticsUseOnlyTheExplicitRequestListener() {
        var source = new FileStructure("Source");
        var ambient = new FileStructure("Ambient").getConstantPool();
        var first = new ErrorList(10);
        var second = new ErrorList(10);
        try (var scope = ConstantPool.withPool(ambient)) {
            source.log(first, Severity.WARNING, "FIRST");
            var copy = new FileStructure(source);
            copy.log(second, Severity.WARNING, "SECOND");
            assertEquals(1, first.getErrors().size());
            assertTrue(first.hasError("FIRST"));
            assertEquals(1, second.getErrors().size());
            assertTrue(second.hasError("SECOND"));
            assertThrows(NullPointerException.class,
                    () -> source.log(null, Severity.WARNING, "NO_LISTENER"));
        }
    }

    @Test
    void recordingPreservesBranchRollbackAndAbortState() {
        var request = new ErrorList(1);
        var recorded = new ErrorList(0);
        var listener = ErrorListener.tee(request, recorded);
        var file = new FileStructure("Source");
        var abandoned = listener.branch(null);
        abandoned.log(Severity.ERROR, "ABANDONED", null, file);
        assertFalse(request.hasErrors());
        assertFalse(recorded.hasErrors());
        var committed = listener.branch(null);
        committed.log(Severity.ERROR, "COMMITTED", null, file);
        committed.merge();
        assertTrue(listener.isAbortDesired());
        assertTrue(listener.hasSeriousErrors());
        assertTrue(listener.hasError("COMMITTED"));
        assertEquals(request.getErrors(), recorded.getErrors());
        assertEquals(1, recorded.getErrors().size());

        // A cleared request must accept the same cached diagnostic again.
        request.clear();
        assertFalse(request.isAbortDesired());
        recorded.logTo(request);
        assertEquals(recorded.getErrors(), request.getErrors());
    }
}
