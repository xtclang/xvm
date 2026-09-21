class BFloat16Tests {

    @Inject Console console;

    void run() {
        testBFloat16Compare();
        testBFloat16AsField();
        testBFloat16AsConstField();
        testBFloat16AsParam();
        testBFloat16Return();
        testBFloat16toArray();

        // regressions
        testBFloat16PowersOfTwoAreExact();
        testBFloat16Arithmetic();
        testBFloat16ResultsStayInFormat();
        testBFloat16Overflow();

        testRoundingConstants();
        testArrays();
        testAppendTo();
    }

    // ----- comparison, field, parameter and return tests ------------------------------------------

    void testBFloat16Compare() {
        BFloat16 n = 4.0;
        assert n == 4.0;
        assert n >= 2.0;
        assert n > 2.0;
        assert n <= 8.0;
        assert n < 8.0;
        assert -n < n;
    }

    void testBFloat16AsField() {
        BFloat16Holder h = new BFloat16Holder();
        assert h.field == 4.0;
    }

    static class BFloat16Holder {
        BFloat16 field = 4.0;
    }

    void testBFloat16AsConstField() {
        NumberHolder h = new NumberHolder(4.0);
        assert h.n == 4.0;
    }

    static const NumberHolder(BFloat16 n) {
    }

    void testBFloat16AsParam() {
        acceptBFloat16(4.0);
    }

    void acceptBFloat16(BFloat16 n) {
        assert n == 4.0;
    }

    void testBFloat16Return() {
        assert returnBFloat16() == 4.0;
    }

    BFloat16 returnBFloat16() {
        BFloat16 n = 4.0;
        return n;
    }

    void testBFloat16toArray() {
        BFloat16 value = 4.0;
        assert new BFloat16(value.toBitArray()) == value;
        assert new BFloat16(value.toByteArray()) == value;
    }

    // ----- regressions ----------------------------------------------------------------------------

    void testBFloat16PowersOfTwoAreExact() {
        BFloat16 one = 1.0;
        assert one.toByteArray()[0] == 0x3F;
        assert one.toByteArray()[1] == 0x80;
        assert one * one == one;
        assert one / one == one;
        assert one + one == 2.0;

        BFloat16 two = 2.0;
        assert two.toByteArray()[0] == 0x40;
        assert two.toByteArray()[1] == 0x00;
        assert two / two == one;
        assert two - one == one;

        BFloat16 half = 0.5;
        assert half + half == one;
        assert half * two == one;

        BFloat16 big = 1024.0;
        assert big / two == 512.0;
        assert big.toByteArray()[0] == 0x44;
        assert big.toByteArray()[1] == 0x80;
    }

    void testBFloat16Arithmetic() {
        BFloat16 a = 1.0;
        BFloat16 b = 2.0;
        BFloat16 c = 3.0;

        assert a + b == 3.0;
        assert c - a == 2.0;
        assert b * c == 6.0;
        assert c / b == 1.5;
        assert a - a == 0.0;
        assert -(-a) == a;
    }

    /**
     * Arithmetic is performed at float precision and must be rounded back into BFloat16: a result
     * BFloat16 cannot represent is a bug, not a more precise answer. BFloat16 keeps 7 stored
     * significand bits, so 1.0 + 2^-8 has to come back as exactly 1.0.
     */
    void testBFloat16ResultsStayInFormat() {
        BFloat16 one  = 1.0;
        BFloat16 tiny = 0.00390625;        // 2^-8
        assert one + tiny == one;

        // and the value itself round-trips through its own bits
        BFloat16 sum = one + tiny;
        assert new BFloat16(sum.toByteArray()) == one;
    }

    /**
     * BFloat16's largest finite value is 0x1.FEp127; exceeding it yields an infinity rather than a number
     * outside the format.
     */
    void testBFloat16Overflow() {
        BFloat16 max = 338953138925153547590470800371487866880.0;
        assert max.finite;
        assert !max.infinity;

        BFloat16 over = max + max;
        assert over.infinity;
        assert !over.finite;
        assert over == BFloat16.PositiveInfinity;
    }

    // ----- Stringable -----------------------------------------------------------------------------

    void testAppendTo() {
        assert callAppendTo(0) == "0.0";
        assert callAppendTo(1) == "1.0";
        assert callAppendTo(-1) == "-1.0";
        assert callAppendTo(2) == "2.0";
    }

    String callAppendTo(BFloat16 n) {
        StringBuffer buf = new StringBuffer();
        n.appendTo(buf);
        return buf.toString();
    }

    void testRoundingConstants() {
        BFloat16 near = -1.5748398;
        assert near == -1.578125;
        assert near.toByteArray()[0] == 0xBF && near.toByteArray()[1] == 0xCA;
        BFloat16 tie = 1.00390625;
        assert tie == 1.0;
        assert BFloat16.PositiveInfinity.infinity;
        assert BFloat16.PositiveNaN.NaN;
    }

    void testArrays() {
        BFloat16[] literal = [1.0, -2.0, 4.0];
        assert literal.size == 3;
        assert literal[0] == 1.0 && literal[1] == -2.0 && literal[2] == 4.0;

        BFloat16[] fixed = new BFloat16[17](-4.0);
        assert fixed.mutability == Fixed;
        assert fixed[0] == -4.0 && fixed[7] == -4.0 && fixed[8] == -4.0 && fixed[16] == -4.0;
        fixed[8] = 2.0;
        fixed[8] += 1.0;
        assert fixed[7] == -4.0 && fixed[8] == 3.0 && fixed[9] == -4.0;

        BFloat16[] values = new Array(1);
        for (Int i : 0..<17) {
            values.add(i % 2 == 0 ? BFloat16:1.0 : BFloat16:-2.0);
        }
        assert values.size == 17;
        assert values[7] == -2.0 && values[8] == 1.0 && values[16] == 1.0;
        Int count = 0;
        for (BFloat16 value : values) {
            assert value == (count % 2 == 0 ? BFloat16:1.0 : BFloat16:-2.0);
            ++count;
        }
        assert count == 17;

        values.insert(8, -4.0);
        assert values.size == 18 && values[7] == -2.0 && values[8] == -4.0 && values[9] == 1.0;
        values.delete(8);
        assert values.size == 17 && values[7] == -2.0 && values[8] == 1.0;

        BFloat16[] copy = new Array(Fixed, values);
        assert copy.size == values.size;
        assert copy[7] == -2.0 && copy[8] == 1.0 && copy[16] == 1.0;
        BFloat16[] duplicate = new Array(values);
        assert duplicate.size == values.size && duplicate[7] == -2.0;
        values[7] = 4.0;
        assert duplicate[7] == -2.0 && copy[7] == -2.0;
    }

}
