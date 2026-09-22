package org.xvm.util;

import java.time.Duration;

import java.util.Objects;

import java.util.function.LongSupplier;

/**
 * A monotonic time budget shared by the steps of one operation.
 */
public final class Deadline {
    /**
     * Start a deadline using the monotonic system clock.
     *
     * @param timeout  the nonnegative time budget; zero permits only work that is already complete
     *
     * @return the deadline
     */
    public static Deadline after(Duration timeout) {
        return new Deadline(timeout, System::nanoTime);
    }

    Deadline(Duration timeout, LongSupplier clock) {
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must not be negative");
        }
        this.budget = timeout.toNanos();
        this.clock  = Objects.requireNonNull(clock);
        this.start  = clock.getAsLong();
    }

    /**
     * @return the remaining budget, clamped to zero after expiry
     */
    public long remainingNanos() {
        return Math.max(0, budget - (clock.getAsLong() - start));
    }

    /**
     * @return the remaining budget for a nested operation
     */
    public Duration remaining() {
        return Duration.ofNanos(remainingNanos());
    }

    private final long         budget;
    private final LongSupplier clock;
    private final long         start;
}
