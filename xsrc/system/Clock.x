/**
 * some possible examples:
 * wall clock
 * -> adjusted to a particular timezone
 * monotonic clock
 * runtime clock for the application
 * container-specific clocks
 * service-specific clocks
 */
interface Clock
    {
    typedef function void () Alarm;
    typedef function void () Cancellable;

    @RO DateTime epoch;
//    @RO TimeZone timezone;
    @RO Interval precision;
    @RO Boolean monotonic;
    @RO Boolean realtime;

    @RO Time time;

    Timer createTimer();

    /**
     *
     * <p>
     * Invoking the returned #Cancellable will attempt to cancel the invocation of the #Alarm, but
     * cancellation is not guaranteed, for example if the request for cancellation occurs concurrently
     * with the clock attempting to invoke the alarm.
     */
    Cancellable scheduleAlarm(Alarm alarm, DateTime timeToWakeUp);

    // well-known (injectable) implementations
    static class RuntimeClock
            implements Clock
        {
        @Override
        Cancellable scheduleAlarm(Alarm alarm, DateTime timeToWakeUp)
            {
            TODO - native
            }
        }
    }
