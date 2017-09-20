/**
 * TODO
 */
const Duration
    {
    /**
     * The total number of hours, rounded down. This is the same as
     * #minutesTotal / 60.
     */
    @RO Int hoursTotal;
    /**
     * The total number of minutes, rounded down. This is the same as
     * #secondsTotal / 60.
     */
    @RO Int minutesTotal;
    /**
     * The total number of seconds, rounded down. This is the same as
     * #nanosecondsTotal / 1000000000.
     */
    @RO Int secondsTotal;
    /**
     * The total number of nanoseconds, rounded down.
     */
    @RO Int nanosecondsTotal;

    /**
     * Exclusive of the time represented by #hoursTotal, the number of minutes,
     * rounded down. This is the same as #minutesTotal - (#hoursTotal * 60).
     */
    @RO Int minutesPart;
    /**
     * Exclusive of the time represented by #minutesTotal, the number of seconds,
     * rounded down. This is the same as #secondsTotal - (#minutesTotal * 60).
     */
    @RO Int secondsPart;
    /**
     * Exclusive of the time represented by #secondsTotal, the number of nanoseconds,
     * rounded down. This is the same as #nanosecondsTotal - (#secondsTotal * 1000000000).
     */
    @RO Int nanosecondsPart;

    Duration add(Duration duration);
    Duration sub(Duration duration);
    Duration mul(Number factor);
    Duration div(Number divisor);

    Duration min(Duration duration);
    Duration max(Duration duration);
    }
