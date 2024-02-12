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

import org.xvm.runtime.template.xNullable;
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
                FunctionHandle hCancel   = hTimer.schedule(hDuration, new WeakCallback(frame, hAlarm));
                return frame.assignValue(iReturn, hCancel);
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
     * Helper method to convert Timeout? handle into a "delay" milliseconds.
     */
    public static long millisFromTimeout(ObjectHandle hTimeout)
        {
        if (hTimeout == xNullable.NULL)
            {
            return 0;
            }

        ObjectHandle hRemaining = ((GenericHandle) hTimeout).getField(null, "duration");
        ObjectHandle hPicos     = ((GenericHandle) hRemaining).getField(null, "picoseconds");

        return ((LongLongHandle) hPicos).getValue().div(PICOS_PER_MILLI_LL).getLowValue();
        }

    /**
     * Injection support.
     */
    public ObjectHandle ensureTimer(Frame frame, ObjectHandle hOpts)
        {
        return createServiceHandle(
                f_container.createServiceContext("Timer"),
                    getCanonicalClass(), getCanonicalType());
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    public static class TimerHandle
            extends ServiceHandle
        {
        protected TimerHandle(TypeComposition clazz, ServiceContext context)
            {
            super(clazz, context);

            m_cNanosStart = System.nanoTime();
            }

        /**
         * @return false iff this timer has some outstanding asynchronous requests
         */
        public boolean isIdle()
            {
            if (isRunning())
                {
                synchronized (f_setAlarms)
                    {
                    for (Alarm alarm : f_setAlarms)
                        {
                        if (!alarm.m_fDead)
                            {
                            return false;
                            }
                        }
                    }
                }
            return true;
            }

        // -----  Timer implementation -------------------------------------------------------------

        /**
         * Start the timer, which also starts all alarms.
         */
        public synchronized void start()
            {
            if (m_cNanosStart == 0)
                {
                m_cNanosStart = System.nanoTime();
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
         * @return true if the timer is running (the last call to "start" has not been followed by a call
         *         to "stop")
         */
        public boolean isRunning()
            {
            return m_cNanosStart != 0;
            }

        /**
         * @return the elapsed time, in nanoseconds
         */
        public synchronized long elapsed()
            {
            long cNanosTotal = m_cNanosPrevSum;
            if (m_cNanosStart != 0)
                {
                long cAdd = System.nanoTime() - m_cNanosStart;
                if (cAdd > 0)
                    {
                    cNanosTotal += cAdd;
                    }
                }
            return cNanosTotal;
            }

        /**
         * Create and schedule an alarm to go off if a specified number of nanoseconds.
         *
         * @param hDuration  the duration before triggering the alarm
         * @param refAlarm   the runtime function to call when the alarm triggers
         *
         * @return the new Alarm
         */
        public FunctionHandle schedule(GenericHandle hDuration, WeakCallback refAlarm)
            {
            // note: the Java Timer uses millisecond scheduling, but we're given scheduling
            // instructions in picoseconds
            LongLongHandle llPicos = (LongLongHandle) hDuration.getField(null, "picoseconds");
            long           cNanos  = Math.max(0, llPicos.getValue().divUnsigned(PICOS_PER_NANO).getLowValue());
            Alarm          alarm   = new Alarm(cNanos, refAlarm);

            return new NativeFunctionHandle((_frame, _ah, _iReturn) ->
                {
                alarm.cancel();
                return Op.R_NEXT;
                });
            }

        /**
         * Stop the timer, which also stops all alarms.
         */
        public synchronized void stop()
            {
            if (m_cNanosStart != 0)
                {
                long cAdd = System.nanoTime() - m_cNanosStart;
                if (cAdd > 0)
                    {
                    m_cNanosPrevSum += cAdd;
                    }

                m_cNanosStart = 0;

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
            m_cNanosPrevSum = 0;
            if (m_cNanosStart != 0)
                {
                m_cNanosStart = System.nanoTime();
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

        public void addAlarm(Alarm alarm)
            {
            synchronized (f_setAlarms)
                {
                f_setAlarms.add(alarm);
                }
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

                TimerHandle timer = TimerHandle.this;
                timer.addAlarm(this);
                if (timer.isRunning())
                    {
                    start();
                    }
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
                    f_refCallback.get().registerNotification();
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
                    context.unregisterNotification();
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
                    context.unregisterNotification();
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
         * The number of nanoseconds previously accumulated before the timer was paused.
         */
        private long m_cNanosPrevSum;

        /**
         * The value (in nanos) when the timer was started, or 0 if the timer is paused.
         */
        private volatile long m_cNanosStart;

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