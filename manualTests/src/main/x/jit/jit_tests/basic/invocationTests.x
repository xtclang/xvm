package invocationTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running InvocationTests >>>>");

        testInvokePrivateMethodAfterInterfaceCast();

        console.print("<<<< Finished InvocationTests <<<<<");
    }

    void testInvokePrivateMethodAfterInterfaceCast() {
        Test t1 = new Test(0);
        Test t2 = new Test(0);
        t1.test(t2);
        assert t2.i == 19;
    }

    interface TestInterface {
        void test(TestInterface t);
    }

    class Test(Int i)
         implements TestInterface {
        @Override
        void test(TestInterface t) {
            if (t.is(Test)) {
                t.privateMethod();
            }
        }

        private void privateMethod() {
            i = 19;
        }
    }
}
