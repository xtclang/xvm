module TestSimple {
    @Inject Console console;

    void run() {
    }

    // that used to assert in the compiler instead of a simple compilation error
    void test(String foo, Int bar, String foo) {
    }
}