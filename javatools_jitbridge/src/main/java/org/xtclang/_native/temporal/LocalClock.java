package org.xtclang._native.temporal;

import java.util.TimeZone;

import org.xtclang.ecstasy.nService;

import org.xvm.javajit.Ctx;

/**
 * Native implementation of a simple wall clock using Java's millisecond-resolution "System" clock.
 */
public class LocalClock extends nService {
    public LocalClock(Ctx ctx, boolean utc) {
        super(ctx);
        this.utc = utc;
    }

    // ----- LocalClock API ------------------------------------------------------------------------

    public boolean utc;

    public long epochMillis$get$p(Ctx ctx) {
        return System.currentTimeMillis();
    }

    public long timezoneMillis$get$p(Ctx ctx) {
        return TimeZone.getDefault().getOffset(System.currentTimeMillis());
    }

    // ----- injection support ---------------------------------------------------------------------

    public static LocalClock $createUtcClock(java.lang.Object opts) {
        return new LocalClock(null, true);
    }

    public static LocalClock $createLocalClock(java.lang.Object opts) {
        return new LocalClock(null, false);
    }
}
