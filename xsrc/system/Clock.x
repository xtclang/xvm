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

    /**
     * @return a DateTime at the time of execution (a.k.a. "now")
     */
    @RO DateTime now;

//    @RO TimeZone timezone;
    @RO Duration precision;
    @RO Boolean monotonic;
    @RO Boolean realtime;

    Timer createTimer();

    /**
     * Schedule an #Alarm that will be invoked after the specified Duration completes.
     *
     * Invoking the returned #Cancellable will attempt to cancel the invocation of the #Alarm, but
     * cancellation is not guaranteed, for example if the request for cancellation occurs concurrently
     * with the clock attempting to invoke the alarm.
     */
    Cancellable scheduleAlarm(DateTime timeToWakeUp, Alarm alarm);

    // well-known (injectable) implementations
    static class RuntimeClock
            implements Clock
        {
        @Override
        @RO DateTime now; // native

        @Override
        Cancellable scheduleAlarm(DateTime timeToWakeUp, Alarm alarm)
            {
            TODO - native
            }
        }
    }
