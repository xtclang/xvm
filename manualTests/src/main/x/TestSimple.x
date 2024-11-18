module TestSimple {

    @Inject Console console;

    typedef Test as T;

    void run() {
        String r = T.report(); // that used to assert the compiler
        console.print(r);
    }

    class Test {
        static String report() = "test";
    }
}