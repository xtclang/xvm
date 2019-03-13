/**
 * Simple timer (stop-watch) using Java's millisecond-resolution "System" clock.
 */
class LoResRealTimeTimer
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
        return Duration.MILLISEC;
        }

    @Override
    Cancellable scheduleAlarm(Duration durationBeforeAlarm, Alarm alarm);
    }