package org.xvm.runtime;


import java.util.Timer;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Op;

import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.xBoolean;

import org.xvm.runtime.template.numbers.LongLong;
import org.xvm.runtime.template.numbers.xInt128;

import org.xvm.runtime.template._native.temporal.xLocalClock;
import org.xvm.runtime.template._native.temporal.xNanosTimer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * Behavioral tests for the native alarm callback registry and for keep-alive ownership on the
 * alarm-scheduling failure path. These boot a real {@link NativeContainer} and drive the real
 * runtime classes; nothing here inspects source text.
 */
public class NativeCallbackRegistrationTest {
    /**
     * The registry is shared between the service thread that registers callbacks and the
     * process-wide Java timer thread that extracts them when an alarm matures, so it must be a live
     * concurrent map from the moment the service exists. It used to be a plain HashMap published
     * lazily through a non-final field, which meant it was null until someone happened to register
     * the first callback, and unsynchronized forever after.
     */
    @Test
    public void callbackRegistryIsLiveBeforeAnyCallbackIsRegistered() {
        assumeTrue(RuntimeTestSupport.systemModulesAvailable(), "compiled XDK system modules are required");

        ServiceContext context = RuntimeTestSupport.newContainer().createServiceContext("registry");

        assertNotNull(context.getCallbackMap(),
                "the callback registry must exist before the first callback is registered");
        assertTrue(context.getCallbackMap().isEmpty(),
                "a fresh service must start with an empty callback registry");
    }

    /**
     * Extraction runs on the shared Java timer thread. A callback that is already gone - because
     * the service was collected, or because a racing cancel discarded it - used to raise
     * IllegalStateException there. An exception escaping a TimerTask kills the shared static Timer,
     * which silently disables every alarm in every container in the process, so a missing callback
     * has to be an ordinary null result instead.
     */
    @Test
    public void extractingAMissingCallbackReturnsNullInsteadOfThrowing() {
        assumeTrue(RuntimeTestSupport.systemModulesAvailable(), "compiled XDK system modules are required");

        ServiceContext context = RuntimeTestSupport.newContainer().createServiceContext("extract");
        WeakCallback   ref     = new WeakCallback(RuntimeTestSupport.entryFrame(context), null);

        assertNotNull(ref.extractCallback(), "the first extraction must hand back the callback");
        assertNull(ref.extractCallback(),
                "extracting an already-extracted callback must not throw on the timer thread");
        assertTrue(context.getCallbackMap().isEmpty(),
                "extraction must remove the entry from the registry");
    }

    /**
     * The real cross-thread access pattern: the owning service registers callbacks on its own
     * thread while previously scheduled alarms mature and extract them on the Java timer thread,
     * with no monitor in common. Against the old plain HashMap a put that resizes the table racing
     * a remove could lose an entry or corrupt the map outright.
     */
    @Test
    public void registryToleratesConcurrentServiceAndTimerThreadAccess() throws InterruptedException {
        assumeTrue(RuntimeTestSupport.systemModulesAvailable(), "compiled XDK system modules are required");

        ServiceContext context = RuntimeTestSupport.newContainer().createServiceContext("race");
        Frame          frame   = RuntimeTestSupport.entryFrame(context);

        var pending   = new ArrayBlockingQueue<WeakCallback>(QUEUE_DEPTH);
        var failures  = new CopyOnWriteArrayList<Throwable>();
        var extracted = new AtomicInteger();

        // stands in for the shared Java timer thread draining matured alarms
        Thread timer = new Thread(() -> {
            for (int i = 0; i < CALLBACK_COUNT; i++) {
                try {
                    if (pending.take().extractCallback() != null) {
                        extracted.incrementAndGet();
                    }
                } catch (Throwable e) {
                    failures.add(e);
                    return;
                }
            }
        }, "test-timer-thread");
        timer.setDaemon(true);
        timer.start();

        // this thread stands in for the owning service registering new alarms
        for (int i = 0; i < CALLBACK_COUNT; i++) {
            pending.put(new WeakCallback(frame, null));
        }
        timer.join(JOIN_TIMEOUT_MILLIS);

        assertTrue(failures.isEmpty(),
                () -> "the timer thread must never see a corrupt or lost registry entry, but got: "
                        + failures.getFirst());
        assertFalse(timer.isAlive(),
                "the timer thread must not be stuck inside the callback registry");
        assertEquals(CALLBACK_COUNT, extracted.get(),
                "every registered callback must be extractable exactly once");
        assertTrue(context.getCallbackMap().isEmpty(),
                "a fully drained registry must be empty");
    }

    /**
     * Keep-alive ownership is a lifecycle count that pins the container open while a native timer
     * may still call back into it. Registration used to happen in the Alarm constructor, so when
     * Timer.schedule(...) then failed, the recovery path called cancel() - whose unregister was
     * gated on TimerTask.cancel(), which reports false for a task that was never scheduled. The
     * count stayed up forever: the container could never go idle, so a "once and done" run could
     * not terminate.
     * <p/>
     * This drives the real native "schedule" entry point with the shared timer forced into a state
     * where scheduling fails.
     */
    @Test
    public void failedAlarmScheduleDoesNotPinTheContainerAlive() {
        assumeTrue(RuntimeTestSupport.systemModulesAvailable(), "compiled XDK system modules are required");

        NativeContainer container = RuntimeTestSupport.newContainer();
        ServiceContext  context   = container.createServiceContext("alarm");
        Frame           frame     = RuntimeTestSupport.entryFrame(context);

        GenericHandle hDelay = new GenericHandle(
                container.getTemplate("temporal.Duration").getCanonicalClass());
        hDelay.setField(null, "picoseconds",
                xInt128.INSTANCE.makeHandle(new LongLong(PICOS_PER_MILLI)));

        // keepAlive=True, so a successful schedule would pin the container until the alarm fires
        ObjectHandle[] ahArg = new ObjectHandle[]{hDelay, null, xBoolean.TRUE};

        assertTrue(container.isIdle(), "a container with no alarms must start out idle");

        Timer timerLive = xLocalClock.TIMER;
        Timer timerDead = new Timer("test-cancelled-timer", true);
        timerDead.cancel();
        xLocalClock.TIMER = timerDead;
        try {
            int nResult = xLocalClock.INSTANCE.invokeNativeN(frame,
                    xLocalClock.INSTANCE.getStructure().findMethod("schedule", 3),
                    null, ahArg, Op.A_IGNORE);

            assertEquals(Op.R_EXCEPTION, nResult,
                    "a scheduler failure must be reported to natural code");
            assertNotNull(frame.m_hException, "the raised exception must be recorded on the frame");
        } finally {
            xLocalClock.TIMER = timerLive;
        }

        assertTrue(container.isIdle(),
                "a failed alarm schedule must not leave the container pinned alive");
    }

    /**
     * The NanoTimer variant of the same defect, with an extra failure of its own: scheduling ran
     * under "catch (Throwable)" that cancelled the trigger and then fell through to return a
     * perfectly ordinary "cancel" function. Natural code was told the alarm had been scheduled when
     * it never would fire, and the keep-alive count stayed elevated on top of that. The scheduler
     * failure has to reach natural code as an exception, with the registration unwound.
     */
    @Test
    public void failedNanosTimerScheduleIsReportedAndUnwound() {
        assumeTrue(RuntimeTestSupport.systemModulesAvailable(), "compiled XDK system modules are required");

        NativeContainer container = RuntimeTestSupport.newContainer();
        ServiceContext  context   = container.createServiceContext("nanos");
        Frame           frame     = RuntimeTestSupport.entryFrame(context);

        ObjectHandle hTimer = xNanosTimer.INSTANCE.ensureTimer(frame, null);
        xNanosTimer.INSTANCE.invokeNativeN(frame,
                xNanosTimer.INSTANCE.getStructure().findMethod("start", 0),
                hTimer, Utils.OBJECTS_NONE, Op.A_IGNORE);

        GenericHandle hDelay = new GenericHandle(
                container.getTemplate("temporal.Duration").getCanonicalClass());
        hDelay.setField(null, "picoseconds",
                xInt128.INSTANCE.makeHandle(new LongLong(PICOS_PER_MILLI)));

        ObjectHandle[] ahArg = new ObjectHandle[]{hDelay, null, xBoolean.TRUE};

        assertTrue(container.isIdle(), "a container with no alarms must start out idle");

        Timer timerLive = xLocalClock.TIMER;
        Timer timerDead = new Timer("test-cancelled-timer", true);
        timerDead.cancel();
        xLocalClock.TIMER = timerDead;
        try {
            int nResult = xNanosTimer.INSTANCE.invokeNativeN(frame,
                    xNanosTimer.INSTANCE.getStructure().findMethod("schedule", 3),
                    hTimer, ahArg, Op.A_IGNORE);

            assertEquals(Op.R_EXCEPTION, nResult,
                    "a swallowed scheduler failure would hand natural code an alarm "
                            + "that can never fire");
        } finally {
            xLocalClock.TIMER = timerLive;
        }

        assertTrue(container.isIdle(),
                "a failed alarm schedule must not leave the container pinned alive");
    }

    // ----- constants -----------------------------------------------------------------------------

    private static final long PICOS_PER_MILLI = 1_000_000_000L;

    private static final int CALLBACK_COUNT = 50_000;

    private static final int QUEUE_DEPTH = 256;

    private static final long JOIN_TIMEOUT_MILLIS = 60_000L;
}
