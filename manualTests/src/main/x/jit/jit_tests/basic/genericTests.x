package genericTests {

    @Inject Console console;

    void run() {

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

        function Int128(Boolean) transform = value -> value ? 42 : 0;
        Int128 result = apply(transform, True);
        assert result == 42;

        function Int(Object) broadTransform = value -> 42;
        GenericFunction<Int> intApply       = new GenericFunction();
        assert intApply.applyInt(broadTransform, 7) == 42;

        testFormalComparison();
        testFormalType();
        testGenericLocalClassName();
        testEscapedClassName();
    }

    void testFormalComparison() {
        Formal<String> test = new Formal();
        assert test.less("alpha", "beta");

        class Formal<Element> {
            Boolean less(Element value1, Element value2) {
                assert Element.is(Type<Orderable>);
                return value1 < value2;
            }
        }
    }

    void testFormalType() {
        Iterator<String> iterator = ["alpha"].iterator();
        assert iterator.Element.is(Type<Orderable>);
    }

    void testGenericLocalClassName() {
        // this validates the synthetic class name generation for generic classes inside methods
        class Box<Element>(Element value) {}

        assert new Box<Int>(42).value == 42;
        assert new Box<String>("value").value == "value";
    }

    void testEscapedClassName() {
        // literal markers in names must not be interpreted as generated constant-id suffixes
        class Boxꖛ1ꖛ2<Element>(Element value) {}

        assert new Boxꖛ1ꖛ2<Int>(42).value == 42;
        assert new Boxꖛ1ꖛ2<String>("value").value == "value";

        // this class has a literal marker but no generated suffix
        assert new Valueꖛ1(9).value == 9;
    }

    class Valueꖛ1(Int value) {}

    static <Element, Result> Result apply(
            function Result(Element) transform, Element value) = transform(value);

    class GenericFunction<Element> {
        Int applyInt(function Int(Element) transform, Element value) = transform(value);
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
                console.print($"it's an Int; next is {++i}");
            }
            if (String s := value.is(String)) {
                console.print($"it's a String; size is {"5"}");
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
