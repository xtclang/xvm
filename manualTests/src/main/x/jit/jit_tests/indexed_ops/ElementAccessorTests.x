/**
 * Tests for getting and setting indexed elements.
 */
class ElementAccessorTests {

    @Inject Console console;

    void run() {
        testAccessPrimitiveElement();
        testAccessNullablePrimitiveElement();
        testNullablePrimitiveArrayGetElement();

        testAccessXvmPrimitiveElement();
        testAccessNullableXvmPrimitiveElement();
        testNullableXvmPrimitiveArrayGetElement();

        testAccessNonPrimitiveElement();
        testAccessNullableNonPrimitiveElement();
        testNullableArrayGetElement();
        testNullableArrayTarget();
        testNullablePrimitiveArrayTarget();
        testNarrowedArrayTarget();

    }

    void testAccessPrimitiveElement() {
        IndexedHolder<Int> holder = new IndexedHolder(10);
        assert holder[0] == 10;
        holder[0] = 20;
        assert holder[0] == 20;
    }

    void testAccessNullablePrimitiveElement() {
        IndexedHolder<Int?> holder = new IndexedHolder(10);
        assert holder[0] == 10;
        holder[0] = 20;
        assert holder[0] == 20;
        holder[0] = Null;
        assert holder[0] == Null;
    }

    void testNullablePrimitiveArrayGetElement() {
        Array<Int?> array = new Array();
        array.add(10);
        assert array[0] == 10;
        array[0] = Null;
        assert array[0] == Null;
    }

    void testAccessXvmPrimitiveElement() {
        IndexedHolder<Int128> holder = new IndexedHolder(18446744073709551619);
        assert holder[0] == 18446744073709551619;
        holder[0] = 18446744073709551620;
        assert holder[0] == 18446744073709551620;
    }

    void testAccessNullableXvmPrimitiveElement() {
        IndexedHolder<Int128?> holder = new IndexedHolder(18446744073709551619);
        assert holder[0] == 18446744073709551619;
        holder[0] = 18446744073709551620;
        assert holder[0] == 18446744073709551620;
        holder[0] = Null;
        assert holder[0] == Null;
    }

    void testNullableXvmPrimitiveArrayGetElement() {
        Array<Int128?> array = new Array();
        array.add(18446744073709551619);
        assert array[0] == 18446744073709551619;
        array[0] = Null;
        assert array[0] == Null;
    }

    void testAccessNonPrimitiveElement() {
        IndexedHolder<String> holder = new IndexedHolder("Foo");
        assert holder[0] == "Foo";
        holder[0] = "Bar";
        assert holder[0] == "Bar";
    }

    void testAccessNullableNonPrimitiveElement() {
        IndexedHolder<String?> holder = new IndexedHolder("Foo");
        assert holder[0] == "Foo";
        holder[0] = "Bar";
        assert holder[0] == "Bar";
        holder[0] = Null;
        assert holder[0] == Null;
    }

    void testNullableArrayGetElement() {
        Array<String?> array = new Array();
        array.add("foo");
        assert array[0] == "foo";
        array[0] = Null;
        assert array[0] == Null;
    }

    void testNullableArrayTarget() {
        String? getFirst(String[]? array) = array?[0] : Null;

        void setFirst(String[]? array, String value) {
            array?[0] = value;
        }

        String[] array = new Array();
        array.add("before");
        assert getFirst(array) == "before";
        setFirst(array, "after");
        assert getFirst(array) == "after";

        assert getFirst(Null) == Null;
        setFirst(Null, "ignored");
    }

    void testNullablePrimitiveArrayTarget() {
//        TODO: the conditional get tries to store a nullable-extension flag that is not on stack
//        Int? getFirst(Int[]? array) = array?[0] : Null;
//
//        void setFirst(Int[]? array, Int value) {
//            array?[0] = value;
//        }
//
//        Int[] array = [10];
//        assert getFirst(array) == 10;
//        setFirst(array, 20);
//        assert getFirst(array) == 20;
//
//        assert getFirst(Null) == Null;
//        setFirst(Null, 30);
    }

    void testNarrowedArrayTarget() {
        void verify(Object array) {
            if (array.is(Int[])) {
                assert array[0] == 10;
                array[0] = 20;
                assert array[0] == 20;
            } else {
                assert;
            }
        }

        Int[] ints = new Array();
        ints.add(10);
        verify(ints);
    }

    static class IndexedHolder<Element>(Element e) {

        @Op("[]") Element getElement(Int index) = e;

        @Op("[]=") void setElement(Int index, Element value) {
            e = value;
        }
    }

}
