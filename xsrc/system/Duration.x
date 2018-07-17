/**
 * TODO
 */
const Duration
    {
    /**
     * The total number of hours, rounded down. This is the same as
     * #minutesTotal / 60.
     */
    Int hoursTotal;
    /**
     * The total number of minutes, rounded down. This is the same as
     * #secondsTotal / 60.
     */
    Int minutesTotal;
    /**
     * The total number of seconds, rounded down. This is the same as
     * #nanosecondsTotal / 1000000000.
     */
    Int secondsTotal;
    /**
     * The total number of nanoseconds, rounded down.
     */
    Int nanosecondsTotal;
    /**
     * Exclusive of the time represented by #hoursTotal, the number of minutes,
     * rounded down. This is the same as #minutesTotal - (#hoursTotal * 60).
     */
    Int minutesPart;
    /**
     * Exclusive of the time represented by #minutesTotal, the number of seconds,
     * rounded down. This is the same as #secondsTotal - (#minutesTotal * 60).
     */
    Int secondsPart;
    /**
     * Exclusive of the time represented by #secondsTotal, the number of nanoseconds,
     * rounded down. This is the same as #nanosecondsTotal - (#secondsTotal * 1000000000).
     */
    Int nanosecondsPart;

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

    static Duration INSTANT = new Duration();
    }
