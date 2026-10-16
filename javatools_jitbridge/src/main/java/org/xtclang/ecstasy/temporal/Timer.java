package org.xtclang.ecstasy.temporal;

import org.xtclang.ecstasy.nFunction;

import org.xvm.javajit.Ctx;

/**
 * The native implementation of {@code ecstasy.temporal.Timer}.
 */
public interface Timer {
    /**
     * The native implementation of Timer method:
     *
     * <p>{@code    Timer start();}
     */
    Timer start(Ctx ctx);

    /**
     * The native implementation of Timer method:
     *
     * <p>{@code    Timer stop();}
     */
    Timer stop(Ctx ctx);

    /**
     * The native implementation of Timer method:
     *
     * <p>{@code    Timer reset();}
     */
    Timer reset(Ctx ctx);

    /**
     * The native implementation of Timer method:
     * <p>{@code    Cancellable schedule(Duration delay, Alarm alarm, Boolean keepAlive = False);}
     *
     * @param ctx            the {@link Ctx}
     * @param delayLow       the low 64-bit part of the delay (primitive Duration picoseconds)
     * @param delayHigh      the high 64-bit part of the delay (primitive Duration picoseconds)
     * @param function       the function to schedule
     * @param keepAlive      {@code true} to indicate that pending alarm is not a "daemon
     *                       process", i.e. the container should not terminate while the timer is
     *                       running and the alarm is pending
     * @param dfltKeepAlive  {@code true} to indicate that the default value of {@code false}
     *                       should be used for the {@code keepAlive} parameter
     */
    nFunction scheduleꖛ0$p(Ctx ctx, long delayLow, long delayHigh, nFunction function,
                            boolean keepAlive, boolean dfltKeepAlive);
}
