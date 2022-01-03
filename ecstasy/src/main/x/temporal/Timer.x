/**
 * A Timer is used to determine elapsed time, analogous to a stop-watch or a kitchen timer in the
 * real world. A Timer can be stopped and started; its elapsed time can be obtained and reset; and
 * alarms can be registered for triggering after a specified period of time.
 *
 * The Timer interface is designed to support injection. Usually, obtaining a Timer is as simple as:
 *
 *   @Inject Timer timer;
 *   timer.start();
 *   // do some work here
 *   Duration elapsed = timer.elapsed;
 */
interface Timer
    {
    /**
     * If the Timer is stopped, then this method starts the Timer, such that the elapsed time begins
     * accumulating again. If the Timer is already started, then this method has no effect.
     */
    void start();

    /**
     * Obtain the duration of time that has elapsed on this timer. An elapsed value from this Timer
     * should only be compared with other elapsed values from this Timer, because the manner of time
     * measurement can differ from one Timer implementation to another, and even from one machine to
     * another.
     */
    @RO Duration elapsed;

    typedef function void () as Alarm;
    typedef function void () as Cancellable;

    /**
     * Schedule an Alarm that will be invoked after the specified Duration has elapsed.
     *
     * Invoking the returned Cancellable will _attempt_ to cancel the invocation of the #Alarm, but
     * cancellation is not guaranteed, since the Clock may have already invoked the Alarm.
     */
    Cancellable schedule(Duration delay, Alarm alarm);

    /**
     * If the Timer is started, then this method stops the Timer, such that the elapsed time stops
     * accumulating. If the Timer is already stopped, then this
     * method has no effect.
     *
     * This method also affects any previously registered alarms that have not already triggered,
     * such that the accumulation of elapsed time for those alarms is also stopped until the Timer
     * is resumed.
     */
    void stop();

    /**
     * Restart the timer, as if the timer were stopped, reset, and re-started.
     */
    void restart()
        {
        stop();
        reset();
        start();
        }

    /**
     * Resets the Timer such that the elapsed time starts accumulating again from zero.
     *
     * This method does not change the running state of the Timer: It neither starts the Timer if
     * the Timer is stopped, nor stops the Timer if the Timer is running.
     *
     * This method also resets any previously registered alarms that have not already triggered;
     * the alarms are not destroyed, but the period of elapsed time for each alarm is reset to zero.
     */
    void reset();

    /**
     * The resolution of a Timer is the length (or "period") of the "tick" of the Timer; it defines
     * the lower bound of the smallest time unit that can be measured.
     */
    @RO Duration resolution;
    }
