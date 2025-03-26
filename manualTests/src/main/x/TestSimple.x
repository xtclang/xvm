module TestSimple {
    @Inject Console console;

    void run() {
        Descr<TestSimple> m = test; // this used to fail to compile
        testReflection();
    }

    @Descr("")
    void test() {}

    annotation Descr(String descr) into Method {}

    static void testReflection() {
        Method m = TestSimple.test; // this used to assert at runtime
    }
}