/**
 * xtc run -L build/xtc/main/lib -o  build/xtc/main/lib --jit src/main/x/jit/numbers/UInt128Tests
 .x
 */
class UInt128Tests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running UInt128Tests >>>>");

        // Comparison tests
        testUInt128CompareEqLowOnly();
        testUInt128CompareEqBig();
        testUInt128CompareGeLowOnly();
        testUInt128CompareGeBig();
        testUInt128CompareGtLowOnly();
        testUInt128CompareGtBig();
        testUInt128CompareLeLowOnly();
        testUInt128CompareLeBig();
        testUInt128CompareLtLowOnly();
        testUInt128CompareLtBig();
        testUInt128MaxValueCompareEq();
        testUInt128MinValueCompareEq();
        testUInt128ZeroCompareEq();

        // Field tests
        testUInt128AsFieldLowOnly();
        testUInt128AsFieldBig();
        testUInt128AsNullableFieldLowOnly();
        testUInt128AsNullableFieldBig();
        testUInt128AsNullableFieldNull();

        // Constant field and constructor tests
        testUInt128AsConstFieldLowOnly();
        testUInt128AsConstFieldBig();
        testNullableUInt128AsConstFieldLowOnly();
        testNullableUInt128AsConstFieldBig();
        testNullableUInt128AsConstFieldNull();

        // Method parameter tests
        testUInt128AsParamLowOnly();
        testUInt128AsParamBig();
        testUInt128AsNullableParamLowOnly();
        testUInt128AsNullableParamBig();
        testUInt128AsNullableParamNull();
        testUInt128AsMultiParams();
        testUInt128AsMultiNullableParams();

        // Method return tests
        testUInt128ReturnLowOnly();
        testUInt128ReturnBig();
        testUInt128ConditionalReturnLowOnly();
        testUInt128ConditionalReturnBig();
        testUInt128ReturnStringUInt128();
        testUInt128ReturnUInt128Int();
        testUInt128ReturnTwoUInt128();
        testNullableUInt128Return();
        testNullableUInt128AsUInt128Return();
        testUInt128AsNullableUInt128Return();
        testNullableUInt128ConditionalReturn();

        // Op tests
        // Add
        testUInt128OpAddBig();
        testUInt128OpAddLowOnly();
        testUInt128OpAddOverflow();
        // Sub
        testUInt128OpSubBig();
        testUInt128OpSubLowOnly();
        testUInt128OpSubOverflow();
        // And
        testUInt128OpAnd();
        // Inc
        testUInt128OpIncLowOnly();
        testUInt128OpPreIncLowOnly();
        testUInt128OpIncBig();
        testUInt128OpPreIncBig();
        testUInt128OpPostIncBig();
        testUInt128OpIncOverflowLow();
        testUInt128OpIncOverflowBig();
        // Dec
        testUInt128OpDecLowOnly();
        testUInt128OpPreDecLowOnly();
        testUInt128OpDecBig();
        testUInt128OpPreDecBig();
        testUInt128OpPostDecBig();
        testUInt128OpDecOverflowLow();
        testUInt128OpDecOverflowBig();

        console.print("<<<<< Finished UInt128Tests Conversion tests >>>>>");
    }

    // ----- comparison tests ----------------------------------------------------------------------

    void testUInt128CompareEqBig() {
        UInt128 n = 18446744073709551616;
        assert n == 18446744073709551616;
    }

    void testUInt128MaxValueCompareEq() {
        UInt128 n = UInt128.MaxValue;
        assert n == UInt128.MaxValue;
    }

    void testUInt128MinValueCompareEq() {
        UInt128 n = UInt128.MinValue;
        assert n == UInt128.MinValue;
    }

    void testUInt128ZeroCompareEq() {
        UInt128 n = 0;
        assert n == 0;
    }

    void testUInt128CompareEqLowOnly() {
        UInt128 n = 1234;
        assert n == 1234;
    }

    void testUInt128CompareGeBig() {
        UInt128 n = 18446744073709551620;
        assert n >= 18446744073709551619;
        assert n >= 18446744073709551620;
    }

    void testUInt128CompareGeLowOnly() {
        UInt128 n = 1234;
        assert n >= 1000;
        assert n >= 1234;
    }

    void testUInt128CompareGtBig() {
        UInt128 n = 18446744073709551620;
        assert n > 18446744073709551619;
    }

    void testUInt128CompareGtLowOnly() {
        UInt128 n = 1234;
        assert n > 1233;
    }

    void testUInt128CompareLeBig() {
        UInt128 n = 18446744073709551620;
        assert n <= 18446744073709551621;
        assert n <= 18446744073709551620;
    }

    void testUInt128CompareLeLowOnly() {
        UInt128 n = 1234;
        assert n <= 1235;
        assert n <= 1234;
    }

    void testUInt128CompareLtBig() {
        UInt128 n = 18446744073709551620;
        assert n < 18446744073709551621;
    }

    void testUInt128CompareLtLowOnly() {
        UInt128 n = 1234;
        assert n < 1235;
    }

    // ----- field tests ---------------------------------------------------------------------------

    void testUInt128AsFieldLowOnly() {
        UInt128AsField n = new UInt128AsField();
        assert n.lowOnlyField == 1234;
    }

    void testUInt128AsFieldBig() {
        UInt128AsField n = new UInt128AsField();
        assert n.bigField == 18446744073709551699;
    }

    static class UInt128AsField {
        UInt128 lowOnlyField = 1234;
        UInt128 bigField = 18446744073709551699;
    }

    void testUInt128AsNullableFieldLowOnly() {
        UInt128AsNullableField n = new UInt128AsNullableField();
        assert n.lowOnlyField == 9876;
    }

    void testUInt128AsNullableFieldBig() {
        UInt128AsNullableField n = new UInt128AsNullableField();
        assert n.bigField == 18446744073709552599;
    }

    static class UInt128AsNullableField {
        UInt128? lowOnlyField = 9876;
        UInt128? bigField = 18446744073709552599;
    }

    void testUInt128AsNullableFieldNull() {
        UInt128NullField n = new UInt128NullField();
        assert n.n == Null;
    }

    static class UInt128NullField {
        UInt128? n = Null;
    }

    void testUInt128AsConstFieldLowOnly() {
        UInt128      n = 1234;
        NumberHolder h = new NumberHolder(n);
        assert h.n == 1234;
    }

    void testUInt128AsConstFieldBig() {
        UInt128      n = 18446744073709551900;
        NumberHolder h = new NumberHolder(n);
        assert h.n == n;
    }

    static const NumberHolder(UInt128 n) {
    }

    void testNullableUInt128AsConstFieldLowOnly() {
        UInt128              n = 1234;
        NullableNumberHolder h = new NullableNumberHolder(n);
        assert h.n == 1234;
    }

    void testNullableUInt128AsConstFieldBig() {
        UInt128              n = 18446744073709551900;
        NullableNumberHolder h = new NullableNumberHolder(n);
        assert h.n == n;
    }

    void testNullableUInt128AsConstFieldNull() {
        NullableNumberHolder h = new NullableNumberHolder(Null);
        assert h.n == Null;
    }

    static const NullableNumberHolder(UInt128? n) {
    }

    // ----- parameter tests -----------------------------------------------------------------------

    void testUInt128AsParamLowOnly() {
        UInt128 n = 1234;
        uInt128ParamLowOnly(n);
    }

    void uInt128ParamLowOnly(UInt128 n) {
        assert n == 1234;
    }

    void testUInt128AsParamBig() {
        UInt128 n = 18446744073709551699;
        uInt128ParamBig(n);
    }

    void uInt128ParamBig(UInt128 n) {
        assert n == 18446744073709551699;
    }

    void testUInt128AsNullableParamLowOnly() {
        UInt128 n     = 1234;
        Boolean isNum = uInt128NullableParamLowOnly(n);
        assert isNum == True;
    }

    Boolean uInt128NullableParamLowOnly(UInt128? n) {
        if (n.is(UInt128)) {
            assert n == 1234;
            return True;
        }
        return False;
    }

    void testUInt128AsNullableParamBig() {
        UInt128 n      = 18446744073709551999;
        Boolean isNull = uInt128NullableParamBig(n);
        assert isNull == False;
    }

    void testUInt128AsNullableParamNull() {
        Boolean isNull = uInt128NullableParamBig(Null);
        assert isNull == True;
    }

    Boolean uInt128NullableParamBig(UInt128? n) {
        if (n.is(UInt128)) {
            assert n == 18446744073709551999;
            return False;
        }
        return True;
    }

    void testUInt128AsMultiParams() {
        uInt128MultiParams(1234, 18446744073709581999);
    }

    void uInt128MultiParams(UInt128 n1, UInt128 n2) {
        assert n1 == 1234;
        assert n2 == 18446744073709581999;
    }

    void testUInt128AsMultiNullableParams() {
        Boolean b1;
        Boolean b2;
        (b1, b2) = uInt128MultiNullableParams(1234, 18446744073709571999);
        assert b1 == True;
        assert b2 == True;
        (b1, b2) = uInt128MultiNullableParams(1234, Null);
        assert b1 == True;
        assert b2 == False;
        (b1, b2) = uInt128MultiNullableParams(Null, 18446744073709571999);
        assert b1 == False;
        assert b2 == True;
        (b1, b2) = uInt128MultiNullableParams(Null, Null);
        assert b1 == False;
        assert b2 == False;
    }

    (Boolean, Boolean) uInt128MultiNullableParams(UInt128? n1, UInt128? n2) {
        Boolean b1 = False;
        Boolean b2 = False;
        if (n1.is(UInt128)) {
            assert n1 == 1234;
            b1 = True;
        }
        if (n2.is(UInt128)) {
            assert n2 == 18446744073709571999;
            b2 = True;
        }
        return b1, b2;
    }

    // ----- return tests --------------------------------------------------------------------------

    void testUInt128ReturnLowOnly() {
        UInt128 n = returnUInt128LowOnly();
        assert n == 1234;
    }

    UInt128 returnUInt128LowOnly() {
        UInt128 n = 1234;
        return n;
    }

    void testUInt128ReturnBig() {
        UInt128 n = returnUInt128Big();
        assert n == 18446744073709551899;
    }

    UInt128 returnUInt128Big() {
        UInt128 n = 18446744073709551899;
        return n;
    }

    void testUInt128ConditionalReturnLowOnly() {
        assert UInt128 n := returnConditionalUInt128LowOnly();
        assert n == 1234;
    }

    conditional UInt128 returnConditionalUInt128LowOnly() {
        UInt128 n = 1234;
        return True, n;
    }

    void testUInt128ConditionalReturnBig() {
        assert UInt128 n := returnConditionalUInt128Big();
        assert n == 18446744073709551899;
    }

    conditional UInt128 returnConditionalUInt128Big() {
        UInt128 n = 18446744073709551899;
        return True, n;
    }

    void testUInt128ReturnStringUInt128() {
        (String s, UInt128 n) = returnStringUInt128();
        assert s == "Foo";
        assert n == 18446744073709551999;
    }

    (String, UInt128) returnStringUInt128() {
        UInt128 n = 18446744073709551999;
        return "Foo", n;
    }

    void testUInt128ReturnUInt128Int() {
        (UInt128 n, Int i) = returnUInt128Int();
        assert n == 18446744073709551999;
        assert i == 19;
    }

    (UInt128, Int) returnUInt128Int() {
        UInt128 n = 18446744073709551999;
        return n, 19;
    }

    void testUInt128ReturnTwoUInt128() {
        (UInt128 n1, UInt128 n2) = returnTwoUInt128();
        assert n1 == 18446744073709551999;
        assert n2 == 18446744073709552999;
    }

    (UInt128, UInt128) returnTwoUInt128() {
        UInt128 n1 = 18446744073709551999;
        UInt128 n2 = 18446744073709552999;
        return n1, n2;
    }

    void testNullableUInt128Return() {
        UInt128? n = returnNullableUInt128(True);
        assert n == 18446744073709591999;
        n = returnNullableUInt128(False);
        assert n == Null;
    }

    UInt128? returnNullableUInt128(Boolean b) {
        UInt128 n = 18446744073709591999;
        if (b) {
            return n;
        }
        return Null;
    }

    void testNullableUInt128AsUInt128Return() {
        UInt128 n = returnNullableUInt128AsUInt128();
        assert n == 98987676;
    }

    UInt128 returnNullableUInt128AsUInt128() {
        UInt128? n = 98987676;
        return n;
    }

    void testUInt128AsNullableUInt128Return() {
        UInt128? n = returnUInt128AsNullableUInt128();
        assert n == 98987676;
    }

    UInt128? returnUInt128AsNullableUInt128() {
        UInt128 n = 98987676;
        return n;
    }

    void testNullableUInt128ConditionalReturnLowOnly() {
        assert UInt128? n := returnConditionalNullableUInt128LowOnly();
        assert n == 1234;
    }

    conditional UInt128? returnConditionalNullableUInt128LowOnly() {
        UInt128? n = 1234;
        return True, n;
    }

    void testNullableUInt128ConditionalReturn() {
        assert UInt128? n := returnConditionalNullableUInt128(0);
        assert n == 18446744073709551899;
        assert n := returnConditionalNullableUInt128(1);
        assert n == Null;
        assert returnConditionalNullableUInt128(2) == False;
    }

    conditional UInt128? returnConditionalNullableUInt128(Int i) {
        UInt128? n = 18446744073709551899;
        if (i == 0) {
            return True, n;
        }
        if (i == 1) {
            return True, Null;
        }
        return False;
    }

    // ----- Op tests (Add) ------------------------------------------------------------------------

    void testUInt128OpAddBig() {
        UInt128 n1 = 18446744073709551616;
        UInt128 n2 = 18446744073709200000;
        UInt128 n3 = n1 + n2;
        assert n3 == 36893488147418751616;
    }

    void testUInt128OpAddLowOnly() {
        UInt128 n1 = 1000;
        UInt128 n2 = 19;
        UInt128 n3 = n1 + n2;
        assert n3 == 1019;
    }

    void testUInt128OpAddOverflow() {
        UInt128 n1 = UInt128.MaxValue;
        UInt128 n2 = 1;
        UInt128 n3 = n1 + n2;
        assert n3 == UInt128.MinValue;
    }

    // ----- Op tests (Sub) ------------------------------------------------------------------------

    void testUInt128OpSubBig() {
        UInt128 n1 = 18446744073709551616;
        UInt128 n2 = 18446744073709200000;
        UInt128 n3 = n1 - n2;
        assert n3 == 351616;
    }

    void testUInt128OpSubLowOnly() {
        UInt128 n1 = 1000;
        UInt128 n2 = 19;
        UInt128 n3 = n1 - n2;
        assert n3 == 981;
    }

    void testUInt128OpSubOverflow() {
        UInt128 n1 = UInt128.MaxValue;
        UInt128 n2 = 1;
        UInt128 n3 = n1 - n2;
        assert n3 == UInt128.MinValue;
    }

    // ----- Op tests (logical And) ----------------------------------------------------------------

    void testUInt128OpAnd() {
        UInt128 n1 = 0x00F2_F0F2_F0F0_F0F0_F0F0_F0F0_F0F0_F0F0;
        UInt128 n2 = 0x0AAA_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA;
        UInt128 n3 = n1 & n2;
        assert n3 == 0x00A2_A0A2_A0A0_A0A0_A0A0_A0A0_A0A0_A0A0;
    }

    // ----- Op tests (Inc ++) ---------------------------------------------------------------------

    void testUInt128OpIncLowOnly() {
        UInt128 n = 1234;
        n++;
        assert n == 1235;
    }

    void testUInt128OpPreIncLowOnly() {
        UInt128 n1 = 1234;
        UInt128 n2 = ++n1;
        assert n1 == 1235;
        assert n2 == 1235;
    }

    void testUInt128OpPostIncLowOnly() {
        UInt128 n1 = 1234;
        UInt128 n2 = n1++;
        assert n1 == 1235;
        assert n2 == 1234;
    }

    void testUInt128OpIncBig() {
        UInt128 n = 18446744073709551616;
        n++;
        assert n == 18446744073709551617;
    }

    void testUInt128OpPreIncBig() {
        UInt128 n1 = 18446744073709551000;
        UInt128 n2 = ++n1;
        assert n1 == 18446744073709551001;
        assert n2 == 18446744073709551001;
    }

    void testUInt128OpPostIncBig() {
        UInt128 n1 = 18446744073709551010;
        UInt128 n2 = n1++;
        assert n1 == 18446744073709551011;
        assert n2 == 18446744073709551010;
    }

    void testUInt128OpIncOverflowLow() {
        UInt128 n = 2147483647;
        n++;
        assert n == 2147483648;
    }

    void testUInt128OpIncOverflowBig() {
        UInt128 n = UInt128.MaxValue;
        n++;
        assert n == UInt128.MinValue;
    }

    // ----- Op tests (Dec -- ) --------------------------------------------------------------------

    void testUInt128OpDecLowOnly() {
        UInt128 n = 1234;
        n--;
        assert n == 1233;
    }

    void testUInt128OpPreDecLowOnly() {
        UInt128 n1 = 1234;
        UInt128 n2 = --n1;
        assert n1 == 1233;
        assert n2 == 1233;
    }

    void testUInt128OpPostDecLowOnly() {
        UInt128 n1 = 1234;
        UInt128 n2 = n1--;
        assert n1 == 1233;
        assert n2 == 1234;
    }

    void testUInt128OpDecBig() {
        UInt128 n = 18446744073709551616;
        n--;
        assert n == 18446744073709551615;
    }

    void testUInt128OpPreDecBig() {
        UInt128 n1 = 18446744073709551000;
        UInt128 n2 = --n1;
        assert n1 == 18446744073709550999;
        assert n2 == 18446744073709550999;
    }

    void testUInt128OpPostDecBig() {
        UInt128 n1 = 18446744073709551010;
        UInt128 n2 = n1--;
        assert n1 == 18446744073709551009;
        assert n2 == 18446744073709551010;
    }

    void testUInt128OpDecOverflowLow() {
        UInt128 n = 2147483648;
        n--;
        assert n == 2147483647;
    }

    void testUInt128OpDecOverflowBig() {
        UInt128 n = UInt128.MinValue;
        n--;
        assert n == UInt128.MaxValue;
    }
}
