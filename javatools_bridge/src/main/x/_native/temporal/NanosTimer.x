/**
 * Simple timer (stop-watch) using Java's nanosecond-resolution "System" clock.
 */
service NanosTimer
        implements Timer {

    @Override
    NanosTimer start();

    @Override
    @RO Duration elapsed;

    @Override
    Cancellable schedule(Duration delay, Alarm alarm, Boolean keepAlive = False);

    @Override
    NanosTimer stop();

    @Override
    NanosTimer reset();

    @Override
    Duration resolution.get() = Duration.Nanosec;

    @Override
    String toString() = "Timer";
}