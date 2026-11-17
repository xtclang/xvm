package org.xvm.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The watchdog fails a wait for what the threads and processes do, not for how long the wait takes: a deadlock at
 * once, a wait that nothing works toward after its idle window, and work that takes longer than that window never.
 * Each test has a watchdog of its own, with windows short enough that the test takes seconds, and the one that must
 * not fire long enough that it cannot.
 */
class WatchdogTest {
    @TempDir
    Path directory;

    /** A window that no test reaches, for the verdict that the test must not see. */
    private static final Duration NEVER = Watchdog.LIVELOCK;

    /** An idle window shorter than the work that must still pass. */
    private static final Duration SHORT = Duration.ofSeconds(2);

    @Test
    void aDeadlockFailsAtOnceWithTheDump() throws Exception {
        ReentrantLock first  = new ReentrantLock();
        ReentrantLock second = new ReentrantLock();
        CountDownLatch holding = new CountDownLatch(2);
        Thread one = locker("locker one", first, second, holding);
        Thread two = locker("locker two", second, first, holding);
        // the window of idleness is one that the test never reaches: only the deadlock can fail the wait
        try (Watchdog watchdog = new Watchdog(NEVER, NEVER)) {
            AssertionError hung = assertThrows(AssertionError.class,
                    () -> watchdog.get(new CompletableFuture<>(), "a future the deadlocked threads would complete"));
            String message = hung.getMessage();
            assertTrue(message.contains("hung waiting for a future the deadlocked threads would complete"), message);
            assertTrue(message.contains("are deadlocked"), message);
            assertTrue(message.contains("\"locker one\"") && message.contains("\"locker two\""), message);
            assertTrue(message.contains("Threads of this JVM:"), message);
            assertTrue(message.contains("WatchdogTest.lambda$locker"), message);
        } finally {
            // the locks are taken interruptibly, so this ends the deadlock
            one.interrupt();
            two.interrupt();
            Await.join(one, "the first locker to end");
            Await.join(two, "the second locker to end");
        }
    }

    @Test
    void aLatchThatNothingCountsDownFailsAfterTheIdleWindow() throws Exception {
        try (Watchdog watchdog = new Watchdog(SHORT, NEVER)) {
            AssertionError hung = assertThrows(AssertionError.class,
                    () -> watchdog.await(new CountDownLatch(1), "a latch that nothing counts down"));
            String message = hung.getMessage();
            assertTrue(message.contains("hung waiting for a latch that nothing counts down"), message);
            assertTrue(message.contains("WatchdogTest.aLatchThatNothingCountsDownFailsAfterTheIdleWindow"), message);
            assertTrue(message.contains("nothing used CPU for 2 s"), message);
            assertTrue(message.contains("Threads of this JVM:"), message);
        }
    }

    @Test
    void aBusyComputationLongerThanTheIdleWindowPasses() throws Exception {
        try (Watchdog watchdog = new Watchdog(SHORT, NEVER)) {
            // twice the idle window of work on another thread, while the waiting thread does nothing
            long end = System.nanoTime() + SHORT.multipliedBy(2).toNanos();
            CompletableFuture<Long> busy = CompletableFuture.supplyAsync(() -> {
                long sum = 0;
                while (System.nanoTime() < end) {
                    sum += Long.numberOfTrailingZeros(sum + System.nanoTime());
                }
                return sum;
            });
            assertTrue(watchdog.get(busy, "a busy computation") >= 0);
        }
    }

    @Test
    void aSlowChildProcessPasses() throws Exception {
        Path busy = Files.writeString(directory.resolve("Busy.java"), """
                class Busy {
                    public static void main(String[] args) {
                        long end = System.nanoTime() + Long.parseLong(args[0]);
                        long sum = 0;
                        while (System.nanoTime() < end) {
                            sum += Long.numberOfTrailingZeros(sum + System.nanoTime());
                        }
                        System.exit(sum >= 0 ? 0 : 1);
                    }
                }
                """);
        try (Watchdog watchdog = new Watchdog(SHORT, NEVER)) {
            // twice the idle window of work in a child JVM, while this one only waits for it
            Process child = start(busy, SHORT.multipliedBy(2));
            assertTrue(watchdog.waitFor(child, "a busy child process"));
            assertEquals(0, child.exitValue());
        }
    }

    @Test
    void aWaitThatStaysBusyFailsAtTheLivelockGuard() throws Exception {
        AtomicBoolean spinning = new AtomicBoolean(true);
        Thread spinner = Thread.ofPlatform().daemon().start(() -> {
            long sum = 0;
            while (spinning.get()) {
                sum += Long.numberOfTrailingZeros(sum + System.nanoTime());
            }
        });
        try (Watchdog watchdog = new Watchdog(NEVER, SHORT)) {
            AssertionError hung = assertThrows(AssertionError.class,
                    () -> watchdog.until(() -> false, "a condition that a spinning thread never meets"));
            assertTrue(hung.getMessage().contains("livelock guard"), hung.getMessage());
        } finally {
            spinning.set(false);
            Await.join(spinner, "the spinning thread to end");
        }
    }

    /** A thread that takes {@code first}, says so, then takes {@code second}, both interruptibly. */
    private static Thread locker(String name, ReentrantLock first, ReentrantLock second, CountDownLatch holding) {
        return Thread.ofPlatform().daemon().name(name).start(() -> {
            try {
                first.lockInterruptibly();
                try {
                    holding.countDown();
                    holding.await();
                    second.lockInterruptibly();
                    second.unlock();
                } finally {
                    first.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /** A child JVM that runs {@code source} busy for {@code busy}. */
    private static Process start(Path source, Duration busy) throws IOException {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        return new ProcessBuilder(List.of(java.toString(), source.toString(), String.valueOf(busy.toNanos())))
                .inheritIO()
                .start();
    }
}
