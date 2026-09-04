package org.xvm.runtime;


import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import org.xvm.asm.Constants;
import org.xvm.asm.DirRepository;
import org.xvm.asm.LinkedRepository;
import org.xvm.asm.ModuleRepository;
import org.xvm.asm.Op;

import org.xvm.asm.op.Return_0;

import org.xvm.runtime.ObjectHandle.GenericHandle;

import org.xvm.runtime.template.xBoolean;

import org.xvm.runtime.template.numbers.LongLong;
import org.xvm.runtime.template.numbers.xInt128;

import org.xvm.runtime.template._native.temporal.xLocalClock;
import org.xvm.runtime.template._native.temporal.xNanosTimer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * The alarm triggers run on one process-wide {@link Timer} with a single thread, shared by every
 * alarm in every container. {@link java.util.Timer} kills that thread and cancels the Timer
 * permanently if a {@link TimerTask} throws, so an exception escaping a trigger would silently
 * disable every alarm in the whole JVM - with the symptom appearing as an unrelated hang, much
 * later and somewhere else entirely.
 * <p/>
 * Each test schedules a real alarm, arranges for it to fail when it matures, and then checks that
 * the timer thread is still alive and the Timer still accepts work. Failure is injected by dropping
 * the callback registry entry, which is a state the runtime can reach on its own: a service that
 * has been collected, or an alarm cancelled concurrently with its own maturation.
 */
public class AlarmTimerSurvivesCallbackFailureTest {
    /**
     * A LocalClock alarm whose callback has gone missing must not take the shared timer with it.
     */
    @Test
    public void localClockAlarmFailureDoesNotKillTheSharedTimer() throws InterruptedException {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        NativeContainer container = newContainer();
        ServiceContext  context   = container.createServiceContext("clock");
        Frame           frame     = entryFrame(context);

        Timer timerLive  = xLocalClock.TIMER;
        Timer timerProbe = new Timer("test-alarm-timer", true);

        // run against a private timer, so that a thread this test deliberately kills can never be
        // the one the rest of the suite depends on
        xLocalClock.TIMER = timerProbe;
        try {
            int nResult = xLocalClock.INSTANCE.invokeNativeN(frame,
                    xLocalClock.INSTANCE.getStructure().findMethod("schedule", 3),
                    null, scheduleArgs(container), Op.A_IGNORE);
            assertEquals(Op.R_NEXT, nResult, "the alarm must schedule successfully");

            assertTimerSurvives(context, timerProbe);
        } finally {
            xLocalClock.TIMER = timerLive;
            timerProbe.cancel();
        }
    }

    /**
     * NanoTimer alarms are scheduled onto the very same process-wide timer, through a trigger of
     * their own, so they carry the same hazard and need the same guard.
     */
    @Test
    public void nanosTimerAlarmFailureDoesNotKillTheSharedTimer() throws InterruptedException {
        assumeTrue(systemModulesAvailable(), "compiled XDK system modules are required");

        NativeContainer container = newContainer();
        ServiceContext  context   = container.createServiceContext("nanos");
        Frame           frame     = entryFrame(context);

        ObjectHandle hTimer = xNanosTimer.INSTANCE.ensureTimer(frame, null);
        xNanosTimer.INSTANCE.invokeNativeN(frame,
                xNanosTimer.INSTANCE.getStructure().findMethod("start", 0),
                hTimer, Utils.OBJECTS_NONE, Op.A_IGNORE);

        Timer timerLive  = xLocalClock.TIMER;
        Timer timerProbe = new Timer("test-alarm-timer", true);

        xLocalClock.TIMER = timerProbe;
        try {
            int nResult = xNanosTimer.INSTANCE.invokeNativeN(frame,
                    xNanosTimer.INSTANCE.getStructure().findMethod("schedule", 3),
                    hTimer, scheduleArgs(container), Op.A_IGNORE);
            assertEquals(Op.R_NEXT, nResult, "the alarm must schedule successfully");

            assertTimerSurvives(context, timerProbe);
        } finally {
            xLocalClock.TIMER = timerLive;
            timerProbe.cancel();
        }
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Drop the scheduled alarm's callback so that it fails when it matures, then prove the timer
     * survived it.
     * <p/>
     * A {@link Timer} runs every task on one thread, so a marker task queued behind the alarm can
     * only run if that thread outlived the alarm's failure.
     */
    private static void assertTimerSurvives(ServiceContext context, Timer timer)
            throws InterruptedException {
        // the alarm has ALARM_DELAY_MILLIS still to run, so this always lands first
        context.getCallbackMap().clear();

        var fired = new CountDownLatch(1);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                fired.countDown();
            }
        }, MARKER_DELAY_MILLIS);

        assertTrue(fired.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "a failing alarm must not kill the shared timer thread");

        assertDoesNotThrow(() -> timer.schedule(new TimerTask() {
            @Override
            public void run() {
            }
        }, 0L), "the shared timer must still accept new alarms");
    }

    /**
     * @return the argument list for a "schedule(Duration, Alarm, Boolean)" native call
     */
    private static ObjectHandle[] scheduleArgs(NativeContainer container) {
        GenericHandle hDelay = new GenericHandle(
                container.getTemplate("temporal.Duration").getCanonicalClass());
        hDelay.setField(null, "picoseconds",
                xInt128.INSTANCE.makeHandle(new LongLong(ALARM_DELAY_MILLIS * PICOS_PER_MILLI)));

        return new ObjectHandle[]{hDelay, null, xBoolean.FALSE};
    }

    private static NativeContainer newContainer() {
        return new NativeContainer(new Runtime(), systemRepository());
    }

    /**
     * Create a native entry frame for the specified service, of the shape a native method receives.
     */
    private static Frame entryFrame(ServiceContext context) {
        var message = new ServiceContext.Message(null) {
            @Override
            public boolean isAsync() {
                return true;
            }

            @Override
            public int getCallDepth() {
                return 0;
            }

            @Override
            public ObjectHandle getTimeoutHandle() {
                return null;
            }

            @Override
            public long getTimeoutStamp() {
                return 0L;
            }

            @Override
            Frame createFrame(ServiceContext ctx) {
                return ctx.createServiceEntryFrame(this, 0, NATIVE_OPS);
            }
        };
        return context.createServiceEntryFrame(message, 0, NATIVE_OPS);
    }

    private static boolean systemModulesAvailable() {
        ModuleRepository repository = systemRepository();
        return repository != null
            && repository.loadModule(Constants.ECSTASY_MODULE) != null
            && repository.loadModule(Constants.TURTLE_MODULE)  != null
            && repository.loadModule(Constants.NATIVE_MODULE)  != null;
    }

    /**
     * Test-only locator for the gradle build outputs that hold the compiled system modules.
     */
    private static ModuleRepository systemRepository() {
        var repositories = SYSTEM_MODULE_PATHS.stream()
                .map(AlarmTimerSurvivesCallbackFailureTest::repositoryFor)
                .filter(Objects::nonNull)
                .toList();
        return repositories.isEmpty()
                ? null
                : new LinkedRepository(repositories.toArray(ModuleRepository.NO_REPOS));
    }

    private static ModuleRepository repositoryFor(String path) {
        File directory = checkoutFile(path);
        return directory.isDirectory() ? new DirRepository(directory, true) : null;
    }

    private static File checkoutFile(String path) {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("javatools"))) {
            root = root.getParent();
        }
        return Objects.requireNonNull(root, "checkout root").resolve(path).toFile();
    }


    // ----- constants -----------------------------------------------------------------------------

    private static final List<String> SYSTEM_MODULE_PATHS = List.of(
            "lib_ecstasy/build/xtc/main/lib",
            "javatools_bridge/build/xtc/main/lib",
            "xdk/build/install/xdk/lib",
            "xdk/build/install/xdk/javatools");

    private static final Op[] NATIVE_OPS = new Op[]{Return_0.INSTANCE};

    private static final long PICOS_PER_MILLI = 1_000_000_000L;

    /** Long enough that the callback is always dropped before the alarm matures. */
    private static final long ALARM_DELAY_MILLIS = 500L;

    /** Queued behind the alarm, so it only runs if the timer thread outlived the failure. */
    private static final long MARKER_DELAY_MILLIS = 1_500L;

    private static final long AWAIT_TIMEOUT_SECONDS = 10L;
}
