package org.xtclang.plugin.runtime;

import java.time.Duration;

/**
 * One owned embedding session. Hosts serialize execution, supply a fresh output destination per
 * request, and close the executor before releasing its implementation classloader.
 */
public interface RuntimeExecutor extends AutoCloseable {
    Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

    /** Execute one request; the caller must not overlap this with another execution. */
    int execute(Object request, RuntimeOutput output);

    /** Cancel an active run, returning false when there is no published control to cancel. */
    boolean cancel(Duration timeout);

    /** Stop owned execution and native resources before returning, within the supplied budget. */
    void close(Duration timeout);

    @Override
    default void close() {
        close(DEFAULT_SHUTDOWN_TIMEOUT);
    }
}
