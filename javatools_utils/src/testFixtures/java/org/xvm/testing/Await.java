package org.xvm.testing;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/**
 * The waits of a test, each until what it waits for happens: a hang fails it when nothing has used CPU for a minute, a
 * deadlock at once, and a livelock after {@link #LIVELOCK}, as the {@linkplain Watchdog#shared() shared watchdog}
 * finds them. None has a limit of time that a slow machine could reach. Each takes, optionally, what it waits for, for
 * the failure's message, which otherwise names the object waited on and the line that waits.
 */
public final class Await {
    /**
     * The livelock guard, {@link Watchdog#LIVELOCK}, for a limit that the code under test takes, such as a worker's
     * startup, so that only a livelock reaches it, as only one reaches a wait's.
     */
    public static final Duration LIVELOCK = Watchdog.LIVELOCK;

    /** {@link #LIVELOCK} in seconds, a constant for annotations such as JUnit's {@code @Timeout}. */
    public static final long LIVELOCK_SECONDS = Watchdog.LIVELOCK_SECONDS;

    private Await() {
    }

    public static <T> T get(Future<T> future) throws InterruptedException, ExecutionException {
        return get(future, null);
    }

    public static <T> T get(Future<T> future, Object what) throws InterruptedException, ExecutionException {
        return Watchdog.shared().get(future, what);
    }

    public static boolean await(CountDownLatch latch) throws InterruptedException {
        return await(latch, null);
    }

    public static boolean await(CountDownLatch latch, Object what) throws InterruptedException {
        return Watchdog.shared().await(latch, what);
    }

    public static int await(CyclicBarrier barrier) throws InterruptedException, BrokenBarrierException {
        return await(barrier, null);
    }

    public static int await(CyclicBarrier barrier, Object what) throws InterruptedException, BrokenBarrierException {
        return Watchdog.shared().await(barrier, what);
    }

    public static <T> T poll(BlockingQueue<T> queue) throws InterruptedException {
        return poll(queue, null);
    }

    public static <T> T poll(BlockingQueue<T> queue, Object what) throws InterruptedException {
        return Watchdog.shared().poll(queue, what);
    }

    public static boolean tryAcquire(Semaphore semaphore) throws InterruptedException {
        return tryAcquire(semaphore, 1, null);
    }

    public static boolean tryAcquire(Semaphore semaphore, int permits, Object what) throws InterruptedException {
        return Watchdog.shared().tryAcquire(semaphore, permits, what);
    }

    public static boolean waitFor(Process process) throws InterruptedException {
        return waitFor(process, null);
    }

    public static boolean waitFor(Process process, Object what) throws InterruptedException {
        return Watchdog.shared().waitFor(process, what);
    }

    public static boolean awaitTermination(ExecutorService executor) throws InterruptedException {
        return awaitTermination(executor, null);
    }

    public static boolean awaitTermination(ExecutorService executor, Object what) throws InterruptedException {
        return Watchdog.shared().awaitTermination(executor, what);
    }

    public static void join(Thread thread) throws InterruptedException {
        join(thread, null);
    }

    public static void join(Thread thread, Object what) throws InterruptedException {
        Watchdog.shared().join(thread, what);
    }

    public static void until(BooleanSupplier condition) {
        until(condition, null);
    }

    public static void until(BooleanSupplier condition, Object what) {
        Watchdog.shared().until(condition, what);
    }

    public static void untilAsserted(Watchdog.Action assertion) {
        untilAsserted(assertion, null);
    }

    public static void untilAsserted(Watchdog.Action assertion, Object what) {
        Watchdog.shared().untilAsserted(assertion, what);
    }

    /**
     * Run the block on a thread of its own and wait for it, in place of JUnit's {@code assertTimeoutPreemptively}.
     */
    public static <T> T call(Watchdog.Block<T> block) {
        return call(null, block);
    }

    public static <T> T call(Object what, Watchdog.Block<T> block) {
        return Watchdog.shared().call(what == null ? block : what, block);
    }

    public static void run(Watchdog.Action action) {
        run(null, action);
    }

    public static void run(Object what, Watchdog.Action action) {
        Watchdog.shared().run(what == null ? action : what, action);
    }
}
