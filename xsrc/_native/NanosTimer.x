/**
 * Simple timer (stop-watch) using Java's nanosecond-resolution "System" clock.
 */
class NanosTimer
        implements Timer
    {
    @Override
    void pause();

    @Override
    void resume();

    @Override
    void reset();

    @Override
    @RO Duration elapsed;

    @Override
    Duration resolution.get()
        {
        return Duration.NANOSEC;
        }

    @Override
    Cancellable scheduleAlarm(Duration durationBeforeAlarm, Alarm alarm);
    }