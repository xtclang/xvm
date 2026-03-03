class Float64Tests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Float64Tests >>>>");
        // Comparison tests
        testFloat64CompareEq();
        testFloat64CompareGe();
        testFloat64CompareGt();
        testFloat64CompareLe();
        testFloat64CompareLt();

        // Field tests
        testFloat64AsField();
        testFloat64AsNullableField();
        testFloat64AsNullableFieldNull();

        // Constant field and constructor tests
        testFloat64AsConstField();
        testNullableDecAsConstField();
        testNullableDecAsConstFieldNull();

        // Method parameter tests
        testFloat64AsParam();
        testFloat64AsNullableParam();
        testFloat64AsNullableParamNull();
        testFloat64AsMultiParams();
        testFloat64AsMultiNullableParams();

        // Method return tests
        testFloat64Return();
        testFloat64ConditionalReturn();
        testFloat64ReturnStringFloat64();
        testFloat64ReturnFloat64Dec();
        testFloat64ReturnTwoFloat64();
        testNullableDecReturn();
        testNullableDecAsDecReturn();
        testDecAsNullableDecReturn();
        testNullableDecConditionalReturn();

        console.print("<<<<< Finished Float64Tests Conversion tests >>>>>");
    }

    // ----- comparison tests ----------------------------------------------------------------------

    void testFloat64CompareEq() {
        Float64 n = 1234;
        assert n == 1234;
    }

    void testFloat64CompareGe() {
        Float64 n = 1234;
        assert n >= 1000;
        assert n >= 1234;
    }

    void testFloat64CompareGt() {
        Float64 n = 1234;
        assert n > 1233;
    }

    void testFloat64CompareLe() {
        Float64 n = 1234;
        assert n <= 1235;
        assert n <= 1234;
    }

    void testFloat64CompareLt() {
        Float64 n = 1234;
        assert n < 1235;
    }

    // ----- field tests ---------------------------------------------------------------------------

    void testFloat64AsField() {
        Float64AsField n = new Float64AsField();
        assert n.Field == 1234;
    }

    static class Float64AsField {
        Float64 Field = 1234;
    }

    void testFloat64AsNullableField() {
        Float64AsNullableField n = new Float64AsNullableField();
        assert n.field == 9876;
    }

    static class Float64AsNullableField {
        Float64? field = 9876;
    }

    void testFloat64AsNullableFieldNull() {
        Float64NullField n = new Float64NullField();
        assert n.field == Null;
    }

    static class Float64NullField {
        Float64? field = Null;
    }

    void testFloat64AsConstField() {
        Float64       n = 1234;
        NumberHolder h = new NumberHolder(n);
        assert h.n == 1234;
    }

    static const NumberHolder(Float64 n) {
    }

    void testNullableDecAsConstField() {
        Float64                  n = 9876543;
        NullableNumberHolder h = new NullableNumberHolder(n);
        assert h.n == n;
    }

    void testNullableDecAsConstFieldNull() {
        NullableNumberHolder h = new NullableNumberHolder(Null);
        assert h.n == Null;
    }

    static const NullableNumberHolder(Float64? n) {
    }

    // ----- parameter tests -----------------------------------------------------------------------

    void testFloat64AsParam() {
        Float64 n = 1234;
        Float64Param(n);
    }

    void Float64Param(Float64 n) {
        assert n == 1234;
    }

    void testFloat64AsNullableParam() {
        Float64 n = 1234;
        Boolean isNull = Float64NullableParam(n);
        assert isNull == False;
    }

    void testFloat64AsNullableParamNull() {
        Boolean isNull = Float64NullableParam(Null);
        assert isNull == True;
    }

    Boolean Float64NullableParam(Float64? n) {
        if (n.is(Float64)) {
            assert n == 1234;
            return False;
        }
        return True;
    }

    void testFloat64AsMultiParams() {
        Float64MultiParams(1234, 98765432);
    }

    void Float64MultiParams(Float64 n1, Float64 n2) {
        assert n1 == 1234;
        assert n2 == 98765432;
    }

    void testFloat64AsMultiNullableParams() {
        Boolean b1;
        Boolean b2;
        (b1, b2) = Float64MultiNullableParams(1234, 12349876);
        assert b1 == True;
        assert b2 == True;
        (b1, b2) = Float64MultiNullableParams(1234, Null);
        assert b1 == True;
        assert b2 == False;
        (b1, b2) = Float64MultiNullableParams(Null, 12349876);
        assert b1 == False;
        assert b2 == True;
        (b1, b2) = Float64MultiNullableParams(Null, Null);
        assert b1 == False;
        assert b2 == False;
    }

    (Boolean, Boolean) Float64MultiNullableParams(Float64? n1, Float64? n2) {
        Boolean b1 = False;
        Boolean b2 = False;
        if (n1.is(Float64)) {
            assert n1 == 1234;
            b1 = True;
        }
        if (n2.is(Float64)) {
            assert n2 == 12349876;
            b2 = True;
        }
        return b1, b2;
    }

    // ----- return tests --------------------------------------------------------------------------

    void testFloat64Return() {
        Float64 n = returnFloat64();
        assert n == 1234;
    }

    Float64 returnFloat64() {
        Float64 n = 1234;
        return n;
    }

    void testFloat64ConditionalReturn() {
        assert Float64 n := returnConditionalFloat64();
        assert n == 1234;
    }

    conditional Float64 returnConditionalFloat64() {
        Float64 n = 1234;
        return True, n;
    }

    void testFloat64ReturnStringFloat64() {
        (String s, Float64 n) = returnStringFloat64();
        assert s == "Foo";
        assert n == 9876;
    }

    (String, Float64) returnStringFloat64() {
        Float64 n = 9876;
        return "Foo", n;
    }

    void testFloat64ReturnFloat64Dec() {
        (Float64 n, Float64 i) = returnFloat64Dec();
        assert n == 9999;
        assert i == 19;
    }

    (Float64, Float64) returnFloat64Dec() {
        Float64 n = 9999;
        return n, 19;
    }

    void testFloat64ReturnTwoFloat64() {
        (Float64 n1, Float64 n2) = returnTwoFloat64();
        assert n1 == 4567;
        assert n2 == 1290;
    }

    (Float64, Float64) returnTwoFloat64() {
        Float64 n1 = 4567;
        Float64 n2 = 1290;
        return n1, n2;
    }

    void testNullableDecReturn() {
        Float64? n = returnNullableDec(True);
        assert n == 987654321;
        n = returnNullableDec(False);
        assert n == Null;
    }

    Float64? returnNullableDec(Boolean b) {
        Float64 n = 987654321;
        if (b) {
            return n;
        }
        return Null;
    }

    void testNullableDecAsDecReturn() {
        Float64 n = returnNullableDecAsDec();
        assert n == 98987676;
    }

    Float64 returnNullableDecAsDec() {
        Float64? n = 98987676;
        return n;
    }

    void testDecAsNullableDecReturn() {
        Float64? n = returnDecAsNullableDec();
        assert n == 98987676;
    }

    Float64? returnDecAsNullableDec() {
        Float64 n = 98987676;
        return n;
    }

    void testNullableDecConditionalReturn() {
        assert Float64? n := returnConditionalNullableDec(0);
        assert n == 191919;
        assert n := returnConditionalNullableDec(1);
        assert n == Null;
        assert returnConditionalNullableDec(2) == False;
    }

    conditional Float64? returnConditionalNullableDec(Float64 i) {
        Float64? n = 191919;
        if (i == 0) {
            return True, n;
        }
        if (i == 1) {
            return True, Null;
        }
        return False;
    }
}
