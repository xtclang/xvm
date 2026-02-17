import ecstasy.io.IOException;

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

        testFinallyReturn();

        // Op tests
        // Add
        testInt128OpAddBig();
        testInt128OpAddLowOnly();
        testInt128OpAddOverflowLow();
        testInt128OpAddFirstNegativeBig();
        testInt128OpAddSecondNegativeBig();
        testInt128OpAddBothNegativeBig();
        testInt128OpAddOverflow();
        // Sub
        testInt128OpSubBig();
        testInt128OpSubLowOnly();
        testInt128OpSubOverflowLow();
        testInt128OpSubFirstNegativeBig();
        testInt128OpSubSecondNegativeBig();
        testInt128OpSubBothNegativeBig();
        testInt128OpSubOverflow();
        // And
        testInt128OpAnd();
        // Complement
        testInt128ComplementLowOnly();
        testInt128ComplementBig();
        // Inc
        testInt128OpIncLowOnly();
        testInt128OpPreIncLowOnly();
        testInt128OpIncBig();
        testInt128OpPreIncBig();
        testInt128OpPostIncBig();
        testInt128OpIncOverflowLow();
        testInt128OpIncOverflowBig();
        testInt128OpIncNegativeBig();
        // Dec
        testInt128OpDecLowOnly();
        testInt128OpPreDecLowOnly();
        testInt128OpDecBig();
        testInt128OpPreDecBig();
        testInt128OpPostDecBig();
        testInt128OpDecOverflowLow();
        testInt128OpDecOverflowBig();
        testInt128OpDecNegativeBig();
        // Or
        testInt128OpOr();
        testInt128OpOrInPlace();
        // Shl
        testInt128OpShiftLeft();
        testInt128OpShiftLeftZero();
        testInt128OpShiftLeft64();
        testInt128OpShiftLeft128();
        testInt128OpShiftLeft132();
        testInt128OpShiftLeftMinus4();
        // Shr
        testInt128OpShiftRight();
        testInt128OpShiftRightNegative();
        testInt128OpShiftRightZero();
        testInt128OpShiftRight128();
        testInt128OpShiftRight132();
        testInt128OpShiftRightMinus4();
        // Ushr
        testInt128OpUnsignedShiftRight();
        testInt128OpUnsignedShiftRightNegative();
        testInt128OpUnsignedShiftRightZero();
        testInt128OpUnsignedShiftRight128();
        testInt128OpUnsignedShiftRight132();
        testInt128OpUnsignedShiftRightMinus4();
        // Xor
        testInt128OpXor();
        testInt128OpXorInPlace();

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

    // ----- finally -------------------------------------------------------------------------------

    void testFinallyReturn() {
        Int128 n = returnFinally();
        assert n == 11;
    }

    Int128 returnFinally() {
        try {
            for (Int128 i : 0..2) {
                try {
                    testThrow(i);
                } catch (IOException e) {
                    console.print("IOException caught");
                    continue;
                } catch (Unsupported e) {
                    console.print("Unsupported caught");
                    return i + 10;
                } finally {
                    console.print("Finally: ", True);
                    console.print(i);
                    if (i == 2) {
                        return i + 40;
                    }
                }
            }
            return -1;
        } finally {
            console.print("Done");
        }
    }

    void testThrow(Int128 i) {
// ToDo why does this fail to compile with the JIT?
//        if (i < 0) {
//            return;
//        }
        if (i == 0) {
            throw new IOException("Test IO");
        } else if (i == 1) {
            throw new Unsupported("Test Unsupported");
        } else if (i > 1) {
            throw new IllegalState("Test IllegalState");
        }
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

    void testInt128OpAddOverflowLow() {
        Int128 n1 = 2147483647;
        Int128 n2 = 1;
        Int128 n3 = n1 + n2;
        assert n3 == 2147483648;
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

    // ----- Op tests (Sub) ------------------------------------------------------------------------

    void testInt128OpSubBig() {
        Int128 n1 = 18446744073709551616;
        Int128 n2 = 18446744073709200000;
        Int128 n3 = n1 - n2;
        assert n3 == 351616;
    }

    void testInt128OpSubLowOnly() {
        Int128 n1 = 1000;
        Int128 n2 = 19;
        Int128 n3 = n1 - n2;
        assert n3 == 981;
    }

    void testInt128OpSubOverflowLow() {
        Int128 n1 = 2147483648;
        Int128 n2 = 1;
        Int128 n3 = n1 - n2;
        assert n3 == 2147483647;
    }

    void testInt128OpSubFirstNegativeBig() {
        Int128 n1 = -18446744073709551616;
        Int128 n2 = 18446744073709200000;
        Int128 n3 = n1 - n2;
        assert n3 == -36893488147418751616;
    }

    void testInt128OpSubSecondNegativeBig() {
        Int128 n1 = 18446744073709551616;
        Int128 n2 = -18446744073709200000;
        Int128 n3 = n1 - n2;
        assert n3 == 36893488147418751616;
    }

    void testInt128OpSubBothNegativeBig() {
        Int128 n1 = -18446744073709551616;
        Int128 n2 = -18446744073709200000;
        Int128 n3 = n1 - n2;
        assert n3 == -351616;
    }

    void testInt128OpSubOverflow() {
        Int128 n1 = Int128.MinValue;
        Int128 n2 = 1;
        Int128 n3 = n1 - n2;
        assert n3 == Int128.MaxValue;
    }

    // ----- Op tests (logical And) ----------------------------------------------------------------

    void testInt128OpAnd() {
        Int128 n1 = 0x00F2_F0F2_F0F0_F0F0_F0F0_F0F0_F0F0_F0F0;
        Int128 n2 = 0x0AAA_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA;
        Int128 n3 = n1 & n2;
        assert n3 == 0x00A2_A0A2_A0A0_A0A0_A0A0_A0A0_A0A0_A0A0;
    }

    // ----- Op tests (Complement ~) ---------------------------------------------------------------

    void testInt128ComplementLowOnly() {
        Int128 value1 = 0;
        Int128 value2 = 0x5ABC5432;
        value1 = ~value2;
        assert value1 == -1522291763;
    }

    void testInt128ComplementBig() {
        Int128 value1 = 0;
        Int128 value2 = 0x00A0_8585_A0A0_8585_A0A0_1919_A0A0_1919;
        value1 = ~value2;
//                        0xFF5F_7A7A_5F5F_7A7A_5F5F_E6E6_5F5F_E6E6;
        assert value1 == -833475644900132732175178108745292058;
    }

    // ----- Op tests (Inc ++) ---------------------------------------------------------------------

    void testInt128OpIncLowOnly() {
        Int128 n = 1234;
        n++;
        assert n == 1235;
    }

    void testInt128OpPreIncLowOnly() {
        Int128 n1 = 1234;
        Int128 n2 = ++n1;
        assert n1 == 1235;
        assert n2 == 1235;
    }

    void testInt128OpPostIncLowOnly() {
        Int128 n1 = 1234;
        Int128 n2 = n1++;
        assert n1 == 1235;
        assert n2 == 1234;
    }

    void testInt128OpIncBig() {
        Int128 n = 18446744073709551616;
        n++;
        assert n == 18446744073709551617;
    }

    void testInt128OpPreIncBig() {
        Int128 n1 = 18446744073709551000;
        Int128 n2 = ++n1;
        assert n1 == 18446744073709551001;
        assert n2 == 18446744073709551001;
    }

    void testInt128OpPostIncBig() {
        Int128 n1 = 18446744073709551010;
        Int128 n2 = n1++;
        assert n1 == 18446744073709551011;
        assert n2 == 18446744073709551010;
    }

    void testInt128OpIncOverflowLow() {
        Int128 n = 2147483647;
        n++;
        assert n == 2147483648;
    }

    void testInt128OpIncOverflowBig() {
        Int128 n = Int128.MaxValue;
        n++;
        assert n == Int128.MinValue;
    }

    void testInt128OpIncNegativeBig() {
        Int128 n = -18446744073709551616;
        n++;
        assert n == -18446744073709551615;
    }

    // ----- Op tests (Dec --) ---------------------------------------------------------------------

    void testInt128OpDecLowOnly() {
        Int128 n = 1234;
        n--;
        assert n == 1233;
    }

    void testInt128OpPreDecLowOnly() {
        Int128 n1 = 1234;
        Int128 n2 = --n1;
        assert n1 == 1233;
        assert n2 == 1233;
    }

    void testInt128OpPostDecLowOnly() {
        Int128 n1 = 1234;
        Int128 n2 = n1--;
        assert n1 == 1233;
        assert n2 == 1234;
    }

    void testInt128OpDecBig() {
        Int128 n = 18446744073709551616;
        n--;
        assert n == 18446744073709551615;
    }

    void testInt128OpPreDecBig() {
        Int128 n1 = 18446744073709551000;
        Int128 n2 = --n1;
        assert n1 == 18446744073709550999;
        assert n2 == 18446744073709550999;
    }

    void testInt128OpPostDecBig() {
        Int128 n1 = 18446744073709551010;
        Int128 n2 = n1--;
        assert n1 == 18446744073709551009;
        assert n2 == 18446744073709551010;
    }

    void testInt128OpDecOverflowLow() {
        Int128 n = 2147483648;
        n--;
        assert n == 2147483647;
    }

    void testInt128OpDecOverflowBig() {
        Int128 n = Int128.MinValue;
        n--;
        assert n == Int128.MaxValue;
    }

    void testInt128OpDecNegativeBig() {
        Int128 n = -18446744073709551616;
        n--;
        assert n == -18446744073709551617;
    }

    // ----- Op tests (logical Or) -----------------------------------------------------------------

    void testInt128OpOr() {
        Int128 n1 = 0x00F2_F0F2_F0F0_F0F0_F0F0_F0F0_F0F0_F0F0;
        Int128 n2 = 0x0AA0_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA;
        Int128 n3 = n1 | n2;
        assert n3 == 0x0AF2_FAFA_FAFA_FAFA_FAFA_FAFA_FAFA_FAFA;
    }

    void testInt128OpOrInPlace() {
        Int128 n = 0x00F2_F0F2_F0F0_F0F0_F0F0_F0F0_F0F0_F0F0;
        n |= 0x0AA0_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA;
        assert n == 0x0AF2_FAFA_FAFA_FAFA_FAFA_FAFA_FAFA_FAFA;
    }

    // ----- Op tests (Shift left <<) --------------------------------------------------------------

    void testInt128OpShiftLeft() {
        Int128 n = 0x1142_F0F2_F0F0_F0F0_0FF0_F0F0_F0F0_F0F1;
        Int128 n2 = n << 8;
        assert n2 == 0x42F0_F2F0_F0F0_F00F_F0F0_F0F0_F0F0_F100;
    }

    void testInt128OpShiftLeftZero() {
        Int128 n = 0x00F2_F0F2_F0F0_F0F0_0FF0_F0F0_F0F0_F0F0;
        Int128 n2 = n << 0;
        assert n2 == n;
    }

    void testInt128OpShiftLeft64() {
        Int128 n = 0x00F2_F0F2_F0F0_F0F0_0FF0_F0F0_F0F0_F0F0;
        Int128 n2 = n << 64;
        assert n2 == 0x0FF0_F0F0_F0F0_F0F0_0000_0000_0000_0000;
    }

    void testInt128OpShiftLeft128() {
        Int128 n = 1;
        Int128 n2 = n << 128;
        assert n2 == 1; // equivalent to << 0
    }

    void testInt128OpShiftLeft132() {
        Int128 n = 1;
        Int128 n2 = n << 132;
        assert n2 == 16; // 132 & 0x7F equivalent to << 4
    }

    void testInt128OpShiftLeftMinus4() {
        Int128 n = 1;
        Int128 n2 = n << -4;
        assert n2 == 0x1000_0000_0000_0000_0000_0000_0000_0000;
        // -4 == 0xFC,  0xFC & 0x7F == 0x7C equivalent to 1 << 124
    }

    // ----- Op tests (Shift right >>) -------------------------------------------------------------

    void testInt128OpShiftRight() {
        Int128 n =   0x1142_F0F2_F0F0_F0F0_0FF0_F0F0_F0F0_F0F1;
        Int128 n2 = n >> 8;
        assert n2 == 0x0011_42F0_F2F0_F0F0_F00F_F0F0_F0F0_F0F0;
    }

    void testInt128OpShiftRightNegative() {
        Int128 n = -2000;
        Int128 n2 = n >> 8;
        assert n2 == -8; // preserved sign bit
    }

    void testInt128OpShiftRightZero() {
        Int128 n =   0x1142_F0F2_F0F0_F0F0_0FF0_F0F0_F0F0_F0F1;
        Int128 n2 = n >> 0;
        assert n2 == 0x1142_F0F2_F0F0_F0F0_0FF0_F0F0_F0F0_F0F1;
    }

    void testInt128OpShiftRight128() {
        Int128 n = 1;
        Int128 n2 = n >> 128;
        assert n2 == 1; // equivalent to >> 0
    }

    void testInt128OpShiftRight132() {
        Int128 n = 16;
        Int128 n2 = n >> 132;
        assert n2 == 1; // 132 & 0x7F equivalent to >> 4
    }

    void testInt128OpShiftRightMinus4() {
        Int128 n = 0x1000_0000_0000_0000_0000_0000_0000_0000;
        Int128 n2 = n >> -4;
        assert n2 == 1;
        // -4 == 0xFC,  0xFC & 0x7F == 0x7C equivalent to 1 >> 124
    }

    // ----- Op tests (Unsigned shift right >>>) ---------------------------------------------------

    void testInt128OpUnsignedShiftRight() {
        Int128 n =   0x1142_F0F2_F0F0_F0F0_0FF0_F0F0_F0F0_F0F1;
        Int128 n2 = n >>> 8;
        assert n2 == 0x0011_42F0_F2F0_F0F0_F00F_F0F0_F0F0_F0F0;
    }

    void testInt128OpUnsignedShiftRightNegative() {
        Int128 n = -2000; // 0xFFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_F830
        Int128 n2 = n >>> 8;
        assert n2 == 0x00FF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFF8;
    }

    void testInt128OpUnsignedShiftRightZero() {
        Int128 n =   0x1142_F0F2_F0F0_F0F0_0FF0_F0F0_F0F0_F0F1;
        Int128 n2 = n >>> 0;
        assert n2 == 0x1142_F0F2_F0F0_F0F0_0FF0_F0F0_F0F0_F0F1;
    }

    void testInt128OpUnsignedShiftRight128() {
        Int128 n = 1;
        Int128 n2 = n >>> 128;
        assert n2 == 1; // equivalent to >>> 0
    }

    void testInt128OpUnsignedShiftRight132() {
        Int128 n = 16;
        Int128 n2 = n >>> 132;
        assert n2 == 1; // 132 & 0x7F equivalent to >>> 4
    }

    void testInt128OpUnsignedShiftRightMinus4() {
        Int128 n = 0x1000_0000_0000_0000_0000_0000_0000_0000;
        Int128 n2 = n >>> -4;
        assert n2 == 1;
        // -4 == 0xFC,  0xFC & 0x7F == 0x7C equivalent to 1 >>> 124
    }

    // ----- Op tests (logical Xor) ----------------------------------------------------------------

    void testInt128OpXor() {
        Int128 n1 = 0x00F2_F0F2_F0F0_F0F0_F0F0_F0F0_F0F0_F0F0;
        Int128 n2 = 0x0AA0_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA;
        Int128 n3 = n1 ^ n2;
        assert n3 == 0x0A52_5A58_5A5A_5A5A_5A5A_5A5A_5A5A_5A5A;
    }

    void testInt128OpXorInPlace() {
        Int128 n = 0x00F2_F0F2_F0F0_F0F0_F0F0_F0F0_F0F0_F0F0;
        n ^= 0x0AA0_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA;
        assert n == 0x0A52_5A58_5A5A_5A5A_5A5A_5A5A_5A5A_5A5A;
    }
}
