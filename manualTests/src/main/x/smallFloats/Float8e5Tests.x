class Float8e5Tests {

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
        testArrays();
        testArrayInitializer();
        testBoxedArrayInitializer();
        testFailingArrayInitializer();
        testNegativeFields();
        testFloat8e5Rounding();
        testFloat8e5Negate();
        testFloat8e5Arithmetic();

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

    /**
     * Arithmetic is performed at wider precision and must be rounded back into this format: a
     * result that is not representable here is a bug, not a more precise answer. E5M2 is IEEE-style, so an out-of-range result becomes an infinity.
     */
    void testFloat8e5Arithmetic() {
        Float8e5 a = 1.0;
        Float8e5 b = 2.0;
        Float8e5 c = 3.0;

        assert a + b == 3.0;
        assert b + b == 4.0;
        assert c - a == 2.0;
        assert a - b == -1.0;
        assert b * c == 6.0;
        assert c / b == 1.5;
        assert a - a == 0.0;

        // the significand is far too short to hold this, so it must round away entirely
        Float8e5 tiny = 0.0625;
        assert a + tiny == 1.0;

        Float8e5 max = 57344.0;
        assert max + max == Float8e5.PositiveInfinity;
        assert (max + max).infinity == True;
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

    void testNegativeFields() {
        Float8e5AsField holder = new Float8e5AsField();
        holder.field = -4.0;
        assert holder.field == -4.0;
        assert holder.field.toFloat32() == -4.0;
        assert holder.field.toByteArray()[0] == 0xC4;

        Float8e5AsNullableField nullable = new Float8e5AsNullableField();
        nullable.field = -4.0;
        assert nullable.field == -4.0;
        nullable.field = Null;
        assert nullable.field == Null;
    }

    void testArrays() {
        Float8e5[] literal = [1.0, -2.0, 4.0];
        assert literal.size == 3;
        assert literal[0] == 1.0 && literal[1] == -2.0 && literal[2] == 4.0;

        Float8e5[] fixed = new Float8e5[17](-4.0);
        assert fixed.mutability == Fixed;
        assert fixed[0] == -4.0 && fixed[7] == -4.0 && fixed[8] == -4.0 && fixed[16] == -4.0;
        fixed[8] = 2.0;
        fixed[8] += 1.0;
        assert fixed[7] == -4.0 && fixed[8] == 3.0 && fixed[9] == -4.0;

        Float8e5[] values = new Array(1);
        for (Int i : 0..<17) {
            values.add(i % 2 == 0 ? Float8e5:1.0 : Float8e5:-2.0);
        }
        assert values.size == 17;
        assert values[7] == -2.0 && values[8] == 1.0 && values[16] == 1.0;
        Int count = 0;
        for (Float8e5 value : values) {
            assert value == (count % 2 == 0 ? Float8e5:1.0 : Float8e5:-2.0);
            ++count;
        }
        assert count == 17;

        values.insert(8, -4.0);
        assert values.size == 18 && values[7] == -2.0 && values[8] == -4.0 && values[9] == 1.0;
        values.delete(8);
        assert values.size == 17 && values[7] == -2.0 && values[8] == 1.0;

        Float8e5[] copy = new Array(Fixed, values);
        assert copy.size == values.size;
        assert copy[7] == -2.0 && copy[8] == 1.0 && copy[16] == 1.0;
        Float8e5[] duplicate = new Array(values);
        assert duplicate.size == values.size && duplicate[7] == -2.0;
        values[7] = 4.0;
        assert duplicate[7] == -2.0 && copy[7] == -2.0;
    }

    void testArrayInitializer() {
        InitializerState state = new InitializerState();
        Float8e5 negative = -4.0;
        function Float8e5(Int) supply = i -> {
            assert i == state.calls;
            ++state.calls;
            return i % 2 == 0 ? Float8e5:2.0 : negative;
        };

        // Cross two packed-long boundaries and check call order and captured values.
        Float8e5[] values = new Float8e5[17](supply);
        assert values.mutability == Fixed && values.size == 17;
        assert state.calls == 17;
        for (Int i : 0..<values.size) {
            Float8e5 expected = i % 2 == 0 ? Float8e5:2.0 : negative;
            assert values[i] == expected;
        }
        values[8] = -1.0;
        assert values[7] == negative && values[8] == -1.0 && values[9] == negative;

        Float8e5[] empty = new Float8e5[0](supply);
        assert empty.empty && empty.mutability == Fixed;
        assert state.calls == 17;
    }

    void testBoxedArrayInitializer() {
        // A generic return type requires the boxed calling convention in the JIT.
        function Float8e5(Int) boxedSupply = alternatingInitializer(Float8e5:-2.0, Float8e5:4.0);
        Float8e5[] boxed = new Float8e5[17](boxedSupply);
        for (Int i : 0..<boxed.size) {
            Float8e5 expected = i % 2 == 0 ? Float8e5:-2.0 : Float8e5:4.0;
            assert boxed[i] == expected;
        }
    }

    void testFailingArrayInitializer() {
        InitializerState state = new InitializerState();
        try {
            Float8e5[] failed = new Float8e5[17](i -> {
                ++state.calls;
                if (i == 3) {
                    throw new IllegalState("initializer failed");
                }
                return Float8e5:1.0;
            });
            assert False;
        } catch (IllegalState e) {
            assert e.text == "initializer failed";
        }
        assert state.calls == 4;
    }

    static <Element> function Element(Int) alternatingInitializer(Element even, Element odd) {
        return i -> i % 2 == 0 ? even : odd;
    }

    static class InitializerState {
        Int calls = 0;
    }

}
