module test1.examples.org {

    Int prop1 = 42;
    String prop2.get() = "hello";
    Int prop3.get() = 43;

    void run() {
        @Inject Console console;

        console.print(prop1);
        console.print(prop2);
        console.print(prop3);

        TestBase t0 = new TestBase(5);
        console.print(t0.x);

        TestBase t1 = new TestDerived(6);
        console.print(t1.x);

//        StringBuffer buf = new StringBuffer();
//        buf.add('c');
//        console.print(buf.toString());
    }

    class TestBase(Int x);
    class TestDerived(Int x) extends TestBase(x) {}
}