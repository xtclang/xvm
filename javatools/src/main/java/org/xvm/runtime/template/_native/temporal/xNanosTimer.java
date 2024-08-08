package org.xvm.runtime.template._native.temporal;


import java.util.TimerTask;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;
import org.xvm.runtime.WeakCallback;

import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.numbers.LongLong;
import org.xvm.runtime.template.numbers.BaseInt128.LongLongHandle;
import org.xvm.runtime.template.numbers.xInt128;

import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction.NativeFunctionHandle;

import org.xvm.util.ListSet;


/**
 * Native implementation of a simple timer (stop-watch) using Java's nanosecond-resolution "System"
 * clock.
 */
public class xNanosTimer
        extends xService
    {
    public static xNanosTimer INSTANCE;

    public xNanosTimer(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        s_clzDuration = f_container.getTemplate("temporal.Duration").getCanonicalClass();

        markNativeProperty("elapsed");

        markNativeMethod("start"   , VOID, null);
        markNativeMethod("stop"    , VOID, null);
        markNativeMethod("reset"   , VOID, null);
        markNativeMethod("schedule", new String[]{"temporal.Duration", "temporal.Timer.Alarm"}, null);

        invalidateTypeInfo();
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        return pool().ensureEcstasyTypeConstant("temporal.Timer");
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        TimerHandle hTimer = (TimerHandle) hTarget;
        switch (sPropName)
            {
            case "elapsed":
                return frame.assignValue(iReturn, hTimer.elapsedDuration());
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        TimerHandle hTimer = (TimerHandle) hTarget;
        switch (method.getName())
            {
            case "start":
                hTimer.start();
                return Op.R_NEXT;

            case "stop":
                hTimer.stop();
                return Op.R_NEXT;

            case "reset":
                hTimer.reset();
                return Op.R_NEXT;

            case "schedule": // duration, alarm
                {
                GenericHandle  hDuration = (GenericHandle ) ahArg[0];
                FunctionHandle hAlarm    = (FunctionHandle) ahArg[1];
                return invokeSchedule(frame, hTimer, hDuration, hAlarm, iReturn);
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public ServiceHandle createServiceHandle(ServiceContext context, ClassComposition clz, TypeConstant typeMask)
        {
        TimerHandle hTimer = new TimerHandle(clz.maskAs(typeMask), context);
        context.setService(hTimer);
        return hTimer;
        }

    /**
     * Injection support.
     */
    public ObjectHandle ensureTimer(Frame frame, ObjectHandle hOpts)
        {
        // quite intentionally the NanoTimer service always belongs to the native container, holding
        // onto Alarm objects that are registered with Java's Timer (xLocalClock.TIMER);
        // in turn, the Alarm holds a user-supplied function via a WeakRef, allowing the
        // corresponding container to be shut down and garbage collected
        return createServiceHandle(
                f_container.createServiceContext("Timer"),
                    getCanonicalClass(), getCanonicalType());
        }

    /**
     * Helper method to get a "milliseconds" value from the Duration handle.
     */
    public static long millisFromDuration(ObjectHandle hDuration)
        {
        ObjectHandle hPicos = ((GenericHandle) hDuration).getField(null, "picoseconds");
        return ((LongLongHandle) hPicos).getValue().div(PICOS_PER_MILLI_LL).getLowValue();
        }

    /**
     * Native implementation of "Cancellable schedule(Duration delay, Alarm alarm)"
     */
    private int invokeSchedule(Frame frame, TimerHandle hTimer, GenericHandle hDuration,
                               FunctionHandle hAlarm, int iReturn)
        {
        // Java's Timer uses millisecond scheduling, but we're given duration in picoseconds
        LongLongHandle llPicos = (LongLongHandle) hDuration.getField(frame, "picoseconds");
        long           cNanos  = Math.max(0, llPicos.getValue().divUnsigned(PICOS_PER_NANO).getLowValue());

        return frame.assignValue(iReturn,
                hTimer.addAlarm(cNanos, new WeakCallback(frame, hAlarm)));
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    public static class TimerHandle
            extends ServiceHandle
        {
        protected TimerHandle(TypeComposition clazz, ServiceContext context)
            {
            super(clazz, context);
            }

        // -----  Timer implementation -------------------------------------------------------------

        /**
         * Start the timer, which also starts all alarms.
         */
        public synchronized void start()
            {
            if (f_acNanos[START] == 0)
                {
                f_acNanos[START] = System.nanoTime();
                }

            synchronized (f_setAlarms)
                {
                for (Alarm alarm : f_setAlarms)
                    {
                    alarm.start();
                    }
                }
            }

        /**
         * @return true if the timer is running (the last call to "start" has not been followed by
         *         a call to "stop")
         */
        public boolean isRunning()
            {
            return f_acNanos[START] != 0;
            }

        /**
         * @return the elapsed time, in nanoseconds
         */
        public synchronized long elapsed()
            {
            long cNanosTotal = f_acNanos[PREV];
            if (f_acNanos[START] != 0)
                {
                long cAdd = System.nanoTime() - f_acNanos[START];
                if (cAdd > 0)
                    {
                    cNanosTotal += cAdd;
                    }
                }
            return cNanosTotal;
            }

        /**
         * Stop the timer, which also stops all alarms.
         */
        public synchronized void stop()
            {
            if (f_acNanos[START] != 0)
                {
                long cAdd = System.nanoTime() - f_acNanos[START];
                if (cAdd > 0)
                    {
                    f_acNanos[PREV] += cAdd;
                    }

                f_acNanos[START] = 0;

                synchronized (f_setAlarms)
                    {
                    for (Alarm alarm : f_setAlarms)
                        {
                        alarm.stop();
                        }
                    }
                }
            }

        /**
         * Reset the timer, which resets the elapsed time and all alarms.
         */
        public synchronized void reset()
            {
            f_acNanos[PREV] = 0;
            if (f_acNanos[START] != 0)
                {
                f_acNanos[START] = System.nanoTime();
                }

            synchronized (f_setAlarms)
                {
                for (Alarm alarm : f_setAlarms)
                    {
                    alarm.reset();
                    }
                }
            }

        /**
         * @return the elapsed time, as an Ecstasy Duration object
         */
        public GenericHandle elapsedDuration()
            {
            GenericHandle hDuration = new GenericHandle(s_clzDuration);
            LongLong      llPicos   = new LongLong(elapsed()).mul(PICOS_PER_NANO_LL);

            hDuration.setField(null, "picoseconds", xInt128.INSTANCE.makeHandle(llPicos));
            hDuration.makeImmutable();

            return hDuration;
            }

        /**
         * @return a "cancel" function for the alarm
         */
        public FunctionHandle addAlarm(long cNanos, WeakCallback refCallback)
            {
            Alarm alarm = new Alarm(cNanos, refCallback);

            synchronized (f_setAlarms)
                {
                f_setAlarms.add(alarm);
                }

            if (isRunning())
                {
                alarm.start();
                }

            return new NativeFunctionHandle((_frame, _ah, _iReturn) ->
                {
                alarm.cancel();
                return Op.R_NEXT;
                });
            }

        public void removeAlarm(Alarm alarm)
            {
            boolean fUnregistered;
            synchronized (f_setAlarms)
                {
                fUnregistered = f_setAlarms.remove(alarm);
                }
            assert fUnregistered;
            }

        // ----- inner class: Alarm --------------------------------------------------------------------

        /**
         * Represents a pending alarm.
         */
        public class Alarm
            {
            /**
             * Construct and register an alarm, and start it if the timer is running.
             *
             * @param cNanosDelay  the delay before triggering the alarm
             * @param refCallback  the weak ref to a function to call when the alarm triggers
             */
            public Alarm(long cNanosDelay, WeakCallback refCallback)
                {
                f_cNanosDelay = cNanosDelay;
                f_refCallback = refCallback;
                }

            /**
             * Start the alarm maturing on a running timer.
             */
            public synchronized void start()
                {
                if (m_fDead || m_trigger != null)
                    {
                    return;
                    }

                m_cNanosStart = System.nanoTime();
                m_trigger     = new Trigger(this);
                try
                    {
                    f_refCallback.get().f_container.registerNotification();
                    xLocalClock.TIMER.schedule(
                        m_trigger, Math.max(1, (f_cNanosDelay - m_cNanosBurnt) / NANOS_PER_MILLI));
                    }
                catch (Throwable e)
                    {
                    m_trigger.cancel();
                    m_trigger = null;
                    System.err.println("Exception in xNanosTimer.Alarm.start(): " + e);
                    }
                }

            /**
             * Stop the alarm from maturing.
             */
            public synchronized void stop()
                {
                if (m_fDead)
                    {
                    return;
                    }

                if (m_trigger != null)
                    {
                    m_trigger.cancel();
                    m_trigger = null;

                    long cNanosAdd = System.nanoTime() - m_cNanosStart;
                    if (cNanosAdd > 0)
                        {
                        m_cNanosBurnt += cNanosAdd;
                        }
                    m_cNanosStart = 0;
                    }
                }

            /**
             * Reset the alarm maturity back to its initial state.
             */
            public synchronized void reset()
                {
                if (m_fDead)
                    {
                    return;
                    }

                if (m_trigger != null)
                    {
                    m_trigger.cancel();
                    m_trigger = null;

                    m_cNanosStart = 0;
                    m_cNanosBurnt = 0;

                    if (TimerHandle.this.isRunning())
                        {
                        start();
                        }
                    }
                }

            /**
             * Called when the alarm is triggered.
             */
            public void run()
                {
                synchronized (this)
                    {
                    if (m_fDead)
                        {
                        return;
                        }
                    m_fDead   = true;
                    m_trigger = null;
                    }

                ServiceContext context = f_refCallback.get();
                if (context != null)
                    {
                    WeakCallback.Callback callback = f_refCallback.extractCallback();
                    context.callLater(callback.frame(), callback.functionHandle(), Utils.OBJECTS_NONE);
                    context.f_container.unregisterNotification();
                    }
                TimerHandle.this.removeAlarm(this);
                }

            /**
             * Called after alarm has finished or has been cancelled.
             */
            public void unregister()
                {
                ServiceContext context = f_refCallback.get();
                if (context != null)
                    {
                    context.f_container.unregisterNotification();
                    }
                }

            /**
             * Called when the alarm is canceled.
             */
            public void cancel()
                {
                synchronized (this)
                    {
                    if (m_fDead)
                        {
                        return;
                        }
                    m_fDead = true;

                    Trigger trigger = m_trigger;
                    if (trigger != null)
                        {
                        m_trigger = null;
                        try
                            {
                            trigger.cancel();
                            }
                        catch (Exception e)
                            {
                            System.err.println("Exception in xNanosTimer.Alarm.cancel(): " + e);
                            }
                        }
                    }

                TimerHandle.this.removeAlarm(this);
                }

            /**
             * A TimerTask that is scheduled on the Java timer and used to trigger the alarm.
             */
            protected static class Trigger
                    extends TimerTask
                {
                protected Trigger(Alarm alarm)
                    {
                    m_alarm = alarm;
                    }

                @Override
                public void run()
                    {
                    Alarm alarm = m_alarm;
                    m_alarm = null;
                    alarm.run();
                    }

                @Override
                public boolean cancel()
                    {
                    boolean fCancelled = super.cancel();
                    Alarm   alarm      = m_alarm;
                    if (alarm != null)
                        {
                        alarm.unregister();
                        m_alarm = null;
                        }
                    return fCancelled;
                    }

                private Alarm m_alarm;
                }

            private final    WeakCallback f_refCallback;
            private final    long         f_cNanosDelay;
            private          long         m_cNanosStart;
            private          long         m_cNanosBurnt;
            private volatile boolean      m_fDead;
            private volatile Trigger      m_trigger;
            }

        // ----- data fields ---------------------------------------------------------------------


        /**
         * We use an array rather than individual long values to avoid "splitting state" during
         * handle cloning. All access to those values is always synchronized. Values are:
         *
         *  [0] The timestamp (in nanos) when the timer was started, or 0 if the timer is paused
         *  [1] The number of nanoseconds previously accumulated before the timer was paused
         */
        private final long[] f_acNanos = new long[2];

        private static final int START = 0;
        private static final int PREV  = 1;

        /**
         * The registered alarms.
         */
        private final ListSet<Alarm> f_setAlarms = new ListSet<>();
        }


    // ----- constants and fields ------------------------------------------------------------------

    protected static final long     PICOS_PER_MILLI    = 1_000_000_000;
    protected static final long     PICOS_PER_NANO     = 1_000;
    protected static final LongLong PICOS_PER_MILLI_LL = new LongLong(PICOS_PER_MILLI);
    protected static final LongLong PICOS_PER_NANO_LL  = new LongLong(PICOS_PER_NANO);
    protected static final long     NANOS_PER_MILLI    = 1_000_000;

    /**
     * Cached Duration class.
     */
    private static TypeComposition s_clzDuration;
    }