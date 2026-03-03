class Int64Tests {

    @Inject Console console;

// 2 ^ 64 = 709551616

    void run() {
        console.print(">>>> Running Int64Tests >>>>");
        // Comparison tests
        testInt64CompareEq();
        testInt64CompareGe();
        testInt64CompareGt();
        testInt64CompareLe();
        testInt64CompareLt();

        // Field tests
        testInt64AsField();
        testInt64AsNullableField();
        testInt64AsNullableFieldNull();

        // Constant field and constructor tests
        testInt64AsConstField();
        testNullableIntAsConstField();
        testNullableIntAsConstFieldNull();

        // Method parameter tests
        testInt64AsParam();
        testInt64AsNullableParam();
        testInt64AsNullableParamNull();
        testInt64AsMultiParams();
        testInt64AsMultiNullableParams();

        // Method return tests
        testInt64Return();
        testInt64ConditionalReturn();
        testInt64ReturnStringInt64();
        testInt64ReturnInt64Int();
        testInt64ReturnTwoInt64();
        testNullableIntReturn();
        testNullableIntAsIntReturn();
        testIntAsNullableIntReturn();
        testNullableIntConditionalReturn();

        // GP/IP Op tests
        // Add
        testInt64OpAdd();
        testInt64OpAddInPlace();
        testInt64OpAddOverflow();
        testInt64OpAddFirstNegative();
        testInt64OpAddSecondNegative();
        testInt64OpAddBothNegative();
        // And
        testInt64OpAnd();
        // Complement
        testInt64Complement();
        // Inc
        testInt64OpInc();
        testInt64OpPreInc();
        testInt64OpPostInc();
        testInt64OpIncOverflow();
        testInt64OpIncNegative();
        // Dec
        testInt64OpDec();
        testInt64OpPreDec();
        testInt64OpPostDec();
        testInt64OpDecOverflow();
        testInt64OpDecNegative();
        // Div
        testInt64OpDiv();
        testInt64OpDivInPlace();
        // Mod
        testInt64OpMod();
        // Multiply
        testInt64OpMultiply();
        testInt64OpMultiply();
        testInt64OpMultiplyInPlace();
        // Negate
        testInt64OpNegate();
        // Or
        testInt64OpOr();
        testInt64OpOrInPlace();
        // Shl
        testInt64OpShiftLeft();
        testInt64OpShiftLeftZero();
        testInt64OpShiftLeft64();
        testInt64OpShiftLeft64();
        testInt64OpShiftLeft68();
        testInt64OpShiftLeftMinus4();
        // Shr
        testInt64OpShiftRight();
        testInt64OpShiftRightNegative();
        testInt64OpShiftRightZero();
        testInt64OpShiftRight64();
        testInt64OpShiftRight68();
        testInt64OpShiftRightMinus4();
        // Sub
        testInt64OpSubBig();
        testInt64OpSub();
        testInt64OpSubOverflow();
        testInt64OpSubFirstNegative();
        testInt64OpSubSecondNegative();
        testInt64OpSubBothNegative();
        // Ushr
        testInt64OpUnsignedShiftRight();
        testInt64OpUnsignedShiftRightNegative();
        testInt64OpUnsignedShiftRightZero();
        testInt64OpUnsignedShiftRight64();
        testInt64OpUnsignedShiftRight68();
        testInt64OpUnsignedShiftRightMinus4();
        // Xor
        testInt64OpXor();
        testInt64OpXorInPlace();

        console.print("<<<<< Finished Int64Tests Conversion tests >>>>>");
    }

    // ----- comparison tests ----------------------------------------------------------------------

    void testInt64CompareEq() {
        Int n = 1234;
        assert n == 1234;
    }

    void testInt64CompareGe() {
        Int n = 1234;
        assert n >= 1000;
        assert n >= 1234;
    }

    void testInt64CompareGt() {
        Int n = 1234;
        assert n > 1233;
    }

    void testInt64CompareLe() {
        Int n = 1234;
        assert n <= 1235;
        assert n <= 1234;
    }

    void testInt64CompareLt() {
        Int n = 1234;
        assert n < 1235;
    }

    // ----- field tests ---------------------------------------------------------------------------

    void testInt64AsField() {
        Int64AsField n = new Int64AsField();
        assert n.Field == 1234;
    }

    static class Int64AsField {
        Int Field = 1234;
    }

    void testInt64AsNullableField() {
        Int64AsNullableField n = new Int64AsNullableField();
        assert n.field == 9876;
    }

    static class Int64AsNullableField {
        Int? field = 9876;
    }

    void testInt64AsNullableFieldNull() {
        Int64NullField n = new Int64NullField();
        assert n.field == Null;
    }

    static class Int64NullField {
        Int? field = Null;
    }

    void testInt64AsConstField() {
        Int       n = 1234;
        NumberHolder h = new NumberHolder(n);
        assert h.n == 1234;
    }

    static const NumberHolder(Int n) {
    }

    void testNullableIntAsConstField() {
        Int                  n = 9876543;
        NullableNumberHolder h = new NullableNumberHolder(n);
        assert h.n == n;
    }

    void testNullableIntAsConstFieldNull() {
        NullableNumberHolder h = new NullableNumberHolder(Null);
        assert h.n == Null;
    }

    static const NullableNumberHolder(Int? n) {
    }

    // ----- parameter tests -----------------------------------------------------------------------

    void testInt64AsParam() {
        Int n = 1234;
        int64Param(n);
    }

    void int64Param(Int n) {
        assert n == 1234;
    }

    void testInt64AsNullableParam() {
        Int n = 1234;
        Boolean isNull = int64NullableParam(n);
        assert isNull == False;
    }

    void testInt64AsNullableParamNull() {
        Boolean isNull = int64NullableParam(Null);
        assert isNull == True;
    }

    Boolean int64NullableParam(Int? n) {
        if (n.is(Int)) {
            assert n == 1234;
            return False;
        }
        return True;
    }

    void testInt64AsMultiParams() {
        int64MultiParams(1234, 98765432);
    }

    void int64MultiParams(Int n1, Int n2) {
        assert n1 == 1234;
        assert n2 == 98765432;
    }

    void testInt64AsMultiNullableParams() {
        Boolean b1;
        Boolean b2;
        (b1, b2) = int64MultiNullableParams(1234, 12349876);
        assert b1 == True;
        assert b2 == True;
        (b1, b2) = int64MultiNullableParams(1234, Null);
        assert b1 == True;
        assert b2 == False;
        (b1, b2) = int64MultiNullableParams(Null, 12349876);
        assert b1 == False;
        assert b2 == True;
        (b1, b2) = int64MultiNullableParams(Null, Null);
        assert b1 == False;
        assert b2 == False;
    }

    (Boolean, Boolean) int64MultiNullableParams(Int? n1, Int? n2) {
        Boolean b1 = False;
        Boolean b2 = False;
        if (n1.is(Int)) {
            assert n1 == 1234;
            b1 = True;
        }
        if (n2.is(Int)) {
            assert n2 == 12349876;
            b2 = True;
        }
        return b1, b2;
    }

    // ----- return tests --------------------------------------------------------------------------

    void testInt64Return() {
        Int n = returnInt64();
        assert n == 1234;
    }

    Int returnInt64() {
        Int n = 1234;
        return n;
    }

    void testInt64ConditionalReturn() {
        assert Int n := returnConditionalInt64();
        assert n == 1234;
    }

    conditional Int returnConditionalInt64() {
        Int n = 1234;
        return True, n;
    }

    void testInt64ReturnStringInt64() {
        (String s, Int n) = returnStringInt64();
        assert s == "Foo";
        assert n == 9876;
    }

    (String, Int) returnStringInt64() {
        Int n = 9876;
        return "Foo", n;
    }

    void testInt64ReturnInt64Int() {
        (Int n, Int i) = returnInt64Int();
        assert n == 9999;
        assert i == 19;
    }

    (Int, Int) returnInt64Int() {
        Int n = 9999;
        return n, 19;
    }

    void testInt64ReturnTwoInt64() {
        (Int n1, Int n2) = returnTwoInt64();
        assert n1 == 4567;
        assert n2 == 1290;
    }

    (Int, Int) returnTwoInt64() {
        Int n1 = 4567;
        Int n2 = 1290;
        return n1, n2;
    }

    void testNullableIntReturn() {
        Int? n = returnNullableInt(True);
        assert n == 987654321;
        n = returnNullableInt(False);
        assert n == Null;
    }

    Int? returnNullableInt(Boolean b) {
        Int n = 987654321;
        if (b) {
            return n;
        }
        return Null;
    }

    void testNullableIntAsIntReturn() {
        Int n = returnNullableIntAsInt();
        assert n == 98987676;
    }

    Int returnNullableIntAsInt() {
        Int? n = 98987676;
        return n;
    }

    void testIntAsNullableIntReturn() {
        Int? n = returnIntAsNullableInt();
        assert n == 98987676;
    }

    Int? returnIntAsNullableInt() {
        Int n = 98987676;
        return n;
    }

    void testNullableIntConditionalReturn() {
        assert Int? n := returnConditionalNullableInt(0);
        assert n == 191919;
        assert n := returnConditionalNullableInt(1);
        assert n == Null;
        assert returnConditionalNullableInt(2) == False;
    }

    conditional Int? returnConditionalNullableInt(Int i) {
        Int? n = 191919;
        if (i == 0) {
            return True, n;
        }
        if (i == 1) {
            return True, Null;
        }
        return False;
    }

    // ----- Op tests (Add) ------------------------------------------------------------------------

    void testInt64OpAddBig() {
        Int64 n1 = 709551616;
        Int64 n2 = 709200000;
        Int64 n3 = n1 + n2;
        assert n3 == 147418751616;
    }

    void testInt64OpAdd() {
        Int64 n1 = 1000;
        Int64 n2 = 19;
        Int64 n3 = n1 + n2;
        assert n3 == 1019;
    }

    void testInt64OpAddInPlace() {
        Int64 n1 = 709551616;
        n1 += 709200000;
        assert n1 == 1418751616;
    }

    void testInt64OpAddFirstNegative() {
        Int64 n1 = -709551616;
        Int64 n2 = 709200000;
        Int64 n3 = n1 + n2;
        assert n3 == -351616;
    }

    void testInt64OpAddSecondNegative() {
        Int64 n1 = 709551616;
        Int64 n2 = -709200000;
        Int64 n3 = n1 + n2;
        assert n3 == 351616;
    }

    void testInt64OpAddBothNegative() {
        Int64 n1 = -709551616;
        Int64 n2 = -709200000;
        Int64 n3 = n1 + n2;
        assert n3 == -1418751616;
    }

    void testInt64OpAddOverflow() {
        Int64 n1 = Int64.MaxValue;
        Int64 n2 = 1;
        Int64 n3 = n1 + n2;
        assert n3 == Int64.MinValue;
    }

    // ----- Op tests (Sub) ------------------------------------------------------------------------

    void testInt64OpSubBig() {
        Int64 n1 = 709551616;
        Int64 n2 = 709200000;
        Int64 n3 = n1 - n2;
        assert n3 == 351616;
    }

    void testInt64OpSub() {
        Int64 n1 = 1000;
        Int64 n2 = 19;
        Int64 n3 = n1 - n2;
        assert n3 == 981;
    }

    void testInt64OpSubInPlace() {
        Int64 n1 = 709551616;
        n1 -= 709200000;
        assert n1 == 351616;
    }

    void testInt64OpSubFirstNegative() {
        Int64 n1 = -709551616;
        Int64 n2 = 709200000;
        Int64 n3 = n1 - n2;
        assert n3 == -1418751616;
    }

    void testInt64OpSubSecondNegative() {
        Int64 n1 = 709551616;
        Int64 n2 = -709200000;
        Int64 n3 = n1 - n2;
        assert n3 == 1418751616;
    }

    void testInt64OpSubBothNegative() {
        Int64 n1 = -709551616;
        Int64 n2 = -709200000;
        Int64 n3 = n1 - n2;
        assert n3 == -351616;
    }

    void testInt64OpSubOverflow() {
        Int64 n1 = Int64.MinValue;
        Int64 n2 = 1;
        Int64 n3 = n1 - n2;
        assert n3 == Int64.MaxValue;
    }

    // ----- Op tests (logical And) ----------------------------------------------------------------

    void testInt64OpAnd() {
        Int64 n1 = 0x00F2_F0F2_F0F0_F0F0;
        Int64 n2 = 0x0AAA_AAAA_AAAA_AAAA;
        Int64 n3 = n1 & n2;
        assert n3 == 0x00A2_A0A2_A0A0_A0A0;
    }

    // ----- Op tests (Complement ~) ---------------------------------------------------------------

    void testInt64Complement() {
        Int64 value1 = 0;
        Int64 value2 = 0x5ABC5432;
        value1 = ~value2;
        assert value1 == -1522291763;
    }

    // ----- Op tests (Inc ++) ---------------------------------------------------------------------

    void testInt64OpInc() {
        Int64 n = 1234;
        n++;
        assert n == 1235;
    }

    void testInt64OpPreInc() {
        Int64 n1 = 1234;
        Int64 n2 = ++n1;
        assert n1 == 1235;
        assert n2 == 1235;
    }

    void testInt64OpPostInc() {
        Int64 n1 = 1234;
        Int64 n2 = n1++;
        assert n1 == 1235;
        assert n2 == 1234;
    }

    void testInt64OpIncOverflow() {
        Int64 n = Int64.MaxValue;
        n++;
        assert n == Int64.MinValue;
    }

    void testInt64OpIncNegative() {
        Int64 n = -709551616;
        n++;
        assert n == -709551615;
    }

    // ----- Op tests (Dec --) ---------------------------------------------------------------------

    void testInt64OpDec() {
        Int64 n = 1234;
        n--;
        assert n == 1233;
    }

    void testInt64OpPreDec() {
        Int64 n1 = 1234;
        Int64 n2 = --n1;
        assert n1 == 1233;
        assert n2 == 1233;
    }

    void testInt64OpPostDec() {
        Int64 n1 = 1234;
        Int64 n2 = n1--;
        assert n1 == 1233;
        assert n2 == 1234;
    }

    void testInt64OpDecOverflow() {
        Int64 n = Int64.MinValue;
        n--;
        assert n == Int64.MaxValue;
    }

    void testInt64OpDecNegative() {
        Int64 n = -709551616;
        n--;
        assert n == -709551617;
    }

    // ----- Op tests (divide) ---------------------------------------------------------------------

    void testInt64OpDiv() {
        Int64 n = 1234;
        Int64 n2 = n / 10;
        assert n2 == 123;
    }

    void testInt64OpDivInPlace() {
        Int64 n = 709551616;
        n /= 10;
        assert n == 70955161;
    }

    // ----- Op tests (modulus) --------------------------------------------------------------------

    void testInt64OpMod() {
        Int64 n = 1234;
        Int64 n2 = n % 10;
        assert n2 == 4;
    }

    // ----- Op tests (multiply) -------------------------------------------------------------------

    void testInt64OpMultiply() {
        Int64 n = 1234;
        Int64 n2 = n * 10;
        assert n2 == 12340;
    }

    void testInt64OpMultiplyInPlace() {
        Int64 n = 709551616;
        n *= 10;
        assert n == 7095516160;
    }

    // ----- Op tests (negate) ---------------------------------------------------------------------

    void testInt64OpNegate() {
        Int64 n = 1234;
        Int64 n2 = -n;
        assert n2 == -1234;
    }

    // ----- Op tests (logical Or) -----------------------------------------------------------------

    void testInt64OpOr() {
        Int64 n1 = 0x00F2_F0F2_F0F0_F0F0;
        Int64 n2 = 0x0AA0_AAAA_AAAA_AAAA;
        Int64 n3 = n1 | n2;
        assert n3 == 0x0AF2_FAFA_FAFA_FAFA;
    }

    void testInt64OpOrInPlace() {
        Int64 n = 0x00F2_F0F2_F0F0_F0F0;
        n |= 0x0AA0_AAAA_AAAA_AAAA;
        assert n == 0x0AF2_FAFA_FAFA_FAFA;
    }

    // ----- Op tests (Shift left <<) --------------------------------------------------------------

    void testInt64OpShiftLeft() {
        Int64 n = 0x1142_F0F2_F0F0_F0F0;
        Int64 n2 = n << 8;
        assert n2 == 0x42F0_F2F0_F0F0_F000;
    }

    void testInt64OpShiftLeftZero() {
        Int64 n = 0x00F2_F0F2_F0F0_F0F0;
        Int64 n2 = n << 0;
        assert n2 == n;
    }

    void testInt64OpShiftLeft64() {
        Int64 n = 1;
        Int64 n2 = n << 64;
        assert n2 == 1; // equivalent to << 0
    }

    void testInt64OpShiftLeft68() {
        Int64 n = 1;
        Int64 n2 = n << 68;
        assert n2 == 16; // 68 & 0x3F equivalent to << 4
    }

    void testInt64OpShiftLeftMinus4() {
        Int64 n = 1;
        Int64 n2 = n << -4;
        assert n2 == 0x1000_0000_0000_0000;
        // -4 == 0xFC,  0xFC & 0x3F == 0x3C equivalent to 1 << 60
    }

    // ----- Op tests (Shift right >>) -------------------------------------------------------------

    void testInt64OpShiftRight() {
        Int64 n =   0x1142_F0F2_F0F0_F0F0;
        Int64 n2 = n >> 8;
        assert n2 == 0x0011_42F0_F2F0_F0F0;
    }

    void testInt64OpShiftRightNegative() {
        Int64 n = -2000;
        Int64 n2 = n >> 8;
        assert n2 == -8; // preserved sign bit
    }

    void testInt64OpShiftRightZero() {
        Int64 n =   0x1142_F0F2_F0F0_F0F0;
        Int64 n2 = n >> 0;
        assert n2 == 0x1142_F0F2_F0F0_F0F0;
    }

    void testInt64OpShiftRight64() {
        Int64 n = 1;
        Int64 n2 = n >> 64;
        assert n2 == 1; // 0x40 & 0x3F equivalent to >> 0
    }

    void testInt64OpShiftRight68() {
        Int64 n = 16;
        Int64 n2 = n >> 132;
        assert n2 == 1; // 68 == 44 & 0x7F equivalent to >> 4
    }

    void testInt64OpShiftRightMinus4() {
        Int64 n = 0x1000_0000_0000_0000;
        Int64 n2 = n >> -4;
        assert n2 == 1;
        // -4 == 0xFC,  0xFC & 0x3F == 0x3C equivalent to 1 >> 60
    }

    // ----- Op tests (Unsigned shift right >>>) ---------------------------------------------------

    void testInt64OpUnsignedShiftRight() {
        Int64 n =   0x1142_F0F2_F0F0_F0F0;
        Int64 n2 = n >>> 8;
        assert n2 == 0x0011_42F0_F2F0_F0F0;
    }

    void testInt64OpUnsignedShiftRightNegative() {
        Int64 n = -2000; // 0xFFFF_FFFF_FFFF_F830
        Int64 n2 = n >>> 8;
        assert n2 == 0x00FF_FFFF_FFFF_FFF8;
    }

    void testInt64OpUnsignedShiftRightZero() {
        Int64 n =   0x1142_F0F2_F0F0_F0F0;
        Int64 n2 = n >>> 0;
        assert n2 == 0x1142_F0F2_F0F0_F0F0;
    }

    void testInt64OpUnsignedShiftRight64() {
        Int64 n = 1;
        Int64 n2 = n >>> 64;
        assert n2 == 1; // 64 == 0x40 & 0x3F equivalent to >>> 0
    }

    void testInt64OpUnsignedShiftRight68() {
        Int64 n = 16;
        Int64 n2 = n >>> 68;
        assert n2 == 1; // 68 == 44 & 0x7F equivalent to >> 4
    }

    void testInt64OpUnsignedShiftRightMinus4() {
        Int64 n = 0x1000_0000_0000_0000;
        Int64 n2 = n >>> -4;
        assert n2 == 1;
        // -4 == 0xFC,  0xFC & 0x3F == 0x3C equivalent to 1 >> 60
    }

    // ----- Op tests (logical Xor) ----------------------------------------------------------------

    void testInt64OpXor() {
        Int64 n1 = 0x00F2_F0F2_F0F0_F0F0;
        Int64 n2 = 0x0AA0_AAAA_AAAA_AAAA;
        Int64 n3 = n1 ^ n2;
        assert n3 == 0x0A52_5A58_5A5A_5A5A;
    }

    void testInt64OpXorInPlace() {
        Int64 n = 0x00F2_F0F2_F0F0_F0F0;
        n ^= 0x0AA0_AAAA_AAAA_AAAA;
        assert n == 0x0A52_5A58_5A5A_5A5A;
    }
}
