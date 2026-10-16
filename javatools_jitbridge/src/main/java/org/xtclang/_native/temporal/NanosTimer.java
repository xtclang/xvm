package org.xtclang._native.temporal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.lang.ref.WeakReference;

import java.util.Set;
import java.util.TimerTask;

import org.xtclang.ecstasy.nFunction;
import org.xtclang.ecstasy.nService;

import org.xtclang.ecstasy.numbers.Int128;
import org.xtclang.ecstasy.numbers.Int64;

import org.xtclang.ecstasy.temporal.Timer;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Ctx;

import org.xvm.util.ListSet;

/**
 * The native implementation of NanosTimer.x from the Java Tools bridge.
 */
@SuppressWarnings("unused")
public class NanosTimer
        extends nService
        implements Timer {

    /**
     * Create a {@link NanosTimer}.
     */
    public NanosTimer(Ctx ctx) {
        super(ctx);
    }

    public static final long PICOS_PER_NANO  = 1000L;
    public static final long NANOS_PER_MILLI = 1000_000L;

    /**
     * The timestamp, in nanoseconds, that this timer was started, or zero if the timer is not
     * running.
     */
    private long started;

    /**
     * An accumulation of previous elapsed run times for this timer.
     */
    private long previousElapsed;

    /**
     * The set of {@link nAlarmHolder} instances schedule on this timer.
     */
    private final Set<nAlarmHolder> alarms = new ListSet<>();

    /**
     * The Java {@link java.util.Timer} used to schedule alarms.
     */
    public static java.util.Timer TIMER = new java.util.Timer("ecstasy:NanosTimer", true);

    // ----- NanosTimer implementation -------------------------------------------------------------

    /**
     * The primitive property getter method:
     *
     * <p>{@code Int nanoTime();}
     */
    public long nanoTime$get$p(Ctx ctx) {
        return ctx.container.nanoTime();
    }

    /**
     * The primitive property getter method:
     *
     * <p>{@code Int startTime();}
     */
    public long startTime$get$p(Ctx ctx) {
        return started;
    }

    /**
     * The primitive property getter method:
     *
     * <p>{@code  Int previousTime();}
     */
    public long previousElapsed$get$p(Ctx ctx) {
        return previousElapsed;
    }

    // ----- Timer implementation ------------------------------------------------------------------

    @Override
    public synchronized NanosTimer start(Ctx ctx) {
        if (started != 0) {
            return this;
        }

        started = ctx.container.nanoTime();

        for (nAlarmHolder holder : alarms) {
            holder.start(ctx);
        }
        return this;
    }

    @Override
    public synchronized NanosTimer stop(Ctx ctx) {
        if (started == 0) {
            return this;
        }

        previousElapsed += ctx.container.nanoTime() - started;
        started          = 0L;

        for (nAlarmHolder holder : alarms) {
            holder.stop(ctx);
        }
        return this;
    }

    @Override
    public synchronized NanosTimer reset(Ctx ctx) {
        previousElapsed = 0L;
        if (started != 0) {
            started = ctx.container.nanoTime();
        }
        for (nAlarmHolder holder : alarms) {
            holder.reset(ctx);
        }
        return this;
    }

    @Override
    public nFunction scheduleꖛ0$p(Ctx ctx, long delayLow, long delayHigh, nFunction function,
                                   boolean keepAlive, boolean dfltKeepAlive) {
        long         nanos       = Int128.$div(ctx, delayLow, delayHigh, PICOS_PER_NANO, 0L);
        boolean      daemon      = !dfltKeepAlive && keepAlive;
        long         now         = ctx.container.nanoTime();
        long         triggerTime = now + nanos;
        nAlarmHolder alarm       = new nAlarmHolder(triggerTime, new WeakReference<>(function), daemon);

        synchronized (alarms) {
            alarms.add(alarm);
        }

        if (isRunning()) {
            alarm.start(ctx);
        }
        return alarm.createCancelFunction(ctx);
    }

    /**
     * Remove the specified alarm from this timer.
     */
    public void removeAlarm(nAlarmHolder alarm) {
        boolean unregistered;
        synchronized (alarms) {
            unregistered = alarms.remove(alarm);
        }
        assert unregistered;
    }

    /**
     * @return true if the timer is running (the last call to "start" has not been followed by
     *         a call to "stop")
     */
    public boolean isRunning() {
        return started != 0;
    }

    // ----- injection support ---------------------------------------------------------------------

    /**
     * Create a new instance of a {@link NanosTimer}.
     *
     * @param opts  the injectable options
     *
     * @return a new instance of a {@link NanosTimer}
     */
    public static NanosTimer $create(java.lang.Object opts) {
        return new NanosTimer(null);
    }

    // ----- inner class: AlarmHolder --------------------------------------------------------------

    /**
     * A holder for an alarm function.
     */
    public class nAlarmHolder {

        public nAlarmHolder(long triggerTime, WeakReference<nFunction> alarm,
                            boolean keepAlive) {
            this.triggerTime = triggerTime;
            this.alarm       = alarm;
            this.keepAlive   = keepAlive;
        }

        /**
         * The nanosecond timestamp when the alarm function should execute.
         */
        private final long triggerTime;

        /**
         * {@code true} if this is a daemon alarm.
         */
        private final boolean keepAlive;

        /**
         * A reference to the alarm function.
         */
        private final WeakReference<nFunction> alarm;

        /**
         * {@code true} if this alarm is dead and should not execute.
         */
        private boolean dead;

        /**
         * The {@link nTrigger} used to register this alarm with the {@link #TIMER}.
         */
        private nTrigger trigger;

        /**
         * The nanosecond timestamp that this alarm started execution.
         */
        private long started;

        /**
         * The amount of time in nanoseconds that this alarm has consumed.
         */
        private long burnt;

        /**
         * Invoke the alarm function.
         */
        public void run(Ctx ctx) {
            synchronized (this) {
                if (dead) {
                    return;
                }
                dead    = true;
                trigger = null;
            }

            nFunction function = alarm.get();
            if (function != null) {
                // TODO post the function for execution somewhere
                System.err.println("TODO: invoke function " + function);
            }
            NanosTimer.this.removeAlarm(this);
        }

        /**
         * Start this alarm, registering its {@code Trigger} with the {@link #TIMER}.
         */
        public synchronized void start(Ctx ctx) {
            if (dead || trigger != null) {
                return;
            }
            // TODO there is currently no way to use keep-alive
            started = ctx.container.nanoTime();
            nTrigger trigger = createTrigger(ctx);
            long     millis  = (triggerTime - started - burnt) / NANOS_PER_MILLI;
            TIMER.schedule(trigger, Math.max(1L, millis));
        }

        /**
         * Stop this alarm, unregistering its {@code Trigger} from the {@link #TIMER}.
         */
        public synchronized void stop(Ctx ctx) {
            if (dead) {
                return;
            }

            if (trigger != null) {
                cancelTrigger();

                long runTime = ctx.container.nanoTime() - started;
                if (runTime > 0) {
                    burnt += runTime;
                }
                started = 0;
            }
        }

        /**
         * Reset this alarm and reregister its {@link nTrigger} with the {@link #TIMER}.
         */
        public void reset(Ctx ctx) {
            if (dead) {
                return;
            }

            if (trigger != null) {
                cancelTrigger();

                started = 0;
                burnt = 0;

                if (NanosTimer.this.isRunning()) {
                    start(ctx);
                }
            }
        }

        /**
         * Cancel execution of this alarm.
         */
        public void cancel(Ctx ignored) {
            if (dead) {
                return;
            }
            synchronized (this) {
                if (!dead) {
                    dead = true;
                    cancelTrigger();
                }
            }
            NanosTimer.this.removeAlarm(this);
        }

        /**
         * @return the {@link nTrigger} to use to register this alarm with the {@link #TIMER}.
         */
        protected nTrigger createTrigger(Ctx ctx) {
            return trigger = new nTrigger(ctx, this);
        }

        /**
         * Cancel the {@link nTrigger} use to register this alarm with the {@link #TIMER}.
         */
        protected void cancelTrigger() {
            if (trigger != null) {
                trigger.cancel();
                trigger = null;
            }
        }

        /**
         * @return a {@link nFunction} that can be invoked to cancel this alarm.
         */
        protected nFunction createCancelFunction(Ctx ctx) {
            try {
                TypeConstant fType  = ctx.xvm.ecstasyPool.typeFunction();
                MethodType   mType  = MethodType.methodType(void.class, Ctx.class);
                MethodHandle handle = MethodHandles.lookup()
                                            .findVirtual(nAlarmHolder.class, "cancel", mType)
                                            .bindTo(this);

                return new nFunction(ctx, fType, handle, null, true);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                // should not occur
                throw new RuntimeException(e);
            }
        }

        /**
         * @return zero if the alarm is already up, or time interval in milliseconds until it's
         *         going to be up
         */
        protected long checkReadyMillis(Ctx ctx) {
            nFunction function = alarm.get();
            if (function == null) {
                // we can pretend it's ready since it's not going to be executed anyway
                return 0L;
            }

            // if the time is currently frozen, we have no way of knowing when it's going to be
            // ready (i.e. how long will someone be looking at the debugger screen);
            // so re-checking in a second seems like a reasonable compromise
            long    now    = ctx.container.nanoTime();
            boolean frozen = ctx.container.isFrozen();
            return frozen ? 1000 : Math.max(0, (triggerTime - now) / NANOS_PER_MILLI);
        }

        // ----- inner class: Trigger --------------------------------------------------------------

        /**
         * A {@link TimerTask} that can be used to register an alarm with the {@link #TIMER}.
         */
        public class nTrigger
                extends TimerTask {

            public nTrigger(Ctx ctx, nAlarmHolder alarm) {
                this.ctx   = ctx;
                this.alarm = alarm;
            }

            private final Ctx ctx;

            private nAlarmHolder alarm;

            @Override
            public void run() {
                nAlarmHolder alarm       = this.alarm;
                long         extraMillis = alarm.checkReadyMillis(ctx);

                if (extraMillis == 0) {
                    this.alarm = null;
                    alarm.run(ctx);
                } else {
                    // reschedule
                    TIMER.schedule(alarm.createTrigger(ctx), extraMillis);
                }
            }

            @Override
            public boolean cancel() {
                boolean      cancelled = super.cancel();
                nAlarmHolder holder    = this.alarm;
                if (holder != null && cancelled) {
                    this.alarm = null;
                }
                return cancelled;
            }
        }
    }
}
