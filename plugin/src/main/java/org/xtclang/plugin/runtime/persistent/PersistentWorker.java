package org.xtclang.plugin.runtime.persistent;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

import org.xtclang.plugin.runtime.IsolatedRuntime;
import org.xtclang.plugin.runtime.RuntimeExecutor;
import org.xtclang.plugin.runtime.RuntimeOutput;

/**
 * Headless owner of one compatible runtime, independent of any Gradle invocation. Each authenticated
 * connection is a build lease. Requests run serially; disconnect cancels only that connection's
 * pending request. A failed runtime is retired, never reused or transparently replayed.
 */
public final class PersistentWorker implements AutoCloseable {
    private static final Duration ACCEPT_POLL = Duration.ofSeconds(1);
    private final RuntimeExecutor runtime;
    private final WorkerSettings settings;
    private final ServerSocket listener;
    private final String token;
    private final String instance = UUID.randomUUID().toString();
    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final ExecutorService execution = Executors.newSingleThreadExecutor();
    private final LongSupplier clock;
    private volatile long lastActivity;

    PersistentWorker(final RuntimeExecutor runtime, final WorkerSettings settings, final String token) throws IOException {
        this(runtime, settings, token, System::nanoTime);
    }

    PersistentWorker(final RuntimeExecutor runtime, final WorkerSettings settings, final String token, final LongSupplier clock) throws IOException {
        this.runtime = runtime;
        this.settings = settings;
        this.clock = clock;
        lastActivity = clock.getAsLong();
        this.token = token;
        listener = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
        listener.setSoTimeout((int) Math.min(ACCEPT_POLL.toMillis(), Math.max(1, settings.idleTimeout().toMillis())));
    }

    /**
     * Start only after the launcher has written immutable settings. The lifetime lock prevents a
     * stale discovery entry or concurrent starter from producing two owners of the same image.
     */
    public static void main(final String[] args) throws Exception {
        final var directory = Path.of(args[0]);
        try (var channel = FileChannel.open(directory.resolve("lifetime.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) {
                throw new IllegalStateException("Worker image already has an owner");
            }
            final var settings = WorkerSettings.read(directory.resolve("settings.bin"));
            final var token = Files.readString(directory.resolve("token"));
            try (var worker = new PersistentWorker(new IsolatedRuntime(settings.classpath(), settings.coreModules()), settings, token)) {
                worker.publish(directory);
                try {
                    worker.serve();
                } finally {
                    Files.deleteIfExists(directory.resolve("endpoint.properties"));
                }
            }
        } catch (final Throwable failure) {
            failure.printStackTrace(System.err);
            // This dedicated process may contain uncooperative native work. The failed operation
            // is not replayed; process exit is the final isolation boundary after bounded cleanup.
            System.exit(1);
        }
    }

    void publish(final Path directory) throws IOException {
        final var properties = new Properties();
        properties.setProperty("identity", settings.identity());
        properties.setProperty("instance", instance);
        properties.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
        properties.setProperty("port", Integer.toString(listener.getLocalPort()));
        properties.setProperty("token", token);
        final var temporary = directory.resolve("endpoint.tmp");
        try (var out = Files.newOutputStream(temporary)) {
            properties.store(out, "Local XTC worker endpoint");
        }
        Files.move(temporary, directory.resolve("endpoint.properties"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    void serve() throws IOException {
        while (!stopping.get()) {
            try {
                final var socket = listener.accept();
                socket.setSoTimeout((int) Math.min(Integer.MAX_VALUE, Math.max(1, settings.shutdownTimeout().toMillis())));
                synchronized (connections) {
                    if (stopping.get()) {
                        socket.close();
                        break;
                    }
                    try {
                        final var connection = new Connection(socket);
                        connections.add(connection);
                        Thread.ofVirtual().name("XtcWorkerLease").start(connection);
                    } catch (final IOException failure) {
                        socket.close();
                        throw failure;
                    }
                }
            } catch (final SocketTimeoutException idle) {
                if (idleExpired()) {
                    requestStop();
                }
            } catch (final IOException failure) {
                if (!stopping.get()) {
                    throw failure;
                }
            }
        }
    }

    boolean idleExpired() {
        return connections.isEmpty() && clock.getAsLong() - lastActivity >= settings.idleTimeout().toNanos();
    }

    private void requestStop() {
        synchronized (connections) {
            if (stopping.compareAndSet(false, true)) {
                try {
                    listener.close();
                } catch (final IOException ignored) {
                    // Closing the listener only wakes accept; runtime cleanup is awaited by close().
                }
                connections.forEach(Connection::disconnect);
            }
        }
    }

    @Override
    public void close() {
        requestStop();
        execution.shutdownNow();
        final long deadline = System.nanoTime() + settings.shutdownTimeout().toNanos();
        try {
            if (!execution.awaitTermination(settings.shutdownTimeout().toNanos(), TimeUnit.NANOSECONDS)) {
                throw new IllegalStateException("Worker execution did not stop within its shutdown budget");
            }
            runtime.close(Duration.ofNanos(Math.max(0, deadline - System.nanoTime())));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during worker shutdown", e);
        }
    }

    private final class Connection implements Runnable {
        private final Socket socket;
        private final DataInputStream input;
        private final DataOutputStream output;
        private volatile Job pending;

        Connection(final Socket socket) throws IOException {
            this.socket = socket;
            input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        @Override
        public void run() {
            try (socket) {
                if (input.readInt() != WorkerProtocol.VERSION || !token.equals(WorkerProtocol.text(input))
                        || !settings.identity().equals(WorkerProtocol.text(input))) {
                    return;
                }
                WorkerProtocol.text(output, instance);
                output.writeLong(ProcessHandle.current().pid());
                output.flush();
                socket.setSoTimeout(0);
                while (!stopping.get()) {
                    switch (input.readInt()) {
                        case WorkerProtocol.RUN -> {
                            final var request = WorkerProtocol.request(input);
                            if (pending != null) {
                                throw new IOException("Only one outstanding request is allowed per build lease");
                            }
                            final var job = new Job(request);
                            pending = job;
                            execution.execute(job);
                        }
                        case WorkerProtocol.STOP -> {
                            synchronized (connections) {
                                if (connections.size() != 1 || pending != null) {
                                    message(WorkerProtocol.FAILURE, "Worker is leased by another build");
                                } else {
                                    message(WorkerProtocol.RESULT, "0");
                                    requestStop();
                                }
                            }
                            return;
                        }
                        default -> throw new IOException("Unknown worker command");
                    }
                }
            } catch (final EOFException disconnected) {
                // EOF releases this build lease; it is not permission to abandon its work.
            } catch (final IOException failure) {
                // A lost connection follows the same cancellation path as an orderly disconnect.
            } finally {
                final var job = pending;
                if (job != null) {
                    job.cancel();
                }
                connections.remove(this);
                lastActivity = clock.getAsLong();
            }
        }

        synchronized void message(final int kind, final String text) {
            try {
                output.writeInt(kind);
                WorkerProtocol.text(output, text);
                output.flush();
            } catch (final IOException failure) {
                disconnect();
            }
        }

        void disconnect() {
            try {
                socket.close();
            } catch (final IOException ignored) {
                // The reader's finally block owns cancellation and lease removal.
            }
        }

        private final class Job implements Runnable {
            private final Object request;
            private Thread thread;
            private boolean cancelled;

            Job(final Object request) {
                this.request = request;
            }

            synchronized void cancel() {
                cancelled = true;
                if (thread != null) {
                    try {
                        if (!runtime.cancel(settings.shutdownTimeout())) {
                            thread.interrupt();
                        }
                    } catch (final RuntimeException failure) {
                        requestStop();
                    }
                }
            }

            @Override
            public void run() {
                synchronized (this) {
                    if (cancelled) {
                        pending = null;
                        return;
                    }
                    thread = Thread.currentThread();
                }
                int result = 1;
                Throwable failure = null;
                try {
                    result = runtime.execute(request, new RuntimeOutput() {
                        @Override
                        public void out(final String text) { message(WorkerProtocol.OUT, text); }
                        @Override
                        public void err(final String text) { message(WorkerProtocol.ERR, text); }
                    });
                } catch (final Throwable problem) {
                    failure = problem;
                } finally {
                    synchronized (this) {
                        thread = null;
                        Thread.interrupted();
                    }
                    pending = null;
                    lastActivity = clock.getAsLong();
                }
                if (failure == null) {
                    message(WorkerProtocol.RESULT, Integer.toString(result));
                } else {
                    message(WorkerProtocol.FAILURE, failure.toString());
                    requestStop();
                }
            }
        }
    }
}
