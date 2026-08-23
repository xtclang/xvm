package org.xvm.runtime.template._native.temporal;


import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.Utils;
import org.xvm.runtime.WeakCallback;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xService;


import org.xvm.runtime.template.numbers.BaseInt128.LongLongHandle;
import org.xvm.runtime.template.numbers.LongLong;
import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction.NativeFunctionHandle;


/**
 * Native implementation of a simple wall clock using Java's millisecond-resolution "System" clock.
 */
public class xLocalClock
        extends xService {
    public xLocalClock(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void initNative() {
        markNativeProperty("epochMillis");
        markNativeProperty("timezoneMillis");

        // LocalClock has two "schedule" methods and while one is a trivial default implementation,
        // it would execute on the LocalClock service that belongs to the native container.
        // As a result, the "alarm" handle would be proxied via FunctionProxyHandle, which would
        // keep a hard reference to the calling service and would "leak" that service unless the
        // caller explicitly canceled.
        // To prevent that, we need to implement it natively, which would execute the "schedule"
        // method on the caller's context and avoid creation of the alarm proxy.
        markNativeMethod("schedule",  new String[]{"temporal.Duration", "temporal.Clock.Alarm", "Boolean"}, null);
        markNativeMethod("schedule",  new String[]{"temporal.Time"    , "temporal.Clock.Alarm", "Boolean"}, null);

        invalidateTypeInfo();
    }

    @Override
    public TypeConstant getCanonicalType() {
        return pool().ensureEcstasyTypeConstant("temporal.Clock");
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        switch (sPropName) {
        case "epochMillis":
            return frame.assignValue(iReturn, epochMillis(frame));

        case "timezoneMillis":
            return frame.assignValue(iReturn, timezoneMillis(frame));
        }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn) {
        switch (method.getName()) {
        case "schedule": {
            GenericHandle  hWakeup = (GenericHandle)  ahArg[0];
            FunctionHandle hAlarm  = (FunctionHandle) ahArg[1];
            BooleanHandle  hKeep   = ahArg[2] instanceof BooleanHandle hB ? hB : xBoolean.falseHandle(frame);
            long           ldtNow  = frame.f_context.f_container.currentTimeMillis();
            long           ldtWakeup;
            long           cDelay;
            if (hWakeup.getType().equals(frame.poolContext().typeDuration())) {
                // hWakeUp is "Duration"
                LongLong llPicos = ((LongLongHandle) hWakeup.getField(null, "picoseconds")).getValue();

                cDelay    = llPicos.divUnsigned(xNanosTimer.PICOS_PER_MILLI).getLowValue();
                ldtWakeup = ldtNow + cDelay;
            } else {
                // hWakeUp is "Time"
                LongLong llEpoch = ((LongLongHandle) hWakeup.getField(null, "epochPicos")).getValue();

                ldtWakeup = llEpoch.divUnsigned(xNanosTimer.PICOS_PER_MILLI).getLowValue();
                cDelay    = Math.max(0, ldtWakeup - ldtNow);
            }

            return invokeSchedule(frame, ldtWakeup, cDelay, hAlarm, hKeep, iReturn);
        }
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    /**
     * Native implementation of
     *  "Cancellable schedule(Time     when, Alarm alarm, Boolean keepAlive = False)" and
     *  "Cancellable schedule(Duration delay, Alarm alarm, Boolean keepAlive = False)"
     */
    private int invokeSchedule(Frame frame, long ldtWakeup, long cDelay,
                               FunctionHandle hAlarm, BooleanHandle hKeepAlive, int iReturn) {
        Alarm alarm = new Alarm(new WeakCallback(frame, hAlarm), ldtWakeup, hKeepAlive.get());
        try {
            alarm.registerKeepAlive();
            scheduleTimer(alarm.getTrigger(), cDelay);
        } catch (Exception e) {
            alarm.cancelAfterScheduleFailure();
            return frame.raiseException(e.getMessage());
        }

        FunctionHandle hCancel = new NativeFunctionHandle(frame.container(), (_, _, _) -> {
            alarm.cancel();
            return Op.R_NEXT;
        });

        return frame.assignValue(iReturn, hCancel);
    }

    /**
     * Injection support.
     */
    public ServiceHandle ensureLocalClock(Frame frame, ObjectHandle hOpts) {
        ServiceHandle hClock = m_hLocalClock;
        if (hClock == null) {
            m_hLocalClock = hClock = createServiceHandle(
                f_container.createServiceContext("LocalClock"),
                    getCanonicalClass(), getCanonicalType());
            hClock.setField(frame, "utc", xBoolean.falseHandle(frame));
        }

        return hClock;
    }

    public ServiceHandle ensureDefaultClock(Frame frame, ObjectHandle hOpts) {
        // for now the default clock is just the local clock
        return ensureUTCClock(frame, hOpts);
    }

    public ServiceHandle ensureUTCClock(Frame frame, ObjectHandle hOpts) {
        ServiceHandle hClock = m_hUTCClock;
        if (hClock == null) {
            m_hUTCClock = hClock = createServiceHandle(
                f_container.createServiceContext("LocalClock"),
                getCanonicalClass(), getCanonicalType());
            hClock.setField(frame, "utc", xBoolean.trueHandle(frame));
        }

        return hClock;
    }


    // -----  helpers ------------------------------------------------------------------------------

    protected JavaLong epochMillis(Frame frame) {
        return xInt64.makeHandle(frame, System.currentTimeMillis());
    }

    protected JavaLong timezoneMillis(Frame frame) {
        return xInt64.makeHandle(frame, TimeZone.getDefault().getOffset(System.currentTimeMillis()));
    }

    /**
     * Represents a pending alarm.
     */
    protected static class Alarm {
        /**
         * Construct an alarm.
         *
         * @param refCallback  the weak ref to a function to call when the alarm triggers
         * @param ldtWakeUp    the wakeup timestamp in milliseconds (using
         *                     Container.currentTimeMillis())
         * @param fKeepAlive   if true, the alarm needs to be registered with the container
         */
        protected Alarm(WeakCallback refCallback, long ldtWakeUp, boolean fKeepAlive) {
            f_refCallback = refCallback;
            f_ldtWakeup   = ldtWakeUp;
            m_trigger     = new Trigger(this);
            f_fKeepAlive  = fKeepAlive;
        }

        /**
         * @return the current trigger
         */
        public Trigger getTrigger() {
            return m_trigger;
        }

        /**
         * Register the keep-alive callback as part of the schedule attempt, not from the
         * constructor. The old constructor-time registration leaked callback ownership when
         * Timer.schedule(...) failed before the trigger could be canceled normally.
         */
        public void registerKeepAlive() {
            if (!f_fKeepAlive) {
                return;
            }

            ServiceContext context = f_refCallback.get();
            if (context == null) {
                return;
            }

            Container container = context.f_container;
            synchronized (this) {
                if (m_fFinished || m_containerRegistered != null) {
                    return;
                }
                // Store the exact owner while the callback is registered. The WeakCallback can
                // legitimately clear before cleanup, but keep-alive ownership must still unwind.
                container.registerNativeCallback();
                m_containerRegistered = container;
            }
        }

        /**
         * Called when the alarm is triggered by the Java timer.
         */
        public void run() {
            ServiceContext context = f_refCallback.get();
            if (context != null) {
                Container container = context.f_container;
                if (container.isTimeFrozen()) {
                    // if the time is currently frozen, we have no way of knowing when it's going to
                    // be ready (how long will someone be looking at the debugger screen); so
                    // re-checking in a second seems like a reasonable compromise
                    scheduleTimer(m_trigger = new Trigger(this), 1000);
                    return;
                }

                long ldtNow = container.currentTimeMillis();
                if (ldtNow >= f_ldtWakeup) {
                    Container containerRegistered = finish();
                    try {
                        WeakCallback.Callback callback = f_refCallback.extractCallback();
                        context.callLater(callback.frame(), callback.functionHandle(),
                                Utils.OBJECTS_NONE);
                    } finally {
                        unregister(containerRegistered);
                    }
                } else {
                    // reschedule
                    scheduleTimer(m_trigger = new Trigger(this), f_ldtWakeup - ldtNow);
                }
            } else {
                unregister(finish());
            }
        }

        /**
         * Called when the alarm is canceled by the natural code.
         */
        public boolean cancel() {
            boolean        fCancelled = m_trigger.cancel();
            if (fCancelled) {
                unregister(finish());
            }
            return fCancelled;
        }

        /**
         * Roll back a failed schedule attempt. This alarm has not escaped to natural code yet, so
         * it can be marked finished even if TimerTask.cancel() reports false for an unscheduled
         * task.
         */
        public void cancelAfterScheduleFailure() {
            m_trigger.cancel();
            unregister(finish());
        }

        private synchronized Container finish() {
            m_fFinished = true;
            Container container = m_containerRegistered;
            m_containerRegistered = null;
            return container;
        }

        private static void unregister(Container container) {
            if (container != null) {
                container.unregisterNativeCallback();
            }
        }

        /**
         * A TimerTask that is scheduled on the Java timer and is used to trigger the alarm.
         */
        protected static class Trigger
                extends TimerTask {
            protected Trigger(Alarm alarm) {
                f_alarm = alarm;
            }

            @Override
            public void run() {
                f_alarm.run();
            }

            private final Alarm f_alarm;
        }

        private final WeakCallback f_refCallback;
        private final long         f_ldtWakeup;
        private final boolean      f_fKeepAlive;
        private       Trigger      m_trigger;
        private       Container    m_containerRegistered;
        private       boolean      m_fFinished;
    }


    // ----- constants and fields ------------------------------------------------------------------

    /**
     * Schedule a task on the process-wide wall-clock timer.
     * <p/>
     * The single daemon timer preserves the legacy scheduling behavior: LocalClock, NanoTimer, and
     * service wake-ups all share one Java scheduler, while each task carries its own callback or
     * container owner. Exposing only this narrow operation prevents unrelated code from rebinding or
     * cancelling the scheduler and stranding callbacks that belong to other containers.
     *
     * @param task          the timer task to schedule
     * @param cDelayMillis  the delay in milliseconds
     */
    public static void scheduleTimer(TimerTask task, long cDelayMillis) {
        TIMER.schedule(task, cDelayMillis);
    }

    /**
     * Shared daemon scheduler for runtime wall-clock wakeups. Keep this private and final; the
     * public contract is scheduling work, not owning the Timer lifecycle. This used to be a public
     * non-final field; making the reference private final preserves one process-wide scheduler while
     * closing the reassignment/cancellation hole.
     */
    private static final Timer TIMER = new Timer("ecstasy:LocalClock", true);

    /**
     * Cached LocalClock handle.
     */
    private ServiceHandle m_hLocalClock;

    /**
     * Cached UTCClock handle.
     */
    private ServiceHandle m_hUTCClock;
}
