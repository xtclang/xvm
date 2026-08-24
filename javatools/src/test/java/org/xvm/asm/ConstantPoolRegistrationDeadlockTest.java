package org.xvm.asm;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.xvm.asm.constants.StringConstant;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards {@link ConstantPool#register} against blocking on another thread's registration
 * completion while holding the pool monitor.
 *
 * <p>The registration-completion guard publishes a constant before its recursive child
 * registration finishes and makes other threads wait for completion. The pre-fix shape awaited
 * that completion <em>inside</em> {@code synchronized (this)} when the double-checked lookup found
 * a concurrently inserted constant. The registering thread must reacquire the same monitor to
 * publish its completion, so the waiter deadlocked the pool: the waiter parked on the completion
 * future holding the monitor, and every other pool user (including the completing thread) blocked
 * on monitor entry. This hung the whole test JVM in
 * {@code MethodInfoTest.typeInfoConstructionCopiesMethodInfoInParallel} with eight threads racing
 * {@code ConstantPool.register} on one pool.
 */
class ConstantPoolRegistrationDeadlockTest {
    private static final String VALUE = "registration-deadlock-probe";

    /**
     * Deterministically reproduces the deadlock interleaving. Thread B publishes a constant and
     * stalls in its recursive registration phase (published but incomplete, monitor released).
     * Thread A races registration of an equal constant, misses the pre-monitor lookup, and finds
     * B's constant in the double-checked lookup, so A must wait for B's completion. B is then only
     * allowed to finish after A has parked in {@code awaitRegistrationComplete}. On the pre-fix
     * shape A parks while holding the pool monitor, B blocks forever trying to publish its
     * completion, and this test fails by join timeout. With the fix A waits outside the monitor,
     * B completes, and A returns B's registered constant.
     */
    @Test
    void concurrentInsertWaitsForCompletionWithoutHoldingPoolMonitor() throws Exception {
        var poolTarget = new FileStructure("DeadlockTarget").getConstantPool();
        var poolOther  = new FileStructure("DeadlockOther").getConstantPool();

        var aInsideAdoption     = new CountDownLatch(1);
        var bPublishedIncomplete = new CountDownLatch(1);
        var bMayFinish          = new CountDownLatch(1);

        var xB = new LatchStringConstant(poolTarget, bPublishedIncomplete, bMayFinish, null, null);
        var xA = new LatchStringConstant(poolOther, null, null, aInsideAdoption, bPublishedIncomplete);

        var resultA  = new AtomicReference<Constant>();
        var failureA = new AtomicReference<Throwable>();
        var failureB = new AtomicReference<Throwable>();

        var threadA = new Thread(() -> {
            try {
                resultA.set(poolTarget.register(xA));
            } catch (Throwable e) {
                failureA.set(e);
            }
        }, "deadlock-test-A");
        var threadB = new Thread(() -> {
            try {
                poolTarget.register(xB);
            } catch (Throwable e) {
                failureB.set(e);
            }
        }, "deadlock-test-B");
        threadA.setDaemon(true);
        threadB.setDaemon(true);

        threadA.start();
        assertTrue(aInsideAdoption.await(10, TimeUnit.SECONDS),
                "thread A never reached the pre-monitor adoption hook");

        // A has passed its pre-monitor lookup (miss) and is parked before entering the monitor;
        // B now publishes the equal constant and stalls incomplete in recursive registration
        threadB.start();
        assertTrue(bPublishedIncomplete.await(10, TimeUnit.SECONDS),
                "thread B never published its incomplete registration");

        assertTrue(awaitParkedInRegistrationWait(threadA),
                "thread A never parked waiting for B's registration completion");

        // only now may B finish; finishing requires the pool monitor, which A must not be holding
        bMayFinish.countDown();

        threadB.join(10_000);
        threadA.join(10_000);
        if (threadA.isAlive() || threadB.isAlive()) {
            // pre-fix shape: A parks on the completion future holding the pool monitor and B
            // blocks forever publishing its completion; unwedge the threads so the JVM can exit
            threadA.interrupt();
            threadB.interrupt();
            fail("deadlock: register() waited for another thread's registration completion"
                    + " while holding the pool monitor");
        }

        assertNull(failureA.get(), () -> "thread A failed: " + failureA.get());
        assertNull(failureB.get(), () -> "thread B failed: " + failureB.get());
        assertSame(xB, resultA.get(),
                "the racing registration must return the concurrently registered constant");
    }

    private static boolean awaitParkedInRegistrationWait(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (thread.getState() == Thread.State.WAITING && inRegistrationWait(thread)) {
                return true;
            }
            Thread.sleep(5);
        }
        return false;
    }

    private static boolean inRegistrationWait(Thread thread) {
        for (var frame : thread.getStackTrace()) {
            if ("awaitRegistrationComplete".equals(frame.getMethodName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * A string constant with test seams at the two points the deadlock interleaving needs:
     * the pre-monitor adoption copy (thread A) and the post-publication recursive registration
     * phase (thread B). The hooks contain no synchronization on the pool, so they cannot mask or
     * cause the deadlock themselves.
     */
    private static final class LatchStringConstant extends StringConstant {
        private final CountDownLatch signalOnRegisterConstants;
        private final CountDownLatch awaitInRegisterConstants;
        private final CountDownLatch signalOnAdoption;
        private final CountDownLatch awaitInAdoption;

        LatchStringConstant(ConstantPool pool,
                            CountDownLatch signalOnRegisterConstants,
                            CountDownLatch awaitInRegisterConstants,
                            CountDownLatch signalOnAdoption,
                            CountDownLatch awaitInAdoption) {
            super(pool, VALUE);
            this.signalOnRegisterConstants = signalOnRegisterConstants;
            this.awaitInRegisterConstants  = awaitInRegisterConstants;
            this.signalOnAdoption          = signalOnAdoption;
            this.awaitInAdoption           = awaitInAdoption;
        }

        @Override
        protected StringConstant copyForAdoption(AdoptionContext context) {
            if (signalOnAdoption != null) {
                signalOnAdoption.countDown();
            }
            awaitQuietly(awaitInAdoption, "adoption hook");
            return new LatchStringConstant(context.pool(), null, null, null, null);
        }

        @Override
        public void registerConstants(ConstantPool pool) {
            if (signalOnRegisterConstants != null) {
                signalOnRegisterConstants.countDown();
            }
            awaitQuietly(awaitInRegisterConstants, "recursive registration hook");
            super.registerConstants(pool);
        }

        private static void awaitQuietly(CountDownLatch latch, String where) {
            if (latch == null) {
                return;
            }
            try {
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out in " + where);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted in " + where, e);
            }
        }
    }
}
