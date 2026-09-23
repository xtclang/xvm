package org.xtclang.plugin.runtime.persistent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import org.xtclang.plugin.runtime.DirectCompileRequest;
import org.xtclang.plugin.runtime.DirectRunRequest;
import org.xtclang.plugin.runtime.DirectTestRequest;
import org.xtclang.plugin.runtime.RuntimeExecutor;
import org.xtclang.plugin.runtime.RuntimeOutput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deterministic host/protocol coverage; no installed XDK, public network, sleeps or GC required. */
@Timeout(30)
class PersistentWorkerTest {
    @TempDir Path directory;
    private static final Duration GUARD = Duration.ofSeconds(10);
    private static final RuntimeOutput SILENT = new RuntimeOutput() {
        public void out(final String text) {}
        public void err(final String text) {}
    };

    @Test
    void requestCodecPreservesAllCompilerRunnerAndTestOptions() throws Exception {
        final var file = directory.toFile();
        final var inputs = List.of(file);
        final var requests = List.of(
            new DirectCompileRequest(file, null, file, file, null, inputs, inputs, true, false, true, false, true, false, "1.2.3"),
            request("run"),
            new DirectTestRequest(file, null, file, file, inputs, false, true, false, "Example", "test", List.of("arg")));
        for (final var request : requests) {
            final var bytes = new ByteArrayOutputStream();
            WorkerProtocol.request(new DataOutputStream(bytes), request);
            assertEquals(request, WorkerProtocol.request(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        }
        final var bytes = new ByteArrayOutputStream();
        new DataOutputStream(bytes).writeInt(Integer.MAX_VALUE);
        assertThrows(IOException.class, () -> WorkerProtocol.text(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }

    @Test
    void separateBuildLeasesReuseOneRuntimeAndKeepTheirOutputSeparate() throws Exception {
        final var closed = new AtomicBoolean();
        final RuntimeExecutor runtime = new RuntimeExecutor() {
            private int requests;
            public int execute(final Object request, final RuntimeOutput output) {
                output.out("request-" + ++requests);
                output.err("diagnostic-" + requests);
                return requests;
            }
            public boolean cancel(final Duration timeout) { return false; }
            public void close(final Duration timeout) { closed.set(true); }
        };
        try (var threads = Executors.newVirtualThreadPerTaskExecutor();
             var worker = new PersistentWorker(runtime, settings(), "secret")) {
            worker.publish(directory);
            final var serving = threads.submit(() -> { worker.serve(); return null; });
            String instance = null;
            for (int expected = 1; expected <= 2; expected++) {
                final var messages = new ArrayList<String>();
                try (var client = WorkerClient.connect(directory, "test", GUARD)) {
                    if (instance != null) {
                        assertEquals(instance, client.instance());
                    }
                    instance = client.instance();
                    assertEquals(expected, client.execute(request("run"), new RuntimeOutput() {
                        public void out(final String text) { messages.add(text); }
                        public void err(final String text) { messages.add(text); }
                    }));
                }
                assertEquals(List.of("request-" + expected, "diagnostic-" + expected), messages);
                assertFalse(closed.get(), "Releasing a build must keep the runtime warm");
            }
            worker.close();
            serving.get(10, TimeUnit.SECONDS);
            assertTrue(closed.get());
        }
    }

    @Test
    void disconnectCancelsItsRequestAndLeavesAnotherLeaseUsable() throws Exception {
        final var started = new CountDownLatch(1);
        final var released = new CompletableFuture<Void>();
        final var cancelled = new AtomicBoolean();
        final RuntimeExecutor runtime = new RuntimeExecutor() {
            public int execute(final Object value, final RuntimeOutput output) {
                if (((DirectRunRequest) value).methodName().equals("blocked")) {
                    started.countDown();
                    released.join();
                    return 1;
                }
                return 7;
            }
            public boolean cancel(final Duration timeout) {
                cancelled.set(true);
                released.complete(null);
                return true;
            }
            public void close(final Duration timeout) {}
        };
        try (var threads = Executors.newVirtualThreadPerTaskExecutor();
             var worker = new PersistentWorker(runtime, settings(), "secret")) {
            worker.publish(directory);
            final var serving = threads.submit(() -> { worker.serve(); return null; });
            try (var first = WorkerClient.connect(directory, "test", GUARD);
                 var second = WorkerClient.connect(directory, "test", GUARD)) {
                final var blocked = threads.submit(() -> first.execute(request("blocked"), SILENT));
                try {
                    assertTrue(started.await(10, TimeUnit.SECONDS));
                    first.close();
                    assertThrows(ExecutionException.class, () -> blocked.get(10, TimeUnit.SECONDS));
                    assertEquals(7, second.execute(request("run"), SILENT));
                    assertTrue(cancelled.get());
                } finally {
                    released.complete(null);
                }
            }
            worker.close();
            serving.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void infrastructureFailureRetiresWorkerWithoutReplayingTheRequest() throws Exception {
        final var calls = new ArrayList<Object>();
        final RuntimeExecutor runtime = new RuntimeExecutor() {
            public int execute(final Object request, final RuntimeOutput output) {
                calls.add(request);
                throw new IllegalStateException("broken runtime");
            }
            public boolean cancel(final Duration timeout) { return false; }
            public void close(final Duration timeout) {}
        };
        try (var threads = Executors.newVirtualThreadPerTaskExecutor();
             var worker = new PersistentWorker(runtime, settings(), "secret")) {
            worker.publish(directory);
            final var serving = threads.submit(() -> { worker.serve(); return null; });
            try (var client = WorkerClient.connect(directory, "test", GUARD)) {
                assertThrows(IOException.class, () -> client.execute(request("run"), SILENT));
            }
            serving.get(10, TimeUnit.SECONDS);
            assertEquals(1, calls.size());
        }
    }

    @Test
    void idleExpiryUsesTheConfiguredBudgetWithoutWaitingForTimeToPass() throws Exception {
        final var clock = new AtomicLong();
        final RuntimeExecutor runtime = new RuntimeExecutor() {
            public int execute(final Object request, final RuntimeOutput output) { return 0; }
            public boolean cancel(final Duration timeout) { return false; }
            public void close(final Duration timeout) {}
        };
        try (var worker = new PersistentWorker(runtime, settings(), "secret", clock::get)) {
            assertFalse(worker.idleExpired());
            clock.set(settings().idleTimeout().toNanos() - 1);
            assertFalse(worker.idleExpired());
            clock.incrementAndGet();
            assertTrue(worker.idleExpired());
        }
    }

    @Test
    void explicitStopRefusesToInvalidateAnotherBuildLease() throws Exception {
        final RuntimeExecutor runtime = new RuntimeExecutor() {
            public int execute(final Object request, final RuntimeOutput output) { return 7; }
            public boolean cancel(final Duration timeout) { return false; }
            public void close(final Duration timeout) {}
        };
        try (var threads = Executors.newVirtualThreadPerTaskExecutor();
             var worker = new PersistentWorker(runtime, settings(), "secret")) {
            worker.publish(directory);
            final var serving = threads.submit(() -> { worker.serve(); return null; });
            try (var active = WorkerClient.connect(directory, "test", GUARD);
                 var stopper = WorkerClient.connect(directory, "test", GUARD)) {
                assertThrows(IOException.class, () -> stopper.stop(GUARD));
                assertEquals(7, active.execute(request("run"), SILENT));
            }
            worker.close();
            serving.get(10, TimeUnit.SECONDS);
        }
    }

    private WorkerSettings settings() {
        return new WorkerSettings("test", List.of(), List.of(), Duration.ofMinutes(10), GUARD);
    }

    private DirectRunRequest request(final String method) {
        return new DirectRunRequest(directory.toFile(), null, null, List.of(new File("module.xtc").getAbsoluteFile()),
            false, false, false, "Example", method, List.of("argument with spaces", "åäö"));
    }
}
