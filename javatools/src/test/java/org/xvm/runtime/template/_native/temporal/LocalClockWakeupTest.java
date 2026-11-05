package org.xvm.runtime.template._native.temporal;

import org.junit.jupiter.api.Test;

import org.xvm.runtime.template._native.temporal.xLocalClock.Wakeup;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When an alarm of the local clock is due, as the time of the container that sets it: that time
 * leaves out the time the container has been frozen for, as while the debugger holds the program
 * stopped. The container's time has no source to inject, so the tests give the readings an alarm is
 * set at: here the container has been frozen for 6 seconds, so its time is 4,000 when the wall
 * clock's is 10,000.
 */
public class LocalClockWakeupTest {
    private static final long WALL   = 10_000;
    private static final long FROZEN = 6_000;
    private static final long NOW    = WALL - FROZEN;

    @Test
    public void anAlarmForAWallClockTimeIsNotDelayedByTheTimeFrozenBeforeItWasSet() {
        assertEquals(new Wakeup(NOW + 10, 10), Wakeup.at(WALL + 10, NOW, FROZEN));
    }

    @Test
    public void anAlarmForAWallClockTimeIsDueWithAnAlarmAfterTheDelayUntilThen() {
        assertEquals(Wakeup.after(250, NOW), Wakeup.at(WALL + 250, NOW, FROZEN));
    }

    @Test
    public void anAlarmForAWallClockTimeThatHasPassedIsDueAtOnce() {
        assertEquals(new Wakeup(NOW - 1_000, 0), Wakeup.at(WALL - 1_000, NOW, FROZEN));
    }

    @Test
    public void anAlarmAfterADelayIsDueThatLongAfterTheContainersNow() {
        assertEquals(new Wakeup(NOW + 10, 10), Wakeup.after(10, NOW));
    }
}
