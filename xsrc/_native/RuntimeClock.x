/**
 * Simple runtime clock.
 */
class RuntimeClock
        implements Clock
    {
    @Override
    @RO DateTime now;

    @Override
    Cancellable scheduleAlarm(DateTime timeToWakeUp, Alarm alarm);
    }