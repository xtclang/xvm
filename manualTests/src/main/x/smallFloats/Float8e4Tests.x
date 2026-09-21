class Float8e4Tests {

    @Inject Console console;

    void run() {
        // Comparison tests
        testFloat8e4CompareEq();
        testFloat8e4CompareGe();
        testFloat8e4CompareGt();
        testFloat8e4CompareLe();
        testFloat8e4CompareLt();

        // Field tests
        testFloat8e4AsField();
        testFloat8e4AsNullableField();
        testFloat8e4AsNullableFieldNull();

        // Constant field and constructor tests
        testFloat8e4AsConstField();
        testNullableFloat8e4AsConstField();
        testNullableFloat8e4AsConstFieldNull();

        // Method parameter tests
        testFloat8e4AsParam();
        testFloat8e4AsNullableParam();
        testFloat8e4AsNullableParamNull();
        testFloat8e4AsMultiParams();

        // Method return tests
        testFloat8e4Return();
        testFloat8e4ConditionalReturn();
        testFloat8e4ReturnStringFloat8e4();
        testFloat8e4ReturnTwoFloat8e4();
        testNullableFloat8e4Return();

        // Number tests
        testFloat8e4toArray();
        testArrays();
        testNegativeFields();
        testFloat8e4Rounding();
        testFloat8e4Negate();
        testFloat8e4Arithmetic();

        // FP8 format tests
        testFloat8e4Encoding();
        testFloat8e4Limits();

        // Stringable
        testAppendTo();
        testEstimateStringLength();
    }

    // ----- comparison tests ----------------------------------------------------------------------

    void testFloat8e4CompareEq() {
        Float8e4 n = 1.0;
        assert n == 1.0;
    }

    void testFloat8e4CompareGe() {
        Float8e4 n = 4.0;
        assert n >= 2.0;
        assert n >= 4.0;
    }

    void testFloat8e4CompareGt() {
        Float8e4 n = 4.0;
        assert n > 2.0;
    }

    void testFloat8e4CompareLe() {
        Float8e4 n = 2.0;
        assert n <= 4.0;
        assert n <= 2.0;
    }

    void testFloat8e4CompareLt() {
        Float8e4 n = 2.0;
        assert n < 4.0;
    }

    // ----- field tests ---------------------------------------------------------------------------

    void testFloat8e4AsField() {
        Float8e4AsField n = new Float8e4AsField();
        assert n.field == 4.0;
    }

    static class Float8e4AsField {
        Float8e4 field = 4.0;
    }

    void testFloat8e4AsNullableField() {
        Float8e4AsNullableField n = new Float8e4AsNullableField();
        assert n.field == 256.0;
    }

    static class Float8e4AsNullableField {
        Float8e4? field = 256.0;
    }

    void testFloat8e4AsNullableFieldNull() {
        Float8e4NullField n = new Float8e4NullField();
        assert n.field == Null;
    }

    static class Float8e4NullField {
        Float8e4? field = Null;
    }

    void testFloat8e4AsConstField() {
        Float8e4          n = 4.0;
        NumberHolder h = new NumberHolder(n);
        assert h.n == 4.0;
    }

    static const NumberHolder(Float8e4 n) {
    }

    void testNullableFloat8e4AsConstField() {
        Float8e4                  n = 256.0;
        NullableNumberHolder h = new NullableNumberHolder(n);
        assert h.n == n;
    }

    void testNullableFloat8e4AsConstFieldNull() {
        NullableNumberHolder h = new NullableNumberHolder(Null);
        assert h.n == Null;
    }

    static const NullableNumberHolder(Float8e4? n) {
    }

    // ----- parameter tests -----------------------------------------------------------------------

    void testFloat8e4AsParam() {
        Float8e4 n = 4.0;
        Float8e4Param(n);
    }

    void Float8e4Param(Float8e4 n) {
        assert n == 4.0;
    }

    void testFloat8e4AsNullableParam() {
        Float8e4     n      = 4.0;
        Boolean isNull = Float8e4NullableParam(n);
        assert isNull == False;
    }

    void testFloat8e4AsNullableParamNull() {
        Boolean isNull = Float8e4NullableParam(Null);
        assert isNull == True;
    }

    Boolean Float8e4NullableParam(Float8e4? n) {
        if (n.is(Float8e4)) {
            assert n == 4.0;
            return False;
        }
        return True;
    }

    void testFloat8e4AsMultiParams() {
        Float8e4MultiParams(4.0, 256.0);
    }

    void Float8e4MultiParams(Float8e4 n1, Float8e4 n2) {
        assert n1 == 4.0;
        assert n2 == 256.0;
    }

    // ----- return tests --------------------------------------------------------------------------

    void testFloat8e4Return() {
        Float8e4 n = returnFloat8e4();
        assert n == 4.0;
    }

    Float8e4 returnFloat8e4() {
        Float8e4 n = 4.0;
        return n;
    }

    void testFloat8e4ConditionalReturn() {
        assert Float8e4 n := returnConditionalFloat8e4();
        assert n == 4.0;
    }

    conditional Float8e4 returnConditionalFloat8e4() {
        Float8e4 n = 4.0;
        return True, n;
    }

    void testFloat8e4ReturnStringFloat8e4() {
        (String s, Float8e4 n) = returnStringFloat8e4();
        assert s == "Foo";
        assert n == 2.0;
    }

    (String, Float8e4) returnStringFloat8e4() {
        Float8e4 n = 2.0;
        return "Foo", n;
    }

    void testFloat8e4ReturnTwoFloat8e4() {
        (Float8e4 n1, Float8e4 n2) = returnTwoFloat8e4();
        assert n1 == 2.0;
        assert n2 == 4.0;
    }

    (Float8e4, Float8e4) returnTwoFloat8e4() {
        Float8e4 n1 = 2.0;
        Float8e4 n2 = 4.0;
        return n1, n2;
    }

    void testNullableFloat8e4Return() {
        Float8e4? n = returnNullableFloat8e4(True);
        assert n == 256.0;
        n = returnNullableFloat8e4(False);
        assert n == Null;
    }

    Float8e4? returnNullableFloat8e4(Boolean b) {
        Float8e4 n = 256.0;
        if (b) {
            return n;
        }
        return Null;
    }

    // ----- Number tests --------------------------------------------------------------------------

    void testFloat8e4toArray() {
        Float8e4 value = 4.0;

        assert new Float8e4(value.toBitArray()) == value;
        assert new Float8e4(value.toByteArray()) == value;
    }

    void testFloat8e4Rounding() {
        Float8e4 positive = 3.5;
        assert positive.floor() == 3.0;
        assert positive.ceil() == 4.0;
        assert positive.round(TowardZero) == 3.0;

        Float8e4 negative = -3.5;
        assert negative.floor() == -4.0;
        assert negative.ceil() == -3.0;
        assert negative.round(TowardZero) == -3.0;
    }

    void testFloat8e4Negate() {
        Float8e4 n = 4.0;
        assert -n == -4.0;
        assert -(-n) == n;
        assert (-n) < n;
        assert (-n).abs() == n;
    }

    /**
     * Arithmetic is performed at wider precision and must be rounded back into this format: a
     * result that is not representable here is a bug, not a more precise answer. E4M3FN has no infinity, so an out-of-range result saturates.
     */
    void testFloat8e4Arithmetic() {
        Float8e4 a = 1.0;
        Float8e4 b = 2.0;
        Float8e4 c = 3.0;

        assert a + b == 3.0;
        assert b + b == 4.0;
        assert c - a == 2.0;
        assert a - b == -1.0;
        assert b * c == 6.0;
        assert c / b == 1.5;
        assert a - a == 0.0;

        // the significand is far too short to hold this, so it must round away entirely
        Float8e4 tiny = 0.0625;
        assert a + tiny == 1.0;

        Float8e4 max = 448.0;
        assert max + max == 448.0;
        assert (max + max).infinity == False;
    }

    // ----- FP8 format tests ----------------------------------------------------------------------

    /**
     * An FP8 value is carried as its 8-bit encoding, so the encoding itself is worth asserting.
     */
    void testFloat8e4Encoding() {
        Float8e4 one = 1.0;
        Byte[] oneBytes = [0x38];
        assert one.toByteArray()[0] == 0x38;
        assert new Float8e4(oneBytes) == one;

        Float8e4 max = 448.0;
        assert max.toByteArray()[0] == 0x7E;
        assert max.finite;
        assert !max.NaN;
    }

    /**
     * Float8e4 is the OCP "E4M3FN" variant: the all-1s exponent is not reserved, so the format has
     * no infinities and its only NaN encodings are #7F and #FF.
     */
    void testFloat8e4Limits() {
        Float8e4 max = 448.0;
        assert !max.infinity;
        assert max.finite;

        // 256.0 is #78, which in an IEEE-style E4M3 would have been an infinity
        Float8e4 v256 = 256.0;
        assert v256.toByteArray()[0] == 0x78;
        assert !v256.infinity;
        assert !v256.NaN;
        assert v256.finite;

        // smallest subnormal, 2^-9
        Float8e4 tiny = 0.001953125;
        assert tiny.toByteArray()[0] == 0x01;
        assert tiny > 0.0;

        assert Float8e4.PositiveNaN.NaN;
        assert Float8e4.NegativeNaN.NaN;
        assert !Float8e4.PositiveNaN.finite;
    }

    // ----- Stringable tests ----------------------------------------------------------------------

    void testAppendTo() {
        assert callAppendTo(0) == "0.0";
        assert callAppendTo(1) == "1.0";
        assert callAppendTo(-1) == "-1.0";
        assert callAppendTo(10) == "10.0";
        assert callAppendTo(-10) == "-10.0";
    }

    String callAppendTo(Float8e4 n) {
        StringBuffer buf = new StringBuffer();
        n.appendTo(buf);
        return buf.toString();
    }

    void testEstimateStringLength() {
        Float8e4 n = 0;
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

    void testNegativeFields() {
        Float8e4AsField holder = new Float8e4AsField();
        holder.field = -4.0;
        assert holder.field == -4.0;
        assert holder.field.toFloat32() == -4.0;
        assert holder.field.toByteArray()[0] == 0xC8;

        Float8e4AsNullableField nullable = new Float8e4AsNullableField();
        nullable.field = -4.0;
        assert nullable.field == -4.0;
        nullable.field = Null;
        assert nullable.field == Null;
    }

    void testArrays() {
        Float8e4[] literal = [1.0, -2.0, 4.0];
        assert literal.size == 3;
        assert literal[0] == 1.0 && literal[1] == -2.0 && literal[2] == 4.0;

        Float8e4[] fixed = new Float8e4[17](-4.0);
        assert fixed.mutability == Fixed;
        assert fixed[0] == -4.0 && fixed[7] == -4.0 && fixed[8] == -4.0 && fixed[16] == -4.0;
        fixed[8] = 2.0;
        fixed[8] += 1.0;
        assert fixed[7] == -4.0 && fixed[8] == 3.0 && fixed[9] == -4.0;

        Float8e4[] values = new Array(1);
        for (Int i : 0..<17) {
            values.add(i % 2 == 0 ? Float8e4:1.0 : Float8e4:-2.0);
        }
        assert values.size == 17;
        assert values[7] == -2.0 && values[8] == 1.0 && values[16] == 1.0;
        Int count = 0;
        for (Float8e4 value : values) {
            assert value == (count % 2 == 0 ? Float8e4:1.0 : Float8e4:-2.0);
            ++count;
        }
        assert count == 17;

        values.insert(8, -4.0);
        assert values.size == 18 && values[7] == -2.0 && values[8] == -4.0 && values[9] == 1.0;
        values.delete(8);
        assert values.size == 17 && values[7] == -2.0 && values[8] == 1.0;

        Float8e4[] copy = new Array(Fixed, values);
        assert copy.size == values.size;
        assert copy[7] == -2.0 && copy[8] == 1.0 && copy[16] == 1.0;
        Float8e4[] duplicate = new Array(values);
        assert duplicate.size == values.size && duplicate[7] == -2.0;
        values[7] = 4.0;
        assert duplicate[7] == -2.0 && copy[7] == -2.0;
    }

}
