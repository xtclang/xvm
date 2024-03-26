module TestTimeouts {
    @Inject Console console;

    static Duration RUN_TIME = Duration:2S;
    static Duration TIMEOUT  = Duration:1S;

    void run() {
        testCurrentLongCompute();
        testCurrentSlowIO();
        testSyncLongCompute();
        testSyncSlowIO();
        testAsyncLongCompute();
        testAsyncSlowIO();
    }

    void testCurrentLongCompute() {
        console.print("testCurrentLongCompute: ", True);
        try {
            using (new Timeout(TIMEOUT)) {
                Utils.simulateLongCompute(RUN_TIME);
                console.print("Failed timeout");
            }
        } catch (TimedOut expected) {
            console.print("Successfully timed out");
        }
    }

    void testCurrentSlowIO() {
        console.print("testCurrentSlowIO: ", True);
        try {
            using (new Timeout(TIMEOUT)) {
                Utils.simulateSlowIO(RUN_TIME);
                console.print("Failed timeout");
            }
        } catch (TimedOut expected) {
            console.print("Successfully timed out");
        }
    }

    void testSyncLongCompute() {
        console.print("testSyncLongCompute: ", True);

        try {
            Tester t = new Tester();
            using (new Timeout(TIMEOUT)) {
                t.simulateLongCompute(RUN_TIME);
                console.print("Failed timeout");
            }
        } catch (TimedOut expected) {
            console.print("Successfully timed out");
        }
    }

    void testSyncSlowIO() {
        console.print("testSyncSlowIO: ", True);

        try {
            Tester t = new Tester();
            using (new Timeout(TIMEOUT)) {
                t.simulateSlowIO(RUN_TIME);
                console.print("Failed timeout");
            }
        } catch (TimedOut expected) {
            console.print("Successfully timed out");
        }
    }

    void testAsyncLongCompute() {
        console.print("testAsyncLongCompute: ", True);

        Tester t = new Tester();
        using (new Timeout(TIMEOUT)) {
            @Future Tuple done;
            @Future Int r = t.simulateLongCompute^(RUN_TIME);
            &r.whenComplete((_, e) -> {
                if (e == Null) {
                    console.print("Failed to timeout");
                } else {
                    console.print("Successfully timed out");
                }
                done = Tuple:();
            });
            return done;
        }
    }

    void testAsyncSlowIO() {
        console.print("testAsyncSlowIO: ", True);

        Tester t = new Tester();
        using (new Timeout(TIMEOUT)) {
            @Future Tuple done;
            @Future Tuple r = t.simulateSlowIO^(RUN_TIME);
            &r.whenComplete((_, e) -> {
                if (e == Null) {
                    console.print("Failed to timeout");
                } else {
                    console.print("Successfully timed out");
                }
                done = Tuple:();
            });
            return done;
        }
    }

    service Tester() {
        void simulateSlowIO(Duration duration) {
            return Utils.simulateSlowIO(duration);
        }
        Int simulateLongCompute(Duration duration) {
            return Utils.simulateLongCompute(duration);
        }
    }

    class Utils {
        static void simulateSlowIO(Duration duration) {
            @Inject Timer timer;
            @Future Tuple done;
            timer.schedule(duration, () -> {done = Tuple:();});

            return done;
        }

        static Int simulateLongCompute(Duration duration) {
            @Inject Timer timer;
            timer.start();
            Int sum = 0;
            for (Int i = 0; True; i++) {
                sum += i;
                if (i % 10_000 == 0 && timer.elapsed > duration) {
                    timer.stop();
                    break;
                }
            }
            return sum;
        }
    }
}