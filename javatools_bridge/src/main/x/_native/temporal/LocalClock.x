/**
 * Simple wall clock using Java's millisecond-resolution "System" clock.
 */
service LocalClock
        implements Clock {

    @Override
    @RO Time now;

    @Override
    @RO TimeZone timezone;

    @Override
    Duration resolution.get() {
        return Duration.Millisec;
    }

    @Override
    Boolean monotonic.get() = False;

    @Override
    Cancellable schedule(Time when, Alarm alarm, Boolean keepAlive = False);

    @Override
    String toString() = "Clock";
}