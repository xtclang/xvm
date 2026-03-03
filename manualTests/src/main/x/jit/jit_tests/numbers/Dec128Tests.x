class Dec128Tests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Dec128Tests >>>>");
        // Comparison tests
        testDec128CompareEq();
        testDec128CompareGe();
        testDec128CompareGt();
        testDec128CompareLe();
        testDec128CompareLt();

        // Field tests
        testDec128AsField();
        testDec128AsNullableField();
        testDec128AsNullableFieldNull();

        // Constant field and constructor tests
        testDec128AsConstField();
        testNullableDecAsConstField();
        testNullableDecAsConstFieldNull();

        // Method parameter tests
        testDec128AsParam();
        testDec128AsNullableParam();
        testDec128AsNullableParamNull();
        testDec128AsMultiParams();
        testDec128AsMultiNullableParams();

        // Method return tests
        testDec128Return();
        testDec128ConditionalReturn();
        testDec128ReturnStringDec128();
        testDec128ReturnDec128Dec();
        testDec128ReturnTwoDec128();
        testNullableDecReturn();
        testNullableDecAsDecReturn();
        testDecAsNullableDecReturn();
        testNullableDecConditionalReturn();

        console.print("<<<<< Finished Dec128Tests Conversion tests >>>>>");
    }

    // ----- comparison tests ----------------------------------------------------------------------

    void testDec128CompareEq() {
        Dec128 n = 1234;
        assert n == 1234;
    }

    void testDec128CompareGe() {
        Dec128 n = 1234;
        assert n >= 1000;
        assert n >= 1234;
    }

    void testDec128CompareGt() {
        Dec128 n = 1234;
        assert n > 1233;
    }

    void testDec128CompareLe() {
        Dec128 n = 1234;
        assert n <= 1235;
        assert n <= 1234;
    }

    void testDec128CompareLt() {
        Dec128 n = 1234;
        assert n < 1235;
    }

    // ----- field tests ---------------------------------------------------------------------------

    void testDec128AsField() {
        Dec128AsField n = new Dec128AsField();
        assert n.Field == 1234;
    }

    static class Dec128AsField {
        Dec128 Field = 1234;
    }

    void testDec128AsNullableField() {
        Dec128AsNullableField n = new Dec128AsNullableField();
        assert n.field == 9876;
    }

    static class Dec128AsNullableField {
        Dec128? field = 9876;
    }

    void testDec128AsNullableFieldNull() {
        Dec128NullField n = new Dec128NullField();
        assert n.field == Null;
    }

    static class Dec128NullField {
        Dec128? field = Null;
    }

    void testDec128AsConstField() {
        Dec128       n = 1234;
        NumberHolder h = new NumberHolder(n);
        assert h.n == 1234;
    }

    static const NumberHolder(Dec128 n) {
    }

    void testNullableDecAsConstField() {
        Dec128                  n = 9876543;
        NullableNumberHolder h = new NullableNumberHolder(n);
        assert h.n == n;
    }

    void testNullableDecAsConstFieldNull() {
        NullableNumberHolder h = new NullableNumberHolder(Null);
        assert h.n == Null;
    }

    static const NullableNumberHolder(Dec128? n) {
    }

    // ----- parameter tests -----------------------------------------------------------------------

    void testDec128AsParam() {
        Dec128 n = 1234;
        Dec128Param(n);
    }

    void Dec128Param(Dec128 n) {
        assert n == 1234;
    }

    void testDec128AsNullableParam() {
        Dec128 n = 1234;
        Boolean isNull = Dec128NullableParam(n);
        assert isNull == False;
    }

    void testDec128AsNullableParamNull() {
        Boolean isNull = Dec128NullableParam(Null);
        assert isNull == True;
    }

    Boolean Dec128NullableParam(Dec128? n) {
        if (n.is(Dec128)) {
            assert n == 1234;
            return False;
        }
        return True;
    }

    void testDec128AsMultiParams() {
        Dec128MultiParams(1234, 98765432);
    }

    void Dec128MultiParams(Dec128 n1, Dec128 n2) {
        assert n1 == 1234;
        assert n2 == 98765432;
    }

    void testDec128AsMultiNullableParams() {
        Boolean b1;
        Boolean b2;
        (b1, b2) = Dec128MultiNullableParams(1234, 12349876);
        assert b1 == True;
        assert b2 == True;
        (b1, b2) = Dec128MultiNullableParams(1234, Null);
        assert b1 == True;
        assert b2 == False;
        (b1, b2) = Dec128MultiNullableParams(Null, 12349876);
        assert b1 == False;
        assert b2 == True;
        (b1, b2) = Dec128MultiNullableParams(Null, Null);
        assert b1 == False;
        assert b2 == False;
    }

    (Boolean, Boolean) Dec128MultiNullableParams(Dec128? n1, Dec128? n2) {
        Boolean b1 = False;
        Boolean b2 = False;
        if (n1.is(Dec128)) {
            assert n1 == 1234;
            b1 = True;
        }
        if (n2.is(Dec128)) {
            assert n2 == 12349876;
            b2 = True;
        }
        return b1, b2;
    }

    // ----- return tests --------------------------------------------------------------------------

    void testDec128Return() {
        Dec128 n = returnDec128();
        assert n == 1234;
    }

    Dec128 returnDec128() {
        Dec128 n = 1234;
        return n;
    }

    void testDec128ConditionalReturn() {
        assert Dec128 n := returnConditionalDec128();
        assert n == 1234;
    }

    conditional Dec128 returnConditionalDec128() {
        Dec128 n = 1234;
        return True, n;
    }

    void testDec128ReturnStringDec128() {
        (String s, Dec128 n) = returnStringDec128();
        assert s == "Foo";
        assert n == 9876;
    }

    (String, Dec128) returnStringDec128() {
        Dec128 n = 9876;
        return "Foo", n;
    }

    void testDec128ReturnDec128Dec() {
        (Dec128 n, Dec128 i) = returnDec128Dec();
        assert n == 9999;
        assert i == 19;
    }

    (Dec128, Dec128) returnDec128Dec() {
        Dec128 n = 9999;
        return n, 19;
    }

    void testDec128ReturnTwoDec128() {
        (Dec128 n1, Dec128 n2) = returnTwoDec128();
        assert n1 == 4567;
        assert n2 == 1290;
    }

    (Dec128, Dec128) returnTwoDec128() {
        Dec128 n1 = 4567;
        Dec128 n2 = 1290;
        return n1, n2;
    }

    void testNullableDecReturn() {
        Dec128? n = returnNullableDec(True);
        assert n == 987654321;
        n = returnNullableDec(False);
        assert n == Null;
    }

    Dec128? returnNullableDec(Boolean b) {
        Dec128 n = 987654321;
        if (b) {
            return n;
        }
        return Null;
    }

    void testNullableDecAsDecReturn() {
        Dec128 n = returnNullableDecAsDec();
        assert n == 98987676;
    }

    Dec128 returnNullableDecAsDec() {
        Dec128? n = 98987676;
        return n;
    }

    void testDecAsNullableDecReturn() {
        Dec128? n = returnDecAsNullableDec();
        assert n == 98987676;
    }

    Dec128? returnDecAsNullableDec() {
        Dec128 n = 98987676;
        return n;
    }

    void testNullableDecConditionalReturn() {
        assert Dec128? n := returnConditionalNullableDec(0);
        assert n == 191919;
        assert n := returnConditionalNullableDec(1);
        assert n == Null;
        assert returnConditionalNullableDec(2) == False;
    }

    conditional Dec128? returnConditionalNullableDec(Dec128 i) {
        Dec128? n = 191919;
        if (i == 0) {
            return True, n;
        }
        if (i == 1) {
            return True, Null;
        }
        return False;
    }
}
