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

    /**
     * Addition: return a sum of durations.
     */
    @Op Duration add(Duration duration)
        {
        TODO
        }

    /**
     * Subtraction: return a difference of durations.
     */
    @Op Duration sub(Duration duration)
        {
        TODO
        }

    /**
     * Multiplication: return a multiple of this duration.
     */
    @Op Duration mul(Number factor)
        {
        TODO
        }

    /**
     * Division: return a fraction of this duration.
     */
    @Op Duration div(Number divisor)
        {
        TODO
        }

    /**
     * Return a minimum of two durations.
     */
    Duration min(Duration duration)
        {
        return this <= duration ? this : duration;
        }

    /**
     * Return a maximum of two durations.
     */
    Duration max(Duration duration)
        {
        return this >= duration ? this : duration;
        }
    }
