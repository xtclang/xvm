/**
 * Tests for getting and setting indexed elements.
 */
class ElementAccessorTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running ElementAccessorTests >>>>");
        testAccessPrimitiveElement();
        testAccessNullablePrimitiveElement();
        testNullablePrimitiveArrayGetElement();

        testAccessXvmPrimitiveElement();
        testAccessNullableXvmPrimitiveElement();
        testNullableXvmPrimitiveArrayGetElement();

        testAccessNonPrimitiveElement();
        testAccessNullableNonPrimitiveElement();
        testNullableArrayGetElement();

        console.print("<<<< Finished ElementAccessorTests <<<<<");
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

    static class IndexedHolder<Element>(Element e) {

        @Op("[]") Element getElement(Int index) = e;

        @Op("[]=") void setElement(Int index, Element value) {
            e = value;
        }
    }

}