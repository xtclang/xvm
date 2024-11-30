module TestSimple {

    @Inject Console console;

    void run() {
        test(0, flat=False); // typo in the argument name used to produce a generic
                             // "cannot find method" error
    }

    void test(Int i, Boolean flag = True) {}
}