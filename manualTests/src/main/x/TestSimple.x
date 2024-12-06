module TestSimple {

    @Inject Console console;

    void run() {

        Int i = 0;
        new Test(new Test2()).test^();
        Int j = 1;
        Int k = 2;
}

    service Test(Test2 t2) {

        void test() {
            Int i = 0;
            t2.test();
            Int j = 1;
            Int k = 2;
        }
    }

    service Test2 {

        void test() {
            Int i = 0;
            assert:debug;
            Int j = 1;
            Int k = 2; // it used to lose the association at return here and wouldn't stop at
                       // Test.test() line 18
        }
    }
}