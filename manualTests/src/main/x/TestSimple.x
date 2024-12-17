module TestSimple {

    @Inject Console console;

    void run() {
        test(1);
    }

    void test(Int i) {
        Boolean f = i % 1000 == 0 || True; // used to blow up the compiler
    }
}