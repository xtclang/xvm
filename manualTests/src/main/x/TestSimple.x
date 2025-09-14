module TestSimple {

    import ecstasy.SharedContext;

    @Inject Console console;
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
