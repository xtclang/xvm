/**
 * A service that cleans out old `Session` objects from memory.
 *
 * TODO explain why NOT concurrent
 */
service SessionPurger(Duration cycleTime=Duration:1M)
    {
    // ----- properties ----------------------------------------------------------------------------

    @Inject Timer timer;

    /**
     * How often to scan.
     */
    Duration cycleTime;

    /**
     * The timer-cancel for the next scan.
     */
    Timer.Cancellable? cancelNextScan;

    /**
     * Session ids that have been added since the last scan began.
     */
    Int[] newIds;

    /**
     * Set to `True` when the `SessionPurger` is supposed to stop its work.
     */
    Boolean stopping;


    // ----- session control -----------------------------------------------------------------------

    /**
     * This is a notification from the SessionManager that the specified session ID needs to be
     * tracked (managed) by the purger.
     */
    void track(Int id)
        {
        newIds.add(id);

        if (cancelNextScan == Null)
            {
            cancelNextScan = timer.schedule(cycleTime, scan);
            }
        }

    /**
     * Ask the SessionPurger to shut down nicely.
     */
    void stopPurging()
        {
        if (!stopping)
            {
            cancelNextScan?();
            stopping = True;
            }
        }


    // ----- internal -----------------------------------------------------------------------

    void scan()
        {
        Time scanStart = clock.now;
        try
            {
            // TODO
            }
        finally
            {
            // schedule the next time to run the purge processing
            // TODO
            }
        }
    }
