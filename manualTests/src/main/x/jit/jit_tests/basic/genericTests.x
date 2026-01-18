package genericTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running GenericTests >>>>");

        TestBase t0 = new TestBase(5);
        console.print(t0);
        assert t0.augment() == t0.x + 1;

        TestBase t1 = new TestDerived(6);
        console.print(t1);
        assert t1.augment() == (t1.x + 1) * t1.x;

        TestFormal<String> ts = new TestFormal("hello");
        assert ts.value == "hello";
        ts.testType();

        TestFormal<Int> ti = new TestFormal(7);
        assert ti.value == 7;
        ti.setValue(9);
        assert ti.value == 9;
        assert ti.getValue() == 9;
        ti.testType();

        TestFormal<TestBase> to = new TestFormal(t1);
        console.print(to.value);
    }

    class TestBase(Int x) {
        Int augment() = x + 1;
    }

    class TestDerived(Int x) extends TestBase(x) {
        @Override Int augment() = super() * x;
    }

    class TestFormal<Element> (Element value) {
        Element getValue() = value;

        void setValue(Element value) {
            this.value = value;
        }

        void testType() {
            if (Int i := value.is(Int)) {
                console.print("it's an Int; next is ", True);
                console.print(++i);
            }
            if (String s := value.is(String)) {
                console.print("it's a String; size is ", True);
                console.print(s.size);
            } else {
                console.print("Not a String");
            }

            Element value = this.value;
            if (value.is(Int)) {
                console.print(++value);
            } else {
                console.print("Not an Int");
            }

            if (value.is(String), value.size > 0) {
                assert value.size == 5;
            }
        }
    }
}