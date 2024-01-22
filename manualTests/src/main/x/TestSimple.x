module TestSimple {
    @Inject Console console;

    void test1() {
        @Inject Timer timer;
        @Future Tuple done;

        timer.schedule(Duration.Millisec, () ->
            {
            console.print("Done");
            // this used to compile; now produces a compiler error
            // done = Tuple:();
            });
        return done;
    }

    void test2() {
        Int n;
        &n.set(1);
        console.print($"{n=}"); // this used to fail to compile
    }
}

