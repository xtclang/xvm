typedef function Void () Alarm;
typedef function Void () Cancellable;

/**
 * some possible examples:
 * wall clock
 * -> adjusted to a particular timezone
 * monotonic clock
 * runtime clock for the application
 * container-specific clocks
 * service-specific clocks
 */
service Clock
    {
    @ro DateTime epoch;
    @ro TimeZone timezone;
    @ro Interval precision;
    @ro Boolean monotonic;
    @ro Boolean realtime;

    @ro Time time;

    Timer createTimer();

    /**
     *
     * <p>
     * Invoking the returned #Cancellable will attempt to cancel the invocation of the #Alarm, but
     * cancellation is not guaranteed, for example if the request for cancellation occurs concurrently
     * with the clock attempting to invoke the alarm.
     */
    Cancellable scheduleAlarm(Alarm alarm, DateTime timeToWakeUp);
    }
