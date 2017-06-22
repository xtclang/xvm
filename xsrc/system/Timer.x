typedef function Void () Alarm;
typedef function Void () Cancellable;

interface Timer
    {
    /**
     * Determine the resolution of this timer.
     */
    @ro Duration resolution;

    /**
     * Obtain the duration of time that has elapsed on this timer. Durations from this
     * Timer should only be compared with other Durations from this Timer.
     */
    @ro Duration elapsed;

    /**
     * Schedule an #Alarm that will be invoked after the specified Duration completes.
     */
    Cancellable scheduleAlarm(Alarm alarm, Duration durationBeforeAlarm);
    }
