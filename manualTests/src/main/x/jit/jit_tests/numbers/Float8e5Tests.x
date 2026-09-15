class Float8e5Tests {

    @Inject Console console;

    void run() {
        // Comparison tests
        testFloat8e5CompareEq();
        testFloat8e5CompareGe();
        testFloat8e5CompareGt();
        testFloat8e5CompareLe();
        testFloat8e5CompareLt();

        // Field tests
        testFloat8e5AsField();
        testFloat8e5AsNullableField();
        testFloat8e5AsNullableFieldNull();

        // Constant field and constructor tests
        testFloat8e5AsConstField();
        testNullableFloat8e5AsConstField();
        testNullableFloat8e5AsConstFieldNull();

        // Method parameter tests
        testFloat8e5AsParam();
        testFloat8e5AsNullableParam();
        testFloat8e5AsNullableParamNull();
        testFloat8e5AsMultiParams();

        // Method return tests
        testFloat8e5Return();
        testFloat8e5ConditionalReturn();
        testFloat8e5ReturnStringFloat8e5();
        testFloat8e5ReturnTwoFloat8e5();
        testNullableFloat8e5Return();

        // Number tests
        testFloat8e5toArray();
        testFloat8e5Rounding();
        testFloat8e5Negate();

        // FP8 format tests
        testFloat8e5Encoding();
        testFloat8e5Limits();

        // Stringable
        testAppendTo();
        testEstimateStringLength();
    }

    // ----- comparison tests ----------------------------------------------------------------------

    void testFloat8e5CompareEq() {
        Float8e5 n = 1.0;
        assert n == 1.0;
    }

    void testFloat8e5CompareGe() {
        Float8e5 n = 4.0;
        assert n >= 2.0;
        assert n >= 4.0;
    }

    void testFloat8e5CompareGt() {
        Float8e5 n = 4.0;
        assert n > 2.0;
    }

    void testFloat8e5CompareLe() {
        Float8e5 n = 2.0;
        assert n <= 4.0;
        assert n <= 2.0;
    }

    void testFloat8e5CompareLt() {
        Float8e5 n = 2.0;
        assert n < 4.0;
    }

    // ----- field tests ---------------------------------------------------------------------------

    void testFloat8e5AsField() {
        Float8e5AsField n = new Float8e5AsField();
        assert n.field == 4.0;
    }

    static class Float8e5AsField {
        Float8e5 field = 4.0;
    }

    void testFloat8e5AsNullableField() {
        Float8e5AsNullableField n = new Float8e5AsNullableField();
        assert n.field == 1024.0;
    }

    static class Float8e5AsNullableField {
        Float8e5? field = 1024.0;
    }

    void testFloat8e5AsNullableFieldNull() {
        Float8e5NullField n = new Float8e5NullField();
        assert n.field == Null;
    }

    static class Float8e5NullField {
        Float8e5? field = Null;
    }

    void testFloat8e5AsConstField() {
        Float8e5          n = 4.0;
        NumberHolder h = new NumberHolder(n);
        assert h.n == 4.0;
    }

    static const NumberHolder(Float8e5 n) {
    }

    void testNullableFloat8e5AsConstField() {
        Float8e5                  n = 1024.0;
        NullableNumberHolder h = new NullableNumberHolder(n);
        assert h.n == n;
    }

    void testNullableFloat8e5AsConstFieldNull() {
        NullableNumberHolder h = new NullableNumberHolder(Null);
        assert h.n == Null;
    }

    static const NullableNumberHolder(Float8e5? n) {
    }

    // ----- parameter tests -----------------------------------------------------------------------

    void testFloat8e5AsParam() {
        Float8e5 n = 4.0;
        Float8e5Param(n);
    }

    void Float8e5Param(Float8e5 n) {
        assert n == 4.0;
    }

    void testFloat8e5AsNullableParam() {
        Float8e5     n      = 4.0;
        Boolean isNull = Float8e5NullableParam(n);
        assert isNull == False;
    }

    void testFloat8e5AsNullableParamNull() {
        Boolean isNull = Float8e5NullableParam(Null);
        assert isNull == True;
    }

    Boolean Float8e5NullableParam(Float8e5? n) {
        if (n.is(Float8e5)) {
            assert n == 4.0;
            return False;
        }
        return True;
    }

    void testFloat8e5AsMultiParams() {
        Float8e5MultiParams(4.0, 1024.0);
    }

    void Float8e5MultiParams(Float8e5 n1, Float8e5 n2) {
        assert n1 == 4.0;
        assert n2 == 1024.0;
    }

    // ----- return tests --------------------------------------------------------------------------

    void testFloat8e5Return() {
        Float8e5 n = returnFloat8e5();
        assert n == 4.0;
    }

    Float8e5 returnFloat8e5() {
        Float8e5 n = 4.0;
        return n;
    }

    void testFloat8e5ConditionalReturn() {
        assert Float8e5 n := returnConditionalFloat8e5();
        assert n == 4.0;
    }

    conditional Float8e5 returnConditionalFloat8e5() {
        Float8e5 n = 4.0;
        return True, n;
    }

    void testFloat8e5ReturnStringFloat8e5() {
        (String s, Float8e5 n) = returnStringFloat8e5();
        assert s == "Foo";
        assert n == 2.0;
    }

    (String, Float8e5) returnStringFloat8e5() {
        Float8e5 n = 2.0;
        return "Foo", n;
    }

    void testFloat8e5ReturnTwoFloat8e5() {
        (Float8e5 n1, Float8e5 n2) = returnTwoFloat8e5();
        assert n1 == 2.0;
        assert n2 == 4.0;
    }

    (Float8e5, Float8e5) returnTwoFloat8e5() {
        Float8e5 n1 = 2.0;
        Float8e5 n2 = 4.0;
        return n1, n2;
    }

    void testNullableFloat8e5Return() {
        Float8e5? n = returnNullableFloat8e5(True);
        assert n == 1024.0;
        n = returnNullableFloat8e5(False);
        assert n == Null;
    }

    Float8e5? returnNullableFloat8e5(Boolean b) {
        Float8e5 n = 1024.0;
        if (b) {
            return n;
        }
        return Null;
    }

    // ----- Number tests --------------------------------------------------------------------------

    void testFloat8e5toArray() {
        Float8e5 value = 4.0;

        assert new Float8e5(value.toBitArray()) == value;
        assert new Float8e5(value.toByteArray()) == value;
    }

    void testFloat8e5Rounding() {
        Float8e5 positive = 3.5;
        assert positive.floor() == 3.0;
        assert positive.ceil() == 4.0;
        assert positive.round(TowardZero) == 3.0;

        Float8e5 negative = -3.5;
        assert negative.floor() == -4.0;
        assert negative.ceil() == -3.0;
        assert negative.round(TowardZero) == -3.0;
    }

    void testFloat8e5Negate() {
        Float8e5 n = 4.0;
        assert -n == -4.0;
        assert -(-n) == n;
        assert (-n) < n;
        assert (-n).abs() == n;
    }

    // ----- FP8 format tests ----------------------------------------------------------------------

    /**
     * An FP8 value is carried as its 8-bit encoding, so the encoding itself is worth asserting.
     */
    void testFloat8e5Encoding() {
        Float8e5 one = 1.0;
        Byte[] oneBytes = [0x3C];
        assert one.toByteArray()[0] == 0x3C;
        assert new Float8e5(oneBytes) == one;

        Float8e5 max = 57344.0;
        assert max.toByteArray()[0] == 0x7B;
        assert max.finite;
        assert !max.NaN;
    }

    /**
     * Float8e5 is the IEEE-style "E5M2": the all-1s exponent is reserved, giving infinities at #7C
     * and #FC and NaNs above those.
     */
    void testFloat8e5Limits() {
        Float8e5 max = 57344.0;
        assert !max.infinity;
        assert max.finite;

        assert Float8e5.PositiveInfinity.infinity;
        assert Float8e5.NegativeInfinity.infinity;
        assert !Float8e5.PositiveInfinity.finite;
        assert Float8e5.PositiveInfinity.toByteArray()[0] == 0x7C;
        assert Float8e5.PositiveInfinity > max;

        assert Float8e5.PositiveNaN.NaN;
        assert !Float8e5.PositiveNaN.finite;

        // smallest subnormal, 2^-16
        Float8e5 tiny = 0.0000152587890625;
        assert tiny.toByteArray()[0] == 0x01;
        assert tiny > 0.0;
    }


    // ----- Stringable tests ----------------------------------------------------------------------

    void testAppendTo() {
        assert callAppendTo(0) == "0.0";
        assert callAppendTo(1) == "1.0";
        assert callAppendTo(-1) == "-1.0";
        assert callAppendTo(10) == "10.0";
        assert callAppendTo(-10) == "-10.0";
    }

    String callAppendTo(Float8e5 n) {
        StringBuffer buf = new StringBuffer();
        n.appendTo(buf);
        return buf.toString();
    }

    void testEstimateStringLength() {
        Float8e5 n = 0;
        assert n.estimateStringLength() == 3; // "0.0"
        n = 1;
        assert n.estimateStringLength() == 3; // "1.0"
        n = -1;
        assert n.estimateStringLength() == 4; // "-1.0"
        n = 10;
        assert n.estimateStringLength() == 4; // "10.0"
        n = -10;
        assert n.estimateStringLength() == 5; // "-10.0"
    }
}
