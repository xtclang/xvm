/**
 * Simple wall clock using Java's millisecond-resolution "System" clock.
 */
class LocalClock
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
    Cancellable schedule(DateTime when, Alarm alarm);
    }