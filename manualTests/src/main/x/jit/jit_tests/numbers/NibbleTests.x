class NibbleTests {

    @Inject Console console;

    void run() {
        // Comparison tests
        testNibbleCompareEq();
        testNibbleCompareGe();
        testNibbleCompareGt();
        testNibbleCompareLe();
        testNibbleCompareLt();

        // Field tests
        testNibbleAsField();
        testNibbleAsNullableField();
        testNibbleAsNullableFieldNull();

        // Constant field and constructor tests
        testNibbleAsConstField();
        testNullableNibbleAsConstField();
        testNullableNibbleAsConstFieldNull();

        // Method parameter tests
        testNibbleAsParam();
        testNibbleAsNullableParam();
        testNibbleAsNullableParamNull();
        testNibbleAsMultiParams();
        testNibbleAsMultiNullableParams();

        // Method return tests
        testNibbleReturn();
        testNibbleConditionalReturn();
        testNibbleReturnStringIntN();
        testNibbleReturnIntNInt();
        testNibbleReturnTwoIntN();
        testNullableNibbleReturn();
        testNullableNibbleAsIntReturn();
        testIntAsNullableNibbleReturn();
        testNullableNibbleConditionalReturn();

        // GP/IP Op tests
        // Add
        testNibbleOpAdd();
        testNibbleOpAddInPlace();
        testNibblePropertyAdd();
        testNibbleOpAddWhenFifteen();
        // And
        testNibbleOpAnd();
        // Complement
        testNibbleComplement();
        // Inc
        testNibbleOpInc();
        testNibbleOpPreInc();
        testNibbleOpPostInc();
        testNibbleOpIncOverflow();
        // Dec
        testNibbleOpDec();
        testNibbleOpPreDec();
        testNibbleOpPostDec();
        testNibbleOpDecZero();
        // Div
        testNibbleOpDiv();
        testNibbleOpDivInPlace();
        // DivRem
        testNibbleOpDivRem();
        // Mod
        testNibbleOpMod();
        // Multiply
        testNibbleOpMultiply();
        testNibbleOpMultiply();
        testNibbleOpMultiplyInPlace();
        // Or
        testNibbleOpOr();
        testNibbleOpOrInPlace();
        // Rotate
        testNibbleRotateLeft();
        testNibbleZeroRotateLeft();
        testNibbleRotateLeftByZero();
        testNibbleRotateLeftByBitLength();
        testNibbleRotateRight();
        testNibbleZeroRotateRight();
        testNibbleRotateRightByZero();
        testNibbleRotateRightByBitLength();
        // Retain Bits
        testNibbleRetainLSBits();
        testNibbleRetainLSBitsAllBits();
        testNibbleRetainLSBitsLargerThanBits();
        testNibbleRetainLSBitsZero();
        testNibbleRetainLSBitsNegative();
        testNibbleRetainMSBits();
        testNibbleRetainMSBitsAllBits();
        testNibbleRetainMSBitsLargerThanBits();
        testNibbleRetainMSBitsZero();
        testNibbleRetainMSBitsNegative();
        // Shl
        testNibbleOpShiftLeft();
        testNibbleOpShiftLeftZero();
        testNibbleOpShiftLeftMinus2();
        // Shr
        testNibbleOpShiftRight();
        testNibbleOpShiftRightZero();
        testNibbleOpShiftRightMinus2();
        // Sub
        testNibbleOpSub();
        testNibbleOpSubWhenZero();
        testNibbleOpSubInPlace();
        // Ushr
        testNibbleOpUnsignedShiftRight();
        testNibbleOpUnsignedShiftRightZero();
        testNibbleOpUnsignedShiftRightMinus2();
        // Xor
        testNibbleOpXor();
        testNibbleOpXorInPlace();

        // Misc
        testNibbleAbsPositive();
        testNibbleAbsZero();
        testNibblePow();
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

        // As Sequential tests
        testIncAsSequential();
        testDecAsSequential();
    }

    // ----- comparison tests ----------------------------------------------------------------------

    void testNibbleCompareEq() {
        Nibble n = 10;
        assert n == 10;
    }

    void testNibbleCompareGe() {
        Nibble n = 10;
        assert n >= 5;
        assert n >= 10;
    }

    void testNibbleCompareGt() {
        Nibble n = 10;
        assert n > 2;
    }

    void testNibbleCompareLe() {
        Nibble n = 10;
        assert n <= 15;
        assert n <= 10;
    }

    void testNibbleCompareLt() {
        Nibble n = 10;
        assert n < 15;
    }

    // ----- field tests ---------------------------------------------------------------------------

    void testNibbleAsField() {
        NibbleAsField n = new NibbleAsField();
        assert n.field == 10;
    }

    static class NibbleAsField {
        Nibble field = 10;
    }

    void testNibbleAsNullableField() {
        IntNAsNullableField n = new IntNAsNullableField();
        assert n.field == 15;
    }

    static class IntNAsNullableField {
        Nibble? field = 15;
    }

    void testNibbleAsNullableFieldNull() {
        IntNNullField n = new IntNNullField();
        assert n.field == Null;
    }

    static class IntNNullField {
        Nibble? field = Null;
    }

    void testNibbleAsConstField() {
        Nibble       n = 10;
        NumberHolder h = new NumberHolder(n);
        assert h.n == 10;
    }

    static const NumberHolder(Nibble n = 0) {
    }

    void testNullableNibbleAsConstField() {
        Nibble               n = 14;
        NullableNumberHolder h = new NullableNumberHolder(n);
        assert h.n == n;
    }

    void testNullableNibbleAsConstFieldNull() {
        NullableNumberHolder h = new NullableNumberHolder(Null);
        assert h.n == Null;
    }

    static const NullableNumberHolder(Nibble? n = Null) {
    }

    // ----- parameter tests -----------------------------------------------------------------------

    void testNibbleAsParam() {
        Nibble n = 10;
        IntNParam(n);
    }

    void IntNParam(Nibble n) {
        assert n == 10;
    }

    void testNibbleAsNullableParam() {
        Nibble  n      = 10;
        Boolean isNull = IntNNullableParam(n);
        assert isNull == False;
    }

    void testNibbleAsNullableParamNull() {
        Boolean isNull = IntNNullableParam(Null);
        assert isNull == True;
    }

    Boolean IntNNullableParam(Nibble? n) {
        if (n.is(Nibble)) {
            assert n == 10;
            return False;
        }
        return True;
    }

    void testNibbleAsMultiParams() {
        IntNMultiParams(10, 15);
    }

    void IntNMultiParams(Nibble n1, Nibble n2) {
        assert n1 == 10;
        assert n2 == 15;
    }

    void testNibbleAsMultiNullableParams() {
        Boolean b1;
        Boolean b2;
        (b1, b2) = IntNMultiNullableParams(10, 15);
        assert b1 == True;
        assert b2 == True;
        (b1, b2) = IntNMultiNullableParams(10, Null);
        assert b1 == True;
        assert b2 == False;
        (b1, b2) = IntNMultiNullableParams(Null, 15);
        assert b1 == False;
        assert b2 == True;
        (b1, b2) = IntNMultiNullableParams(Null, Null);
        assert b1 == False;
        assert b2 == False;
    }

    (Boolean, Boolean) IntNMultiNullableParams(Nibble? n1, Nibble? n2) {
        Boolean b1 = False;
        Boolean b2 = False;
        if (n1.is(Nibble)) {
            assert n1 == 10;
            b1 = True;
        }
        if (n2.is(Nibble)) {
            assert n2 == 15;
            b2 = True;
        }
        return b1, b2;
    }

    // ----- return tests --------------------------------------------------------------------------

    void testNibbleReturn() {
        Nibble n = returnIntN();
        assert n == 10;
    }

    Nibble returnIntN() {
        Nibble n = 10;
        return n;
    }

    void testNibbleConditionalReturn() {
        assert Nibble n := returnConditionalIntN();
        assert n == 10;
    }

    conditional Nibble returnConditionalIntN() {
        Nibble n = 10;
        return True, n;
    }

    void testNibbleReturnStringIntN() {
        (String s, Nibble n) = returnStringIntN();
        assert s == "Foo";
        assert n == 15;
    }

    (String, Nibble) returnStringIntN() {
        Nibble n = 15;
        return "Foo", n;
    }

    void testNibbleReturnIntNInt() {
        (Nibble n, Nibble i) = returnIntNInt();
        assert n == 13;
        assert i == 12;
    }

    (Nibble, Nibble) returnIntNInt() {
        Nibble n = 13;
        return n, 12;
    }

    void testNibbleReturnTwoIntN() {
        (Nibble n1, Nibble n2) = returnTwoIntN();
        assert n1 == 5;
        assert n2 == 2;
    }

    (Nibble, Nibble) returnTwoIntN() {
        Nibble n1 = 5;
        Nibble n2 = 2;
        return n1, n2;
    }

    void testNullableNibbleReturn() {
        Nibble? n = returnNullableNibble(True);
        assert n == 1;
        n = returnNullableNibble(False);
        assert n == Null;
    }

    Nibble? returnNullableNibble(Boolean b) {
        Nibble n = 1;
        if (b) {
            return n;
        }
        return Null;
    }

    void testNullableNibbleAsIntReturn() {
        Nibble n = returnNullableNibbleAsInt();
        assert n == 3;
    }

    Nibble returnNullableNibbleAsInt() {
        Nibble? n = 3;
        return n;
    }

    void testIntAsNullableNibbleReturn() {
        Nibble? n = returnIntAsNullableNibble();
        assert n == 3;
    }

    Nibble? returnIntAsNullableNibble() {
        Nibble n = 3;
        return n;
    }

    void testNullableNibbleConditionalReturn() {
        assert Nibble? n := returnConditionalNullableNibble(0);
        assert n == 4;
        assert n := returnConditionalNullableNibble(1);
        assert n == Null;
        assert returnConditionalNullableNibble(2) == False;
    }

    conditional Nibble? returnConditionalNullableNibble(Nibble i) {
        Nibble? n = 4;
        if (i == 0) {
            return True, n;
        }
        if (i == 1) {
            return True, Null;
        }
        return False;
    }

    // ----- Op tests (Add) ------------------------------------------------------------------------

    void testNibbleOpAdd() {
        Nibble n1 = 5;
        Nibble n2 = 2;
        Nibble n3 = n1 + n2;
        assert n3 == 7;
    }

    void testNibbleOpAddInPlace() {
        Nibble n1 = 9;
        n1 += 2;
        assert n1 == 11;
    }

    void testNibblePropertyAdd() {
        NibbleAsField test = new NibbleAsField();
        test.field = 5;
        Nibble n2 = 2;
        Nibble n3 = test.field + n2;
        assert n3 == 7;
        test.field = test.field + 1;
        assert test.field == 6;
    }

    void testNibbleOpAddWhenFifteen() {
        Nibble n1 = 0xF;
        n1 += 2;
        assert n1 == 0x1;
    }

    // ----- Op tests (Sub) ------------------------------------------------------------------------

    void testNibbleOpSub() {
        Nibble n1 = 5;
        Nibble n2 = 2;
        Nibble n3 = n1 - n2;
        assert n3 == 3;
    }

    void testNibbleOpSubInPlace() {
        Nibble n1 = 9;
        n1 -= 2;
        assert n1 == 7;
    }

    void testNibbleOpSubWhenZero() {
        Nibble n1 = 0;
        n1 -= 2;
        assert n1 == 0xE;
    }

    // ----- Op tests (logical And) ----------------------------------------------------------------

    void testNibbleOpAnd() {
        Nibble n1 = 0b1001;
        Nibble n2 = 0b1000;
        Nibble n3 = n1 & n2;
        assert n3 == 0b1000;
    }

    // ----- Op tests (Complement ~) ---------------------------------------------------------------

    void testNibbleComplement() {
        Nibble value1 = 0;
        Nibble value2 = 0b0101;
        value1 = ~value2;
        assert value1 == 0b1010;
    }

    // ----- Op tests (Inc ++) ---------------------------------------------------------------------

    void testNibbleOpInc() {
        Nibble n = 10;
        n++;
        assert n == 11;
    }

    void testNibbleOpPreInc() {
        Nibble n1 = 10;
        Nibble n2 = ++n1;
        assert n1 == 11;
        assert n2 == 11;
    }

    void testNibbleOpPostInc() {
        Nibble n1 = 10;
        Nibble n2 = n1++;
        assert n1 == 11;
        assert n2 == 10;
    }

    void testNibbleOpIncOverflow() {
        Nibble n = 15;
        n++;
        assert n == 0;
    }

    // ----- Op tests (Dec --) ---------------------------------------------------------------------

    void testNibbleOpDec() {
        Nibble n = 10;
        n--;
        assert n == 9;
    }

    void testNibbleOpPreDec() {
        Nibble n1 = 10;
        Nibble n2 = --n1;
        assert n1 == 9;
        assert n2 == 9;
    }

    void testNibbleOpPostDec() {
        Nibble n1 = 10;
        Nibble n2 = n1--;
        assert n1 == 9;
        assert n2 == 10;
    }

    void testNibbleOpDecZero() {
        Nibble n = 0;
        n--;
        assert n == 0xF;
    }

    // ----- Op tests (divide) ---------------------------------------------------------------------

    void testNibbleOpDiv() {
        Nibble n = 10;
        Nibble n2 = n / 2;
        assert n2 == 5;
    }

    void testNibbleOpDivInPlace() {
        Nibble n = 10;
        n /= 5;
        assert n == 2;
    }

    // ----- Op tests (div/rem) --------------------------------------------------------------------

    void testNibbleOpDivRem() {
        Nibble   n1 = 11;
        Nibble   n2 = 5;
        (Nibble quotient, Nibble remainder) = n1/% n2;
        assert quotient == 2;
        assert remainder == 1;
    }

    // ----- Op tests (modulus) --------------------------------------------------------------------

    void testNibbleOpMod() {
        Nibble n = 14;
        Nibble n2 = n % 5;
        assert n2 == 4;
    }

    // ----- Op tests (multiply) -------------------------------------------------------------------

    void testNibbleOpMultiply() {
        Nibble n = 2;
        Nibble n2 = n * 3;
        assert n2 == 6;
    }

    void testNibbleOpMultiplyInPlace() {
        Nibble n = 2;
        n *= 6;
        assert n == 12;
    }

    // ----- Op tests (logical Or) -----------------------------------------------------------------

    void testNibbleOpOr() {
        Nibble n1 = 0b1010;
        Nibble n2 = 0b1001;
        Nibble n3 = n1 | n2;
        assert n3 == 0b1011;
    }

    void testNibbleOpOrInPlace() {
        Nibble n = 0b1010;
        n |= 0b1001;
        assert n == 0b1011;
    }

    // ----- Op tests (Retain LSB) -----------------------------------------------------------------

    void testNibbleRetainLSBits() {
// TODO NoSuchMethodError: 'org.xtclang.ecstasy.numbers.IntNumber org.xtclang.ecstasy.numbers.Nibble.retainLSBits$p(int, org.xvm.javajit.Ctx, long)'        Nibble n1 = 0b1011;
//        Int  n2 = 2;
//        Nibble n3 = n1.retainLSBits(n2);
//        assert n3 == 0b0011;
    }

    void testNibbleRetainLSBitsAllBits() {
//        Nibble n1 = 0b1011;
//        Int  n2 = n1.bitLength;
//        Nibble n3 = n1.retainLSBits(n2);
//        assert n3 == n1;
    }

    void testNibbleRetainLSBitsLargerThanBits() {
//        Nibble n1 = 0b1011;
//        Int  n2 = n1.bitLength + 10;
//        Nibble n3 = n1.retainLSBits(n2);
//        assert n3 == n1;
    }

    void testNibbleRetainLSBitsZero() {
//        Nibble n1 = 0b1011;
//        Int  n2 = 0;
//        Nibble n3 = n1.retainLSBits(n2);
//        assert n3 == 0;
    }

    void testNibbleRetainLSBitsNegative() {
//        Nibble n1 = 0b1011;
//        Int  n2 = -2;
//        Nibble n3 = n1.retainLSBits(n2);
//        assert n3 == 0;
    }

    // ----- Op tests (Retain MSB) -----------------------------------------------------------------

    void testNibbleRetainMSBits() {
// TODO NoSuchMethodError: 'org.xtclang.ecstasy.numbers.IntNumber org.xtclang.ecstasy.numbers.Nibble.retainMSBits$p(int, org.xvm.javajit.Ctx, long)'
//        Nibble n1 = 0b1110;
//        Int  n2 = 2;
//        Nibble n3 = n1.retainMSBits(n2);
//        assert n3 == 0b1100;
    }

    void testNibbleRetainMSBitsAllBits() {
//        Nibble n1 = 0b1110;
//        Int  n2 = n1.bitLength;
//        Nibble n3 = n1.retainMSBits(n2);
//        assert n3 == n1;
    }

    void testNibbleRetainMSBitsLargerThanBits() {
//        Nibble n1 = 0b1110;
//        Int  n2 = n1.bitLength + 10;
//        Nibble n3 = n1.retainMSBits(n2);
//        assert n3 == n1;
    }

    void testNibbleRetainMSBitsZero() {
//        Nibble n1 = 0b1110;
//        Int  n2 = 0;
//        Nibble n3 = n1.retainMSBits(n2);
//        assert n3 == 0;
    }

    void testNibbleRetainMSBitsNegative() {
//        Nibble n1 = 0b1110;
//        Int  n2 = -2;
//        Nibble n3 = n1.retainMSBits(n2);
//        assert n3 == 0;
    }

    // ----- Op tests (Rotate left) ----------------------------------------------------------------

    void testNibbleRotateLeft() {
// TODO - NoSuchMethodError: 'org.xtclang.ecstasy.numbers.IntNumber org.xtclang.ecstasy.numbers.Nibble.rotateLeft$p(int, org.xvm.javajit.Ctx, long)'
//        Nibble n1 = 0b1110;
//        Nibble n2 = n1.rotateLeft(2);
//        assert n2 == 0b1011;
    }

    void testNibbleZeroRotateLeft() {
// TODO - NoSuchMethodError: 'org.xtclang.ecstasy.numbers.IntNumber org.xtclang.ecstasy.numbers.Nibble.rotateLeft$p(int, org.xvm.javajit.Ctx, long)'
//        Nibble n1 = 0;
//        Nibble n2 = n1.rotateLeft(2);
//        assert n2 == 0;
    }

    void testNibbleRotateLeftByZero() {
// TODO - NoSuchMethodError: 'org.xtclang.ecstasy.numbers.IntNumber org.xtclang.ecstasy.numbers.Nibble.rotateLeft$p(int, org.xvm.javajit.Ctx, long)'
//        Nibble n1 = 0b1110;
//        Nibble n2 = n1.rotateLeft(0);
//        assert n2 == n1;
    }

    void testNibbleRotateLeftByBitLength() {
// TODO - NoSuchMethodError: 'org.xtclang.ecstasy.numbers.IntNumber org.xtclang.ecstasy.numbers.Nibble.rotateLeft$p(int, org.xvm.javajit.Ctx, long)'
//        Nibble n1 = 0b1110;
//        Nibble n2 = n1.rotateLeft(4);
//        assert n2 == n1;
    }

    // ----- Op tests (Rotate right) ---------------------------------------------------------------

    void testNibbleRotateRight() {
// TODO - NoSuchMethodError: 'org.xtclang.ecstasy.numbers.IntNumber org.xtclang.ecstasy.numbers.Nibble.rotateRight$p(int, org.xvm.javajit.Ctx, long)'
//        Nibble n1 = 0b1110;
//        Nibble n2 = n1.rotateRight(2);
//        assert n2 == 0b1011;
    }

    void testNibbleZeroRotateRight() {
// TODO - NoSuchMethodError: 'org.xtclang.ecstasy.numbers.IntNumber org.xtclang.ecstasy.numbers.Nibble.rotateRight$p(int, org.xvm.javajit.Ctx, long)'
//        Nibble n1 = 0;
//        Nibble n2 = n1.rotateRight(2);
//        assert n2 == 0;
    }

    void testNibbleRotateRightByZero() {
// TODO - NoSuchMethodError: 'org.xtclang.ecstasy.numbers.IntNumber org.xtclang.ecstasy.numbers.Nibble.rotateRight$p(int, org.xvm.javajit.Ctx, long)'
//        Nibble n1 = 0b1110;
//        Nibble n2 = n1.rotateRight(0);
//        assert n2 == n1;
    }

    void testNibbleRotateRightByBitLength() {
// TODO - NoSuchMethodError: 'org.xtclang.ecstasy.numbers.IntNumber org.xtclang.ecstasy.numbers.Nibble.rotateRight$p(int, org.xvm.javajit.Ctx, long)'
//        Nibble n1 = 0b1110;
//        Nibble n2 = n1.rotateRight(64);
//        assert n2 == n1;
    }

    // ----- Op tests (Shift left <<) --------------------------------------------------------------

    void testNibbleOpShiftLeft() {
        Nibble n = 0b1110;
        Nibble n2 = n << 2;
        assert n2 == 0b1000;
    }

    void testNibbleOpShiftLeftZero() {
        Nibble n = 0b1110;
        Nibble n2 = n << 0;
        assert n2 == n;
    }

    void testNibbleOpShiftLeftMinus2() {
        Nibble n = 0b1110;
        Nibble n2 = n << -2; // same a right shift 2
// TODO
//        assert n2 == 0b0011;
    }

    // ----- Op tests (Shift right >>) -------------------------------------------------------------

    void testNibbleOpShiftRight() {
        Nibble n =   0b1110;
        Nibble n2 = n >> 2;
        assert n2 == 0b0011;
    }

    void testNibbleOpShiftRightZero() {
        Nibble n =   0b1110;
        Nibble n2 = n >> 0;
        assert n2 == 0b1110;
    }

    void testNibbleOpShiftRightMinus2() {
        Nibble n = 0b1110;
        Nibble n2 = n >> -2; // same as left shift 2
// TODO
//        assert n2 == 0b1000;
    }

    // ----- Op tests (Unsigned shift right >>>) ---------------------------------------------------

    void testNibbleOpUnsignedShiftRight() {
        Nibble n =   0b1110;
        Nibble n2 = n >>> 2;
        assert n2 == 0b0011;
    }

    void testNibbleOpUnsignedShiftRightZero() {
        Nibble n =   0b1110;
        Nibble n2 = n >>> 0;
        assert n2 == 0b1110;
    }

    void testNibbleOpUnsignedShiftRightMinus2() {
        Nibble n = 0b1110;
        Nibble n2 = n >>> -2; // same as left shift 2
// TODO
//        assert n2 == 0b1000;
    }

    // ----- Op tests (logical Xor) ----------------------------------------------------------------

    void testNibbleOpXor() {
        Nibble n1 = 0xF;
        Nibble n2 = 0xA;
        Nibble n3 = n1 ^ n2;
        assert n3 == 0x5;
    }

    void testNibbleOpXorInPlace() {
        Nibble n = 0xF;
        n ^= 0xA;
        assert n == 0x5;
    }

    // ----- Misc tests ----------------------------------------------------------------------------

    void testNibbleAbsPositive() {
// TODO NoSuchMethodError: 'org.xtclang.ecstasy.numbers.UIntNumber org.xtclang.ecstasy.numbers.Nibble.absꖛ4$p(int, org.xvm.javajit.Ctx)'
//        Nibble n1 = 10;
//        Nibble n2 = n1.abs();
//        assert n2 == 10;
    }

    void testNibbleAbsZero() {
//        Nibble n1 = 0;
//        Nibble n2 = n1.abs();
//        assert n2 == 0;
    }

    void testNibblePow() {
        Nibble n1 = 3;
        Nibble n2 = 2;
        Nibble n3 = n1.pow(n2);
        assert n3 == 9;
    }

    void testBitLength() {
        Nibble n = 0;
        assert n.bitLength == 4;
        n = 1;
        assert n.bitLength == 4;
        n = 0xF;
        assert n.bitLength == 4;
    }

    void testByteLength() {
        Nibble n = 0;
        assert n.byteLength == 1;
        n = 1;
        assert n.byteLength == 1;
        n = 2;
        assert n.byteLength == 1;
        n = 0xF;
        assert n.byteLength == 1;
    }

    void testSigned() {
//        Nibble n = 0;
//        assert n.signed == False;
//        n = 0xF;
//        assert n.signed == False;
    }

    void testSign() {
//        Nibble n = 0;
//        assert n.sign == Zero;
//        n = 0xF;
//        assert n.sign == Positive;
    }

    void testNegative() {
//        Nibble n = 0;
//        assert n.negative == False;
//        n = 0xF;
//        assert n.negative == False;
    }

    void testFinite() {
//        Nibble n = 0;
//        assert n.finite == True;
//        n = 0xF;
//        assert n.finite == True;
    }

    void testInfinity() {
//        Nibble n = 0;
//        assert n.infinity == False;
//        n = 0xF;
//        assert n.infinity == False;
    }

    void testNaN() {
//        Nibble n = 0;
//        assert n.NaN == False;
//        n = 0xF;
//        assert n.NaN == False;
    }

    void testMagnitude() {
// TODO fix Nibble.magnitude
//        Nibble n = 0;
//        assert n.magnitude == 0;
//        n = 0xF;
//        assert n.magnitude == 100;
    }

    // ----- Stringable tests ----------------------------------------------------------------------

    void testAppendTo() {
        assert callAppendTo(0) == "0";
        assert callAppendTo(1) == "1";
        assert callAppendTo(2) == "2";
        assert callAppendTo(3) == "3";
        assert callAppendTo(4) == "4";
        assert callAppendTo(5) == "5";
        assert callAppendTo(6) == "6";
        assert callAppendTo(7) == "7";
        assert callAppendTo(8) == "8";
        assert callAppendTo(9) == "9";
        assert callAppendTo(10) == "A";
        assert callAppendTo(11) == "B";
        assert callAppendTo(12) == "C";
        assert callAppendTo(13) == "D";
        assert callAppendTo(14) == "E";
        assert callAppendTo(15) == "F";
    }

    String callAppendTo(Nibble n) {
        StringBuffer buf = new StringBuffer();
        n.appendTo(buf);
        return buf.toString();
    }

    void testEstimateStringLength() {
        Nibble n = 0;
        assert n.estimateStringLength() == 1;
        n = 1;
        assert n.estimateStringLength() == 1;
        n = 10;
        assert n.estimateStringLength() == 1;
        n = 15;
        assert n.estimateStringLength() == 1;
    }

    // ----- As Sequential tests -------------------------------------------------------------------

    void testDecAsSequential() {
// TODO does not work
//        Nibble     n1 = 12;
//        Sequential n2 = decSequential(n1);
//        assert n2.is(Nibble);
//        assert n2 == 11;
    }

    Sequential decSequential(Sequential n) {
        return n.prevValue();
    }

    void testIncAsSequential() {
// TODO does not work
//        Nibble     n1 = 10;
//        Sequential n2 = incSequential(n1);
//        assert n2.is(Nibble);
//        assert n2 == 11;
    }

    Sequential incSequential(Sequential n) {
        return n.nextValue();
    }
}
