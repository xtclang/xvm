package org.xvm.runtime;

import java.time.Duration;

import java.util.TimerTask;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(30)
class RuntimeShutdownTest {
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
}
