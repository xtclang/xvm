module TestSimple {

    @Inject Console console;

    void run() {
        test(1);
    }

    void test(Int i) {
        Boolean f = True || foo(i % 1000) || True; // this used to blow up the compiler
    }

    Boolean foo(Int i) = TODO;
}