class UIntNTests {

    @Inject Console console;

    void run() {

        // Comparison tests
        testUIntNCompareEq();
        testUIntNCompareGe();
        testUIntNCompareGt();
        testUIntNCompareLe();
        testUIntNCompareLt();

        // Field tests
        testUIntNAsField();
        testUIntNAsNullableField();
        testUIntNAsNullableFieldNull();

        // Constant field and constructor tests
        testUIntNAsConstField();
        testNullableIntAsConstField();
        testNullableIntAsConstFieldNull();

        // Method parameter tests
        testUIntNAsParam();
        testUIntNAsNullableParam();
        testUIntNAsNullableParamNull();
        testUIntNAsMultiParams();
        testUIntNAsMultiNullableParams();

        // Method return tests
        testUIntNReturn();
        testUIntNConditionalReturn();
        testUIntNReturnStringIntN();
        testUIntNReturnIntNInt();
        testUIntNReturnTwoIntN();
        testNullableIntReturn();
        testNullableIntAsIntReturn();
        testIntAsNullableIntReturn();
        testNullableIntConditionalReturn();

        // GP/IP Op tests
        // Add
        testUIntNOpAdd();
        testUIntNOpAddInPlace();
        testUIntNPropertyAdd();
        // And
        testUIntNOpAnd();
        // Complement
        testUIntNComplement();
        // Inc
        testUIntNOpInc();
        testUIntNOpPreInc();
        testUIntNOpPostInc();
        // Dec
        testUIntNOpDec();
        testUIntNOpPreDec();
        testUIntNOpPostDec();
        testUIntNOpDecZero();
        // Div
        testUIntNOpDiv();
        testUIntNOpDivInPlace();
        // DivRem
        testUIntNOpDivRem();
        // Mod
        testUIntNOpMod();
        // Multiply
        testUIntNOpMultiply();
        testUIntNOpMultiply();
        testUIntNOpMultiplyInPlace();
        // Or
        testUIntNOpOr();
        testUIntNOpOrInPlace();
        // Rotate
        testUIntNRotateLeft();
        testUIntNZeroRotateLeft();
        testUIntNRotateLeftByZero();
        testUIntNRotateLeftByBitLength();
        testUIntNRotateRight();
        testUIntNZeroRotateRight();
        testUIntNRotateRightByZero();
        testUIntNRotateRightByBitLength();
        // Retain Bits
        testUIntNRetainLSBits();
        testUIntNRetainLSBitsAllBits();
        testUIntNRetainLSBitsLargerThanBits();
        testUIntNRetainLSBitsZero();
        testUIntNRetainLSBitsNegative();
        testUIntNRetainMSBits();
        testUIntNRetainMSBitsAllBits();
        testUIntNRetainMSBitsLargerThanBits();
        testUIntNRetainMSBitsZero();
        testUIntNRetainMSBitsNegative();
        // Shl
        testUIntNOpShiftLeft();
        testUIntNOpShiftLeftZero();
        testUIntNOpShiftLeftMinus4();
        // Shr
        testUIntNOpShiftRight();
        testUIntNOpShiftRightZero();
        testUIntNOpShiftRightMinus4();
        // Sub
        testUIntNOpSub();
        // Ushr
        testUIntNOpUnsignedShiftRight();
        testUIntNOpUnsignedShiftRightZero();
        testUIntNOpUnsignedShiftRightMinus4();
        // Xor
        testUIntNOpXor();
        testUIntNOpXorInPlace();

        // Misc
        testUIntNAbsPositive();
        testUIntNAbsZero();
        testUIntNPow();
        testBitLength();
        testByteLength();
        testSigned();
        testSign();
        testNegative();
        testFinite();
        testInfinity();
        testNaN();
        testMagnitude();

        // Stringable
        testAppendTo();
        testEstimateStringLength();

        // As Number tests
        testAbsAsNumberPositive();
        testAbsAsNumberZero();
        testAddAsNumber();
        testDivAsNumber();
        testDivRemAsNumber();
        testModAsNumber();
        testMultiplyAsNumber();
        testNegateAsNumber();
        testPowAsNumber();
        testSubAsNumber();
        testBitLengthAsNumber();
        testByteLengthAsNumber();
        testSignedAsNumber();
        testSignAsNumber();
        testNegativeAsNumber();
        testFiniteAsNumber();
        testInfinityAsNumber();
        testNaNAsNumber();
        testMagnitudeAsNumber();

        // As IntNumber tests
        testAbsAsIntNumberPositive();
        testAbsAsIntNumberZero();
        testAddAsIntNumber();
        testAndAsIntNumber();
        testComplementAsIntNumber();
        testIncAsIntNumber();
        testDecAsIntNumber();
        testDivAsIntNumber();
        testDivRemAsIntNumber();
        testModAsIntNumber();
        testMultiplyAsIntNumber();
        testNegateAsIntNumber();
        testOrAsIntNumber();
        testPowAsIntNumber();
        testRetainLSBitsAsIntNumber();
        testRetainLSBitsAllBitsAsIntNumber();
        testRetainLSBitsLargerThanBitsAsIntNumber();
        testRetainLSBitsZeroAsIntNumber();
        testRetainLSBitsNegativeAsIntNumber();
        testRetainMSBitsAsIntNumber();
        testRetainMSBitsAllBitsAsIntNumber();
        testRetainMSBitsLargerThanBitsAsIntNumber();
        testRetainMSBitsZeroAsIntNumber();
        testRetainMSBitsNegativeAsIntNumber();
        testRotateLeftAsIntNumber();
        testRotateRightAsIntNumber();
        testShiftLeftAsIntNumber();
        testShiftRightAsIntNumber();
        testSubAsIntNumber();
        testUnsignedShiftRightAsIntNumber();
        testXorAsIntNumber();
        testBitLengthAsIntNumber();
        testByteLengthAsIntNumber();
        testSignedAsIntNumber();
        testSignAsIntNumber();
        testNegativeAsIntNumber();
        testFiniteAsIntNumber();
        testInfinityAsIntNumber();
        testNaNAsIntNumber();
        testMagnitudeAsIntNumber();

        // As Sequential tests
        testIncAsSequential();
        testDecAsSequential();

    }

    // ----- comparison tests ----------------------------------------------------------------------

    void testUIntNCompareEq() {
        UIntN n = 1234;
        assert n == 1234;
    }

    void testUIntNCompareGe() {
        UIntN n = 1234;
        assert n >= 1000;
        assert n >= 1234;
    }

    void testUIntNCompareGt() {
        UIntN n = 1234;
        assert n > 1233;
    }

    void testUIntNCompareLe() {
        UIntN n = 1234;
        assert n <= 1235;
        assert n <= 1234;
    }

    void testUIntNCompareLt() {
        UIntN n = 1234;
        assert n < 1235;
    }

    // ----- field tests ---------------------------------------------------------------------------

    void testUIntNAsField() {
        IntNAsField n = new IntNAsField();
        assert n.field == 1234;
    }

    static class IntNAsField {
        UIntN field = 1234;
    }

    void testUIntNAsNullableField() {
        IntNAsNullableField n = new IntNAsNullableField();
        assert n.field == 9876;
    }

    static class IntNAsNullableField {
        UIntN? field = 9876;
    }

    void testUIntNAsNullableFieldNull() {
        IntNNullField n = new IntNNullField();
        assert n.field == Null;
    }

    static class IntNNullField {
        UIntN? field = Null;
    }

    void testUIntNAsConstField() {
        UIntN        n = 1234;
        NumberHolder h = new NumberHolder(n);
        assert h.n == 1234;
    }

    static const NumberHolder(UIntN n = 0) {
    }

    void testNullableIntAsConstField() {
        UIntN                n = 9876543;
        NullableNumberHolder h = new NullableNumberHolder(n);
        assert h.n == n;
    }

    void testNullableIntAsConstFieldNull() {
        NullableNumberHolder h = new NullableNumberHolder(Null);
        assert h.n == Null;
    }

    static const NullableNumberHolder(UIntN? n = Null) {
    }

    // ----- parameter tests -----------------------------------------------------------------------

    void testUIntNAsParam() {
        UIntN n = 1234;
        IntNParam(n);
    }

    void IntNParam(UIntN n) {
        assert n == 1234;
    }

    void testUIntNAsNullableParam() {
        UIntN   n      = 1234;
        Boolean isNull = IntNNullableParam(n);
        assert isNull == False;
    }

    void testUIntNAsNullableParamNull() {
        Boolean isNull = IntNNullableParam(Null);
        assert isNull == True;
    }

    Boolean IntNNullableParam(UIntN? n) {
        if (n.is(UIntN)) {
            assert n == 1234;
            return False;
        }
        return True;
    }

    void testUIntNAsMultiParams() {
        IntNMultiParams(1234, 98765432);
    }

    void IntNMultiParams(UIntN n1, UIntN n2) {
        assert n1 == 1234;
        assert n2 == 98765432;
    }

    void testUIntNAsMultiNullableParams() {
        Boolean b1;
        Boolean b2;
        (b1, b2) = IntNMultiNullableParams(1234, 12349876);
        assert b1 == True;
        assert b2 == True;
        (b1, b2) = IntNMultiNullableParams(1234, Null);
        assert b1 == True;
        assert b2 == False;
        (b1, b2) = IntNMultiNullableParams(Null, 12349876);
        assert b1 == False;
        assert b2 == True;
        (b1, b2) = IntNMultiNullableParams(Null, Null);
        assert b1 == False;
        assert b2 == False;
    }

    (Boolean, Boolean) IntNMultiNullableParams(UIntN? n1, UIntN? n2) {
        Boolean b1 = False;
        Boolean b2 = False;
        if (n1.is(UIntN)) {
            assert n1 == 1234;
            b1 = True;
        }
        if (n2.is(UIntN)) {
            assert n2 == 12349876;
            b2 = True;
        }
        return b1, b2;
    }

    // ----- return tests --------------------------------------------------------------------------

    void testUIntNReturn() {
        UIntN n = returnIntN();
        assert n == 1234;
    }

    UIntN returnIntN() {
        UIntN n = 1234;
        return n;
    }

    void testUIntNConditionalReturn() {
        assert UIntN n := returnConditionalIntN();
        assert n == 1234;
    }

    conditional UIntN returnConditionalIntN() {
        UIntN n = 1234;
        return True, n;
    }

    void testUIntNReturnStringIntN() {
        (String s, UIntN n) = returnStringIntN();
        assert s == "Foo";
        assert n == 9876;
    }

    (String, UIntN) returnStringIntN() {
        UIntN n = 9876;
        return "Foo", n;
    }

    void testUIntNReturnIntNInt() {
        (UIntN n, UIntN i) = returnIntNInt();
        assert n == 9999;
        assert i == 19;
    }

    (UIntN, UIntN) returnIntNInt() {
        UIntN n = 9999;
        return n, 19;
    }

    void testUIntNReturnTwoIntN() {
        (UIntN n1, UIntN n2) = returnTwoIntN();
        assert n1 == 4567;
        assert n2 == 1290;
    }

    (UIntN, UIntN) returnTwoIntN() {
        UIntN n1 = 4567;
        UIntN n2 = 1290;
        return n1, n2;
    }

    void testNullableIntReturn() {
        UIntN? n = returnNullableInt(True);
        assert n == 987654321;
        n = returnNullableInt(False);
        assert n == Null;
    }

    UIntN? returnNullableInt(Boolean b) {
        UIntN n = 987654321;
        if (b) {
            return n;
        }
        return Null;
    }

    void testNullableIntAsIntReturn() {
        UIntN n = returnNullableIntAsInt();
        assert n == 98987676;
    }

    UIntN returnNullableIntAsInt() {
        UIntN? n = 98987676;
        return n;
    }

    void testIntAsNullableIntReturn() {
        UIntN? n = returnIntAsNullableInt();
        assert n == 98987676;
    }

    UIntN? returnIntAsNullableInt() {
        UIntN n = 98987676;
        return n;
    }

    void testNullableIntConditionalReturn() {
        assert UIntN? n := returnConditionalNullableInt(0);
        assert n == 191919;
        assert n := returnConditionalNullableInt(1);
        assert n == Null;
        assert returnConditionalNullableInt(2) == False;
    }

    conditional UIntN? returnConditionalNullableInt(UIntN i) {
        UIntN? n = 191919;
        if (i == 0) {
            return True, n;
        }
        if (i == 1) {
            return True, Null;
        }
        return False;
    }

    // ----- Op tests (Add) ------------------------------------------------------------------------

    void testUIntNOpAddBig() {
        UIntN n1 = 709551616;
        UIntN n2 = 709200000;
        UIntN n3 = n1 + n2;
        assert n3 == 147418751616;
    }

    void testUIntNOpAdd() {
        UIntN n1 = 1000;
        UIntN n2 = 19;
        UIntN n3 = n1 + n2;
        assert n3 == 1019;
    }

    void testUIntNOpAddInPlace() {
        UIntN n1 = 709551616;
        n1 += 709200000;
        assert n1 == 1418751616;
    }

    void testUIntNPropertyAdd() {
        IntNAsField test = new IntNAsField();
        test.field = 1000;
        UIntN n2 = 19;
        UIntN n3 = test.field + n2;
        assert n3 == 1019;
        test.field = test.field + 100;
        assert test.field == 1100;
    }

    // ----- Op tests (Sub) ------------------------------------------------------------------------

    void testUIntNOpSub() {
        UIntN n1 = 1000;
        UIntN n2 = 19;
        UIntN n3 = n1 - n2;
        assert n3 == 981;
    }

    void testUIntNOpSubWhenZero() {
        UIntN n1 = 0;
        UIntN n2 = 19;
        try {
            UIntN n3 = n1 - n2;
            assert as "Should have failed";
        } catch (OutOfBounds e) {
            // expected
        }
    }

    void testUIntNOpSubInPlace() {
        UIntN n1 = 709551616;
        n1 -= 709200000;
        assert n1 == 351616;
    }

    // ----- Op tests (logical And) ----------------------------------------------------------------

    void testUIntNOpAnd() {
        UIntN n1 = 0x00F2_F0F2_F0F0_F0F0;
        UIntN n2 = 0x0AAA_AAAA_AAAA_AAAA;
        UIntN n3 = n1 & n2;
        assert n3 == 0x00A2_A0A2_A0A0_A0A0;
    }

    // ----- Op tests (Complement ~) ---------------------------------------------------------------

    void testUIntNComplement() {
        UIntN value1 = 0;
        UIntN value2 = 0x5;
        value1 = ~value2;
        assert value1 == 2;

        value2 = 0x5B;
        value1 = ~value2;
        assert value1 == 36;

        value2 = 0x5B5B;
        value1 = ~value2;
        assert value1 == 0x24A4;
    }

    // ----- Op tests (Inc ++) ---------------------------------------------------------------------

    void testUIntNOpInc() {
        UIntN n = 1234;
        n++;
        assert n == 1235;
    }

    void testUIntNOpPreInc() {
        UIntN n1 = 1234;
        UIntN n2 = ++n1;
        assert n1 == 1235;
        assert n2 == 1235;
    }

    void testUIntNOpPostInc() {
        UIntN n1 = 1234;
        UIntN n2 = n1++;
        assert n1 == 1235;
        assert n2 == 1234;
    }

    // ----- Op tests (Dec --) ---------------------------------------------------------------------

    void testUIntNOpDec() {
        UIntN n = 1234;
        n--;
        assert n == 1233;
    }

    void testUIntNOpPreDec() {
        UIntN n1 = 1234;
        UIntN n2 = --n1;
        assert n1 == 1233;
        assert n2 == 1233;
    }

    void testUIntNOpPostDec() {
        UIntN n1 = 1234;
        UIntN n2 = n1--;
        assert n1 == 1233;
        assert n2 == 1234;
    }

    void testUIntNOpDecZero() {
        UIntN n = 0;
        try {
            n--;
            assert as "Should have failed";
        } catch (OutOfBounds e) {
            // expected
        }
    }

    // ----- Op tests (divide) ---------------------------------------------------------------------

    void testUIntNOpDiv() {
        UIntN n = 1234;
        UIntN n2 = n / 10;
        assert n2 == 123;
    }

    void testUIntNOpDivInPlace() {
        UIntN n = 709551616;
        n /= 10;
        assert n == 70955161;
    }

    // ----- Op tests (div/rem) --------------------------------------------------------------------

    void testUIntNOpDivRem() {
        UIntN   n1 = 123;
        UIntN   n2 = 10;
        (UIntN quotient, UIntN remainder) = n1/% n2;
        assert quotient == 12;
        assert remainder == 3;
    }

    // ----- Op tests (modulus) --------------------------------------------------------------------

    void testUIntNOpMod() {
        UIntN n = 1234;
        UIntN n2 = n % 10;
        assert n2 == 4;
    }

    // ----- Op tests (multiply) -------------------------------------------------------------------

    void testUIntNOpMultiply() {
        UIntN n = 1234;
        UIntN n2 = n * 10;
        assert n2 == 12340;
    }

    void testUIntNOpMultiplyInPlace() {
        UIntN n = 709551616;
        n *= 10;
        assert n == 7095516160;
    }

    // ----- Op tests (logical Or) -----------------------------------------------------------------

    void testUIntNOpOr() {
        UIntN n1 = 0x00F2_F0F2_F0F0_F0F0;
        UIntN n2 = 0x0AA0_AAAA_AAAA_AAAA;
        UIntN n3 = n1 | n2;
        assert n3 == 0x0AF2_FAFA_FAFA_FAFA;
    }

    void testUIntNOpOrInPlace() {
        UIntN n = 0x00F2_F0F2_F0F0_F0F0;
        n |= 0x0AA0_AAAA_AAAA_AAAA;
        assert n == 0x0AF2_FAFA_FAFA_FAFA;
    }

    // ----- Op tests (Retain LSB) -----------------------------------------------------------------

    void testUIntNRetainLSBits() {
        UIntN n1 = 0xABCD_1234;
        Int  n2 = 16;
        UIntN n3 = n1.retainLSBits(n2);
        assert n3 == 0x1234;
    }

    void testUIntNRetainLSBitsAllBits() {
        UIntN n1 = 0xABCD_1234;
        Int  n2 = n1.bitLength;
        UIntN n3 = n1.retainLSBits(n2);
        assert n3 == n1;
    }

    void testUIntNRetainLSBitsLargerThanBits() {
        UIntN n1 = 0xABCD_1234;
        Int  n2 = n1.bitLength + 10;
        UIntN n3 = n1.retainLSBits(n2);
        assert n3 == n1;
    }

    void testUIntNRetainLSBitsZero() {
        UIntN n1 = 0xABCD_1234;
        Int  n2 = 0;
        UIntN n3 = n1.retainLSBits(n2);
        assert n3 == 0;
    }

    void testUIntNRetainLSBitsNegative() {
        UIntN n1 = 0xABCD_1234;
        Int  n2 = -16;
        UIntN n3 = n1.retainLSBits(n2);
        assert n3 == 0;
    }

    // ----- Op tests (Retain MSB) -----------------------------------------------------------------

    void testUIntNRetainMSBits() {
        UIntN n1 = 0xABCD_1234;
        Int  n2 = 16;
        UIntN n3 = n1.retainMSBits(n2);
        assert n3 == 0xABCD_0000;
    }

    void testUIntNRetainMSBitsAllBits() {
        UIntN n1 = 0xABCD_1234;
        Int  n2 = n1.bitLength;
        UIntN n3 = n1.retainMSBits(n2);
        assert n3 == n1;
    }

    void testUIntNRetainMSBitsLargerThanBits() {
        UIntN n1 = 0xABCD_1234;
        Int  n2 = n1.bitLength + 10;
        UIntN n3 = n1.retainMSBits(n2);
        assert n3 == n1;
    }

    void testUIntNRetainMSBitsZero() {
        UIntN n1 = 0xABCD_1234;
        Int  n2 = 0;
        UIntN n3 = n1.retainMSBits(n2);
        assert n3 == 0;
    }

    void testUIntNRetainMSBitsNegative() {
        UIntN n1 = 0xABCD_1234;
        Int  n2 = -16;
        UIntN n3 = n1.retainMSBits(n2);
        assert n3 == 0;
    }

    // ----- Op tests (Rotate left) ----------------------------------------------------------------

    void testUIntNRotateLeft() {
        UIntN n1 = 0xFEDC_1234_5678_ABCD;
        UIntN n2 = n1.rotateLeft(16);
        assert n2 == 0x1234_5678_ABCD_FEDC;
    }

    void testUIntNZeroRotateLeft() {
        UIntN n1 = 0;
        UIntN n2 = n1.rotateLeft(16);
        assert n2 == 0;
    }

    void testUIntNRotateLeftByZero() {
        UIntN n1 = 0xFEDC_1234_5678_ABCD;
        UIntN n2 = n1.rotateLeft(0);
        assert n2 == n1;
    }

    void testUIntNRotateLeftByBitLength() {
        UIntN n1 = 0xFEDC_1234_5678_ABCD;
        UIntN n2 = n1.rotateLeft(64);
        assert n2 == n1;
    }

    // ----- Op tests (Rotate right) ---------------------------------------------------------------

    void testUIntNRotateRight() {
        UIntN n1 = 0xFEDC_1234_5678_ABCD;
        UIntN n2 = n1.rotateRight(16);
        assert n2 == 0xABCD_FEDC_1234_5678;
    }

    void testUIntNZeroRotateRight() {
        UIntN n1 = 0;
        UIntN n2 = n1.rotateRight(16);
        assert n2 == 0;
    }

    void testUIntNRotateRightByZero() {
        UIntN n1 = 0xFEDC_1234_5678_ABCD;
        UIntN n2 = n1.rotateRight(0);
        assert n2 == n1;
    }

    void testUIntNRotateRightByBitLength() {
        UIntN n1 = 0xFEDC_1234_5678_ABCD;
        UIntN n2 = n1.rotateRight(64);
        assert n2 == n1;
    }

    // ----- Op tests (Shift left <<) --------------------------------------------------------------

    void testUIntNOpShiftLeft() {
        UIntN n = 0x1142_F0F2_F0F0_F0F0;
        UIntN n2 = n << 8;
        assert n2 == 0x0011_42F0_F2F0_F0F0_F000;
    }

    void testUIntNOpShiftLeftZero() {
        UIntN n = 0x00F2_F0F2_F0F0_F0F0;
        UIntN n2 = n << 0;
        assert n2 == n;
    }

    void testUIntNOpShiftLeftMinus4() {
        UIntN n = 0x10;
        UIntN n2 = n << -4; // same a right shift 4
        assert n2 == 0x01;
    }

    // ----- Op tests (Shift right >>) -------------------------------------------------------------

    void testUIntNOpShiftRight() {
        UIntN n =   0x1142_F0F2_F0F0_F0F0;
        UIntN n2 = n >> 8;
        assert n2 == 0x0011_42F0_F2F0_F0F0;
    }

    void testUIntNOpShiftRightZero() {
        UIntN n =   0x1142_F0F2_F0F0_F0F0;
        UIntN n2 = n >> 0;
        assert n2 == 0x1142_F0F2_F0F0_F0F0;
    }

    void testUIntNOpShiftRightMinus4() {
        UIntN n = 0x01;
        UIntN n2 = n >> -4; // same as left shift 4
        assert n2 == 0x10;
    }

    // ----- Op tests (Unsigned shift right >>>) ---------------------------------------------------

    void testUIntNOpUnsignedShiftRight() {
        UIntN n =   0x1142_F0F2_F0F0_F0F0;
        UIntN n2 = n >>> 8;
        assert n2 == 0x0011_42F0_F2F0_F0F0;
    }

    void testUIntNOpUnsignedShiftRightZero() {
        UIntN n =   0x1142_F0F2_F0F0_F0F0;
        UIntN n2 = n >>> 0;
        assert n2 == 0x1142_F0F2_F0F0_F0F0;
    }

    void testUIntNOpUnsignedShiftRightMinus4() {
        UIntN n = 0x01;
        UIntN n2 = n >>> -4; // same as left shift 4
        assert n2 == 0x10;
    }

    // ----- Op tests (logical Xor) ----------------------------------------------------------------

    void testUIntNOpXor() {
        UIntN n1 = 0x00F2_F0F2_F0F0_F0F0;
        UIntN n2 = 0x0AA0_AAAA_AAAA_AAAA;
        UIntN n3 = n1 ^ n2;
        assert n3 == 0x0A52_5A58_5A5A_5A5A;
    }

    void testUIntNOpXorInPlace() {
        UIntN n = 0x00F2_F0F2_F0F0_F0F0;
        n ^= 0x0AA0_AAAA_AAAA_AAAA;
        assert n == 0x0A52_5A58_5A5A_5A5A;
    }

    // ----- Stringable tests ----------------------------------------------------------------------

    void testAppendTo() {
        assert callAppendTo(0) == "0";
        assert callAppendTo(1) == "1";
        assert callAppendTo(10) == "10";
        assert callAppendTo(1000) == "1000";
        assert callAppendTo(198765) == "198765";
    }

    String callAppendTo(UIntN n) {
        StringBuffer buf = new StringBuffer();
        n.appendTo(buf);
        return buf.toString();
    }

    void testEstimateStringLength() {
        UIntN n = 0;
        assert n.estimateStringLength() == 1;
        n = 1;
        assert n.estimateStringLength() == 1;
        n = 10;
        assert n.estimateStringLength() == 2;
        n = 1000;
        assert n.estimateStringLength() == 4;
        n = 9876543;
        assert n.estimateStringLength() == 7;
    }

    void testUIntNAbsPositive() {
        UIntN n1 = 10;
        UIntN n2 = n1.abs();
        assert n2 == 10;
    }

    void testUIntNAbsZero() {
        UIntN n1 = 0;
        UIntN n2 = n1.abs();
        assert n2 == 0;
    }

    void testUIntNPow() {
        UIntN n1 = 10;
        UIntN n2 = 2;
        UIntN n3 = n1.pow(n2);
        assert n3 == 100;
    }

    void testBitLength() {
        UIntN n = 0;
        assert n.bitLength == 0; // 0b0 is zero bits
        n = 1;
        assert n.bitLength == 1; // 0b1 is one bit
        n = 2;
        assert n.bitLength == 2; // 0b10 is two bits
        n = 0x4D2;
        assert n.bitLength == 11; // 0b100 is three bits + one byte for D2 == 11
        n = 0x499602D2;
        // Four bytes, the most significant byte (49) is seven bits, so 7 + (3 * 8)
        assert n.bitLength == 31;
        // Eight bytes, the most significant byte (AB) is eight bits, so 8 + (7 * 8)
        n = 0xAB54A98CEB1F0AD2;
        assert n.bitLength == 64;
    }

    void testByteLength() {
        UIntN n = 0;
        assert n.byteLength == 0;
        n = 1;
        assert n.byteLength == 1;
        n = 2;
        assert n.byteLength == 1;
        n = 1234;
        assert n.byteLength == 2;
        n = 1234567890;
        assert n.byteLength == 4;
        n = 12345678901234567890;
        assert n.byteLength == 8;
    }

    void testSigned() {
        UIntN n = 0;
        assert n.signed == False;
        n = 100;
        assert n.signed == False;
    }

    void testSign() {
        UIntN n = 0;
        assert n.sign == Zero;
        n = 100;
        assert n.sign == Positive;
    }

    void testNegative() {
        UIntN n = 0;
        assert n.negative == False;
        n = 100;
        assert n.negative == False;
    }

    void testFinite() {
        UIntN n = 0;
        assert n.finite == True;
        n = 100;
        assert n.finite == True;
    }

    void testInfinity() {
        UIntN n = 0;
        assert n.infinity == False;
        n = 100;
        assert n.infinity == False;
    }

    void testNaN() {
        UIntN n = 0;
        assert n.NaN == False;
        n = 100;
        assert n.NaN == False;
    }

    void testMagnitude() {
// TODO fix UIntN.magnitude
//        UIntN n = 0;
//        assert n.magnitude == 0;
//        n = 100;
//        assert n.magnitude == 100;
    }

    // ----- As Number tests -----------------------------------------------------------------------

    void testAbsAsNumberPositive() {
        UIntN  n1 = 10;
        Number n2 = absNumber(n1);
        assert n2.is(UIntN);
        assert n2 == 10;
    }

    void testAbsAsNumberZero() {
        UIntN  n1 = 0;
        Number n2 = absNumber(n1);
        assert n2.is(UIntN);
        assert n2 == 0;
    }

    Number absNumber(Number n) {
        return n.abs();
    }

    void testAddAsNumber() {
        UIntN n1 = 10;
        UIntN n2 = 25;
        Number n3 = addNumber(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 35;
    }

    Number addNumber(Number n1, Number n2) {
        return n1 + n2;
    }

    void testDivAsNumber() {
        UIntN  n1 = 123;
        UIntN  n2 = 10;
        Number n3 = divNumber(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 12;
    }

    Number divNumber(Number n1, Number n2) {
        return n1 / n2;
    }

    void testDivRemAsNumber() {
        UIntN  n1 = 123;
        UIntN  n2 = 10;
        (Number quotient, Number remainder) = divRemNumber(n1, n2);
        assert quotient.is(UIntN);
        assert quotient == 12;
        assert remainder.is(UIntN);
        assert remainder == 3;
    }

    (Number quotient, Number remainder) divRemNumber(Number n1, Number n2) {
        return n1 /% n2;
    }

    void testModAsNumber() {
        UIntN  n1 = 123;
        UIntN  n2 = 10;
        Number n3 = modNumber(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 3;
    }

    Number modNumber(Number n1, Number n2) {
        return n1 % n2;
    }

    void testMultiplyAsNumber() {
        UIntN  n1 = 12;
        UIntN  n2 = 10;
        Number n3 = multiplyNumber(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 120;
    }

    Number multiplyNumber(Number n1, Number n2) {
        return n1 * n2;
    }

    void testNegateAsNumber() {
// TODO needs UIntNUmber to be compiled by the JIT because the neg() method is on UIntNumber
//        UIntN  n1 = 1234;
//        try {
//            Number n2 = negateNumber(n1);
//            assert as "Should have failed";
//        } catch (Unsupported e) {
//            // expected
//        }
    }

    Number negateNumber(Number n) {
        return -n;
    }

    void testPowAsNumber() {
        UIntN  n1 = 10;
        UIntN  n2 = 2;
        Number n3 = powNumber(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 100;
    }

    Number powNumber(Number n1, Number n2) {
        return n1.pow(n2);
    }

    void testSubAsNumber() {
        UIntN  n1 = 30;
        UIntN  n2 = 25;
        Number n3 = subNumber(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 5;
    }

    Number subNumber(Number n1, Number n2) {
        return n1 - n2;
    }

    void testBitLengthAsNumber() {
        UIntN n = 0;
        assert bitLengthNumber(n) == 0; // 0b0 is zero bits
        n = 1;
        assert bitLengthNumber(n) == 1; // 0b1 is one bit
        n = 2;
        assert bitLengthNumber(n)== 2; // 0b10 is two bits
        n = 0x4D2;
        assert bitLengthNumber(n) == 11; // 0b100 is three bits + one byte for D2 == 11
        n = 0x499602D2;
        // Four bytes, the most significant byte (49) is seven bits, so 7 + (3 * 8)
        assert bitLengthNumber(n) == 31;
        // Eight bytes, the most significant byte (AB) is eight bits, so 8 + (7 * 8)
        n = 0xAB54A98CEB1F0AD2;
        assert bitLengthNumber(n) == 64;
    }

    Int bitLengthNumber(Number n) {
        return n.bitLength;
    }

    void testByteLengthAsNumber() {
        UIntN n = 0;
        assert byteLengthNumber(n) == 0;
        n = 1;
        assert byteLengthNumber(n) == 1;
        n = 2;
        assert byteLengthNumber(n) == 1;
        n = 1234;
        assert byteLengthNumber(n) == 2;
        n = 1234567890;
        assert byteLengthNumber(n) == 4;
        n = 12345678901234567890;
        assert byteLengthNumber(n) == 8;
    }

    Int byteLengthNumber(Number n) {
        return n.byteLength;
    }

    void testSignedAsNumber() {
        UIntN n = 0;
        assert isSignedNumber(n) == False;
        n = 100;
        assert isSignedNumber(n) == False;
    }

    Boolean isSignedNumber(Number n) {
        return n.signed;
    }

    void testSignAsNumber() {
        UIntN n = 0;
        assert numberSign(n) == Zero;
        n = 100;
        assert numberSign(n) == Positive;
    }

    Signum numberSign(Number n) {
        return n.sign;
    }

    void testNegativeAsNumber() {
        UIntN n = 0;
        assert isNegativeNumber(n) == False;
        n = 100;
        assert isNegativeNumber(n) == False;
    }

    Boolean isNegativeNumber(Number n) {
        return n.negative;
    }

    void testFiniteAsNumber() {
        UIntN n = 0;
        assert isNumberFinite(n) == True;
        n = 100;
        assert isNumberFinite(n) == True;
    }

    Boolean isNumberFinite(Number n) {
        return n.finite;
    }

    void testInfinityAsNumber() {
        UIntN n = 0;
        assert isNumberInfinity(n) == False;
        n = 100;
        assert isNumberInfinity(n) == False;
    }

    Boolean isNumberInfinity(Number n) {
        return n.infinity;
    }

    void testNaNAsNumber() {
        UIntN n = 0;
        assert isNumberNaN(n) == False;
        n = 100;
        assert isNumberNaN(n) == False;
    }

    Boolean isNumberNaN(Number n) {
        return n.NaN;
    }

    void testMagnitudeAsNumber() {
// TODO fix UIntN.magnitude
//        UIntN   n1 = 0;
//        Number n2 = getNumberMagnitude(n1);
//        assert n2.is(UIntN);
//        assert n2 == 0;
//
//        n1 = 100;
//        n2 = getNumberMagnitude(n1);
//        assert n2.is(UIntN);
//        assert n2 == 100;
    }

    Number getNumberMagnitude(Number n) {
     TODO fix UIntN.magnitude
//        return n.magnitude;
    }

    // ----- As IntNumber tests --------------------------------------------------------------------

    void testAbsAsIntNumberPositive() {
// TODO needs UIntNUmber to be compiled by the JIT because the abs() method is on UIntNumber
//        UIntN      n1 = 10;
//        IntNumber n2 = absIntNumber(n1);
//        assert n2.is(UIntN);
//        assert n2 == 10;
    }

    void testAbsAsIntNumberZero() {
// TODO needs UIntNUmber to be compiled by the JIT because the abs() method is on UIntNumber
//        UIntN      n1 = 0;
//        IntNumber n2 = absIntNumber(n1);
//        assert n2.is(UIntN);
//        assert n2 == 0;
    }

    IntNumber absIntNumber(IntNumber n) {
        return n.abs();
    }

    void testAddAsIntNumber() {
        UIntN n1 = 1000;
        UIntN n2 = 2500;
        IntNumber n3 = addIntNumber(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 3500;
    }

    IntNumber addIntNumber(IntNumber n1, IntNumber n2) {
        return n1 + n2;
    }

    void testAndAsIntNumber() {
        UIntN      n1 = 0x00F2_F0F2_F0F0_F0F0;
        UIntN      n2 = 0x0AAA_AAAA_AAAA_AAAA;
        IntNumber n3 = andIntNumber(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 0x00A2_A0A2_A0A0_A0A0;
    }

    IntNumber andIntNumber(IntNumber n1, IntNumber n2) {
        return n1 & n2;
    }

    void testComplementAsIntNumber() {
        UIntN     n1 = 0x5B5B;
        IntNumber n2 = complementIntNumber(n1);
        assert n2.is(UIntN);
        assert n2 == 0x24A4;
    }

    IntNumber complementIntNumber(IntNumber n) {
        return ~n;
    }

    void testDecAsIntNumber() {
        UIntN      n1 = 1236;
        IntNumber n2 = decIntNumber(n1);
        assert n2.is(UIntN);
        assert n2 == 1235;
    }

    IntNumber decIntNumber(IntNumber n) {
        return --n;
    }

    void testDivAsIntNumber() {
        UIntN      n1 = 1234;
        UIntN      n2 = 10;
        IntNumber n3 = divNumber(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 123;
    }

    IntNumber divNumber(IntNumber n1, IntNumber n2) {
        return n1 / n2;
    }

    void testDivRemAsIntNumber() {
        UIntN  n1 = 123;
        UIntN  n2 = 10;
        (IntNumber quotient, IntNumber remainder) = divRemIntNumber(n1, n2);
        assert quotient.is(UIntN);
        assert quotient == 12;
        assert remainder.is(UIntN);
        assert remainder == 3;
    }

    (IntNumber quotient, IntNumber remainder) divRemIntNumber(IntNumber n1, IntNumber n2) {
        return n1 /% n2;
    }

    void testIncAsIntNumber() {
        UIntN     n1 = 1234;
        IntNumber n2 = incIntNumber(n1);
        assert n2.is(UIntN);
        assert n2 == 1235;
    }

    IntNumber incIntNumber(IntNumber n) {
        return ++n;
    }

    void testModAsIntNumber() {
        UIntN     n1 = 1234;
        UIntN     n2 = 10;
        IntNumber n3 = modIntNumber(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 4;
    }

    IntNumber modIntNumber(IntNumber n1, IntNumber n2) {
        return n1 % n2;
    }

    void testMultiplyAsIntNumber() {
        UIntN     n1 = 12;
        UIntN     n2 = 10;
        IntNumber n3 = multiplyIntNumber(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 120;
    }

    IntNumber multiplyIntNumber(IntNumber n1, IntNumber n2) {
        return n1 * n2;
    }

    void testNegateAsIntNumber() {
// TODO needs UIntNUmber to be compiled by the JIT because the neg() method is on UIntNumber
//        UIntN n1 = 1234;
//        try {
//            IntNumber n2 = negateIntNumber(n1);
//            assert as "Should have failed";
//        } catch (Unsupported e) {
//            // expected
//        }
    }

    IntNumber negateIntNumber(IntNumber n) {
        return -n;
    }

    void testOrAsIntNumber() {
        UIntN     n1 = 0x00F2_F0F2_F0F0_F0F0;
        UIntN     n2 = 0x0AA0_AAAA_AAAA_AAAA;
        IntNumber n3 = orIntNumber(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 0x0AF2_FAFA_FAFA_FAFA;
    }

    IntNumber orIntNumber(IntNumber n1, IntNumber n2) {
        return n1 | n2;
    }

    void testPowAsIntNumber() {
        UIntN     n1 = 10;
        UIntN     n2 = 2;
        IntNumber n3 = powIntNumber(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 100;
    }

    IntNumber powIntNumber(IntNumber n1, IntNumber n2) {
        return n1.pow(n2);
    }

    void testRetainLSBitsAsIntNumber() {
        UIntN     n1 = 0xABCD_1234;
        Int       n2 = 16;
        IntNumber n3 = retainIntNumberLSBits(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 0x1234;
    }

    void testRetainLSBitsAllBitsAsIntNumber() {
        UIntN     n1 = 0xABCD_1234;
        Int       n2 = n1.bitLength;
        IntNumber n3 = retainIntNumberLSBits(n1, n2);
        assert n3.is(UIntN);
        assert n3 == n1;
    }

    void testRetainLSBitsLargerThanBitsAsIntNumber() {
        UIntN     n1 = 0xABCD_1234;
        Int       n2 = n1.bitLength + 10;
        IntNumber n3 = retainIntNumberLSBits(n1, n2);
        assert n3.is(UIntN);
        assert n3 == n1;
    }

    void testRetainLSBitsZeroAsIntNumber() {
        UIntN     n1 = 0xABCD_1234;
        Int       n2 = 0;
        IntNumber n3 = retainIntNumberLSBits(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 0;
    }

    void testRetainLSBitsNegativeAsIntNumber() {
        UIntN     n1 = 0xABCD_1234;
        Int       n2 = -16;
        IntNumber n3 = retainIntNumberLSBits(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 0;
    }

    IntNumber retainIntNumberLSBits(IntNumber n1, Int n2) {
        return n1.retainLSBits(n2);
    }

    void testRetainMSBitsAsIntNumber() {
        UIntN     n1 = 0xABCD_1234;
        Int       n2 = 16;
        IntNumber n3 = retainIntNumberMSBits(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 0xABCD_0000;
    }

    void testRetainMSBitsAllBitsAsIntNumber() {
        UIntN     n1 = 0xABCD_1234;
        Int       n2 = n1.bitLength;
        IntNumber n3 = retainIntNumberMSBits(n1, n2);
        assert n3.is(UIntN);
        assert n3 == n1;
    }

    void testRetainMSBitsLargerThanBitsAsIntNumber() {
        UIntN     n1 = 0xABCD_1234;
        Int       n2 = n1.bitLength + 10;
        IntNumber n3 = retainIntNumberMSBits(n1, n2);
        assert n3.is(UIntN);
        assert n3 == n1;
    }

    void testRetainMSBitsZeroAsIntNumber() {
        UIntN     n1 = 0xABCD_1234;
        Int       n2 = 0;
        IntNumber n3 = retainIntNumberMSBits(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 0;
    }

    void testRetainMSBitsNegativeAsIntNumber() {
        UIntN     n1 = 0xABCD_1234;
        Int       n2 = -16;
        IntNumber n3 = retainIntNumberMSBits(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 0;
    }

    IntNumber retainIntNumberMSBits(IntNumber n1, Int n2) {
        return n1.retainMSBits(n2);
    }

    void testRotateLeftAsIntNumber() {
        UIntN     n1 = 0xFEDC_1234_5678_ABCD;
        IntNumber n2 = rotateLeftIntNumber(n1, 16);
        assert n2.is(UIntN);
        assert n2 == 0x1234_5678_ABCD_FEDC;
    }

    IntNumber rotateLeftIntNumber(IntNumber n1, Int n2) {
        return n1.rotateLeft(n2);
    }

    void testRotateRightAsIntNumber() {
        UIntN     n1 = 0xFEDC_1234_5678_ABCD;
        IntNumber n2 = rotateRightIntNumber(n1, 16);
        assert n2.is(UIntN);
        assert n2 == 0xABCD_FEDC_1234_5678;
    }

    IntNumber rotateRightIntNumber(IntNumber n1, Int n2) {
        return n1.rotateRight(n2);
    }

    void testShiftLeftAsIntNumber() {
        UIntN     n1 = 0x1142_F0F2_F0F0_F0F0;
        Int       n2 = 8;
        IntNumber n3 = shiftLeftIntNumber(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 0x0011_42F0_F2F0_F0F0_F000;
                  // 0x0011_42F0_F2F0_F0F0_F0
    }

    IntNumber shiftLeftIntNumber(IntNumber n1, Int n2) {
        return n1 << n2;
    }

    void testShiftRightAsIntNumber() {
        UIntN     n1 = 0x1142_F0F2_F0F0_F0F0;
        Int       n2 = 8;
        IntNumber n3 = shiftRightIntNumber(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 0x0011_42F0_F2F0_F0F0;
    }

    IntNumber shiftRightIntNumber(IntNumber n1, Int n2) {
        return n1 >> n2;
    }

    void testSubAsIntNumber() {
        UIntN     n1 = 3000;
        UIntN     n2 = 2500;
        IntNumber n3 = subIntNumber(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 500;
    }

    IntNumber subIntNumber(IntNumber n1, IntNumber n2) {
        return n1 - n2;
    }

    void testUnsignedShiftRightAsIntNumber() {
        UIntN     n1 = 0x1142_F0F2_F0F0_F0F0;
        Int       n2 = 8;
        IntNumber n3 = unsignedShiftRightIntNumber(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 0x0011_42F0_F2F0_F0F0;
    }

    IntNumber unsignedShiftRightIntNumber(IntNumber n1, Int n2) {
        return n1 >>> n2;
    }

    void testXorAsIntNumber() {
        UIntN     n1 = 0x00F2_F0F2_F0F0_F0F0;
        UIntN     n2 = 0x0AA0_AAAA_AAAA_AAAA;
        IntNumber n3 = xorIntNumber(n1, n2);
        assert n3.is(UIntN);
        assert n3 == 0x0A52_5A58_5A5A_5A5A;
    }

    IntNumber xorIntNumber(IntNumber n1, IntNumber n2) {
        return n1 ^ n2;
    }

    void testBitLengthAsIntNumber() {
        UIntN n = 0;
        assert bitLengthIntNumber(n) == 0; // 0b0 is zero bits
        n = 1;
        assert bitLengthIntNumber(n) == 1; // 0b1 is one bit
        n = 2;
        assert bitLengthIntNumber(n)== 2; // 0b10 is two bits
        n = 0x4D2;
        assert bitLengthIntNumber(n) == 11; // 0b100 is three bits + one byte for D2 == 11
        n = 0x499602D2;
        // Four bytes, the most significant byte (49) is seven bits, so 7 + (3 * 8)
        assert bitLengthIntNumber(n) == 31;
        // Eight bytes, the most significant byte (AB) is eight bits, so 8 + (7 * 8)
        n = 0xAB54A98CEB1F0AD2;
        assert bitLengthIntNumber(n) == 64;
    }

    Int bitLengthIntNumber(IntNumber n) {
        return n.bitLength;
    }

    void testByteLengthAsIntNumber() {
        UIntN n = 0;
        assert byteLengthIntNumber(n) == 0;
        n = 1;
        assert byteLengthIntNumber(n) == 1;
        n = 2;
        assert byteLengthIntNumber(n) == 1;
        n = 1234;
        assert byteLengthIntNumber(n) == 2;
        n = 1234567890;
        assert byteLengthIntNumber(n) == 4;
        n = 12345678901234567890;
        assert byteLengthIntNumber(n) == 8;
    }

    Int byteLengthIntNumber(IntNumber n) {
        return n.byteLength;
    }

    void testSignedAsIntNumber() {
        UIntN n = 0;
        assert isSignedIntNumber(n) == False;
        n = 100;
        assert isSignedIntNumber(n) == False;
    }

    Boolean isSignedIntNumber(IntNumber n) {
        return n.signed;
    }

    void testSignAsIntNumber() {
        UIntN n = 0;
        assert intNumberSign(n) == Zero;
        n = 100;
        assert intNumberSign(n) == Positive;
    }

    Signum intNumberSign(IntNumber n) {
        return n.sign;
    }

    void testNegativeAsIntNumber() {
        UIntN n = 0;
        assert isNegativeIntNumber(n) == False;
        n = 100;
        assert isNegativeIntNumber(n) == False;
    }

    Boolean isNegativeIntNumber(IntNumber n) {
        return n.negative;
    }

    void testFiniteAsIntNumber() {
        UIntN n = 0;
        assert isIntNumberFinite(n) == True;
        n = 100;
        assert isIntNumberFinite(n) == True;
    }

    Boolean isIntNumberFinite(IntNumber n) {
        return n.finite;
    }

    void testInfinityAsIntNumber() {
        UIntN n = 0;
        assert isIntNumberInfinity(n) == False;
        n = 100;
        assert isIntNumberInfinity(n) == False;
    }

    Boolean isIntNumberInfinity(IntNumber n) {
        return n.infinity;
    }

    void testNaNAsIntNumber() {
        UIntN n = 0;
        assert isIntNumberNaN(n) == False;
        n = 100;
        assert isIntNumberNaN(n) == False;
    }

    Boolean isIntNumberNaN(IntNumber n) {
        return n.NaN;
    }

    void testMagnitudeAsIntNumber() {
// TODO fix UIntN.magnitude
//        UIntN   n1 = 0;
//        IntNumber n2 = getIntNumberMagnitude(n1);
//        assert n2.is(UIntN);
//        assert n2 == 0;
//
//        n1 = 100;
//        n2 = getIntNumberMagnitude(n1);
//        assert n2.is(UIntN);
//        assert n2 == 100;
    }

    IntNumber getIntNumberMagnitude(IntNumber n) {
     TODO fix UIntN.magnitude
//        return n.magnitude;
    }

    // ----- As Sequential tests -------------------------------------------------------------------

    void testDecAsSequential() {
        UIntN      n1 = 1236;
        Sequential n2 = decSequential(n1);
        assert n2.is(UIntN);
        assert n2 == 1235;
    }

    Sequential decSequential(Sequential n) {
        return n.prevValue();
    }

    void testIncAsSequential() {
        UIntN      n1 = 1234;
        Sequential n2 = incSequential(n1);
        assert n2.is(UIntN);
        assert n2 == 1235;
    }

    Sequential incSequential(Sequential n) {
        return n.nextValue();
    }
}
