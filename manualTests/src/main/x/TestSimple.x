module TestSimple {
    @Inject Console console;

    void run() {

        Int i = 0;
        Int j = 1;

        assert i > j as assert False; // this used to blow up in the compiler
    }
}

