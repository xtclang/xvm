package org.xtclang.plugin.runtime.persistent;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Inputs used only by the client to start a compatible, checkout-local worker on demand. */
public record WorkerLaunch(Path directory, String javaExecutable, List<String> jvmArgs,
                           File pluginSource, WorkerSettings settings, Duration startupTimeout) {
    public WorkerLaunch {
        jvmArgs = List.copyOf(jvmArgs);
        if (startupTimeout.isZero() || startupTimeout.isNegative()) {
            throw new IllegalArgumentException("Worker startup timeout must be positive");
        }
    }
}
