package org.xtclang.plugin.runtime.persistent;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.xtclang.plugin.runtime.RuntimeOutput;

/**
 * One build's lease on a persistent worker. Close releases the connection; it does not stop a
 * healthy idle worker. Interrupted calls close their connection so the server cancels that request.
 * A connection failure is reported, never retried after a request may have begun executing.
 */
public final class WorkerClient implements AutoCloseable {
    private static final Duration STARTUP_POLL = Duration.ofMillis(50);
    private final Socket socket;
    private final DataOutputStream output;
    private final String instance;
    private final long pid;
    private volatile Response pending;

    private record Response(CompletableFuture<Integer> result, RuntimeOutput output) {}

    private WorkerClient(final Socket socket, final DataInputStream input, final DataOutputStream output,
                         final String instance, final long pid) {
        this.socket = socket;
        this.output = output;
        this.instance = instance;
        this.pid = pid;
        Thread.ofVirtual().name("XtcWorkerOutput").start(() -> receive(input));
    }

    public String instance() { return instance; }
    public long pid() { return pid; }

    /**
     * Connect before starting another process. Discovery/startup is serialized with a file lock,
     * and a separate lifetime lock prevents overwriting a still-running worker's immutable image.
     * Only this pre-request phase may start a replacement; execution is never replayed.
     */
    public static WorkerClient start(final WorkerLaunch launch) throws IOException, InterruptedException {
        final var directory = launch.directory();
        Files.createDirectories(directory);
        try {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        } catch (final UnsupportedOperationException ignored) {
            // Windows uses the checkout owner's inherited ACL instead of POSIX permissions.
        }
        final long deadline = System.nanoTime() + launch.startupTimeout().toNanos();
        try (var channel = FileChannel.open(directory.resolve("startup.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = acquireLock(channel, deadline)) {
            try {
                return connect(directory, launch.settings().identity(), remaining(deadline));
            } catch (final IOException absent) {
                // A dead or incompatible endpoint can be replaced before any request is sent.
            }
            try (var lifetime = FileChannel.open(directory.resolve("lifetime.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var ownership = acquireLock(lifetime, deadline)) {
                Files.deleteIfExists(directory.resolve("endpoint.properties"));
                removeImages(directory);
                final var image = Files.createTempDirectory(directory, "image-");
                final var plugin = copy(launch.pluginSource(), image.resolve("plugin"));
                final var classpath = copyAll(launch.settings().classpath(), image.resolve("runtime"));
                final var modules = copyAll(launch.settings().coreModules(), image.resolve("modules"));
                new WorkerSettings(launch.settings().identity(), classpath, modules,
                    launch.settings().idleTimeout(), launch.settings().shutdownTimeout()).write(directory.resolve("settings.bin"));
                Files.writeString(directory.resolve("token"), UUID.randomUUID().toString() + UUID.randomUUID());
                final var command = new ArrayList<String>();
                command.add(launch.javaExecutable());
                command.addAll(launch.jvmArgs());
                command.add("-cp");
                command.add(plugin.getAbsolutePath());
                command.add(PersistentWorker.class.getName());
                command.add(directory.toString());
                // Release the lifetime lock before the new owner tries to acquire it. The startup
                // lock still prevents another client from publishing or replacing this image.
                ownership.release();
                final var process = new ProcessBuilder(command)
                    .directory(directory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(directory.resolve("worker.log").toFile()))
                    .start();
                process.getOutputStream().close();
                try {
                    while (process.isAlive()) {
                        try {
                            return connect(directory, launch.settings().identity(), remaining(deadline));
                        } catch (final IOException notReady) {
                            pause(deadline);
                        }
                    }
                    throw new IOException("XTC worker exited during startup; see " + directory.resolve("worker.log"));
                } catch (final IOException | InterruptedException failure) {
                    process.destroy();
                    try {
                        if (!process.waitFor(launch.settings().shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                            process.destroyForcibly();
                        }
                    } catch (final InterruptedException interrupted) {
                        process.destroyForcibly();
                        failure.addSuppressed(interrupted);
                        Thread.currentThread().interrupt();
                    }
                    throw failure;
                }
            }
        }
    }

    private static FileLock acquireLock(final FileChannel channel, final long deadline) throws IOException, InterruptedException {
        while (true) {
            try {
                final var lock = channel.tryLock();
                if (lock != null) {
                    return lock;
                }
            } catch (final OverlappingFileLockException occupied) {
                // Composite builds can have distinct plugin loaders in the same Gradle JVM.
            }
            pause(deadline);
        }
    }

    private static void pause(final long deadline) throws IOException, InterruptedException {
        TimeUnit.NANOSECONDS.sleep(Math.min(STARTUP_POLL.toNanos(), remaining(deadline).toNanos()));
    }

    private static Duration remaining(final long deadline) throws IOException {
        final long nanos = deadline - System.nanoTime();
        if (nanos <= 0) {
            throw new IOException("Timed out starting or connecting to XTC worker");
        }
        return Duration.ofNanos(nanos);
    }

    /** Delete only this launcher's old images, while holding both startup and lifetime locks. */
    private static void removeImages(final Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            for (final var image : entries.filter(path -> path.getFileName().toString().startsWith("image-")).toList()) {
                try (var paths = Files.walk(image)) {
                    for (final var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(path);
                    }
                }
            }
        }
    }

    private static List<File> copyAll(final List<File> sources, final Path target) throws IOException {
        final var result = new ArrayList<File>();
        for (int i = 0; i < sources.size(); i++) {
            result.add(copy(sources.get(i), target.resolve(Integer.toString(i))));
        }
        return List.copyOf(result);
    }

    private static File copy(final File source, final Path parent) throws IOException {
        Files.createDirectories(parent);
        final var target = parent.resolve(source.getName());
        if (source.isDirectory()) {
            try (var files = Files.walk(source.toPath())) {
                for (final var path : files.toList()) {
                    final var destination = target.resolve(source.toPath().relativize(path));
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }
            }
        } else {
            Files.copy(source.toPath(), target, StandardCopyOption.COPY_ATTRIBUTES);
        }
        return target.toFile();
    }

    static WorkerClient connect(final Path directory, final String identity, final Duration timeout) throws IOException {
        final var properties = new Properties();
        try (var in = Files.newInputStream(directory.resolve("endpoint.properties"))) {
            properties.load(in);
        }
        if (!identity.equals(properties.getProperty("identity"))) {
            throw new IOException("Incompatible worker identity");
        }
        final var socket = new Socket();
        try {
            final int millis = (int) Math.min(Integer.MAX_VALUE, Math.max(1, timeout.toMillis()));
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), Integer.parseInt(properties.getProperty("port"))), millis);
            socket.setSoTimeout(millis);
            final var input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            final var output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            output.writeInt(WorkerProtocol.VERSION);
            WorkerProtocol.text(output, properties.getProperty("token"));
            WorkerProtocol.text(output, identity);
            output.flush();
            final var instance = WorkerProtocol.text(input);
            final long pid = input.readLong();
            if (!properties.getProperty("instance").equals(instance)
                    || pid != Long.parseLong(properties.getProperty("pid"))) {
                throw new IOException("Stale worker endpoint");
            }
            socket.setSoTimeout(0);
            return new WorkerClient(socket, input, output, instance, pid);
        } catch (final IOException | RuntimeException failure) {
            socket.close();
            throw new IOException("Unable to connect to XTC worker", failure);
        }
    }

    /** Send one request, await its final result, then release its output sink. Never retry here. */
    public synchronized int execute(final Object request, final RuntimeOutput sink) throws IOException, InterruptedException {
        final var response = new Response(new CompletableFuture<>(), sink);
        pending = response;
        try {
            output.writeInt(WorkerProtocol.RUN);
            WorkerProtocol.request(output, request);
            output.flush();
            return response.result().get();
        } catch (final InterruptedException interrupted) {
            close();
            throw interrupted;
        } catch (final ExecutionException e) {
            throw new IOException("Persistent XTC execution failed; request was not replayed", e.getCause());
        } finally {
            pending = null;
            if (!response.result().isDone()) {
                close();
            }
        }
    }

    private void receive(final DataInputStream input) {
        try {
            while (true) {
                final int kind = input.readInt();
                final var text = WorkerProtocol.text(input);
                final var response = pending;
                if (response == null) {
                    throw new IOException("Unexpected worker response");
                }
                switch (kind) {
                    case WorkerProtocol.OUT -> response.output().out(text);
                    case WorkerProtocol.ERR -> response.output().err(text);
                    case WorkerProtocol.RESULT -> response.result().complete(Integer.parseInt(text));
                    case WorkerProtocol.FAILURE -> response.result().completeExceptionally(new IOException(text));
                    default -> throw new IOException("Unknown worker response");
                }
            }
        } catch (final IOException | RuntimeException failure) {
            final var response = pending;
            if (response != null) {
                response.result().completeExceptionally(failure);
            }
            close();
        }
    }

    /** Stop idle workers explicitly; an active build lease prevents a stop. */
    public static int stopAll(final Path home, final Duration timeout) throws IOException, InterruptedException {
        if (!Files.isDirectory(home)) {
            return 0;
        }
        int stopped = 0;
        try (var paths = Files.list(home)) {
            for (final var directory : paths.filter(Files::isDirectory).toList()) {
                final var endpoint = directory.resolve("endpoint.properties");
                if (!Files.exists(endpoint)) {
                    continue;
                }
                final var properties = new Properties();
                try (var in = Files.newInputStream(endpoint)) {
                    properties.load(in);
                }
                if (ProcessHandle.of(Long.parseLong(properties.getProperty("pid"))).isEmpty()) {
                    continue;
                }
                final long deadline = System.nanoTime() + timeout.toNanos();
                try (var client = connect(directory, properties.getProperty("identity"), remaining(deadline))) {
                    client.stop(remaining(deadline));
                    final var process = ProcessHandle.of(client.pid);
                    if (process.isPresent()) {
                        try {
                            process.get().onExit().get(remaining(deadline).toNanos(), TimeUnit.NANOSECONDS);
                        } catch (final ExecutionException | TimeoutException failure) {
                            throw new IOException("Worker did not finish shutdown", failure);
                        }
                    }
                    stopped++;
                }
            }
        }
        return stopped;
    }

    synchronized void stop(final Duration timeout) throws IOException, InterruptedException {
        final var response = new Response(new CompletableFuture<>(), new RuntimeOutput() {
            public void out(final String text) {}
            public void err(final String text) {}
        });
        pending = response;
        try {
            output.writeInt(WorkerProtocol.STOP);
            output.flush();
            response.result().get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (final ExecutionException | TimeoutException failure) {
            throw new IOException("Unable to stop worker", failure.getCause());
        } finally {
            pending = null;
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (final IOException ignored) {
            // Closing is idempotent; EOF makes the server release this lease.
        }
    }
}
