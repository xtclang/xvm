package org.xvm.util;

import java.time.Duration;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeadlineTest {
    @Test
    void stepsShareOneBudget() {
        var clock = new AtomicLong(100);
        var deadline = new Deadline(Duration.ofNanos(30), clock::get);
        assertEquals(Duration.ofNanos(30), deadline.remaining());

        clock.addAndGet(12);
        assertEquals(18, deadline.remainingNanos());
        clock.addAndGet(18);
        assertEquals(Duration.ZERO, deadline.remaining());
        clock.incrementAndGet();
        assertEquals(0, deadline.remainingNanos());
    }

    @Test
    void monotonicCounterMayWrap() {
        var clock = new AtomicLong(Long.MAX_VALUE - 5);
        var deadline = new Deadline(Duration.ofNanos(20), clock::get);
        clock.addAndGet(10);
        assertEquals(10, deadline.remainingNanos());
    }

    @Test
    void zeroExpiresImmediatelyAndNegativeBudgetsAreRejected() {
        var deadline = new Deadline(Duration.ZERO, () -> 123);
        assertEquals(0, deadline.remainingNanos());
        assertThrows(IllegalArgumentException.class,
                () -> new Deadline(Duration.ofNanos(-1), () -> 123));
    }
}
