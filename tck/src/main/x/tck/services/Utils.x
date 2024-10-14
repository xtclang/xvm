class Utils {

    static void simulateSlowIO(Duration duration) {
        new IO().simulate(duration);
    }

    static Int simulateLongCompute(Int count) {
        Int sum = 0;
        for (Int i : 1 .. count) {
            sum += i;
        }
        return sum;
    }

    static service IO {
        void simulate(Duration duration) {
            @Inject Clock clock;
            @Future Tuple done;
            clock.schedule(duration, () -> {done = Tuple:();});

            return done;
        }
    }
}