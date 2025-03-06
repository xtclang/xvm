module TestSimple {
    @Inject Console console;

    static Int COUNT = 40_000;
    static Int LOGS  = 20;
    static Int BATCH = COUNT/LOGS;

    // this test used to hang or take very long time to finish
    void run() {
        Test[] test = new Test[COUNT](_ -> new Test());
        for (Int i : 0 ..< COUNT) {
            @Future Tuple result = test[i].test();
        }
        @Inject Timer timer;
        timer.start();

        console.print("waiting");
        for (Int i = 0; i < COUNT; i++) {
            test[i].isComplete();

            if ((i+1) % BATCH == 0) {
                console.print($"completed {i+1}");
            }
        }
        console.print($"done in {timer.elapsed.seconds} sec");
    }

    service Test {
        @Future Boolean done = False;

        Boolean isComplete() = done;

        void test() {
            StringBuffer buf = new StringBuffer();
            for (Int i : 0 ..< 10) {
                buf.append(i);
            }
            done = True;
        }
    }
}