package org.xvm.asm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportingTest {
    @Test
    void scopesRestoreTheRequestAndReleaseItAfterFailure() {
        var reporting = new Reporting();
        var request = new ErrorList();
        var nested = new ErrorList();
        assertThrows(IllegalStateException.class, reporting::get);
        assertThrows(TestFailure.class, () -> {
            try (var scope = reporting.to(request)) {
                try (var inner = reporting.to(nested)) {
                    assertSame(nested, reporting.get());
                }
                assertSame(request, reporting.get());
                throw new TestFailure();
            }
        });
        assertThrows(IllegalStateException.class, reporting::get);
        assertThrows(NullPointerException.class, () -> reporting.to(null));
        assertThrows(NullPointerException.class, () -> new Reporting(null));
        assertThrows(IllegalStateException.class, reporting::get);
    }

    private static final class TestFailure extends RuntimeException {}
}
