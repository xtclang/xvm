package org.xtclang.plugin.runtime;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import org.xtclang.plugin.XtcLauncherRuntime;
import org.xtclang.plugin.runtime.persistent.WorkerClient;
import org.xtclang.plugin.runtime.persistent.WorkerLaunch;
import org.xtclang.plugin.runtime.persistent.WorkerSettings;

/**
 * Build-scoped client of a separately owned persistent process. Close releases this build's leases
 * and output destinations; the worker retains only its isolated core runtime until idle shutdown.
 */
public abstract class PersistentRuntimeBuildService
        implements BuildService<PersistentRuntimeBuildService.Parameters>, AutoCloseable {
    public interface Parameters extends BuildServiceParameters {
        DirectoryProperty getWorkerDirectory();
        Property<String> getStartupTimeout();
        Property<String> getIdleTimeout();
        Property<String> getShutdownTimeout();
    }

    private final Map<String, WorkerClient> clients = new HashMap<>();

    public synchronized int execute(final XtcLauncherRuntime runtime, final List<File> modules,
                                    final String java, final List<String> arguments, final Object request, final Logger logger) {
        try {
            final var parameters = getParameters();
            final var idle = Duration.parse(parameters.getIdleTimeout().get());
            final var shutdown = Duration.parse(parameters.getShutdownTimeout().get());
            final var startup = Duration.parse(parameters.getStartupTimeout().get());
            final var identity = identity(runtime, modules, java, arguments, idle, shutdown);
            var client = clients.get(identity);
            if (client == null) {
                final var settings = new WorkerSettings(identity, runtime.classpath(), modules, idle, shutdown);
                client = WorkerClient.start(new WorkerLaunch(parameters.getWorkerDirectory().get().getAsFile().toPath().resolve(identity),
                    java, arguments, new File(IsolatedRuntime.codeSource().toURI()), settings, startup));
                if (!identity.equals(identity(runtime, modules, java, arguments, idle, shutdown))) {
                    client.close();
                    throw new IOException("Runtime files changed during persistent worker startup; retry the build");
                }
                clients.put(identity, client);
                logger.info("[plugin] [PERSISTENT] Worker instance={} pid={} identity={}", client.instance(), client.pid(), identity);
            }
            return client.execute(request, new RuntimeOutput() {
                @Override
                public void out(final String text) { logger.lifecycle(text); }
                @Override
                public void err(final String text) { logger.error(text); }
            });
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Persistent XTC execution interrupted; its lease was released", e);
        } catch (final Exception e) {
            throw new IllegalStateException("Persistent XTC execution failed", e);
        }
    }

    private static String identity(final XtcLauncherRuntime runtime, final List<File> modules,
                                   final String java, final List<String> arguments, final Duration idle, final Duration shutdown)
            throws IOException, NoSuchAlgorithmException {
        final var digest = MessageDigest.getInstance("SHA-256");
        digest.update(DirectRuntimeFingerprint.from(runtime, IsolatedRuntime.codeSource(), modules).toString().getBytes(StandardCharsets.UTF_8));
        final var executable = Path.of(java).toRealPath();
        digest.update(executable.toString().getBytes(StandardCharsets.UTF_8));
        digest.update(Files.readAllBytes(executable));
        final var release = executable.getParent().getParent().resolve("release");
        if (Files.isRegularFile(release)) {
            digest.update(Files.readAllBytes(release));
        }
        digest.update(arguments.toString().getBytes(StandardCharsets.UTF_8));
        digest.update((idle + "/" + shutdown).getBytes(StandardCharsets.UTF_8));
        // A worker inherits its process environment once. Never reuse it for a build requesting a
        // different environment, and never log the environment or persist its values in discovery.
        digest.update(new TreeMap<>(System.getenv()).toString().getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    @Override
    public synchronized void close() {
        clients.values().forEach(WorkerClient::close);
        clients.clear();
    }
}
