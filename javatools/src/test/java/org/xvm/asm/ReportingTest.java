package org.xvm.asm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Reporting destinations follow lexical nesting, including exceptional exits. */
class ReportingTest {
    @Test
    void restoresNestedDestinationsAfterException() {
        var original = new ErrorList();
        var outer = new ErrorList();
        var inner = new ErrorList();
        var reporting = new Reporting(original);
        var failure = new IllegalStateException("operation failed");
        try (var scope = reporting.to(outer)) {
            assertSame(outer, reporting.get());
            assertSame(failure, assertThrows(IllegalStateException.class, () -> {
                try (var nested = reporting.to(inner)) {
                    assertSame(inner, reporting.get());
                    throw failure;
                }
            }));
            assertSame(outer, reporting.get());
        }
        assertSame(original, reporting.get());
    }

    @Test
    void inactiveDestinationIsRestoredAndNullScopesAreRejected() {
        var reporting = new Reporting(null);
        var errors = new ErrorList();
        assertThrows(NullPointerException.class, () -> reporting.to(null));
        assertNull(reporting.get());
        try (var scope = reporting.to(errors)) {
            assertThrows(NullPointerException.class, () -> reporting.to(null));
            assertSame(errors, reporting.get());
        }
        assertNull(reporting.get());
    }
}
