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
        testNestedSlowIO();
    }

    void testCurrentLongCompute() {
        console.print("testCurrentLongCompute: ", True);
        try {
            using (new Timeout(TIMEOUT)) {
                Utils.simulateLongCompute(RUN_TIME);
                console.print("Failed timeout");
            }
        } catch (TimedOut expected) {
            console.print($"Successfully timed out after {expected.timeout.duration}s");
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
            console.print($"Successfully timed out after {expected.timeout.duration}s");
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
            console.print($"Successfully timed out after {expected.timeout.duration}s");
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
            console.print($"Successfully timed out after {expected.timeout.duration}s");
        }
    }

    void testAsyncLongCompute() {
        console.print("testAsyncLongCompute: ", True);

        Tester t = new Tester();
        using (new Timeout(TIMEOUT)) {
            @Future Tuple done;
            @Future Int r = t.simulateLongCompute^(RUN_TIME);
            &r.whenComplete((_, e) -> {
                if (e.is(TimedOut)) {
                    console.print($"Successfully timed out after {e.timeout.duration}s");
                } else {
                    console.print("Failed to timeout");
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
                if (e.is(TimedOut)) {
                    console.print($"Successfully timed out after {e.timeout.duration}s");
                } else {
                    console.print("Failed to timeout");
                }
                done = Tuple:();
            });
            return done;
        }
    }

    void testNestedSlowIO() {
        console.print("testNestedSlowIO: ", True);

        Tester t = new Tester();
        try {
            using (new Timeout(RUN_TIME)) {
                try {
                    using (new Timeout(TIMEOUT)) {
                        t.simulateSlowIO(RUN_TIME);
                        console.print("Failed timeout");
                    }
                } catch (TimedOut expected) {
                    console.print($"Successfully timed out after {expected.timeout.duration}s");
                }

                console.print("testNestedSlowIO #2: ", True);
                using (new Timeout(TIMEOUT)) {
                    t.simulateSlowIO(RUN_TIME);
                    console.print("Failed timeout");
                }
            }
        } catch (TimedOut expected) {
            console.print($"Successfully timed out after {expected.timeout.duration}s");
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
            timer.start();
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