module TestSimple {

    @Lazy function void() log.calc() = () -> {
        @Inject Console console;
        console.print("run");
    };

    void run() {
        new Runner() {
            @Override
            void run() {
                log(); // this used to fail to compile
            }
        }.run();
    }

    interface Runner {
        void run();
    }
}
