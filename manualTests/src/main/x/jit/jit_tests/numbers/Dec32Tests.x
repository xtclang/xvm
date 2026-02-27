class Dec32Tests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Dec32Tests >>>>");
        // Comparison tests
        testDec32CompareEq();
        testDec32CompareGe();
        testDec32CompareGt();
        testDec32CompareLe();
        testDec32CompareLt();

        // Field tests
        testDec32AsField();
        testDec32AsNullableField();
        testDec32AsNullableFieldNull();

        // Constant field and constructor tests
        testDec32AsConstField();
        testNullableDecAsConstField();
        testNullableDecAsConstFieldNull();

        // Method parameter tests
        testDec32AsParam();
        testDec32AsNullableParam();
        testDec32AsNullableParamNull();
        testDec32AsMultiParams();
        testDec32AsMultiNullableParams();

        // Method return tests
        testDec32Return();
        testDec32ConditionalReturn();
        testDec32ReturnStringDec32();
        testDec32ReturnDec32Dec();
        testDec32ReturnTwoDec32();
        testNullableDecReturn();
        testNullableDecAsDecReturn();
        testDecAsNullableDecReturn();
        testNullableDecConditionalReturn();

        // Op tests

        console.print("<<<<< Finished Dec32Tests Conversion tests >>>>>");
    }

    // ----- comparison tests ----------------------------------------------------------------------

    void testDec32CompareEq() {
        Dec32 n = 1234;
        assert n == 1234;
    }

    void testDec32CompareGe() {
        Dec32 n = 1234;
        assert n >= 1000;
        assert n >= 1234;
    }

    void testDec32CompareGt() {
        Dec32 n = 1234;
        assert n > 1233;
    }

    void testDec32CompareLe() {
        Dec32 n = 1234;
        assert n <= 1235;
        assert n <= 1234;
    }

    void testDec32CompareLt() {
        Dec32 n = 1234;
        assert n < 1235;
    }

    // ----- field tests ---------------------------------------------------------------------------

    void testDec32AsField() {
        Dec32AsField n = new Dec32AsField();
        assert n.Field == 1234;
    }

    static class Dec32AsField {
        Dec32 Field = 1234;
    }

    void testDec32AsNullableField() {
        Dec32AsNullableField n = new Dec32AsNullableField();
        assert n.field == 9876;
    }

    static class Dec32AsNullableField {
        Dec32? field = 9876;
    }

    void testDec32AsNullableFieldNull() {
        Dec32NullField n = new Dec32NullField();
        assert n.field == Null;
    }

    static class Dec32NullField {
        Dec32? field = Null;
    }

    void testDec32AsConstField() {
        Dec32       n = 1234;
        NumberHolder h = new NumberHolder(n);
        assert h.n == 1234;
    }

    static const NumberHolder(Dec32 n) {
    }

    void testNullableDecAsConstField() {
        Dec32                  n = 9876543;
        NullableNumberHolder h = new NullableNumberHolder(n);
        assert h.n == n;
    }

    void testNullableDecAsConstFieldNull() {
        NullableNumberHolder h = new NullableNumberHolder(Null);
        assert h.n == Null;
    }

    static const NullableNumberHolder(Dec32? n) {
    }

    // ----- parameter tests -----------------------------------------------------------------------

    void testDec32AsParam() {
        Dec32 n = 1234;
        Dec32Param(n);
    }

    void Dec32Param(Dec32 n) {
        assert n == 1234;
    }

    void testDec32AsNullableParam() {
        Dec32 n = 1234;
        Boolean isNull = Dec32NullableParam(n);
        assert isNull == False;
    }

    void testDec32AsNullableParamNull() {
        Boolean isNull = Dec32NullableParam(Null);
        assert isNull == True;
    }

    Boolean Dec32NullableParam(Dec32? n) {
        if (n.is(Dec32)) {
            assert n == 1234;
            return False;
        }
        return True;
    }

    void testDec32AsMultiParams() {
        Dec32MultiParams(1234, 98765432);
    }

    void Dec32MultiParams(Dec32 n1, Dec32 n2) {
        assert n1 == 1234;
        assert n2 == 98765432;
    }

    void testDec32AsMultiNullableParams() {
        Boolean b1;
        Boolean b2;
        (b1, b2) = Dec32MultiNullableParams(1234, 12349876);
        assert b1 == True;
        assert b2 == True;
        (b1, b2) = Dec32MultiNullableParams(1234, Null);
        assert b1 == True;
        assert b2 == False;
        (b1, b2) = Dec32MultiNullableParams(Null, 12349876);
        assert b1 == False;
        assert b2 == True;
        (b1, b2) = Dec32MultiNullableParams(Null, Null);
        assert b1 == False;
        assert b2 == False;
    }

    (Boolean, Boolean) Dec32MultiNullableParams(Dec32? n1, Dec32? n2) {
        Boolean b1 = False;
        Boolean b2 = False;
        if (n1.is(Dec32)) {
            assert n1 == 1234;
            b1 = True;
        }
        if (n2.is(Dec32)) {
            assert n2 == 12349876;
            b2 = True;
        }
        return b1, b2;
    }

    // ----- return tests --------------------------------------------------------------------------

    void testDec32Return() {
        Dec32 n = returnDec32();
        assert n == 1234;
    }

    Dec32 returnDec32() {
        Dec32 n = 1234;
        return n;
    }

    void testDec32ConditionalReturn() {
        assert Dec32 n := returnConditionalDec32();
        assert n == 1234;
    }

    conditional Dec32 returnConditionalDec32() {
        Dec32 n = 1234;
        return True, n;
    }

    void testDec32ReturnStringDec32() {
        (String s, Dec32 n) = returnStringDec32();
        assert s == "Foo";
        assert n == 9876;
    }

    (String, Dec32) returnStringDec32() {
        Dec32 n = 9876;
        return "Foo", n;
    }

    void testDec32ReturnDec32Dec() {
        (Dec32 n, Dec32 i) = returnDec32Dec();
        assert n == 9999;
        assert i == 19;
    }

    (Dec32, Dec32) returnDec32Dec() {
        Dec32 n = 9999;
        return n, 19;
    }

    void testDec32ReturnTwoDec32() {
        (Dec32 n1, Dec32 n2) = returnTwoDec32();
        assert n1 == 4567;
        assert n2 == 1290;
    }

    (Dec32, Dec32) returnTwoDec32() {
        Dec32 n1 = 4567;
        Dec32 n2 = 1290;
        return n1, n2;
    }

    void testNullableDecReturn() {
        Dec32? n = returnNullableDec(True);
        assert n == 987654321;
        n = returnNullableDec(False);
        assert n == Null;
    }

    Dec32? returnNullableDec(Boolean b) {
        Dec32 n = 987654321;
        if (b) {
            return n;
        }
        return Null;
    }

    void testNullableDecAsDecReturn() {
        Dec32 n = returnNullableDecAsDec();
        assert n == 98987676;
    }

    Dec32 returnNullableDecAsDec() {
        Dec32? n = 98987676;
        return n;
    }

    void testDecAsNullableDecReturn() {
        Dec32? n = returnDecAsNullableDec();
        assert n == 98987676;
    }

    Dec32? returnDecAsNullableDec() {
        Dec32 n = 98987676;
        return n;
    }

    void testNullableDecConditionalReturn() {
        assert Dec32? n := returnConditionalNullableDec(0);
        assert n == 191919;
        assert n := returnConditionalNullableDec(1);
        assert n == Null;
        assert returnConditionalNullableDec(2) == False;
    }

    conditional Dec32? returnConditionalNullableDec(Dec32 i) {
        Dec32? n = 191919;
        if (i == 0) {
            return True, n;
        }
        if (i == 1) {
            return True, Null;
        }
        return False;
    }
}
