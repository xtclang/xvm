module TestSimple {
    @Inject Console console;

    void run() {
        if (foo()) {} // this used to produce a strange error message
    }

    void foo() {}
}