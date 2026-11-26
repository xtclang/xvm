package org.xtclang.ecstasy.temporal;

import org.xtclang.ecstasy.Boolean;
import org.xtclang.ecstasy.nFunction;

import org.xvm.javajit.Ctx;

import org.xvm.javajit.annotations.JitGenerated;
import org.xvm.javajit.annotations.JitName;

/**
 * The native implementation of {@code ecstasy.temporal.Timer}.
 */
@JitGenerated
public interface Timer {
    /**
     * The native implementation of Timer method:
     *
     * <p>{@code    Timer start();}
     */
    @JitName
    Timer start(Ctx ctx);

    /**
     * The native implementation of Timer method:
     *
     * <p>{@code    Timer stop();}
     */
    @JitName
    Timer stop(Ctx ctx);

    /**
     * The native implementation of Timer method:
     *
     * <p>{@code    Timer reset();}
     */
    @JitName
    Timer reset(Ctx ctx);

    @JitName
    @JitGenerated
    default nFunction schedule(Ctx ctx, Duration delay, nFunction function, Boolean keepAlive) {
        return null;
    }

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
    nFunction schedule$p(Ctx ctx, long delayLow, long delayHigh, nFunction function,
                         boolean keepAlive, boolean dfltKeepAlive);
}
