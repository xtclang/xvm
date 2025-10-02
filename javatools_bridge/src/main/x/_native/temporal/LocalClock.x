import TimeOfDay.PicosPerMilli;

/**
 * Simple wall clock using Java's millisecond-resolution "System" clock.
 */
service LocalClock(Boolean utc)
        implements Clock {

    @Override
    @RO Time now.get() = new Time(epochMillis * PicosPerMilli, timezone);

    @Override
    @RO TimeZone timezone.get() = utc ? TimeZone.UTC : TimeZone.of(timezoneMillis * PicosPerMilli);

    @Override
    Duration resolution.get() = Duration.Millisec;

    @Override
    Boolean monotonic.get() = False;

    @Override
    Cancellable schedule(Time when, Alarm alarm, Boolean keepAlive = False);

    @Override
    String toString() = "Clock";

    // internal natives
    @RO Int epochMillis.get() = TODO("native");
    @RO Int timezoneMillis.get() = TODO("native");
}