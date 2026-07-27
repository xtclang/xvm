class IntNTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running IntN Tests >>>>");

        // Comparison tests
        testIntNCompareEq();
        testIntNCompareGe();
        testIntNCompareGt();
        testIntNCompareLe();
        testIntNCompareLt();

        // Field tests
        testIntNAsField();
        testIntNAsNullableField();
        testIntNAsNullableFieldNull();

        // Constant field and constructor tests
        testIntNAsConstField();
        testNullableIntAsConstField();
        testNullableIntAsConstFieldNull();

        // Method parameter tests
        testIntNAsParam();
        testIntNAsNullableParam();
        testIntNAsNullableParamNull();
        testIntNAsMultiParams();
        testIntNAsMultiNullableParams();

        // Method return tests
        testIntNReturn();
        testIntNConditionalReturn();
        testIntNReturnStringIntN();
        testIntNReturnIntNInt();
        testIntNReturnTwoIntN();
        testNullableIntReturn();
        testNullableIntAsIntReturn();
        testIntAsNullableIntReturn();
        testNullableIntConditionalReturn();

        // GP/IP Op tests
        // Add
        testIntNOpAdd();
        testIntNOpAddInPlace();
        testIntNOpAddFirstNegative();
        testIntNOpAddSecondNegative();
        testIntNOpAddBothNegative();
        testIntNPropertyAdd();
        // And
        testIntNOpAnd();
        // Complement
        testIntNComplement();
        // Inc
        testIntNOpInc();
        testIntNOpPreInc();
        testIntNOpPostInc();
        testIntNOpIncNegative();
        // Dec
        testIntNOpDec();
        testIntNOpPreDec();
        testIntNOpPostDec();
        testIntNOpDecNegative();
        // Div
        testIntNOpDiv();
        testIntNOpDivInPlace();
        // DivRem
        testIntNOpDivRem();
        // Mod
        testIntNOpMod();
        // Multiply
        testIntNOpMultiply();
        testIntNOpMultiply();
        testIntNOpMultiplyInPlace();
        // Negate
        testIntNOpNegate();
        // Or
        testIntNOpOr();
        testIntNOpOrInPlace();
        // Rotate
        testIntNRotateLeft();
        testIntNZeroRotateLeft();
        testIntNRotateLeftByZero();
        testIntNRotateLeftByBitLength();
        testIntNRotateRight();
        testIntNZeroRotateRight();
        testIntNRotateRightByZero();
        testIntNRotateRightByBitLength();
        // Retain Bits
        testIntNRetainLSBits();
        testIntNRetainLSBitsAllBits();
        testIntNRetainLSBitsLargerThanBits();
        testIntNRetainLSBitsZero();
        testIntNRetainLSBitsNegative();
        testIntNRetainMSBits();
        testIntNRetainMSBitsAllBits();
        testIntNRetainMSBitsLargerThanBits();
        testIntNRetainMSBitsZero();
        testIntNRetainMSBitsNegative();
        // Shl
        testIntNOpShiftLeft();
        testIntNOpShiftLeftZero();
        testIntNOpShiftLeftMinus4();
        // Shr
        testIntNOpShiftRight();
        testIntNOpShiftRightNegative();
        testIntNOpShiftRightZero();
        testIntNOpShiftRightMinus4();
        // Sub
        testIntNOpSubBig();
        testIntNOpSub();
        testIntNOpSubFirstNegative();
        testIntNOpSubSecondNegative();
        testIntNOpSubBothNegative();
        // Ushr
        testIntNOpUnsignedShiftRight();
        testIntNOpUnsignedShiftRightNegative();
        testIntNOpUnsignedShiftRightZero();
        testIntNOpUnsignedShiftRightMinus4();
        // Xor
        testIntNOpXor();
        testIntNOpXorInPlace();

        // Misc
        testIntNAbsPositive();
        testIntNAbsNegative();
        testIntNAbsZero();
        testIntNPow();
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
        testAbsAsNumberNegative();
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
        testAbsAsIntNumberNegative();
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

        console.print("<<<<< Finished IntN Tests tests >>>>>");
    }

    // ----- comparison tests ----------------------------------------------------------------------

    void testIntNCompareEq() {
        IntN n = 1234;
        assert n == 1234;
    }

    void testIntNCompareGe() {
        IntN n = 1234;
        assert n >= 1000;
        assert n >= 1234;
    }

    void testIntNCompareGt() {
        IntN n = 1234;
        assert n > 1233;
    }

    void testIntNCompareLe() {
        IntN n = 1234;
        assert n <= 1235;
        assert n <= 1234;
    }

    void testIntNCompareLt() {
        IntN n = 1234;
        assert n < 1235;
    }

    // ----- field tests ---------------------------------------------------------------------------

    void testIntNAsField() {
        IntNAsField n = new IntNAsField();
        assert n.field == 1234;
    }

    static class IntNAsField {
        IntN field = 1234;
    }

    void testIntNAsNullableField() {
        IntNAsNullableField n = new IntNAsNullableField();
        assert n.field == 9876;
    }

    static class IntNAsNullableField {
        IntN? field = 9876;
    }

    void testIntNAsNullableFieldNull() {
        IntNNullField n = new IntNNullField();
        assert n.field == Null;
    }

    static class IntNNullField {
        IntN? field = Null;
    }

    void testIntNAsConstField() {
        IntN         n = 1234;
        NumberHolder h = new NumberHolder(n);
        assert h.n == 1234;
    }

    static const NumberHolder(IntN n = 0) {
    }

    void testNullableIntAsConstField() {
        IntN                  n = 9876543;
        NullableNumberHolder h = new NullableNumberHolder(n);
        assert h.n == n;
    }

    void testNullableIntAsConstFieldNull() {
        NullableNumberHolder h = new NullableNumberHolder(Null);
        assert h.n == Null;
    }

    static const NullableNumberHolder(IntN? n = Null) {
    }

    // ----- parameter tests -----------------------------------------------------------------------

    void testIntNAsParam() {
        IntN n = 1234;
        IntNParam(n);
    }

    void IntNParam(IntN n) {
        assert n == 1234;
    }

    void testIntNAsNullableParam() {
        IntN n = 1234;
        Boolean isNull = IntNNullableParam(n);
        assert isNull == False;
    }

    void testIntNAsNullableParamNull() {
        Boolean isNull = IntNNullableParam(Null);
        assert isNull == True;
    }

    Boolean IntNNullableParam(IntN? n) {
        if (n.is(IntN)) {
            assert n == 1234;
            return False;
        }
        return True;
    }

    void testIntNAsMultiParams() {
        IntNMultiParams(1234, 98765432);
    }

    void IntNMultiParams(IntN n1, IntN n2) {
        assert n1 == 1234;
        assert n2 == 98765432;
    }

    void testIntNAsMultiNullableParams() {
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

    (Boolean, Boolean) IntNMultiNullableParams(IntN? n1, IntN? n2) {
        Boolean b1 = False;
        Boolean b2 = False;
        if (n1.is(IntN)) {
            assert n1 == 1234;
            b1 = True;
        }
        if (n2.is(IntN)) {
            assert n2 == 12349876;
            b2 = True;
        }
        return b1, b2;
    }

    // ----- return tests --------------------------------------------------------------------------

    void testIntNReturn() {
        IntN n = returnIntN();
        assert n == 1234;
    }

    IntN returnIntN() {
        IntN n = 1234;
        return n;
    }

    void testIntNConditionalReturn() {
        assert IntN n := returnConditionalIntN();
        assert n == 1234;
    }

    conditional IntN returnConditionalIntN() {
        IntN n = 1234;
        return True, n;
    }

    void testIntNReturnStringIntN() {
        (String s, IntN n) = returnStringIntN();
        assert s == "Foo";
        assert n == 9876;
    }

    (String, IntN) returnStringIntN() {
        IntN n = 9876;
        return "Foo", n;
    }

    void testIntNReturnIntNInt() {
        (IntN n, IntN i) = returnIntNInt();
        assert n == 9999;
        assert i == 19;
    }

    (IntN, IntN) returnIntNInt() {
        IntN n = 9999;
        return n, 19;
    }

    void testIntNReturnTwoIntN() {
        (IntN n1, IntN n2) = returnTwoIntN();
        assert n1 == 4567;
        assert n2 == 1290;
    }

    (IntN, IntN) returnTwoIntN() {
        IntN n1 = 4567;
        IntN n2 = 1290;
        return n1, n2;
    }

    void testNullableIntReturn() {
        IntN? n = returnNullableInt(True);
        assert n == 987654321;
        n = returnNullableInt(False);
        assert n == Null;
    }

    IntN? returnNullableInt(Boolean b) {
        IntN n = 987654321;
        if (b) {
            return n;
        }
        return Null;
    }

    void testNullableIntAsIntReturn() {
        IntN n = returnNullableIntAsInt();
        assert n == 98987676;
    }

    IntN returnNullableIntAsInt() {
        IntN? n = 98987676;
        return n;
    }

    void testIntAsNullableIntReturn() {
        IntN? n = returnIntAsNullableInt();
        assert n == 98987676;
    }

    IntN? returnIntAsNullableInt() {
        IntN n = 98987676;
        return n;
    }

    void testNullableIntConditionalReturn() {
        assert IntN? n := returnConditionalNullableInt(0);
        assert n == 191919;
        assert n := returnConditionalNullableInt(1);
        assert n == Null;
        assert returnConditionalNullableInt(2) == False;
    }

    conditional IntN? returnConditionalNullableInt(IntN i) {
        IntN? n = 191919;
        if (i == 0) {
            return True, n;
        }
        if (i == 1) {
            return True, Null;
        }
        return False;
    }

    // ----- Op tests (Add) ------------------------------------------------------------------------

    void testIntNOpAddBig() {
        IntN n1 = 709551616;
        IntN n2 = 709200000;
        IntN n3 = n1 + n2;
        assert n3 == 147418751616;
    }

    void testIntNOpAdd() {
        IntN n1 = 1000;
        IntN n2 = 19;
        IntN n3 = n1 + n2;
        assert n3 == 1019;
    }

    void testIntNOpAddInPlace() {
        IntN n1 = 709551616;
        n1 += 709200000;
        assert n1 == 1418751616;
    }

    void testIntNOpAddFirstNegative() {
        IntN n1 = -709551616;
        IntN n2 = 709200000;
        IntN n3 = n1 + n2;
        assert n3 == -351616;
    }

    void testIntNOpAddSecondNegative() {
        IntN n1 = 709551616;
        IntN n2 = -709200000;
        IntN n3 = n1 + n2;
        assert n3 == 351616;
    }

    void testIntNOpAddBothNegative() {
        IntN n1 = -709551616;
        IntN n2 = -709200000;
        IntN n3 = n1 + n2;
        assert n3 == -1418751616;
    }

    void testIntNPropertyAdd() {
        IntNAsField test = new IntNAsField();
        test.field = 1000;
        IntN n2 = 19;
        IntN n3 = test.field + n2;
        assert n3 == 1019;
        test.field = test.field + 100;
        assert test.field == 1100;
    }

    // ----- Op tests (Sub) ------------------------------------------------------------------------

    void testIntNOpSubBig() {
        IntN n1 = 709551616;
        IntN n2 = 709200000;
        IntN n3 = n1 - n2;
        assert n3 == 351616;
    }

    void testIntNOpSub() {
        IntN n1 = 1000;
        IntN n2 = 19;
        IntN n3 = n1 - n2;
        assert n3 == 981;
    }

    void testIntNOpSubInPlace() {
        IntN n1 = 709551616;
        n1 -= 709200000;
        assert n1 == 351616;
    }

    void testIntNOpSubFirstNegative() {
        IntN n1 = -709551616;
        IntN n2 = 709200000;
        IntN n3 = n1 - n2;
        assert n3 == -1418751616;
    }

    void testIntNOpSubSecondNegative() {
        IntN n1 = 709551616;
        IntN n2 = -709200000;
        IntN n3 = n1 - n2;
        assert n3 == 1418751616;
    }

    void testIntNOpSubBothNegative() {
        IntN n1 = -709551616;
        IntN n2 = -709200000;
        IntN n3 = n1 - n2;
        assert n3 == -351616;
    }

    // ----- Op tests (logical And) ----------------------------------------------------------------

    void testIntNOpAnd() {
        IntN n1 = 0x00F2_F0F2_F0F0_F0F0;
        IntN n2 = 0x0AAA_AAAA_AAAA_AAAA;
        IntN n3 = n1 & n2;
        assert n3 == 0x00A2_A0A2_A0A0_A0A0;
    }

    // ----- Op tests (Complement ~) ---------------------------------------------------------------

    void testIntNComplement() {
        IntN value1 = 0;
        IntN value2 = 0x5ABC5432;
        value1 = ~value2;
        assert value1 == -1522291763;
    }

    // ----- Op tests (Inc ++) ---------------------------------------------------------------------

    void testIntNOpInc() {
        IntN n = 1234;
        n++;
        assert n == 1235;
    }

    void testIntNOpPreInc() {
        IntN n1 = 1234;
        IntN n2 = ++n1;
        assert n1 == 1235;
        assert n2 == 1235;
    }

    void testIntNOpPostInc() {
        IntN n1 = 1234;
        IntN n2 = n1++;
        assert n1 == 1235;
        assert n2 == 1234;
    }

    void testIntNOpIncNegative() {
        IntN n = -709551616;
        n++;
        assert n == -709551615;
    }

    // ----- Op tests (Dec --) ---------------------------------------------------------------------

    void testIntNOpDec() {
        IntN n = 1234;
        n--;
        assert n == 1233;
    }

    void testIntNOpPreDec() {
        IntN n1 = 1234;
        IntN n2 = --n1;
        assert n1 == 1233;
        assert n2 == 1233;
    }

    void testIntNOpPostDec() {
        IntN n1 = 1234;
        IntN n2 = n1--;
        assert n1 == 1233;
        assert n2 == 1234;
    }

    void testIntNOpDecNegative() {
        IntN n = -709551616;
        n--;
        assert n == -709551617;
    }

    // ----- Op tests (divide) ---------------------------------------------------------------------

    void testIntNOpDiv() {
        IntN n = 1234;
        IntN n2 = n / 10;
        assert n2 == 123;
    }

    void testIntNOpDivInPlace() {
        IntN n = 709551616;
        n /= 10;
        assert n == 70955161;
    }

    // ----- Op tests (div/rem) --------------------------------------------------------------------

    void testIntNOpDivRem() {
// TODO JIT DivRem support
//        IntN   n1 = 123;
//        IntN   n2 = 10;
//        (IntN quotient, IntN remainder) = n1/% n2;
//        assert quotient == 12;
//        assert remainder == 3;
    }

    // ----- Op tests (modulus) --------------------------------------------------------------------

    void testIntNOpMod() {
        IntN n = 1234;
        IntN n2 = n % 10;
        assert n2 == 4;
    }

    // ----- Op tests (multiply) -------------------------------------------------------------------

    void testIntNOpMultiply() {
        IntN n = 1234;
        IntN n2 = n * 10;
        assert n2 == 12340;
    }

    void testIntNOpMultiplyInPlace() {
        IntN n = 709551616;
        n *= 10;
        assert n == 7095516160;
    }

    // ----- Op tests (negate) ---------------------------------------------------------------------

    void testIntNOpNegate() {
        IntN n = 1234;
        IntN n2 = -n;
        assert n2 == -1234;
    }

    // ----- Op tests (logical Or) -----------------------------------------------------------------

    void testIntNOpOr() {
        IntN n1 = 0x00F2_F0F2_F0F0_F0F0;
        IntN n2 = 0x0AA0_AAAA_AAAA_AAAA;
        IntN n3 = n1 | n2;
        assert n3 == 0x0AF2_FAFA_FAFA_FAFA;
    }

    void testIntNOpOrInPlace() {
        IntN n = 0x00F2_F0F2_F0F0_F0F0;
        n |= 0x0AA0_AAAA_AAAA_AAAA;
        assert n == 0x0AF2_FAFA_FAFA_FAFA;
    }

    // ----- Op tests (Retain LSB) -----------------------------------------------------------------

    void testIntNRetainLSBits() {
        IntN n1 = 0xABCD_1234;
        Int  n2 = 16;
        IntN n3 = n1.retainLSBits(n2);
        assert n3 == 0x1234;
    }

    void testIntNRetainLSBitsAllBits() {
        IntN n1 = 0xABCD_1234;
        Int  n2 = n1.bitLength;
        IntN n3 = n1.retainLSBits(n2);
        assert n3 == n1;
    }

    void testIntNRetainLSBitsLargerThanBits() {
        IntN n1 = 0xABCD_1234;
        Int  n2 = n1.bitLength + 10;
        IntN n3 = n1.retainLSBits(n2);
        assert n3 == n1;
    }

    void testIntNRetainLSBitsZero() {
        IntN n1 = 0xABCD_1234;
        Int  n2 = 0;
        IntN n3 = n1.retainLSBits(n2);
        assert n3 == 0;
    }

    void testIntNRetainLSBitsNegative() {
        IntN n1 = 0xABCD_1234;
        Int  n2 = -16;
        IntN n3 = n1.retainLSBits(n2);
        assert n3 == 0;
    }

    // ----- Op tests (Retain MSB) -----------------------------------------------------------------

    void testIntNRetainMSBits() {
        IntN n1 = 0xABCD_1234;
        Int  n2 = 16;
        IntN n3 = n1.retainMSBits(n2);
        assert n3 == 0xABCD_0000;
    }

    void testIntNRetainMSBitsAllBits() {
        IntN n1 = 0xABCD_1234;
        Int  n2 = n1.bitLength;
        IntN n3 = n1.retainMSBits(n2);
        assert n3 == n1;
    }

    void testIntNRetainMSBitsLargerThanBits() {
        IntN n1 = 0xABCD_1234;
        Int  n2 = n1.bitLength + 10;
        IntN n3 = n1.retainMSBits(n2);
        assert n3 == n1;
    }

    void testIntNRetainMSBitsZero() {
        IntN n1 = 0xABCD_1234;
        Int  n2 = 0;
        IntN n3 = n1.retainMSBits(n2);
        assert n3 == 0;
    }

    void testIntNRetainMSBitsNegative() {
        IntN n1 = 0xABCD_1234;
        Int  n2 = -16;
        IntN n3 = n1.retainMSBits(n2);
        assert n3 == 0;
    }

    // ----- Op tests (Rotate left) ----------------------------------------------------------------

    void testIntNRotateLeft() {
        IntN n1 = 0xFEDC_1234_5678_ABCD;
        IntN n2 = n1.rotateLeft(16);
        assert n2 == 0x1234_5678_ABCD_FEDC;
    }

    void testIntNZeroRotateLeft() {
        IntN n1 = 0;
        IntN n2 = n1.rotateLeft(16);
        assert n2 == 0;
    }

    void testIntNRotateLeftByZero() {
        IntN n1 = 0xFEDC_1234_5678_ABCD;
        IntN n2 = n1.rotateLeft(0);
        assert n2 == n1;
    }

    void testIntNRotateLeftByBitLength() {
        IntN n1 = 0xFEDC_1234_5678_ABCD;
        IntN n2 = n1.rotateLeft(64);
        assert n2 == n1;
    }

    // ----- Op tests (Rotate right) ---------------------------------------------------------------

    void testIntNRotateRight() {
        IntN n1 = 0xFEDC_1234_5678_ABCD;
        IntN n2 = n1.rotateRight(16);
        assert n2 == 0xABCD_FEDC_1234_5678;
    }

    void testIntNZeroRotateRight() {
        IntN n1 = 0;
        IntN n2 = n1.rotateRight(16);
        assert n2 == 0;
    }

    void testIntNRotateRightByZero() {
        IntN n1 = 0xFEDC_1234_5678_ABCD;
        IntN n2 = n1.rotateRight(0);
        assert n2 == n1;
    }

    void testIntNRotateRightByBitLength() {
        IntN n1 = 0xFEDC_1234_5678_ABCD;
        IntN n2 = n1.rotateRight(64);
        assert n2 == n1;
    }

    // ----- Op tests (Shift left <<) --------------------------------------------------------------

    void testIntNOpShiftLeft() {
        IntN n = 0x1142_F0F2_F0F0_F0F0;
        IntN n2 = n << 8;
        assert n2 == 0x0011_42F0_F2F0_F0F0_F000;
    }

    void testIntNOpShiftLeftZero() {
        IntN n = 0x00F2_F0F2_F0F0_F0F0;
        IntN n2 = n << 0;
        assert n2 == n;
    }

    void testIntNOpShiftLeftMinus4() {
        IntN n = 0x10;
        IntN n2 = n << -4; // same a right shift 4
        assert n2 == 0x01;
    }

    // ----- Op tests (Shift right >>) -------------------------------------------------------------

    void testIntNOpShiftRight() {
        IntN n =   0x1142_F0F2_F0F0_F0F0;
        IntN n2 = n >> 8;
        assert n2 == 0x0011_42F0_F2F0_F0F0;
    }

    void testIntNOpShiftRightNegative() {
        IntN n = -2000;
        IntN n2 = n >> 8;
        assert n2 == -8; // preserved sign bit
    }

    void testIntNOpShiftRightZero() {
        IntN n =   0x1142_F0F2_F0F0_F0F0;
        IntN n2 = n >> 0;
        assert n2 == 0x1142_F0F2_F0F0_F0F0;
    }

    void testIntNOpShiftRightMinus4() {
        IntN n = 0x01;
        IntN n2 = n >> -4; // same as left shift 4
        assert n2 == 0x10;
    }

    // ----- Op tests (Unsigned shift right >>>) ---------------------------------------------------

    void testIntNOpUnsignedShiftRight() {
        IntN n =   0x1142_F0F2_F0F0_F0F0;
        IntN n2 = n >>> 8;
        assert n2 == 0x0011_42F0_F2F0_F0F0;
    }

    void testIntNOpUnsignedShiftRightNegative() {
        IntN n = -2000; // 0xF830
        IntN n2 = n >>> 8;
        assert n2 == 0x00F8;
    }

    void testIntNOpUnsignedShiftRightZero() {
        IntN n =   0x1142_F0F2_F0F0_F0F0;
        IntN n2 = n >>> 0;
        assert n2 == 0x1142_F0F2_F0F0_F0F0;
    }

    void testIntNOpUnsignedShiftRightMinus4() {
        IntN n = 0x01;
        IntN n2 = n >>> -4; // same as left shift 4
        assert n2 == 0x10;
    }

    // ----- Op tests (logical Xor) ----------------------------------------------------------------

    void testIntNOpXor() {
        IntN n1 = 0x00F2_F0F2_F0F0_F0F0;
        IntN n2 = 0x0AA0_AAAA_AAAA_AAAA;
        IntN n3 = n1 ^ n2;
        assert n3 == 0x0A52_5A58_5A5A_5A5A;
    }

    void testIntNOpXorInPlace() {
        IntN n = 0x00F2_F0F2_F0F0_F0F0;
        n ^= 0x0AA0_AAAA_AAAA_AAAA;
        assert n == 0x0A52_5A58_5A5A_5A5A;
    }

    // ----- Stringable tests ----------------------------------------------------------------------

    void testAppendTo() {
        assert callAppendTo(0) == "0";
        assert callAppendTo(1) == "1";
        assert callAppendTo(-1) == "-1";
        assert callAppendTo(10) == "10";
        assert callAppendTo(-10) == "-10";
        assert callAppendTo(1000) == "1000";
        assert callAppendTo(-1000) == "-1000";
        assert callAppendTo(198765) == "198765";
        assert callAppendTo(-198765) == "-198765";
    }

    String callAppendTo(IntN n) {
        StringBuffer buf = new StringBuffer();
        n.appendTo(buf);
        return buf.toString();
    }

    void testEstimateStringLength() {
        IntN n = 0;
        assert n.estimateStringLength() == 1;
        n = 1;
        assert n.estimateStringLength() == 1;
        n = -1;
        assert n.estimateStringLength() == 2;
        n = 10;
        assert n.estimateStringLength() == 2;
        n = -10;
        assert n.estimateStringLength() == 3;
        n = 1000;
        assert n.estimateStringLength() == 4;
        n = -1000;
        assert n.estimateStringLength() == 5;
        n = 9876543;
        assert n.estimateStringLength() == 7;
        n = -9876543;
        assert n.estimateStringLength() == 8;
    }

    void testIntNAbsPositive() {
        IntN n1 = 10;
        IntN n2 = n1.abs();
        assert n2 == 10;
    }

    void testIntNAbsNegative() {
        IntN n1 = -10;
        IntN n2 = n1.abs();
        assert n2 == 10;
    }

    void testIntNAbsZero() {
        IntN n1 = 0;
        IntN n2 = n1.abs();
        assert n2 == 0;
    }

    void testIntNPow() {
        IntN n1 = 10;
        IntN n2 = 2;
        IntN n3 = n1.pow(n2);
        assert n3 == 100;
    }

    void testBitLength() {
        IntN n = 0;
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
        IntN n = 0;
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
        IntN n = 0;
        assert n.signed == True;
        n = 100;
        assert n.signed == True;
        n = -100;
        assert n.signed == True;
    }

    void testSign() {
        IntN n = 0;
        assert n.sign == Zero;
        n = 100;
        assert n.sign == Positive;
        n = -100;
        assert n.sign == Negative;
    }

    void testNegative() {
        IntN n = 0;
        assert n.negative == False;
        n = 100;
        assert n.negative == False;
        n = -100;
        assert n.negative == True;
    }

    void testFinite() {
        IntN n = 0;
        assert n.finite == True;
        n = 100;
        assert n.finite == True;
        n = -100;
        assert n.finite == True;
    }

    void testInfinity() {
        IntN n = 0;
        assert n.infinity == False;
        n = 100;
        assert n.infinity == False;
        n = -100;
        assert n.infinity == False;
    }

    void testNaN() {
        IntN n = 0;
        assert n.NaN == False;
        n = 100;
        assert n.NaN == False;
        n = -100;
        assert n.NaN == False;
    }

    void testMagnitude() {
// TODO fix IntN.magnitude
//        IntN n = 0;
//        assert n.magnitude == 0;
//        n = 100;
//        assert n.magnitude == 100;
//        n = -100;
//        assert n.magnitude == 100;
    }

    // ----- As Number tests -----------------------------------------------------------------------

    void testAbsAsNumberPositive() {
        IntN   n1 = 10;
        Number n2 = absNumber(n1);
        assert n2.is(IntN);
        assert n2 == 10;
    }

    void testAbsAsNumberNegative() {
        IntN   n1 = -10;
        Number n2 = absNumber(n1);
        assert n2.is(IntN);
        assert n2 == 10;
    }

    void testAbsAsNumberZero() {
        IntN   n1 = 0;
        Number n2 = absNumber(n1);
        assert n2.is(IntN);
        assert n2 == 0;
    }

    Number absNumber(Number n) {
        return n.abs();
    }

    void testAddAsNumber() {
        IntN n1 = 10;
        IntN n2 = 25;
        Number n3 = addNumber(n1, n2);
        assert n3.is(IntN);
        assert n3 == 35;
    }

    Number addNumber(Number n1, Number n2) {
        return n1 + n2;
    }

    void testDivAsNumber() {
        IntN   n1 = 123;
        IntN   n2 = 10;
        Number n3 = divNumber(n1, n2);
        assert n3.is(IntN);
        assert n3 == 12;
    }

    Number divNumber(Number n1, Number n2) {
        return n1 / n2;
    }

    void testDivRemAsNumber() {
// TODO JIT DivRem support
//        IntN   n1 = 123;
//        IntN   n2 = 10;
//        (Number quotient, Number remainder) = divRemNumber(n1, n2);
//        assert quotient.is(IntN);
//        assert quotient == 12;
//        assert remainder.is(IntN);
//        assert remainder == 3;
    }

//    (Number quotient, Number remainder) divRemNumber(Number n1, Number n2) {
// TODO JIT DivRem support
//        return n1 /% n2;
//    }

    void testModAsNumber() {
        IntN   n1 = 123;
        IntN   n2 = 10;
        Number n3 = modNumber(n1, n2);
        assert n3.is(IntN);
        assert n3 == 3;
    }

    Number modNumber(Number n1, Number n2) {
        return n1 % n2;
    }

    void testMultiplyAsNumber() {
        IntN   n1 = 12;
        IntN   n2 = 10;
        Number n3 = multiplyNumber(n1, n2);
        assert n3.is(IntN);
        assert n3 == 120;
    }

    Number multiplyNumber(Number n1, Number n2) {
        return n1 * n2;
    }

    void testNegateAsNumber() {
        IntN   n1 = 1234;
        Number n2 = negateNumber(n1);
        assert n2.is(IntN);
        assert n2 == -1234;
    }

    Number negateNumber(Number n) {
        return -n;
    }

    void testPowAsNumber() {
        IntN   n1 = 10;
        IntN   n2 = 2;
        Number n3 = powNumber(n1, n2);
        assert n3.is(IntN);
        assert n3 == 100;
    }

    Number powNumber(Number n1, Number n2) {
        return n1.pow(n2);
    }

    void testSubAsNumber() {
        IntN   n1 = 30;
        IntN   n2 = 25;
        Number n3 = subNumber(n1, n2);
        assert n3.is(IntN);
        assert n3 == 5;
    }

    Number subNumber(Number n1, Number n2) {
        return n1 - n2;
    }

    void testBitLengthAsNumber() {
        IntN n = 0;
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
        IntN n = 0;
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
        IntN n = 0;
        assert isSignedNumber(n) == True;
        n = 100;
        assert isSignedNumber(n) == True;
        n = -100;
        assert isSignedNumber(n) == True;
    }

    Boolean isSignedNumber(Number n) {
        return n.signed;
    }

    void testSignAsNumber() {
        IntN n = 0;
        assert numberSign(n) == Zero;
        n = 100;
        assert numberSign(n) == Positive;
        n = -100;
        assert numberSign(n) == Negative;
    }

    Signum numberSign(Number n) {
        return n.sign;
    }

    void testNegativeAsNumber() {
        IntN n = 0;
        assert isNegativeNumber(n) == False;
        n = 100;
        assert isNegativeNumber(n) == False;
        n = -100;
        assert isNegativeNumber(n) == True;
    }

    Boolean isNegativeNumber(Number n) {
        return n.negative;
    }

    void testFiniteAsNumber() {
        IntN n = 0;
        assert isNumberFinite(n) == True;
        n = 100;
        assert isNumberFinite(n) == True;
        n = -100;
        assert isNumberFinite(n) == True;
    }

    Boolean isNumberFinite(Number n) {
        return n.finite;
    }

    void testInfinityAsNumber() {
        IntN n = 0;
        assert isNumberInfinity(n) == False;
        n = 100;
        assert isNumberInfinity(n) == False;
        n = -100;
        assert isNumberInfinity(n) == False;
    }

    Boolean isNumberInfinity(Number n) {
        return n.infinity;
    }

    void testNaNAsNumber() {
        IntN n = 0;
        assert isNumberNaN(n) == False;
        n = 100;
        assert isNumberNaN(n) == False;
        n = -100;
        assert isNumberNaN(n) == False;
    }

    Boolean isNumberNaN(Number n) {
        return n.NaN;
    }

    void testMagnitudeAsNumber() {
// TODO fix IntN.magnitude
//        IntN   n1 = 0;
//        Number n2 = getNumberMagnitude(n1);
//        assert n2.is(UIntN);
//        assert n2 == 0;
//
//        n1 = 100;
//        n2 = getNumberMagnitude(n1);
//        assert n2.is(UIntN);
//        assert n2 == 100;
//
//        n1 = -100;
//        n2 = getNumberMagnitude(n1);
//        assert n2.is(UIntN);
//        assert n2 == 100;
    }

    Number getNumberMagnitude(Number n) {
     TODO fix IntN.magnitude
//        return n.magnitude;
    }

    // ----- As IntNumber tests --------------------------------------------------------------------

    void testAbsAsIntNumberPositive() {
        IntN      n1 = 10;
        IntNumber n2 = absIntNumber(n1);
        assert n2.is(IntN);
        assert n2 == 10;
    }

    void testAbsAsIntNumberNegative() {
        IntN      n1 = -10;
        IntNumber n2 = absIntNumber(n1);
        assert n2.is(IntN);
        assert n2 == 10;
    }

    void testAbsAsIntNumberZero() {
        IntN      n1 = 0;
        IntNumber n2 = absIntNumber(n1);
        assert n2.is(IntN);
        assert n2 == 0;
    }

    IntNumber absIntNumber(IntNumber n) {
        return n.abs();
    }

    void testAddAsIntNumber() {
        IntN n1 = 1000;
        IntN n2 = 2500;
        IntNumber n3 = addIntNumber(n1, n2);
        assert n3.is(IntN);
        assert n3 == 3500;
    }

    IntNumber addIntNumber(IntNumber n1, IntNumber n2) {
        return n1 + n2;
    }

    void testAndAsIntNumber() {
        IntN      n1 = 0x00F2_F0F2_F0F0_F0F0;
        IntN      n2 = 0x0AAA_AAAA_AAAA_AAAA;
        IntNumber n3 = andIntNumber(n1, n2);
        assert n3.is(IntN);
        assert n3 == 0x00A2_A0A2_A0A0_A0A0;
    }

    IntNumber andIntNumber(IntNumber n1, IntNumber n2) {
        return n1 & n2;
    }

    void testComplementAsIntNumber() {
        IntN      n1 = 0x5ABC5432;
        IntNumber n2 = complementIntNumber(n1);
        assert n2.is(IntN);
        assert n2 == -1522291763;
    }

    IntNumber complementIntNumber(IntNumber n) {
        return ~n;
    }

    void testDecAsIntNumber() {
        IntN      n1 = 1236;
        IntNumber n2 = decIntNumber(n1);
        assert n2.is(IntN);
        assert n2 == 1235;
    }

    IntNumber decIntNumber(IntNumber n) {
        return --n;
    }

    void testDivAsIntNumber() {
        IntN      n1 = 1234;
        IntN      n2 = 10;
        IntNumber n3 = divNumber(n1, n2);
        assert n3.is(IntN);
        assert n3 == 123;
    }

    IntNumber divNumber(IntNumber n1, IntNumber n2) {
        return n1 / n2;
    }

    void testDivRemAsIntNumber() {
// TODO JIT DivRem support
//        IntN   n1 = 123;
//        IntN   n2 = 10;
//        (IntNumber quotient, IntNumber remainder) = divRemIntNumber(n1, n2);
//        assert quotient.is(IntN);
//        assert quotient == 12;
//        assert remainder.is(IntN);
//        assert remainder == 3;
    }

//    (IntNumber quotient, IntNumber remainder) divRemIntNumber(IntNumber n1, IntNumber n2) {
// TODO JIT DivRem support
//        return n1 /% n2;
//    }

    void testIncAsIntNumber() {
        IntN      n1 = 1234;
        IntNumber n2 = incIntNumber(n1);
        assert n2.is(IntN);
        assert n2 == 1235;
    }

    IntNumber incIntNumber(IntNumber n) {
        return ++n;
    }

    void testModAsIntNumber() {
        IntN      n1 = 1234;
        IntN      n2 = 10;
        IntNumber n3 = modIntNumber(n1, n2);
        assert n3.is(IntN);
        assert n3 == 4;
    }

    IntNumber modIntNumber(IntNumber n1, IntNumber n2) {
        return n1 % n2;
    }

    void testMultiplyAsIntNumber() {
        IntN      n1 = 12;
        IntN      n2 = 10;
        IntNumber n3 = multiplyIntNumber(n1, n2);
        assert n3.is(IntN);
        assert n3 == 120;
    }

    IntNumber multiplyIntNumber(IntNumber n1, IntNumber n2) {
        return n1 * n2;
    }

    void testNegateAsIntNumber() {
        IntN      n1 = 1234;
        IntNumber n2 = negateIntNumber(n1);
        assert n2.is(IntN);
        assert n2 == -1234;
    }

    IntNumber negateIntNumber(IntNumber n) {
        return -n;
    }

    void testOrAsIntNumber() {
        IntN      n1 = 0x00F2_F0F2_F0F0_F0F0;
        IntN      n2 = 0x0AA0_AAAA_AAAA_AAAA;
        IntNumber n3 = orIntNumber(n1, n2);
        assert n3.is(IntN);
        assert n3 == 0x0AF2_FAFA_FAFA_FAFA;
    }

    IntNumber orIntNumber(IntNumber n1, IntNumber n2) {
        return n1 | n2;
    }

    void testPowAsIntNumber() {
        IntN      n1 = 10;
        IntN      n2 = 2;
        IntNumber n3 = powIntNumber(n1, n2);
        assert n3.is(IntN);
        assert n3 == 100;
    }

    IntNumber powIntNumber(IntNumber n1, IntNumber n2) {
        return n1.pow(n2);
    }

    void testRetainLSBitsAsIntNumber() {
        IntN      n1 = 0xABCD_1234;
        Int       n2 = 16;
        IntNumber n3 = retainIntNumberLSBits(n1, n2);
        assert n3.is(IntN);
        assert n3 == 0x1234;
    }

    void testRetainLSBitsAllBitsAsIntNumber() {
        IntN      n1 = 0xABCD_1234;
        Int       n2 = n1.bitLength;
        IntNumber n3 = retainIntNumberLSBits(n1, n2);
        assert n3.is(IntN);
        assert n3 == n1;
    }

    void testRetainLSBitsLargerThanBitsAsIntNumber() {
        IntN      n1 = 0xABCD_1234;
        Int       n2 = n1.bitLength + 10;
        IntNumber n3 = retainIntNumberLSBits(n1, n2);
        assert n3.is(IntN);
        assert n3 == n1;
    }

    void testRetainLSBitsZeroAsIntNumber() {
        IntN      n1 = 0xABCD_1234;
        Int       n2 = 0;
        IntNumber n3 = retainIntNumberLSBits(n1, n2);
        assert n3.is(IntN);
        assert n3 == 0;
    }

    void testRetainLSBitsNegativeAsIntNumber() {
        IntN      n1 = 0xABCD_1234;
        Int       n2 = -16;
        IntNumber n3 = retainIntNumberLSBits(n1, n2);
        assert n3.is(IntN);
        assert n3 == 0;
    }

    IntNumber retainIntNumberLSBits(IntNumber n1, Int n2) {
        return n1.retainLSBits(n2);
    }

    void testRetainMSBitsAsIntNumber() {
        IntN      n1 = 0xABCD_1234;
        Int       n2 = 16;
        IntNumber n3 = retainIntNumberMSBits(n1, n2);
        assert n3.is(IntN);
        assert n3 == 0xABCD_0000;
    }

    void testRetainMSBitsAllBitsAsIntNumber() {
        IntN      n1 = 0xABCD_1234;
        Int       n2 = n1.bitLength;
        IntNumber n3 = retainIntNumberMSBits(n1, n2);
        assert n3.is(IntN);
        assert n3 == n1;
    }

    void testRetainMSBitsLargerThanBitsAsIntNumber() {
        IntN      n1 = 0xABCD_1234;
        Int       n2 = n1.bitLength + 10;
        IntNumber n3 = retainIntNumberMSBits(n1, n2);
        assert n3.is(IntN);
        assert n3 == n1;
    }

    void testRetainMSBitsZeroAsIntNumber() {
        IntN      n1 = 0xABCD_1234;
        Int       n2 = 0;
        IntNumber n3 = retainIntNumberMSBits(n1, n2);
        assert n3.is(IntN);
        assert n3 == 0;
    }

    void testRetainMSBitsNegativeAsIntNumber() {
        IntN      n1 = 0xABCD_1234;
        Int       n2 = -16;
        IntNumber n3 = retainIntNumberMSBits(n1, n2);
        assert n3.is(IntN);
        assert n3 == 0;
    }

    IntNumber retainIntNumberMSBits(IntNumber n1, Int n2) {
        return n1.retainMSBits(n2);
    }

    void testRotateLeftAsIntNumber() {
        IntN      n1 = 0xFEDC_1234_5678_ABCD;
        IntNumber n2 = rotateLeftIntNumber(n1, 16);
        assert n2.is(IntN);
        assert n2 == 0x1234_5678_ABCD_FEDC;
    }

    IntNumber rotateLeftIntNumber(IntNumber n1, Int n2) {
        return n1.rotateLeft(n2);
    }

    void testRotateRightAsIntNumber() {
        IntN      n1 = 0xFEDC_1234_5678_ABCD;
        IntNumber n2 = rotateRightIntNumber(n1, 16);
        assert n2.is(IntN);
        assert n2 == 0xABCD_FEDC_1234_5678;
    }

    IntNumber rotateRightIntNumber(IntNumber n1, Int n2) {
        return n1.rotateRight(n2);
    }

    void testShiftLeftAsIntNumber() {
        IntN      n1 = 0x1142_F0F2_F0F0_F0F0;
        Int       n2 = 8;
        IntNumber n3 = shiftLeftIntNumber(n1, n2);
        assert n3.is(IntN);
        assert n3 == 0x0011_42F0_F2F0_F0F0_F000;
                  // 0x0011_42F0_F2F0_F0F0_F0
    }

    IntNumber shiftLeftIntNumber(IntNumber n1, Int n2) {
        return n1 << n2;
    }

    void testShiftRightAsIntNumber() {
        IntN      n1 = 0x1142_F0F2_F0F0_F0F0;
        Int       n2 = 8;
        IntNumber n3 = shiftRightIntNumber(n1, n2);
        assert n3.is(IntN);
        assert n3 == 0x0011_42F0_F2F0_F0F0;
    }

    IntNumber shiftRightIntNumber(IntNumber n1, Int n2) {
        return n1 >> n2;
    }

    void testSubAsIntNumber() {
        IntN      n1 = 3000;
        IntN      n2 = 2500;
        IntNumber n3 = subIntNumber(n1, n2);
        assert n3.is(IntN);
        assert n3 == 500;
    }

    IntNumber subIntNumber(IntNumber n1, IntNumber n2) {
        return n1 - n2;
    }

    void testUnsignedShiftRightAsIntNumber() {
        IntN      n1 = 0x1142_F0F2_F0F0_F0F0;
        Int       n2 = 8;
        IntNumber n3 = unsignedShiftRightIntNumber(n1, n2);
        assert n3.is(IntN);
        assert n3 == 0x0011_42F0_F2F0_F0F0;
    }

    IntNumber unsignedShiftRightIntNumber(IntNumber n1, Int n2) {
        return n1 >>> n2;
    }

    void testXorAsIntNumber() {
        IntN      n1 = 0x00F2_F0F2_F0F0_F0F0;
        IntN      n2 = 0x0AA0_AAAA_AAAA_AAAA;
        IntNumber n3 = xorIntNumber(n1, n2);
        assert n3.is(IntN);
        assert n3 == 0x0A52_5A58_5A5A_5A5A;
    }

    IntNumber xorIntNumber(IntNumber n1, IntNumber n2) {
        return n1 ^ n2;
    }

    void testBitLengthAsIntNumber() {
        IntN n = 0;
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
        IntN n = 0;
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
        IntN n = 0;
        assert isSignedIntNumber(n) == True;
        n = 100;
        assert isSignedIntNumber(n) == True;
        n = -100;
        assert isSignedIntNumber(n) == True;
    }

    Boolean isSignedIntNumber(IntNumber n) {
        return n.signed;
    }

    void testSignAsIntNumber() {
        IntN n = 0;
        assert intNumberSign(n) == Zero;
        n = 100;
        assert intNumberSign(n) == Positive;
        n = -100;
        assert intNumberSign(n) == Negative;
    }

    Signum intNumberSign(IntNumber n) {
        return n.sign;
    }

    void testNegativeAsIntNumber() {
        IntN n = 0;
        assert isNegativeIntNumber(n) == False;
        n = 100;
        assert isNegativeIntNumber(n) == False;
        n = -100;
        assert isNegativeIntNumber(n) == True;
    }

    Boolean isNegativeIntNumber(IntNumber n) {
        return n.negative;
    }

    void testFiniteAsIntNumber() {
        IntN n = 0;
        assert isIntNumberFinite(n) == True;
        n = 100;
        assert isIntNumberFinite(n) == True;
        n = -100;
        assert isIntNumberFinite(n) == True;
    }

    Boolean isIntNumberFinite(IntNumber n) {
        return n.finite;
    }

    void testInfinityAsIntNumber() {
        IntN n = 0;
        assert isIntNumberInfinity(n) == False;
        n = 100;
        assert isIntNumberInfinity(n) == False;
        n = -100;
        assert isIntNumberInfinity(n) == False;
    }

    Boolean isIntNumberInfinity(IntNumber n) {
        return n.infinity;
    }

    void testNaNAsIntNumber() {
        IntN n = 0;
        assert isIntNumberNaN(n) == False;
        n = 100;
        assert isIntNumberNaN(n) == False;
        n = -100;
        assert isIntNumberNaN(n) == False;
    }

    Boolean isIntNumberNaN(IntNumber n) {
        return n.NaN;
    }

    void testMagnitudeAsIntNumber() {
// TODO fix IntN.magnitude
//        IntN   n1 = 0;
//        IntNumber n2 = getIntNumberMagnitude(n1);
//        assert n2.is(UIntN);
//        assert n2 == 0;
//
//        n1 = 100;
//        n2 = getIntNumberMagnitude(n1);
//        assert n2.is(UIntN);
//        assert n2 == 100;
//
//        n1 = -100;
//        n2 = getIntNumberMagnitude(n1);
//        assert n2.is(UIntN);
//        assert n2 == 100;
    }

    IntNumber getIntNumberMagnitude(IntNumber n) {
     TODO fix IntN.magnitude
//        return n.magnitude;
    }

    // ----- As Sequential tests -------------------------------------------------------------------

    void testDecAsSequential() {
        IntN       n1 = 1236;
        Sequential n2 = decSequential(n1);
        assert n2.is(IntN);
        assert n2 == 1235;
    }

    Sequential decSequential(Sequential n) {
        return n.prevValue();
    }

    void testIncAsSequential() {
        IntN       n1 = 1234;
        Sequential n2 = incSequential(n1);
        assert n2.is(IntN);
        assert n2 == 1235;
    }

    Sequential incSequential(Sequential n) {
        return n.nextValue();
    }
}
