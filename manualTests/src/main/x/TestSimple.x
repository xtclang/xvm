module TestSimple
    {
    @Inject Console console;

    void run() {
        S1 s1 = new S1();
        Int r = s1.deadlock^();
        &r.thenDo(() -> console.print("***** Finished!"));
    }

    service S1 {
        Int deadlock() {
            simulateSlowIO(Duration:1S);
            return 42;
        }
    }

    static void simulateSlowIO(Duration duration) {
        @Inject Clock clock;
        @Future Tuple done;
        clock.schedule(duration, () -> {done = Tuple:();}); // this used to hang (deadlock)
        return done;
    }
}