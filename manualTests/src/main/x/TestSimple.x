module TestSimple {
    @Inject Console console;

    void run() {
    }

    @RO conditional String? foo() { // this used to assert the compiler
        TODO
    }
}
