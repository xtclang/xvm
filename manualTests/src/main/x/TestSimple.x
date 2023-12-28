module TestSimple {
    @Inject Console console;

    void run() {
        Test t = new Test();
        t.&test(True); // this used to assert in the compiler
    }

    class Test {
        void test(Boolean flag) {
            console.print(flag);
        }
    }
}
