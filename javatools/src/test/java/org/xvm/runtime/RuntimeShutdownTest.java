package org.xvm.runtime;

import java.time.Duration;

import java.util.TimerTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.xvm.asm.FileStructure;
import org.xvm.asm.constants.TypeConstant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
class RuntimeShutdownTest {
    @Test
    void requestTerminationPurgesCancelledTasksWithoutCancellingAnotherOwner() {
        try (var runtime = new Runtime()) {
            var survivor = container(runtime);
            var live = alarm();
            survivor.scheduleTimer(live, TimeUnit.DAYS.toMillis(1));
            for (boolean explicitCancel : new boolean[] {false, true}) {
                var owner = container(runtime);
                for (int i = 0; i < 100; i++) {
                    var task = alarm();
                    owner.scheduleTimer(task, TimeUnit.DAYS.toMillis(2));
                    if (explicitCancel) {
                        assertTrue(task.cancel());
                    }
                }
                owner.terminateServices().join();
                assertEquals(0, runtime.purgeCancelledTimers(), "Owner close must already remove cancelled tasks");
            }
            assertTrue(live.cancel(), "Closing other owners must preserve the live alarm");
            survivor.terminateServices().join();
        }
    }

    @Test
    void terminationPurgesTasksCancelledByAsynchronousResourceCleanup() {
        var cleanup = new CompletableFuture<Void>();
        try (var runtime = new Runtime()) {
            var survivor = container(runtime);
            var live = alarm();
            var cancelled = alarm();
            survivor.scheduleTimer(live, TimeUnit.DAYS.toMillis(1));
            survivor.scheduleTimer(cancelled, TimeUnit.DAYS.toMillis(2));
            var owner = container(runtime);
            owner.acquireResource(Object::new, _ -> cleanup.thenRun(cancelled::cancel));
            var stopped = owner.terminateServices();
            assertFalse(stopped.isDone());
            cleanup.complete(null);
            stopped.join();
            assertEquals(0, runtime.purgeCancelledTimers());
            assertTrue(live.cancel());
            survivor.terminateServices().join();
        } finally {
            cleanup.complete(null);
        }
    }

    @Test
    void pendingNativeCleanupPreventsTerminationUntilItCompletes() {
        var cleanup = new CompletableFuture<Void>();
        try (var runtime = new Runtime()) {
            var owner = container(runtime);
            owner.acquireResource(Object::new, _ -> cleanup);
            try {
                assertThrows(IllegalStateException.class, () -> runtime.close(Duration.ZERO));
                assertTrue(runtime.f_executorIO.isTerminated());
                assertFalse(runtime.isTerminated());
                assertThrows(IllegalStateException.class, () -> runtime.close(Duration.ZERO));
                assertFalse(owner.terminateServices().isDone());
            } finally {
                cleanup.complete(null);
            }
            runtime.close(Duration.ZERO);
            assertTrue(runtime.isTerminated());
        }
    }

    @Test
    void repeatedClosePreservesNativeCleanupFailure() {
        var runtime = new Runtime();
        var owner = container(runtime);
        var failure = new IllegalStateException("native cleanup failed");
        owner.acquireResource(Object::new, _ -> CompletableFuture.failedFuture(failure));
        for (int i = 0; i < 2; i++) {
            var thrown = assertThrows(IllegalStateException.class, runtime::close);
            assertEquals(failure, thrown.getCause().getCause());
            assertFalse(runtime.isTerminated());
            assertTrue(runtime.f_executorIO.isTerminated());
            assertTrue(runtime.f_executorXVM.isTerminated());
        }
    }

    @Test
    void noArgumentCloseDispatchesToTheDurationOverride() throws Exception {
        var supplied = new AtomicReference<Duration>();
        var runtime = new Runtime() {
            @Override
            public void close(Duration timeout) {
                supplied.set(timeout);
                super.close(timeout);
            }
        };
        try (AutoCloseable owner = runtime) {
            assertEquals(0, runtime.f_executorXVM.getPoolSize());
        }
        assertEquals(Runtime.DEFAULT_SHUTDOWN_TIMEOUT, supplied.get());

        runtime.close(Duration.ZERO);
        assertEquals(Duration.ZERO, supplied.get());
    }

    @Test
    void closeStopsBothExecutorsAndRejectsNewTimers() throws InterruptedException {
        var started = new CountDownLatch(2);
        var interrupted = new CountDownLatch(2);
        Runnable waiting = () -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        };

        var runtime = new Runtime();
        try (runtime) {
            runtime.f_executorXVM.submit(waiting);
            runtime.f_executorIO.submit(waiting);
            assertTrue(started.await(5, TimeUnit.SECONDS));
        }

        assertTrue(interrupted.await(5, TimeUnit.SECONDS));
        assertTrue(runtime.f_executorIO.isTerminated());
        assertTrue(runtime.f_executorXVM.isTerminated());
        runtime.close();
        assertThrows(IllegalStateException.class, () -> runtime.scheduleTimer(new TimerTask() {
            @Override
            public void run() {}
        }, 1));
    }

    private static Container container(Runtime runtime) {
        return new Container(runtime, null, new FileStructure("test").getModuleId()) {
            @Override
            public ObjectHandle getInjectable(Frame frame, String name, TypeConstant type, ObjectHandle options) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static TimerTask alarm() {
        return new TimerTask() {
            @Override
            public void run() {
                throw new AssertionError("Future test alarm must not execute");
            }
        };
    }
}
