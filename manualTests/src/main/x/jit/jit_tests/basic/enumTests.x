package enumTests {

    import ecstasy.io.IOException;

    void run() {
        @Inject Console console;

        Color c = Blue;
        assert c.ordinal == 2;
        assert c.text == "B";
        assert c.rgb == 65_025;
        console.print(c);

        assert c != Green;
        assert c > Green;

        assert !testRedOrNull(c);
        assert !testRed(c);

        Boolean f = False;
        Boolean t = True;
        assert !t.not();
        assert f.toInt64() == 0;
        assert t.toInt64() == 1;

        assert f == f;
        assert f != t;
        assert f <=> t == Lesser;
        assert t <=> f == Greater;
        assert t <=> t == Equal;

        Color|Int cint = Red;
        assert testRed1(cint);
        assert testRed2(cint);
        assert testRed3(cint);

        Ordered lesser = Lesser;
        assert lesser.reversed.ordinal == Greater.ordinal;

        assert Signum.Negative.factor.toString() == "-1";
        assert Signum.Zero.factor.toString()     == "0";
        assert Signum.Positive.factor.toString() == "+1";
        assert Signum.Negative < Signum.Zero < Signum.Positive;
        assert Signum.Positive > Signum.Zero > Signum.Negative;

        testBooleanCount();
        testBooleanNames();
        testBooleanValues();
        testNullableCount();
        testNullableNames();
        testNullableValues();
        testOrderedCount();
        testOrderedNames();
        testOrderedValues();
        testNumberSignumCount();
        testNumberSignumNames();
        testNumberSignumValues();
        testFPNumberRoundingCount();
        testFPNumberRoundingNames();
        testFPNumberRoundingValues();
    }

    Boolean testRedOrNull(Color? c) {
        return c == Null || c == Red;
    }

    Boolean testRed(Color? c) {
        return c == Red;
    }

    Boolean testRed1(Color|Int cint) {
        if (Blue == cint) {
            assert;
        }
        return Red == cint;
    }

    Boolean testRed2(Color|Int cint) {
        if (cint.is(Color)) {
            cint = 111;
            cint = -cint;
            cint = cint * 43;
        }
        return cint != 42;
    }

    Boolean testRed3(Color|Int cint) {
        if (cint.is(Color)) {
            cint = 111;
            cint = cint * 43;
            cint = Blue;
        }
        return cint != 42;
    }

    enum Color(String text, Int rgb) {
        Red("R", 0), Green("G", 255), Blue("B", 255*255)
    }


    void testBooleanCount() {
        assert Boolean.count == 2;
    }

    void testBooleanNames() {
        assert Boolean.names.size == 2;
        assert Boolean.names[0] == "False";
        assert Boolean.names[1] == "True";
    }

    void testBooleanValues() {
// TODO eBoolean.java values$get(Ctx ctx) must return ArrayᐸBooleanᐳ
//        assert Boolean.values.size == 2;
//        assert Boolean.values[0] == False;
//        assert Boolean.values[1] == True;
    }

    void testNullableCount() {
        assert Nullable.count == 1;
    }

    void testNullableNames() {
        assert Nullable.names.size == 1;
        assert Nullable.names[0] == "Null";
    }

    void testNullableValues() {
        assert Nullable.values.size == 1;
        assert Nullable.values[0] == Null;
    }

    void testOrderedCount() {
        assert Ordered.count == 3;
    }

    void testOrderedNames() {
        assert Ordered.names.size == 3;
        assert Ordered.names[0] == "Lesser";
        assert Ordered.names[1] == "Equal";
        assert Ordered.names[2] == "Greater";
    }

    void testOrderedValues() {
        assert Ordered.values.size == 3;
        assert Ordered.values[0] == Lesser;
        assert Ordered.values[1] == Equal;
        assert Ordered.values[2] == Greater;
    }

    void testNumberSignumCount() {
        assert Number.Signum.count == 3;
    }

    void testNumberSignumNames() {
        assert Number.Signum.names.size == 3;
        assert Number.Signum.names[0] == "Negative";
        assert Number.Signum.names[1] == "Zero";
        assert Number.Signum.names[2] == "Positive";
    }

    void testNumberSignumValues() {
        assert Number.Signum.values.size == 3;
        assert Number.Signum.values[0] == Negative;
        assert Number.Signum.values[1] == Zero;
        assert Number.Signum.values[2] == Positive;
    }

    void testFPNumberRoundingCount() {
        assert FPNumber.Rounding.count == 5;
    }

    void testFPNumberRoundingNames() {
        assert FPNumber.Rounding.names.size == 5;
        assert FPNumber.Rounding.names[0] == "TiesToEven";
        assert FPNumber.Rounding.names[1] == "TiesToAway";
        assert FPNumber.Rounding.names[2] == "TowardPositive";
        assert FPNumber.Rounding.names[3] == "TowardZero";
        assert FPNumber.Rounding.names[4] == "TowardNegative";
    }

    void testFPNumberRoundingValues() {
        assert FPNumber.Rounding.values.size == 5;
        assert FPNumber.Rounding.values[0] == TiesToEven;
        assert FPNumber.Rounding.values[1] == TiesToAway;
        assert FPNumber.Rounding.values[2] == TowardPositive;
        assert FPNumber.Rounding.values[3] == TowardZero;
        assert FPNumber.Rounding.values[4] == TowardNegative;
    }
}
