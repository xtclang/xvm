/**
 * A Clock provides current Time information, for some definition of "current". Clock is an
 * abstraction that hides the various details of the implementation, but does expose important
 * characteristics:
 *
 * * A Clock is associated with a particular TimeZone. All of the Time values provided by the
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
interface Clock {
    /**
     * The instantaneous Time value of the Clock.
     */
    @RO Time now;

    /**
     * The TimeZone of the Time values provided by the Clock.
     */
    @RO TimeZone timezone;

    /**
     * The resolution of a Clock is the length (or "period") of the "tick" of the Clock; it defines
     * the lower bound of the smallest time unit that can be measured.
     */
    @RO Duration resolution;

    /**
     * True iff the Clock always provides Time values that are "greater than or equal to"
     * previously provided values. (A Clock that can return a Time value that is "less than" a
     * previously returned value must return False for the value of monotonic; this is often the
     * case for a computer's "real time clock", for any clock that can be adjusted, and for any
     * clock subject to daylight savings time.)
     */
    @RO Boolean monotonic;

    typedef function void () as Alarm;
    typedef function void () as Cancellable;

    /**
     * Request an Alarm to be scheduled on the Clock to go off at a specific point in time.
     *
     * Invoking the returned #Cancellable will _attempt_ to cancel the invocation of the #Alarm, but
     * cancellation is not guaranteed, since the Clock may have already invoked the Alarm.
     *
     * @param when       the time to trigger the alarm at
     * @param alarm      the alarm function
     * @param keepAlive  (optional) pass `True` to indicate that pending alarm is not a "daemon
     *                   process", i.e. the container should not terminate while the timer is
     *                   running and the alarm is pending
     */
    Cancellable schedule(Time when, Alarm alarm, Boolean keepAlive = False);

    /**
     * Request an Alarm to be scheduled on the Clock to go off after a specified period of time.
     *
     * Invoking the returned #Cancellable will _attempt_ to cancel the invocation of the #Alarm, but
     * cancellation is not guaranteed, since the Clock may have already invoked the Alarm.
     *
     * @param delay      the duration to wait before triggering the alarm
     * @param alarm      the alarm function
     * @param keepAlive  (optional) pass `True` to indicate that pending alarm is not a "daemon
     *                   process", i.e. the container should not terminate while the timer is
     *                   running and the alarm is pending
     */
    Cancellable schedule(Duration delay, Alarm alarm, Boolean keepAlive = False) {
        return schedule(now+delay, alarm, keepAlive);
    }
}
