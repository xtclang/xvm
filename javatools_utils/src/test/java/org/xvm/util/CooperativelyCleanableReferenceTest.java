package org.xvm.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CooperativelyCleanableReference}
 */
public class CooperativelyCleanableReferenceTest {
    @Test
    void shouldClean() {
        AtomicLong cleaned = new AtomicLong();
        while (cleaned.get() == 0) {
            CooperativelyCleanableReference.create(new Object(), cleaned::incrementAndGet);
            System.gc();
    }
}

    @Test
    void shouldIgnoreExceptionsWhileCleaning() {
        AtomicLong cleaned = new AtomicLong();
        while (cleaned.get() == 0) {
            CooperativelyCleanableReference.create(new Object(), () -> {
                cleaned.incrementAndGet();
                throw new NullPointerException();
        });
            System.gc();
    }
}

    @Test
    void shouldIgnoreAndRestoreInterruptExceptionsWhileCleaning() {
        AtomicLong cleaned = new AtomicLong();
        while (cleaned.get() == 0) {
            CooperativelyCleanableReference.create(new Object(), () -> {
                cleaned.incrementAndGet();
                throw new RuntimeException(new InterruptedException());
        });
            System.gc();
    }
        assertTrue(Thread.interrupted());
}
}
