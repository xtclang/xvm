/**
 * A Clock provides current DateTime information, for some definition of "current". Clock is an
 * abstraction that hides the various details of the implementation, but does expose important
 * characteristics:
 *
 * * A Clock is associated with a particular TimeZone. All of the DateTime values provided by the
 *   Clock will use the TimeZone from the Clock.
 * * Is the Clock monotonic? A monotonic Clock only moves forward, while a Clock that is _not_
 *   monotonic can jump backwards, such as when Daylight Savings Time ends, or when the Network Time
 *   Protocol (NTP) daemon automatically adjusts a computer's clock.
 * * Different clocks have different resolutions. A 64Hz real-time clock (RTC) has a resolution of
 *   15.625ms, for example, while a CPU time-stamp count (TSC) on a multi-gigahertz chip will have a
 *   resolution in the hundreds of picoseconds.
 *
 * To measure elapsed time, use a Timer.
 */
interface Clock
    {
    /**
     * The instantaneous DateTime value of the Clock.
     */
    @RO DateTime now;

    /**
     * The TimeZone of the DateTime values provided by the Clock.
     */
    @RO TimeZone timezone;

    /**
     * The resolution of a Clock is the length (or "period") of the "tick" of the Clock; it defines
     * the lower bound of the smallest time unit that can be measured.
     */
    @RO Duration resolution;

    /**
     * True iff the Clock always provides DateTime values that are "greater than or equal to"
     * previously provided values. (A Clock that can return a DateTime value that is "less than" a
     * previously returned value must return False for the value of monotonic; this is often the
     * case for a computer's "real time clock", for any clock that can be adjusted, and for any
     * clock subject to daylight savings time.)
     */
    @RO Boolean monotonic;

    typedef function void () Alarm;
    typedef function void () Cancellable;

    /**
     * Request an Alarm to be scheduled on the Clock to go off at a specific point in time.
     *
     * Invoking the returned #Cancellable will _attempt_ to cancel the invocation of the #Alarm, but
     * cancellation is not guaranteed, since the Clock may have already invoked the Alarm.
     */
    Cancellable schedule(DateTime when, Alarm alarm);

    /**
     * Request an Alarm to be scheduled on the Clock to go off after a specified period of time.
     *
     * Invoking the returned #Cancellable will _attempt_ to cancel the invocation of the #Alarm, but
     * cancellation is not guaranteed, since the Clock may have already invoked the Alarm.
     */
    Cancellable schedule(Duration delay, Alarm alarm)
        {
        return schedule(now+delay, alarm);
        }
    }
