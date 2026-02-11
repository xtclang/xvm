class Int64Tests {

    @Inject Console console;

// 2 ^ 64 = 18446744073709551616

    void run() {
        console.print(">>>> Running Int64Tests >>>>");
        // Comparison tests
        testInt64CompareEq();
        testInt64CompareGe();
        testInt64CompareGt();
        testInt64CompareLe();
        testInt64CompareLt();

        // Field tests
        testInt64AsField();
        testInt64AsNullableField();
        testInt64AsNullableFieldNull();

        // Constant field and constructor tests
        testInt64AsConstField();
        testNullableIntAsConstField();
        testNullableIntAsConstFieldNull();

        // Method parameter tests
        testInt64AsParam();
        testInt64AsNullableParam();
        testInt64AsNullableParamNull();
        testInt64AsMultiParams();
        testInt64AsMultiNullableParams();

        // Method return tests
        testInt64Return();
        testInt64ConditionalReturn();
        testInt64ReturnStringInt64();
        testInt64ReturnInt64Int();
        testInt64ReturnTwoInt64();
        testNullableIntReturn();
        testNullableIntAsIntReturn();
        testIntAsNullableIntReturn();
        testNullableIntConditionalReturn();

        console.print("<<<<< Finished Int64Tests Conversion tests >>>>>");
    }

    // ----- comparison tests ----------------------------------------------------------------------

    void testInt64CompareEq() {
        Int n = 1234;
        assert n == 1234;
    }

    void testInt64CompareGe() {
        Int n = 1234;
        assert n >= 1000;
        assert n >= 1234;
    }

    void testInt64CompareGt() {
        Int n = 1234;
        assert n > 1233;
    }

    void testInt64CompareLe() {
        Int n = 1234;
        assert n <= 1235;
        assert n <= 1234;
    }

    void testInt64CompareLt() {
        Int n = 1234;
        assert n < 1235;
    }

    // ----- field tests ---------------------------------------------------------------------------

    void testInt64AsField() {
        Int64AsField n = new Int64AsField();
        assert n.Field == 1234;
    }

    static class Int64AsField {
        Int Field = 1234;
    }

    void testInt64AsNullableField() {
        Int64AsNullableField n = new Int64AsNullableField();
        assert n.field == 9876;
    }

    static class Int64AsNullableField {
        Int? field = 9876;
    }

    void testInt64AsNullableFieldNull() {
        Int64NullField n = new Int64NullField();
        assert n.field == Null;
    }

    static class Int64NullField {
        Int? field = Null;
    }

    void testInt64AsConstField() {
        Int       n = 1234;
        NumberHolder h = new NumberHolder(n);
        assert h.n == 1234;
    }

    static const NumberHolder(Int n) {
    }

    void testNullableIntAsConstField() {
        Int                  n = 9876543;
        NullableNumberHolder h = new NullableNumberHolder(n);
        assert h.n == n;
    }

    void testNullableIntAsConstFieldNull() {
        NullableNumberHolder h = new NullableNumberHolder(Null);
        assert h.n == Null;
    }

    static const NullableNumberHolder(Int? n) {
    }

    // ----- parameter tests -----------------------------------------------------------------------

    void testInt64AsParam() {
        Int n = 1234;
        int64Param(n);
    }

    void int64Param(Int n) {
        assert n == 1234;
    }

    void testInt64AsNullableParam() {
        Int n = 1234;
        Boolean isNull = int64NullableParam(n);
        assert isNull == False;
    }

    void testInt64AsNullableParamNull() {
        Boolean isNull = int64NullableParam(Null);
        assert isNull == True;
    }

    Boolean int64NullableParam(Int? n) {
        if (n.is(Int)) {
            assert n == 1234;
            return False;
        }
        return True;
    }

    void testInt64AsMultiParams() {
        int64MultiParams(1234, 98765432);
    }

    void int64MultiParams(Int n1, Int n2) {
        assert n1 == 1234;
        assert n2 == 98765432;
    }

    void testInt64AsMultiNullableParams() {
        Boolean b1;
        Boolean b2;
        (b1, b2) = int64MultiNullableParams(1234, 12349876);
        assert b1 == True;
        assert b2 == True;
        (b1, b2) = int64MultiNullableParams(1234, Null);
        assert b1 == True;
        assert b2 == False;
        (b1, b2) = int64MultiNullableParams(Null, 12349876);
        assert b1 == False;
        assert b2 == True;
        (b1, b2) = int64MultiNullableParams(Null, Null);
        assert b1 == False;
        assert b2 == False;
    }

    (Boolean, Boolean) int64MultiNullableParams(Int? n1, Int? n2) {
        Boolean b1 = False;
        Boolean b2 = False;
        if (n1.is(Int)) {
            assert n1 == 1234;
            b1 = True;
        }
        if (n2.is(Int)) {
            assert n2 == 12349876;
            b2 = True;
        }
        return b1, b2;
    }

    // ----- return tests --------------------------------------------------------------------------

    void testInt64Return() {
        Int n = returnInt64();
        assert n == 1234;
    }

    Int returnInt64() {
        Int n = 1234;
        return n;
    }

    void testInt64ConditionalReturn() {
        assert Int n := returnConditionalInt64();
        assert n == 1234;
    }

    conditional Int returnConditionalInt64() {
        Int n = 1234;
        return True, n;
    }

    void testInt64ReturnStringInt64() {
        (String s, Int n) = returnStringInt64();
        assert s == "Foo";
        assert n == 9876;
    }

    (String, Int) returnStringInt64() {
        Int n = 9876;
        return "Foo", n;
    }

    void testInt64ReturnInt64Int() {
        (Int n, Int i) = returnInt64Int();
        assert n == 9999;
        assert i == 19;
    }

    (Int, Int) returnInt64Int() {
        Int n = 9999;
        return n, 19;
    }

    void testInt64ReturnTwoInt64() {
        (Int n1, Int n2) = returnTwoInt64();
        assert n1 == 4567;
        assert n2 == 1290;
    }

    (Int, Int) returnTwoInt64() {
        Int n1 = 4567;
        Int n2 = 1290;
        return n1, n2;
    }

    void testNullableIntReturn() {
        Int? n = returnNullableInt(True);
        assert n == 987654321;
        n = returnNullableInt(False);
        assert n == Null;
    }

    Int? returnNullableInt(Boolean b) {
        Int n = 987654321;
        if (b) {
            return n;
        }
        return Null;
    }

    void testNullableIntAsIntReturn() {
        Int n = returnNullableIntAsInt();
        assert n == 98987676;
    }

    Int returnNullableIntAsInt() {
        Int? n = 98987676;
        return n;
    }

    void testIntAsNullableIntReturn() {
        Int? n = returnIntAsNullableInt();
        assert n == 98987676;
    }

    Int? returnIntAsNullableInt() {
        Int n = 98987676;
        return n;
    }

    void testNullableIntConditionalReturn() {
        assert Int? n := returnConditionalNullableInt(0);
        assert n == 191919;
        assert n := returnConditionalNullableInt(1);
        assert n == Null;
        assert returnConditionalNullableInt(2) == False;
    }

    conditional Int? returnConditionalNullableInt(Int i) {
        Int? n = 191919;
        if (i == 0) {
            return True, n;
        }
        if (i == 1) {
            return True, Null;
        }
        return False;
    }
}
