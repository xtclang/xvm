module TestSimple {
    @Inject Console console;
    @Inject Clock clock;

    void run() {
        Time time1 = clock.now;
        assert:debug;
        Time time2 = clock.now;
        assert time2 - time1 < Duration.ofSeconds(1);

        new Tester().testSleep(Duration.ofSeconds(5));
    }

    service Tester() {
        Int testSleep(Duration duration) {
            return sleep(duration);
        }
    }

    static Int sleep(Duration duration) {
        @Inject Timer timer;
        @Future Int done;

        timer.schedule(duration, () ->
            {
            console.print($"Wake up after {timer.elapsed} sec");
            done = 17;
            });
        timer.start();
        assert:debug;
        return done;
    }
}
