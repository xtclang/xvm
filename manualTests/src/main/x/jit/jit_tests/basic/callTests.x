package callTests {

    @Inject Console console;

    Int val1 = 42;
    Int val2.get() = 43;

    void run() {
        console.print(">>>> Running CallTests >>>>");

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
        if (Int i3 := testConditional(30)) {
            assert i3 == 33;
        }

        assert testStatic(1);
        assert !testStatic(-1);

//        assert testSpecificWithDefault("hi") == 2;
//        assert testSpecificWithDefault() == 3;

        assert testWidened("hi") == 2;
        assert testWidened(7)    == 7;

//        assert testWidenedWithDefault("hi") == 2;
//        assert testWidenedWithDefault(7)    == 7;
//        assert testWidenedWithDefault()     == 42;

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

//        assert testNullablePrimitiveWithDefault(7)     == 7;
//        assert testNullablePrimitiveWithDefault(Null)  == 0;
//        assert testNullablePrimitiveWithDefault()      == 42;

//        assert testXvmPrimitiveWithDefault(7)  == 7;
//        assert testXvmPrimitiveWithDefault()   == 42;

//        assert testNullableXvmPrimitiveWithDefault(7) == 7;
//        assert testNullableXvmPrimitiveWithDefault()  == 42;
    }

    Int testStandardWithDefault(Int i, Int j = 2) = i + j;

    (Int, Int) testMultiReturns(Int i) = (i*2, i-2);

    (Int?, Int) testNullableMultiReturns(Int i) = (Null, i/2);

    conditional Int testConditional(Int i) {
        if (i > -1) {
            return True, i + 3;
        }
        return False;
    }

    static Boolean testStatic(Int i) = i > 0;

//    Int testSpecificWithDefault(String s = "bye") = s.size;

    Int testWidened(String|Int si) = si.is(Int) ? si : si.size;

//    Int testWidenedWithDefault(String|Int si = 42) = si.is(Int) ? si : si.size;

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

//    Int testNullablePrimitiveWithDefault(Int? i = 42) = i == Null ? 0 : i;

//    Int128 testXvmPrimitiveWithDefault(Int128 i = 42) = i;

//    Int128 testNullableXvmPrimitiveWithDefault(Int128? i = 42) = i == Null ? 0 : i;
}
