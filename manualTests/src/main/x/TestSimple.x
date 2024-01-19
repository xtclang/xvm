module TestSimple {
    @Inject Console console;

    void run() {
        new Tester().testSleep(Duration.ofMillis(50));
    }

    service Tester() {
        Int testSleep(Duration duration) {
            return sleep(duration); // this used to dead-lock
        }
    }

    static Int sleep(Duration duration) {
        @Inject Timer timer;
        @Future Int done;
        console.print("Sleep");

        timer.schedule(duration, () ->
            {
            console.print("Wake Up");
            done = 17;
            });
        return done;
    }
}

