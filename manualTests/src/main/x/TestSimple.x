module TestSimple {

    protected @Lazy function void() log.calc() = () -> {
        @Inject Console console;
        console.print("run");
    };

    void run() {
        new Runner() {
            @Override
            void run() {
                log(); // this used to fail to compile with non-public access
            }
        }.run();
    }

    interface Runner {
        void run();
    }
}
