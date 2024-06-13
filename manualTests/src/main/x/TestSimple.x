module TestSimple {
    @Inject Console console;

    void run() {
    }

    interface Test {
        @RO Int size;

        @RO Boolean empty1 = size == 0; // this used to produce a confusing error message
    }
}
