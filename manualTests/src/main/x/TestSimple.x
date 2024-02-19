module TestSimple {
    @Inject Console console;

    void run() {
    }

    service Branch() {
        @Atomic public/private Int totalTx;

        void test() {
            ++totalTx; // this used to assert in the compiler
        }
    }
}

