class Float32Tests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Float32Tests >>>>");
        // Comparison tests
        testFloat32CompareEq();
        testFloat32CompareGe();
        testFloat32CompareGt();
        testFloat32CompareLe();
        testFloat32CompareLt();

        // Field tests
        testFloat32AsField();
        testFloat32AsNullableField();
        testFloat32AsNullableFieldNull();

        // Constant field and constructor tests
        testFloat32AsConstField();
        testNullableDecAsConstField();
        testNullableDecAsConstFieldNull();

        // Method parameter tests
        testFloat32AsParam();
        testFloat32AsNullableParam();
        testFloat32AsNullableParamNull();
        testFloat32AsMultiParams();
        testFloat32AsMultiNullableParams();

        // Method return tests
        testFloat32Return();
        testFloat32ConditionalReturn();
        testFloat32ReturnStringFloat32();
        testFloat32ReturnFloat32Dec();
        testFloat32ReturnTwoFloat32();
        testNullableDecReturn();
        testNullableDecAsDecReturn();
        testDecAsNullableDecReturn();
        testNullableDecConditionalReturn();

        console.print("<<<<< Finished Float32Tests Conversion tests >>>>>");
    }

    // ----- comparison tests ----------------------------------------------------------------------

    void testFloat32CompareEq() {
        Float32 n = 1234;
        assert n == 1234;
    }

    void testFloat32CompareGe() {
        Float32 n = 1234;
        assert n >= 1000;
        assert n >= 1234;
    }

    void testFloat32CompareGt() {
        Float32 n = 1234;
        assert n > 1233;
    }

    void testFloat32CompareLe() {
        Float32 n = 1234;
        assert n <= 1235;
        assert n <= 1234;
    }

    void testFloat32CompareLt() {
        Float32 n = 1234;
        assert n < 1235;
    }

    // ----- field tests ---------------------------------------------------------------------------

    void testFloat32AsField() {
        Float32AsField n = new Float32AsField();
        assert n.Field == 1234;
    }

    static class Float32AsField {
        Float32 Field = 1234;
    }

    void testFloat32AsNullableField() {
        Float32AsNullableField n = new Float32AsNullableField();
        assert n.field == 9876;
    }

    static class Float32AsNullableField {
        Float32? field = 9876;
    }

    void testFloat32AsNullableFieldNull() {
        Float32NullField n = new Float32NullField();
        assert n.field == Null;
    }

    static class Float32NullField {
        Float32? field = Null;
    }

    void testFloat32AsConstField() {
        Float32       n = 1234;
        NumberHolder h = new NumberHolder(n);
        assert h.n == 1234;
    }

    static const NumberHolder(Float32 n) {
    }

    void testNullableDecAsConstField() {
        Float32                  n = 9876543;
        NullableNumberHolder h = new NullableNumberHolder(n);
        assert h.n == n;
    }

    void testNullableDecAsConstFieldNull() {
        NullableNumberHolder h = new NullableNumberHolder(Null);
        assert h.n == Null;
    }

    static const NullableNumberHolder(Float32? n) {
    }

    // ----- parameter tests -----------------------------------------------------------------------

    void testFloat32AsParam() {
        Float32 n = 1234;
        Float32Param(n);
    }

    void Float32Param(Float32 n) {
        assert n == 1234;
    }

    void testFloat32AsNullableParam() {
        Float32 n = 1234;
        Boolean isNull = Float32NullableParam(n);
        assert isNull == False;
    }

    void testFloat32AsNullableParamNull() {
        Boolean isNull = Float32NullableParam(Null);
        assert isNull == True;
    }

    Boolean Float32NullableParam(Float32? n) {
        if (n.is(Float32)) {
            assert n == 1234;
            return False;
        }
        return True;
    }

    void testFloat32AsMultiParams() {
        Float32MultiParams(1234, 98765432);
    }

    void Float32MultiParams(Float32 n1, Float32 n2) {
        assert n1 == 1234;
        assert n2 == 98765432;
    }

    void testFloat32AsMultiNullableParams() {
        Boolean b1;
        Boolean b2;
        (b1, b2) = Float32MultiNullableParams(1234, 12349876);
        assert b1 == True;
        assert b2 == True;
        (b1, b2) = Float32MultiNullableParams(1234, Null);
        assert b1 == True;
        assert b2 == False;
        (b1, b2) = Float32MultiNullableParams(Null, 12349876);
        assert b1 == False;
        assert b2 == True;
        (b1, b2) = Float32MultiNullableParams(Null, Null);
        assert b1 == False;
        assert b2 == False;
    }

    (Boolean, Boolean) Float32MultiNullableParams(Float32? n1, Float32? n2) {
        Boolean b1 = False;
        Boolean b2 = False;
        if (n1.is(Float32)) {
            assert n1 == 1234;
            b1 = True;
        }
        if (n2.is(Float32)) {
            assert n2 == 12349876;
            b2 = True;
        }
        return b1, b2;
    }

    // ----- return tests --------------------------------------------------------------------------

    void testFloat32Return() {
        Float32 n = returnFloat32();
        assert n == 1234;
    }

    Float32 returnFloat32() {
        Float32 n = 1234;
        return n;
    }

    void testFloat32ConditionalReturn() {
        assert Float32 n := returnConditionalFloat32();
        assert n == 1234;
    }

    conditional Float32 returnConditionalFloat32() {
        Float32 n = 1234;
        return True, n;
    }

    void testFloat32ReturnStringFloat32() {
        (String s, Float32 n) = returnStringFloat32();
        assert s == "Foo";
        assert n == 9876;
    }

    (String, Float32) returnStringFloat32() {
        Float32 n = 9876;
        return "Foo", n;
    }

    void testFloat32ReturnFloat32Dec() {
        (Float32 n, Float32 i) = returnFloat32Dec();
        assert n == 9999;
        assert i == 19;
    }

    (Float32, Float32) returnFloat32Dec() {
        Float32 n = 9999;
        return n, 19;
    }

    void testFloat32ReturnTwoFloat32() {
        (Float32 n1, Float32 n2) = returnTwoFloat32();
        assert n1 == 4567;
        assert n2 == 1290;
    }

    (Float32, Float32) returnTwoFloat32() {
        Float32 n1 = 4567;
        Float32 n2 = 1290;
        return n1, n2;
    }

    void testNullableDecReturn() {
        Float32? n = returnNullableDec(True);
        assert n == 987654321;
        n = returnNullableDec(False);
        assert n == Null;
    }

    Float32? returnNullableDec(Boolean b) {
        Float32 n = 987654321;
        if (b) {
            return n;
        }
        return Null;
    }

    void testNullableDecAsDecReturn() {
        Float32 n = returnNullableDecAsDec();
        assert n == 98987676;
    }

    Float32 returnNullableDecAsDec() {
        Float32? n = 98987676;
        return n;
    }

    void testDecAsNullableDecReturn() {
        Float32? n = returnDecAsNullableDec();
        assert n == 98987676;
    }

    Float32? returnDecAsNullableDec() {
        Float32 n = 98987676;
        return n;
    }

    void testNullableDecConditionalReturn() {
        assert Float32? n := returnConditionalNullableDec(0);
        assert n == 191919;
        assert n := returnConditionalNullableDec(1);
        assert n == Null;
        assert returnConditionalNullableDec(2) == False;
    }

    conditional Float32? returnConditionalNullableDec(Float32 i) {
        Float32? n = 191919;
        if (i == 0) {
            return True, n;
        }
        if (i == 1) {
            return True, Null;
        }
        return False;
    }
}
