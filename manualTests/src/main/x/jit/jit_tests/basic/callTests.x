package callTests {

    @Inject Console console;

    Int val1 = 42;
    Int val2.get() = 43;

    void run() {

        // const property initialization
        assert val2 - 1 == val1;

        Int i1 = testStandardWithDefault(0);
        assert i1 == 2;

        i1 = testStandardWithDefault(i1, 5);
        assert i1 == 7;

        (i1, Int i2) = testMultiReturns(0);
        assert i1 == 0 && i2 == -2;

        (Int? i1N, Int i2N) = testNullableMultiReturns(10);
        assert i1N == Null && i2N == 5;

        // conditional
        if (Int i3 := testConditionalPrimitive(30)) {
            assert i3 == 33;
        }
        assert testConditional();
        assert testConditionalNarrowing("hello") == 5;
        assert testConditionalNarrowing(42) == 0;

        assert testStatic(1);
        assert !testStatic(-1);

        assert testSpecificWithDefault("hi") == 2;
        assert testSpecificWithDefault() == 3;

        assert testWidened("hi") == 2;
        assert testWidened(7)    == 7;

        testWidenedReturns();
        testNullableWidenedReturns();

        assert testWidenedWithDefault("hi") == 2;
        assert testWidenedWithDefault(7)    == 7;
        assert testWidenedWithDefault()     == 42;

        assert testPrimitiveWithDefault(7)  == 7;
        assert testPrimitiveWithDefault()   == 42;

        assert testNullablePrimitive(7)     == 7;
        assert testNullablePrimitive(Null)  == 0;

        assert testNullablePrimitiveComplexFlow(1) == 6;
        assert testNullablePrimitiveComplexFlow(Null) == -1;

        Int? i5 = Null;
        assert testNullablePrimitiveComplexFlow(i5) == -1;

        i5 = 1;
        assert testNullablePrimitiveComplexFlow(i5) == 6;

        // formal type parameters
        assert i5.notLessThan(3) != i5;
        assert i5.notGreaterThan(3) == i5;

        assert testNullablePrimitiveWithDefault(7)     == 7;
        assert testNullablePrimitiveWithDefault(Null)  == 0;
        assert testNullablePrimitiveWithDefault()      == 42;

        assert testXvmPrimitiveWithDefault(7)  == 7;
        assert testXvmPrimitiveWithDefault()   == 42;

        assert testNullableXvmPrimitiveWithDefault(7) == 7;
        assert testNullableXvmPrimitiveWithDefault()  == 42;

        testSuperCall();
        testCovariantSuperCall();
        testWidenedArgumentNarrowing();
        testSpecializedCapRouting();
    }

    Int testStandardWithDefault(Int i, Int j = 2) = i + j;

    (Int, Int) testMultiReturns(Int i) = (i*2, i-2);

    (Int?, Int) testNullableMultiReturns(Int i) = (Null, i/2);

    conditional Int testConditionalPrimitive(Int i) {
        if (i > -1) {
            return True, i + 3;
        }
        return False;
    }

    Boolean testConditional() {
        assert Object ignored := presentObject();
        assert !(String missing := absentString());

        String value = "hello";
        value := absentString();
        return value == "hello";

        conditional Object presentObject() {
            return True, console;
        }

        conditional String absentString() {
            return False;
        }
    }

    Int testConditionalNarrowing(Object value) = value.is(String)?.size : 0;

    static Boolean testStatic(Int i) = i > 0;

    Int testSpecificWithDefault(String s = "bye") = s.size;

    Int testWidened(String|Int si) = si.is(Int) ? si : si.size;

    void testWidenedReturns() {
        // both the primary return and the additional result must carry boxed union values
        (Int|String first, Int|String second) = pair(5);
        assert first == 5 && second == 6;

        (first, second) = pairFinally(7);
        assert first == 7 && second == 8;

        // a nonzero upper half verifies that boxing preserves both primitive slots
        Int128 n = 0x1_0000_0000_0000_0005;
        (Int128|String first128, Int128|String second128) = pair128(n);
        assert first128.as(Int128) == n && second128.as(Int128) == n + 1;

        (first128, second128) = pair128Finally(n + 2);
        assert first128.as(Int128) == n + 2 && second128.as(Int128) == n + 3;

        (Int|String, Int|String) pair(Int n) = (n, n + 1);

        (Int|String, Int|String) pairFinally(Int n) {
            try {
                return n, n + 1;
            } finally {
                assert n >= 0;
            }
        }

        (Int128|String, Int128|String) pair128(Int128 n) = (n, n + 1);

        (Int128|String, Int128|String) pair128Finally(Int128 n) {
            try {
                return n, n + 1;
            } finally {
                assert n >= 0;
            }
        }
    }

    void testNullableWidenedReturns() {
        // each return position must preserve both Null and a boxed value
        (Int?|String first, Int?|String second) = pair(5, Null);
        assert first == 5 && second == Null;
        (first, second) = pair(Null, 6);
        assert first == Null && second == 6;

        (first, second) = pairFinally(7, Null);
        assert first == 7 && second == Null;
        (first, second) = pairFinally(Null, 8);
        assert first == Null && second == 8;

        // the null flag follows both words of the Int128 payload
        Int128 n = 0x1_0000_0000_0000_0005;
        (Int128?|String first128, Int128?|String second128) = pair128(n, Null);
        assert first128.as(Int128) == n && second128 == Null;
        (first128, second128) = pair128(Null, n + 1);
        assert first128 == Null && second128.as(Int128) == n + 1;

        (first128, second128) = pair128Finally(n + 2, Null);
        assert first128.as(Int128) == n + 2 && second128 == Null;
        (first128, second128) = pair128Finally(Null, n + 3);
        assert first128 == Null && second128.as(Int128) == n + 3;

        (Int?|String, Int?|String) pair(Int? n1, Int? n2) = (n1, n2);

        (Int?|String, Int?|String) pairFinally(Int? n1, Int? n2) {
            try {
                return n1, n2;
            } finally {
                assert n1 == Null || n2 == Null;
            }
        }

        (Int128?|String, Int128?|String) pair128(Int128? n1, Int128? n2) = (n1, n2);

        (Int128?|String, Int128?|String) pair128Finally(Int128? n1, Int128? n2) {
            try {
                return n1, n2;
            } finally {
                assert n1 == Null || n2 == Null;
            }
        }
    }

    Int testWidenedWithDefault(String|Int si = 42) = si.is(Int) ? si : si.size;

    Int testPrimitiveWithDefault(Int i = 42) = i;

    Int testNullablePrimitiveComplexFlow(Int? n) {
        if (n.is(Int)) {
            assert n > 0;
            n += 2;
        }

        if (n != Null) {
            assert n > 0;
            n *= 2;
        }
        return n ?: -1;
    }

    Int testNullablePrimitive(Int? i) = i == Null ? i.ordinal : i;

    Int testNullablePrimitiveWithDefault(Int? i = 42) = i == Null ? 0 : i;

    Int128 testXvmPrimitiveWithDefault(Int128 i = 42) = i;

    Int128 testNullableXvmPrimitiveWithDefault(Int128? i = 42) = i == Null ? 0 : i;

    void testSuperCall() {
        interface I0 {
            Int f() = 10;
        }

        interface I1 extends I0 {
            @Override
            Int f() = super() + 1;
        }

        class Test implements I1 {
        }

        assert new Test().f() == 11;
    }

    void testCovariantSuperCall() {
        interface Base {
            Base! self() = this;
        }

        interface Derived extends Base {
            @Override
            Derived self() = super().as(Derived);
        }

        class Test(Int value) implements Derived {
        }

        Test result = new Test(42).self();
        assert result.value == 42;
    }

    void testWidenedArgumentNarrowing() {
        Int size(String|Int value) {
            if (value.is(String)) {
                return stringSize(value);
            }
            return 0;
        }

        Int stringSize(String value = "") = value.size;

        assert size("hello") == 5;
        assert size(42) == 0;
    }

    void testSpecializedCapRouting() {
        interface Transformer<Element> {
            Element transform(Element value, Int count);

            (Int, Element) transformMany(Element value, Int count);
        }

        class StringTransformer
                implements Transformer<String> {
            @Override
            String transform(String value, Int count) = count == 2 ? value : "";

            @Override
            (Int, String) transformMany(String value, Int count) = (count, value);
        }

        Transformer<String> transformer = new StringTransformer();
        assert transformer.transform("value", 2) == "value";

        (Int count, String value) = transformer.transformMany("many", 3);
        assert count == 3 && value == "many";
    }
}
