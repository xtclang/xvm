/**
 * Simple runtime clock.
 */
class RuntimeClock
        implements Clock
    {
    @Override
    @RO DateTime now;

    @Override
    @RO TimeZone timezone;

    @Override
    Duration resolution.get()
        {
        return Duration.MILLISEC;
        }

    @Override
    Boolean monotonic.get()
        {
        return false;
        }

    @Override
    Cancellable scheduleAlarm(DateTime when, Alarm alarm);
    }