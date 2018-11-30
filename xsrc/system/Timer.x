/**
 * A Timer is used to determine elapsed time. It can be paused and resumed, and its elapsed time can
 * be obtained and reset.
 */
interface Timer
    {
    typedef function void () Alarm;
    typedef function void () Cancellable;

    /**
     * If the Timer is not already paused, then this method pauses the Timer, such that the elapsed
     * time stops accumulating until the Timer is resumed. If the Timer is already paused, then this
     * method has no effect.
     *
     * This method also affects any previously registered alarms that have not already triggered,
     * such that the accumulation of elapsed time for those alarms is also paused until the Timer
     * is resumed.
     */
    void pause();

    /**
     * If the Timer is not paused, then this method resumes the Timer, such that the elapsed time
     * begins accumulating again. If the Timer is already running, then this method has no effect.
     */
    void resume();

    /**
     * Resets the Timer such that the elapsed time starts accumulating again from zero.
     *
     * This method does not resume the Timer if the Timer has been paused.
     *
     * This method resets any previously registered alarms that have not already triggered, such
     * that the period of elapsed time for those alarms is also reset to zero.
     */
    void reset();

    /**
     * Obtain the duration of time that has elapsed on this timer. Durations from this
     * Timer should only be compared with other Durations from this Timer.
     */
    @RO Duration elapsed;

    /**
     * Determine the resolution of this timer.
     */
    @RO Duration resolution;

    /**
     * Schedule an Alarm that will be invoked after the specified Duration completes.
     */
    Cancellable scheduleAlarm(Duration durationBeforeAlarm, Alarm alarm);
    }
