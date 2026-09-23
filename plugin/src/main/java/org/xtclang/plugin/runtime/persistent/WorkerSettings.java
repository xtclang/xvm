package org.xtclang.plugin.runtime.persistent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Immutable startup inputs. Runtime artifacts are copied into a private worker image before
 * publication so a later build cannot replace jars or core modules underneath an active worker.
 */
public record WorkerSettings(String identity, List<File> classpath, List<File> coreModules,
                             Duration idleTimeout, Duration shutdownTimeout) {
    public WorkerSettings {
        classpath = List.copyOf(classpath);
        coreModules = List.copyOf(coreModules);
        if (idleTimeout.toMillis() < 1 || shutdownTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("Worker timeouts must be at least one millisecond");
        }
    }

    void write(final Path path) throws IOException {
        try (var out = new DataOutputStream(Files.newOutputStream(path))) {
            out.writeInt(WorkerProtocol.VERSION);
            WorkerProtocol.text(out, identity);
            WorkerProtocol.files(out, classpath);
            WorkerProtocol.files(out, coreModules);
            out.writeLong(idleTimeout.toMillis());
            out.writeLong(shutdownTimeout.toMillis());
        }
    }

    static WorkerSettings read(final Path path) throws IOException {
        try (var in = new DataInputStream(Files.newInputStream(path))) {
            if (in.readInt() != WorkerProtocol.VERSION) {
                throw new IOException("Incompatible worker settings");
            }
            return new WorkerSettings(WorkerProtocol.text(in), WorkerProtocol.files(in), WorkerProtocol.files(in),
                Duration.ofMillis(in.readLong()), Duration.ofMillis(in.readLong()));
        }
    }
}
