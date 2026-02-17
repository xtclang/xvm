import ecstasy.io.IOException;

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

        testFinallyReturn();

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
        // Complement
        testUInt128ComplementLowOnly();
        testUInt128ComplementBig();
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
        // Or
        testUInt128OpOr();
        testUInt128OpOrInPlace();
        // Shl
        testUInt128OpShiftLeft();
        testUInt128OpShiftLeftZero();
        testUInt128OpShiftLeft64();
        testUInt128OpShiftLeft128();
        testUInt128OpShiftLeft132();
        testUInt128OpShiftLeftMinus4();
        // Shr
        testUInt128OpShiftRight();
        testUInt128OpShiftRightZero();
        testUInt128OpShiftRight128();
        testUInt128OpShiftRight132();
        testUInt128OpShiftRightMinus4();
        // Ushr
        testUInt128OpUnsignedShiftRight();
        testUInt128OpUnsignedShiftRightZero();
        testUInt128OpUnsignedShiftRight128();
        testUInt128OpUnsignedShiftRight132();
        testUInt128OpUnsignedShiftRightMinus4();
        // Xor
        testUInt128OpXor();
        testUInt128OpXorInPlace();

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

    // ----- finally -------------------------------------------------------------------------------

    void testFinallyReturn() {
        UInt128 n = returnFinally();
        assert n == 11;
    }

    UInt128 returnFinally() {
        try {
            for (UInt128 i : 0..2) {
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
            return 1;
        } finally {
            console.print("Done");
        }
    }

    void testThrow(UInt128 i) {
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

    // ----- Op tests (Complement ~) ---------------------------------------------------------------

    void testUInt128ComplementLowOnly() {
        UInt128 value1 = 0;
        UInt128 value2 = 0x5ABC5432;
        value1 = ~value2;
        assert value1 == 0xFFFF_FFFF_FFFF_FFFF_FFFF_FFFF_A543_ABCD;
    }

    void testUInt128ComplementBig() {
        UInt128 value1 = 0;
        UInt128 value2 = 0x00A0_8585_A0A0_8585_A0A0_1919_A0A0_1919;
        value1 = ~value2;
        assert value1 == 0xFF5F_7A7A_5F5F_7A7A_5F5F_E6E6_5F5F_E6E6;
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

    // ----- Op tests (Dec --) ---------------------------------------------------------------------

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

    // ----- Op tests (logical Or) -----------------------------------------------------------------

    void testUInt128OpOr() {
        UInt128 n1 = 0x00F2_F0F2_F0F0_F0F0_F0F0_F0F0_F0F0_F0F0;
        UInt128 n2 = 0x0AA0_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA;
        UInt128 n3 = n1 | n2;
        assert n3 == 0x0AF2_FAFA_FAFA_FAFA_FAFA_FAFA_FAFA_FAFA;
    }

    void testUInt128OpOrInPlace() {
        UInt128 n = 0x00F2_F0F2_F0F0_F0F0_F0F0_F0F0_F0F0_F0F0;
        n |= 0x0AA0_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA;
        assert n == 0x0AF2_FAFA_FAFA_FAFA_FAFA_FAFA_FAFA_FAFA;
    }

    // ----- Op tests (Shift left <<) --------------------------------------------------------------

    void testUInt128OpShiftLeft() {
        UInt128 n = 0x1142_F0F2_F0F0_F0F0_0FF0_F0F0_F0F0_F0F1;
        UInt128 n2 = n << 8;
        assert n2 == 0x42F0_F2F0_F0F0_F00F_F0F0_F0F0_F0F0_F100;
    }

    void testUInt128OpShiftLeftZero() {
        UInt128 n = 0x00F2_F0F2_F0F0_F0F0_0FF0_F0F0_F0F0_F0F0;
        UInt128 n2 = n << 0;
        assert n2 == n;
    }

    void testUInt128OpShiftLeft64() {
        UInt128 n = 0x00F2_F0F2_F0F0_F0F0_0FF0_F0F0_F0F0_F0F0;
        UInt128 n2 = n << 64;
        assert n2 == 0x0FF0_F0F0_F0F0_F0F0_0000_0000_0000_0000;
    }

    void testUInt128OpShiftLeft128() {
        UInt128 n = 1;
        UInt128 n2 = n << 128;
        assert n2 == 1; // equivalent to << 0
    }

    void testUInt128OpShiftLeft132() {
        UInt128 n = 1;
        UInt128 n2 = n << 132;
        assert n2 == 16; // 132 & 0x7F equivalent to << 4
    }

    void testUInt128OpShiftLeftMinus4() {
        UInt128 n = 1;
        UInt128 n2 = n << -4;
        assert n2 == 0x1000_0000_0000_0000_0000_0000_0000_0000;
        // -4 == 0xFC,  0xFC & 0x7F == 0x7C equivalent to 1 << 124
    }

    // ----- Op tests (Shift right >>) -------------------------------------------------------------

    void testUInt128OpShiftRight() {
        UInt128 n = 0xFF00_1234_5678_9ABC_DEF0_1234_5678_9ABC;
        UInt128 n2 = n >> 8;
        assert n2 == 0x00FF_0012_3456_789A_BCDE_F012_3456_789A; // unsigned right shift
    }

    void testUInt128OpShiftRightZero() {
        UInt128 n =   0x1142_F0F2_F0F0_F0F0_0FF0_F0F0_F0F0_F0F1;
        UInt128 n2 = n >> 0;
        assert n2 == 0x1142_F0F2_F0F0_F0F0_0FF0_F0F0_F0F0_F0F1;
    }

    void testUInt128OpShiftRight128() {
        UInt128 n = 1;
        UInt128 n2 = n >> 128;
        assert n2 == 1; // equivalent to >> 0
    }

    void testUInt128OpShiftRight132() {
        UInt128 n = 16;
        UInt128 n2 = n >> 132;
        assert n2 == 1; // 132 & 0x7F equivalent to >> 4
    }

    void testUInt128OpShiftRightMinus4() {
        UInt128 n = 0x1000_0000_0000_0000_0000_0000_0000_0000;
        UInt128 n2 = n >> -4;
        assert n2 == 1;
        // -4 == 0xFC,  0xFC & 0x7F == 0x7C equivalent to 1 >> 124
    }

    // ----- Op tests (Unsigned shift right >>>) ---------------------------------------------------

    void testUInt128OpUnsignedShiftRight() {
        UInt128 n = 0xFFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_F830;
        UInt128 n2 = n >>> 8;
        assert n2 == 0x00FF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFF8;
    }

    void testUInt128OpUnsignedShiftRightZero() {
        UInt128 n =   0x1142_F0F2_F0F0_F0F0_0FF0_F0F0_F0F0_F0F1;
        UInt128 n2 = n >>> 0;
        assert n2 == 0x1142_F0F2_F0F0_F0F0_0FF0_F0F0_F0F0_F0F1;
    }

    void testUInt128OpUnsignedShiftRight128() {
        UInt128 n = 1;
        UInt128 n2 = n >>> 128;
        assert n2 == 1; // equivalent to >>> 0
    }

    void testUInt128OpUnsignedShiftRight132() {
        UInt128 n = 16;
        UInt128 n2 = n >>> 132;
        assert n2 == 1; // 132 & 0x7F equivalent to >>> 4
    }

    void testUInt128OpUnsignedShiftRightMinus4() {
        UInt128 n = 0x1000_0000_0000_0000_0000_0000_0000_0000;
        UInt128 n2 = n >>> -4;
        assert n2 == 1;
        // -4 == 0xFC,  0xFC & 0x7F == 0x7C equivalent to 1 >>> 124
    }

    // ----- Op tests (logical Xor) ----------------------------------------------------------------

    void testUInt128OpXor() {
        UInt128 n1 = 0x00F2_F0F2_F0F0_F0F0_F0F0_F0F0_F0F0_F0F0;
        UInt128 n2 = 0x0AA0_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA;
        UInt128 n3 = n1 ^ n2;
        assert n3 == 0x0A52_5A58_5A5A_5A5A_5A5A_5A5A_5A5A_5A5A;
    }

    void testUInt128OpXorInPlace() {
        UInt128 n = 0x00F2_F0F2_F0F0_F0F0_F0F0_F0F0_F0F0_F0F0;
        n ^= 0x0AA0_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA_AAAA;
        assert n == 0x0A52_5A58_5A5A_5A5A_5A5A_5A5A_5A5A_5A5A;
    }
}
