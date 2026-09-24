package org.xvm.asm;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import org.xvm.compiler.Compiler;

import org.xvm.util.Severity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reusable definitions report through the current operation, without storing a request sink. */
class FileStructureErrorListenerTest {
    @Test
    void sourceAndCopyReportToTheirOwnOperationUnderAnUnrelatedPool() {
        var source = new FileStructure("Source");
        var copy = new FileStructure(source);
        var unrelated = new FileStructure("Unrelated").getConstantPool();
        var sourceErrors = new ErrorList(1);
        var copyErrors = new ErrorList(1);
        try (var scope = ConstantPool.withPool(unrelated)) {
            assertFalse(source.log(sourceErrors, Severity.WARNING, Compiler.NAME_UNRESOLVABLE, "source"));
            assertTrue(copy.log(copyErrors, Severity.ERROR, Compiler.NAME_UNRESOLVABLE, "copy"));
            assertEquals(1, sourceErrors.getErrors().size());
            assertEquals(1, copyErrors.getErrors().size());
            assertSame(source, sourceErrors.getErrors().getFirst().getXvmStructure());
            assertSame(copy, copyErrors.getErrors().getFirst().getXvmStructure());
            assertFalse(sourceErrors.hasSeriousErrors());
            assertTrue(copyErrors.isAbortDesired());
            assertSame(unrelated, ConstantPool.getCurrentPool());
        }
    }

    @Test
    void explicitListenerAndRuntimeFallbackDoNotNeedAnAmbientPool() {
        var file = new FileStructure("Test");
        var errors = new ErrorList(1);
        try (var scope = ConstantPool.withPool(null)) {
            assertSame(errors, file.ensureErrorListener(errors));
            assertSame(ErrorListener.RUNTIME, file.ensureErrorListener(null));
        }
    }

    @Test
    void reusableStructuresDoNotStoreListeners() {
        for (var type : new Class<?>[] {FileStructure.class, XvmStructure.class}) {
            assertFalse(Arrays.stream(type.getDeclaredFields())
                    .anyMatch(field -> ErrorListener.class.isAssignableFrom(field.getType())));
        }
    }
}
