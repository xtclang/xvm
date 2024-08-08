module TestSimple {
    @Inject Console console;

    void run() {
        @Inject Clock clock;

        Clock.Cancellable cancel = clock.schedule(Duration.ofSeconds(10), &alarm);
        console.print("Alarm is up");

        cancel(); // this used to blow up at run-time
    }

    void alarm() {
        assert;
    }
}