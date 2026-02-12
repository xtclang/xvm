/**
 * xtc run -L build/xtc/main/lib -o  build/xtc/main/lib --jit src/main/x/jit/numbers/Int128Tests.x
 */
class Int128Tests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Int128Tests >>>>");

        // Comparison tests
        testInt128CompareEqLowOnly();
        testInt128CompareEqBig();
        testInt128CompareGeLowOnly();
        testInt128CompareGeBig();
        testInt128CompareGtLowOnly();
        testInt128CompareGtBig();
        testInt128CompareLeLowOnly();
        testInt128CompareLeBig();
        testInt128CompareLtLowOnly();
        testInt128CompareLtBig();
        testInt128MaxValueCompareEq();
        testInt128MinValueCompareEq();
        testInt128ZeroCompareEq();

        // Field tests
        testInt128AsFieldLowOnly();
        testInt128AsFieldBig();
        testInt128AsNullableFieldLowOnly();
        testInt128AsNullableFieldBig();
        testInt128AsNullableFieldNull();

        // Constant field and constructor tests
        testInt128AsConstFieldLowOnly();
        testInt128AsConstFieldBig();
        testNullableInt128AsConstFieldLowOnly();
        testNullableInt128AsConstFieldBig();
        testNullableInt128AsConstFieldNull();

        // Method parameter tests
        testInt128AsParamLowOnly();
        testInt128AsParamBig();
        testInt128AsNullableParamLowOnly();
        testInt128AsNullableParamBig();
        testInt128AsNullableParamNull();
        testInt128AsMultiParams();
        testInt128AsMultiNullableParams();

        // Method return tests
        testInt128ReturnLowOnly();
        testInt128ReturnBig();
        testInt128ConditionalReturnLowOnly();
        testInt128ConditionalReturnBig();
        testInt128ReturnStringInt128();
        testInt128ReturnInt128Int();
        testInt128ReturnTwoInt128();
        testNullableInt128Return();
        testNullableInt128AsInt128Return();
        testInt128AsNullableInt128Return();
        testNullableInt128ConditionalReturn();

        // Op tests
        // Add
        testInt128OpAddBig();
        testInt128OpAddLowOnly();
        testInt128OpAddFirstNegativeBig();
        testInt128OpAddSecondNegativeBig();
        testInt128OpAddBothNegativeBig();
        testInt128OpAddOverflow();
        // And
        testInt128OpAnd();

        console.print("<<<<< Finished Int128Tests Conversion tests >>>>>");
    }

    // ----- comparison tests ----------------------------------------------------------------------

    void testInt128CompareEqBig() {
        Int128 n = 18446744073709551616;
        assert n == 18446744073709551616;
    }

    void testInt128MaxValueCompareEq() {
        Int128 n = Int128.MaxValue;
        assert n == Int128.MaxValue;
    }

    void testInt128MinValueCompareEq() {
        Int128 n = Int128.MinValue;
        assert n == Int128.MinValue;
    }

    void testInt128ZeroCompareEq() {
        Int128 n = 0;
        assert n == 0;
    }

    void testInt128CompareEqLowOnly() {
        Int128 n = 1234;
        assert n == 1234;
    }

    void testInt128CompareGeBig() {
        Int128 n = 18446744073709551620;
        assert n >= 18446744073709551619;
        assert n >= 18446744073709551620;
    }

    void testInt128CompareGeLowOnly() {
        Int128 n = 1234;
        assert n >= 1000;
        assert n >= 1234;
    }

    void testInt128CompareGtBig() {
        Int128 n = 18446744073709551620;
        assert n > 18446744073709551619;
    }

    void testInt128CompareGtLowOnly() {
        Int128 n = 1234;
        assert n > 1233;
    }

    void testInt128CompareLeBig() {
        Int128 n = 18446744073709551620;
        assert n <= 18446744073709551621;
        assert n <= 18446744073709551620;
    }

    void testInt128CompareLeLowOnly() {
        Int128 n = 1234;
        assert n <= 1235;
        assert n <= 1234;
    }

    void testInt128CompareLtBig() {
        Int128 n = 18446744073709551620;
        assert n < 18446744073709551621;
    }

    void testInt128CompareLtLowOnly() {
        Int128 n = 1234;
        assert n < 1235;
    }

    // ----- field tests ---------------------------------------------------------------------------

    void testInt128AsFieldLowOnly() {
        Int128AsField n = new Int128AsField();
        assert n.lowOnlyField == 1234;
    }

    void testInt128AsFieldBig() {
        Int128AsField n = new Int128AsField();
        assert n.bigField == 18446744073709551699;
    }

    static class Int128AsField {
        Int128 lowOnlyField = 1234;
        Int128 bigField = 18446744073709551699;
    }

    void testInt128AsNullableFieldLowOnly() {
        Int128AsNullableField n = new Int128AsNullableField();
        assert n.lowOnlyField == 9876;
    }

    void testInt128AsNullableFieldBig() {
        Int128AsNullableField n = new Int128AsNullableField();
        assert n.bigField == 18446744073709552599;
    }

    static class Int128AsNullableField {
        Int128? lowOnlyField = 9876;
        Int128? bigField = 18446744073709552599;
    }

    void testInt128AsNullableFieldNull() {
        Int128NullField n = new Int128NullField();
        assert n.n == Null;
    }

    static class Int128NullField {
        Int128? n = Null;
    }

    void testInt128AsConstFieldLowOnly() {
        Int128       n = 1234;
        NumberHolder h = new NumberHolder(n);
        assert h.n == 1234;
    }

    void testInt128AsConstFieldBig() {
        Int128       n = 18446744073709551900;
        NumberHolder h = new NumberHolder(n);
        assert h.n == n;
    }

    static const NumberHolder(Int128 n) {
    }

    void testNullableInt128AsConstFieldLowOnly() {
        Int128               n = 1234;
        NullableNumberHolder h = new NullableNumberHolder(n);
        assert h.n == 1234;
    }

    void testNullableInt128AsConstFieldBig() {
        Int128               n = 18446744073709551900;
        NullableNumberHolder h = new NullableNumberHolder(n);
        assert h.n == n;
    }

    void testNullableInt128AsConstFieldNull() {
        NullableNumberHolder h = new NullableNumberHolder(Null);
        assert h.n == Null;
    }

    static const NullableNumberHolder(Int128? n) {
    }

    // ----- parameter tests -----------------------------------------------------------------------

    void testInt128AsParamLowOnly() {
        Int128 n = 1234;
        int128ParamLowOnly(n);
    }

    void int128ParamLowOnly(Int128 n) {
        assert n == 1234;
    }

    void testInt128AsParamBig() {
        Int128 n = 18446744073709551699;
        int128ParamBig(n);
    }

    void int128ParamBig(Int128 n) {
        assert n == 18446744073709551699;
    }

    void testInt128AsNullableParamLowOnly() {
        Int128 n = 1234;
        Boolean isNum = int128NullableParamLowOnly(n);
        assert isNum == True;
    }

    Boolean int128NullableParamLowOnly(Int128? n) {
        if (n.is(Int128)) {
            assert n == 1234;
            return True;
        }
        return False;
    }

    void testInt128AsNullableParamBig() {
        Int128  n      = 18446744073709551999;
        Boolean isNull = int128NullableParamBig(n);
        assert isNull == False;
    }

    void testInt128AsNullableParamNull() {
        Boolean isNull = int128NullableParamBig(Null);
        assert isNull == True;
    }

    Boolean int128NullableParamBig(Int128? n) {
        if (n.is(Int128)) {
            assert n == 18446744073709551999;
            return False;
        }
        return True;
    }

    void testInt128AsMultiParams() {
        int128MultiParams(1234, 18446744073709581999);
    }

    void int128MultiParams(Int128 n1, Int128 n2) {
        assert n1 == 1234;
        assert n2 == 18446744073709581999;
    }

    void testInt128AsMultiNullableParams() {
        Boolean b1;
        Boolean b2;
        (b1, b2) = int128MultiNullableParams(1234, 18446744073709571999);
        assert b1 == True;
        assert b2 == True;
        (b1, b2) = int128MultiNullableParams(1234, Null);
        assert b1 == True;
        assert b2 == False;
        (b1, b2) = int128MultiNullableParams(Null, 18446744073709571999);
        assert b1 == False;
        assert b2 == True;
        (b1, b2) = int128MultiNullableParams(Null, Null);
        assert b1 == False;
        assert b2 == False;
    }

    (Boolean, Boolean) int128MultiNullableParams(Int128? n1, Int128? n2) {
        Boolean b1 = False;
        Boolean b2 = False;
        if (n1.is(Int128)) {
            assert n1 == 1234;
            b1 = True;
        }
        if (n2.is(Int128)) {
            assert n2 == 18446744073709571999;
            b2 = True;
        }
        return b1, b2;
    }

    // ----- return tests --------------------------------------------------------------------------

    void testInt128ReturnLowOnly() {
        Int128 n = returnInt128LowOnly();
        assert n == 1234;
    }

    Int128 returnInt128LowOnly() {
        Int128 n = 1234;
        return n;
    }

    void testInt128ReturnBig() {
        Int128 n = returnInt128Big();
        assert n == 18446744073709551899;
    }

    Int128 returnInt128Big() {
        Int128 n = 18446744073709551899;
        return n;
    }

    void testInt128ConditionalReturnLowOnly() {
        assert Int128 n := returnConditionalInt128LowOnly();
        assert n == 1234;
    }

    conditional Int128 returnConditionalInt128LowOnly() {
        Int128 n = 1234;
        return True, n;
    }

    void testInt128ConditionalReturnBig() {
        assert Int128 n := returnConditionalInt128Big();
        assert n == 18446744073709551899;
    }

    conditional Int128 returnConditionalInt128Big() {
        Int128 n = 18446744073709551899;
        return True, n;
    }

    void testInt128ReturnStringInt128() {
        (String s, Int128 n) = returnStringInt128();
        assert s == "Foo";
        assert n == 18446744073709551999;
    }

    (String, Int128) returnStringInt128() {
        Int128 n = 18446744073709551999;
        return "Foo", n;
    }

    void testInt128ReturnInt128Int() {
        (Int128 n, Int i) = returnInt128Int();
        assert n == 18446744073709551999;
        assert i == 19;
    }

    (Int128, Int) returnInt128Int() {
        Int128 n = 18446744073709551999;
        return n, 19;
    }

    void testInt128ReturnTwoInt128() {
        (Int128 n1, Int128 n2) = returnTwoInt128();
        assert n1 == 18446744073709551999;
        assert n2 == 18446744073709552999;
    }

    (Int128, Int128) returnTwoInt128() {
        Int128 n1 = 18446744073709551999;
        Int128 n2 = 18446744073709552999;
        return n1, n2;
    }

    void testNullableInt128Return() {
        Int128? n = returnNullableInt128(True);
        assert n == 18446744073709591999;
        n = returnNullableInt128(False);
        assert n == Null;
    }

    Int128? returnNullableInt128(Boolean b) {
        Int128 n = 18446744073709591999;
        if (b) {
            return n;
        }
        return Null;
    }

    void testNullableInt128AsInt128Return() {
        Int128 n = returnNullableInt128AsInt128();
        assert n == 98987676;
    }

    Int128 returnNullableInt128AsInt128() {
        Int128? n = 98987676;
        return n;
    }

    void testInt128AsNullableInt128Return() {
        Int128? n = returnInt128AsNullableInt128();
        assert n == 98987676;
    }

    Int128? returnInt128AsNullableInt128() {
        Int128 n = 98987676;
        return n;
    }

    void testNullableInt128ConditionalReturnLowOnly() {
        assert Int128? n := returnConditionalNullableInt128LowOnly();
        assert n == 1234;
    }

    conditional Int128? returnConditionalNullableInt128LowOnly() {
        Int128? n = 1234;
        return True, n;
    }

    void testNullableInt128ConditionalReturn() {
        assert Int128? n := returnConditionalNullableInt128(0);
        assert n == 18446744073709551899;
        assert n := returnConditionalNullableInt128(1);
        assert n == Null;
        assert returnConditionalNullableInt128(2) == False;
    }

    conditional Int128? returnConditionalNullableInt128(Int i) {
        Int128? n = 18446744073709551899;
        if (i == 0) {
            return True, n;
        }
        if (i == 1) {
            return True, Null;
        }
        return False;
    }

    // ----- Op tests (Add) ------------------------------------------------------------------------

    void testInt128OpAddBig() {
        Int128 n1 = 18446744073709551616;
        Int128 n2 = 18446744073709200000;
        Int128 n3 = n1 + n2;
        assert n3 == 36893488147418751616;
    }

    void testInt128OpAddLowOnly() {
        Int128 n1 = 1000;
        Int128 n2 = 19;
        Int128 n3 = n1 + n2;
        assert n3 == 1019;
    }

    void testInt128OpAddFirstNegativeBig() {
        Int128 n1 = -18446744073709551616;
        Int128 n2 = 18446744073709200000;
        Int128 n3 = n1 + n2;
        assert n3 == -351616;
    }

    void testInt128OpAddSecondNegativeBig() {
        Int128 n1 = 18446744073709551616;
        Int128 n2 = -18446744073709200000;
        Int128 n3 = n1 + n2;
        assert n3 == 351616;
    }

    void testInt128OpAddBothNegativeBig() {
        Int128 n1 = -18446744073709551616;
        Int128 n2 = -18446744073709200000;
        Int128 n3 = n1 + n2;
        assert n3 == -36893488147418751616;
    }

    void testInt128OpAddOverflow() {
        Int128 n1 = Int128.MaxValue;
        Int128 n2 = 1;
        Int128 n3 = n1 + n2;
        assert n3 == Int128.MinValue;
    }

    // ----- Op tests (logical And) ----------------------------------------------------------------

    void testInt128OpAnd() {
        Int128 n1 = 0x00F2_F0F2_F0F0_F0F0_F0F0_F0F0_F0F0_F0F0;
        Int128 n2 = 0x0AAA_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA;
        Int128 n3 = n1 & n2;
        assert n3 == 0x00A2_A0A2_A0A0_A0A0_A0A0_A0A0_A0A0_A0A0;
    }

}
