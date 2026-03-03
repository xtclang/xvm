class Dec64Tests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Dec64Tests >>>>");
        // Comparison tests
        testDec64CompareEq();
        testDec64CompareGe();
        testDec64CompareGt();
        testDec64CompareLe();
        testDec64CompareLt();

        // Field tests
        testDec64AsField();
        testDec64AsNullableField();
        testDec64AsNullableFieldNull();

        // Constant field and constructor tests
        testDec64AsConstField();
        testNullableDecAsConstField();
        testNullableDecAsConstFieldNull();

        // Method parameter tests
        testDec64AsParam();
        testDec64AsNullableParam();
        testDec64AsNullableParamNull();
        testDec64AsMultiParams();
        testDec64AsMultiNullableParams();

        // Method return tests
        testDec64Return();
        testDec64ConditionalReturn();
        testDec64ReturnStringDec64();
        testDec64ReturnDec64Dec();
        testDec64ReturnTwoDec64();
        testNullableDecReturn();
        testNullableDecAsDecReturn();
        testDecAsNullableDecReturn();
        testNullableDecConditionalReturn();

        console.print("<<<<< Finished Dec64Tests Conversion tests >>>>>");
    }

    // ----- comparison tests ----------------------------------------------------------------------

    void testDec64CompareEq() {
        Dec64 n = 1234;
        assert n == 1234;
    }

    void testDec64CompareGe() {
        Dec64 n = 1234;
        assert n >= 1000;
        assert n >= 1234;
    }

    void testDec64CompareGt() {
        Dec64 n = 1234;
        assert n > 1233;
    }

    void testDec64CompareLe() {
        Dec64 n = 1234;
        assert n <= 1235;
        assert n <= 1234;
    }

    void testDec64CompareLt() {
        Dec64 n = 1234;
        assert n < 1235;
    }

    // ----- field tests ---------------------------------------------------------------------------

    void testDec64AsField() {
        Dec64AsField n = new Dec64AsField();
        assert n.Field == 1234;
    }

    static class Dec64AsField {
        Dec64 Field = 1234;
    }

    void testDec64AsNullableField() {
        Dec64AsNullableField n = new Dec64AsNullableField();
        assert n.field == 9876;
    }

    static class Dec64AsNullableField {
        Dec64? field = 9876;
    }

    void testDec64AsNullableFieldNull() {
        Dec64NullField n = new Dec64NullField();
        assert n.field == Null;
    }

    static class Dec64NullField {
        Dec64? field = Null;
    }

    void testDec64AsConstField() {
        Dec64       n = 1234;
        NumberHolder h = new NumberHolder(n);
        assert h.n == 1234;
    }

    static const NumberHolder(Dec64 n) {
    }

    void testNullableDecAsConstField() {
        Dec64                  n = 9876543;
        NullableNumberHolder h = new NullableNumberHolder(n);
        assert h.n == n;
    }

    void testNullableDecAsConstFieldNull() {
        NullableNumberHolder h = new NullableNumberHolder(Null);
        assert h.n == Null;
    }

    static const NullableNumberHolder(Dec64? n) {
    }

    // ----- parameter tests -----------------------------------------------------------------------

    void testDec64AsParam() {
        Dec64 n = 1234;
        Dec64Param(n);
    }

    void Dec64Param(Dec64 n) {
        assert n == 1234;
    }

    void testDec64AsNullableParam() {
        Dec64 n = 1234;
        Boolean isNull = Dec64NullableParam(n);
        assert isNull == False;
    }

    void testDec64AsNullableParamNull() {
        Boolean isNull = Dec64NullableParam(Null);
        assert isNull == True;
    }

    Boolean Dec64NullableParam(Dec64? n) {
        if (n.is(Dec64)) {
            assert n == 1234;
            return False;
        }
        return True;
    }

    void testDec64AsMultiParams() {
        Dec64MultiParams(1234, 98765432);
    }

    void Dec64MultiParams(Dec64 n1, Dec64 n2) {
        assert n1 == 1234;
        assert n2 == 98765432;
    }

    void testDec64AsMultiNullableParams() {
        Boolean b1;
        Boolean b2;
        (b1, b2) = Dec64MultiNullableParams(1234, 12349876);
        assert b1 == True;
        assert b2 == True;
        (b1, b2) = Dec64MultiNullableParams(1234, Null);
        assert b1 == True;
        assert b2 == False;
        (b1, b2) = Dec64MultiNullableParams(Null, 12349876);
        assert b1 == False;
        assert b2 == True;
        (b1, b2) = Dec64MultiNullableParams(Null, Null);
        assert b1 == False;
        assert b2 == False;
    }

    (Boolean, Boolean) Dec64MultiNullableParams(Dec64? n1, Dec64? n2) {
        Boolean b1 = False;
        Boolean b2 = False;
        if (n1.is(Dec64)) {
            assert n1 == 1234;
            b1 = True;
        }
        if (n2.is(Dec64)) {
            assert n2 == 12349876;
            b2 = True;
        }
        return b1, b2;
    }

    // ----- return tests --------------------------------------------------------------------------

    void testDec64Return() {
        Dec64 n = returnDec64();
        assert n == 1234;
    }

    Dec64 returnDec64() {
        Dec64 n = 1234;
        return n;
    }

    void testDec64ConditionalReturn() {
        assert Dec64 n := returnConditionalDec64();
        assert n == 1234;
    }

    conditional Dec64 returnConditionalDec64() {
        Dec64 n = 1234;
        return True, n;
    }

    void testDec64ReturnStringDec64() {
        (String s, Dec64 n) = returnStringDec64();
        assert s == "Foo";
        assert n == 9876;
    }

    (String, Dec64) returnStringDec64() {
        Dec64 n = 9876;
        return "Foo", n;
    }

    void testDec64ReturnDec64Dec() {
        (Dec64 n, Dec64 i) = returnDec64Dec();
        assert n == 9999;
        assert i == 19;
    }

    (Dec64, Dec64) returnDec64Dec() {
        Dec64 n = 9999;
        return n, 19;
    }

    void testDec64ReturnTwoDec64() {
        (Dec64 n1, Dec64 n2) = returnTwoDec64();
        assert n1 == 4567;
        assert n2 == 1290;
    }

    (Dec64, Dec64) returnTwoDec64() {
        Dec64 n1 = 4567;
        Dec64 n2 = 1290;
        return n1, n2;
    }

    void testNullableDecReturn() {
        Dec64? n = returnNullableDec(True);
        assert n == 987654321;
        n = returnNullableDec(False);
        assert n == Null;
    }

    Dec64? returnNullableDec(Boolean b) {
        Dec64 n = 987654321;
        if (b) {
            return n;
        }
        return Null;
    }

    void testNullableDecAsDecReturn() {
        Dec64 n = returnNullableDecAsDec();
        assert n == 98987676;
    }

    Dec64 returnNullableDecAsDec() {
        Dec64? n = 98987676;
        return n;
    }

    void testDecAsNullableDecReturn() {
        Dec64? n = returnDecAsNullableDec();
        assert n == 98987676;
    }

    Dec64? returnDecAsNullableDec() {
        Dec64 n = 98987676;
        return n;
    }

    void testNullableDecConditionalReturn() {
        assert Dec64? n := returnConditionalNullableDec(0);
        assert n == 191919;
        assert n := returnConditionalNullableDec(1);
        assert n == Null;
        assert returnConditionalNullableDec(2) == False;
    }

    conditional Dec64? returnConditionalNullableDec(Dec64 i) {
        Dec64? n = 191919;
        if (i == 0) {
            return True, n;
        }
        if (i == 1) {
            return True, Null;
        }
        return False;
    }
}
