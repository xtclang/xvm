/**
 * Simple timer (stop-watch) using Java's nanosecond-resolution "System" clock.
 */
service NanosTimer
        implements Timer {

    @Override
    NanosTimer start();

    @Override
    @RO Duration elapsed.get() {
        Int prev    = previousElapsed;
        Int started = startTime;
        if (started != 0) {
            return Duration.ofNanos(nanoTime - started + prev);
        }
        return Duration.ofNanos(prev);
    }

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

    /**
     * The current time in nanoseconds.
     */
    @RO Int nanoTime.get() = TODO("native");

    /**
     * The nanoseconds timestamp that this timer was started, or zero if the timer is not running.
     */
    @RO Int startTime.get() = TODO("native");

    /**
     * An accumulator of previous elapsed run times for this timer (in nanoseconds).
     */
    @RO Int previousElapsed.get() = TODO("native");
}
