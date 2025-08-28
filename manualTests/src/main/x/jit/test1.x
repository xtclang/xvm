module test1.examples.org {

    Int prop1 = 42;
    String prop2.get() = "42";

    void run() {
        @Inject Console console;

        console.print(prop1);
        // console.print(prop2);
    }

    void test(TestDerived t) {
    }

    class TestBase {
        protected Int x;
    }

    class TestDerived extends TestBase {
        @Override
        public Int x;
    }
}