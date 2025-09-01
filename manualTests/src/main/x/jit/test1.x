module test1.examples.org {

    Int prop1 = 42;
    String prop2.get() = "hello";
    Int prop3.get() = 43;

    void run() {
        @Inject Console console;

        console.print(prop1);
        console.print(prop2);
        console.print(prop3);
    }

    void test(TestDerived t) {
        @Inject Console console;

        console.print(t.x);
    }

    class TestBase(Int x);

    class TestDerived(Int x) extends TestBase(x) {
    }
}