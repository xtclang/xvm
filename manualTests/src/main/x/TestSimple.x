module TestSimple {
    @Inject Console console;

    void run() {
        console.print("Hello"); // wouldn't show up at all
    }

    // that used to assert in the compiler instead of a simple compilation error
    void test(String foo, Int bar, String foo) {
    }
}