/**
 * TODO
 */
const Duration
    {
    /**
     * The total number of hours, rounded down. This is the same as
     * #minutesTotal / 60.
     */
    @ro Int hoursTotal;
    /**
     * The total number of minutes, rounded down. This is the same as
     * #secondsTotal / 60.
     */
    @ro Int minutesTotal;
    /**
     * The total number of seconds, rounded down. This is the same as
     * #nanosecondsTotal / 1000000000.
     */
    @ro Int secondsTotal;
    /**
     * The total number of nanoseconds, rounded down.
     */
    @ro Int nanosecondsTotal;

    /**
     * Exclusive of the time represented by #hoursTotal, the number of minutes,
     * rounded down. This is the same as #minutesTotal - (#hoursTotal * 60).
     */
    @ro Int minutesPart;
    /**
     * Exclusive of the time represented by #minutesTotal, the number of seconds,
     * rounded down. This is the same as #secondsTotal - (#minutesTotal * 60).
     */
    @ro Int secondsPart;
    /**
     * Exclusive of the time represented by #secondsTotal, the number of nanoseconds,
     * rounded down. This is the same as #nanosecondsTotal - (#secondsTotal * 1000000000).
     */
    @ro Int nanosecondsPart;

    Duration add(Duration duration);
    Duration sub(Duration duration);
    Duration mul(Number factor);
    Duration div(Number divisor);

    Duration min(Duration duration);
    Duration max(Duration duration);
    }
