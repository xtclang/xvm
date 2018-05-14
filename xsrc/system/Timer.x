typedef function void () Alarm;
typedef function void () Cancellable;

interface Timer
    {
    /**
     * Determine the resolution of this timer.
     */
    @RO Duration resolution;

    /**
     * Obtain the duration of time that has elapsed on this timer. Durations from this
     * Timer should only be compared with other Durations from this Timer.
     */
    @RO Duration elapsed;

    /**
     * Schedule an #Alarm that will be invoked after the specified Duration completes.
     */
    Cancellable scheduleAlarm(Alarm alarm, Duration durationBeforeAlarm);
    }
