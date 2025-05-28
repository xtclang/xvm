module TestSimple {
    @Inject Console console;

    void run() {
        new Test().run();
    }

    service Test {
        annotation Data into SessionImpl;

        class SessionImpl(String name);

        void run() {
            Data d = new @Data SessionImpl("Test"); // this used to report a "suspicious assignment"
                                                    // claiming that Data is not a "service"
        }
    }
}
