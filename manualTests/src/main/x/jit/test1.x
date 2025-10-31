module test1.examples.org {

    @Inject Console console;

    Int prop1 = 42;
    String prop2.get() = "hello";
    Int prop3.get() = 43;

    void run() {
        console.print(prop1);
        console.print(prop2);
        console.print(prop3);

        TestBase t0 = new TestBase(5);
        console.print(t0);
        assert t0.augment() == t0.x + 1;

        TestBase t1 = new TestDerived(6);
        console.print(t1);
        assert t1.augment() == (t1.x + 1) * t1.x;

        TestFormal<String> ts = new TestFormal("hello");
        console.print(ts.value);
        ts.testType();

        TestFormal<Int> ti = new TestFormal(7);
        console.print(ti.value + 1);
        ti.setValue(9);
        assert ti.value == 9;
        assert ti.getValue() == 9;
        ti.testType();

        TestFormal<TestBase> to = new TestFormal(t1);
        console.print(to.value);

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
    class TestFormal<Element> (Element value) {
        Element getValue() {
            return value;
        }
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
            }

//            Element value = this.value;
//            if (value.is(String)) {
//                console.print(value.size);
//                console.print(value);
//            }
        }
    }
}