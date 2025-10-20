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
        console.print(t0);
        assert t0.augment() == t0.x + 1;

        TestBase t1 = new TestDerived(6);
        console.print(t1);
        assert t1.augment() == (t1.x + 1) * t1.x;

        TestFormal<Int> t2 = new TestFormal(7);
        console.print(t2.value);

        TestFormal<TestBase> t3 = new TestFormal(t1);
        console.print(t3.value);

//        StringBuffer buf = new StringBuffer();
//        buf.add('c');
//        console.print(buf.toString());
    }

    class TestBase(Int x) {
        Int augment() {
            return x + 1;
        }
    }
    class TestDerived(Int x) extends TestBase(x) {
        @Override Int augment() {
            return super() * x;
        }
    }
    class TestFormal<Element> (Element value) {}
}