class Utils {

    /**
     * WARNING: this is an anti-pattern used only for a simulation purposes. Anyone calling this
     * method *must be* `@Concurrent`. Otherwise the caller will be dead-locked.
     */
    static void simulateSlowIO(Duration duration) {
        @Inject Clock clock;
        @Future Tuple done;
        clock.schedule(duration, () -> {done = Tuple:();});

        return done;
    }

    // Timer marks the alarm fn as a "continuation of 'this' fiber, making it re-entrant

    static Int simulateLongCompute(Int count) {
        Int sum = 0;
        for (Int i : 1 .. count) {
            sum += i;
        }
        return sum;
    }
}