package org.xvm.util;

/**
 * A simplified version of {@link java.util.concurrent.locks.Condition} abstraction.
 */
public interface Notifier
    {
    /**
     * Block the caller's thread until it is signalled, interrupted or the
     * specified time period has elapsed.
     *
     * @param cMillis the maximum time interval to wait, in milliseconds
     *
     * @throws InterruptedException if the current thread is interrupted
     *         (and interruption of thread suspension is supported)
     */
    void await(long cMillis)
            throws InterruptedException;

    /**
     * Wake up at least one of the awaiting thread(s).
     */
    void signal();
    }
