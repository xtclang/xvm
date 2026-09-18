class Float16Tests {

    @Inject Console console;

    void run() {
        testFloat16Compare();
        testFloat16AsField();
        testFloat16AsConstField();
        testFloat16AsParam();
        testFloat16Return();
        testFloat16toArray();

        // regressions
        testFloat16PowersOfTwoAreExact();
        testFloat16Arithmetic();
        testFloat16ResultsStayInFormat();
        testFloat16Overflow();

        testAppendTo();
    }

    // ----- comparison, field, parameter and return tests ------------------------------------------

    void testFloat16Compare() {
        Float16 n = 4.0;
        assert n == 4.0;
        assert n >= 2.0;
        assert n > 2.0;
        assert n <= 8.0;
        assert n < 8.0;
        assert -n < n;
    }

    void testFloat16AsField() {
        Float16Holder h = new Float16Holder();
        assert h.field == 4.0;
    }

    static class Float16Holder {
        Float16 field = 4.0;
    }

    void testFloat16AsConstField() {
        NumberHolder h = new NumberHolder(4.0);
        assert h.n == 4.0;
    }

    static const NumberHolder(Float16 n) {
    }

    void testFloat16AsParam() {
        acceptFloat16(4.0);
    }

    void acceptFloat16(Float16 n) {
        assert n == 4.0;
    }

    void testFloat16Return() {
        assert returnFloat16() == 4.0;
    }

    Float16 returnFloat16() {
        Float16 n = 4.0;
        return n;
    }

    void testFloat16toArray() {
        Float16 value = 4.0;
        assert new Float16(value.toBitArray()) == value;
        assert new Float16(value.toByteArray()) == value;
    }

    // ----- regressions ----------------------------------------------------------------------------

    /**
     * Every Float16 whose significand is zero - which is every exact power of two, 1.0 included -
     * used to decode 1023 ULPs high, because the decoder carried a "smooth transition" special case
     * from the algorithm it was adapted from. 1.0 came back as 1.000122 and 2.0 as 2.000244.
     */
    void testFloat16PowersOfTwoAreExact() {
        Float16 one = 1.0;
        assert one.toByteArray()[0] == 0x3C;
        assert one.toByteArray()[1] == 0x00;
        assert one * one == one;
        assert one / one == one;
        assert one + one == 2.0;

        Float16 two = 2.0;
        assert two.toByteArray()[0] == 0x40;
        assert two.toByteArray()[1] == 0x00;
        assert two / two == one;
        assert two - one == one;

        Float16 half = 0.5;
        assert half + half == one;
        assert half * two == one;

        Float16 big = 1024.0;
        assert big / two == 512.0;
        assert big.toByteArray()[0] == 0x64;
        assert big.toByteArray()[1] == 0x00;
    }

    void testFloat16Arithmetic() {
        Float16 a = 1.0;
        Float16 b = 2.0;
        Float16 c = 3.0;

        assert a + b == 3.0;
        assert c - a == 2.0;
        assert b * c == 6.0;
        assert c / b == 1.5;
        assert a - a == 0.0;
        assert -(-a) == a;
    }

    /**
     * Arithmetic is performed at float precision and must be rounded back into Float16: a result
     * Float16 cannot represent is a bug, not a more precise answer. Float16 keeps 10 stored
     * significand bits, so 1.0 + 2^-11 has to come back as exactly 1.0.
     */
    void testFloat16ResultsStayInFormat() {
        Float16 one  = 1.0;
        Float16 tiny = 0.00048828125;        // 2^-11
        assert one + tiny == one;

        // and the value itself round-trips through its own bits
        Float16 sum = one + tiny;
        assert new Float16(sum.toByteArray()) == one;
    }

    /**
     * Float16's largest finite value is 65504; exceeding it yields an infinity rather than a number
     * outside the format.
     */
    void testFloat16Overflow() {
        Float16 max = 65504.0;
        assert max.finite;
        assert !max.infinity;

        Float16 over = max + max;
        assert over.infinity;
        assert !over.finite;
        assert over == Float16.PositiveInfinity;
    }

    // ----- Stringable -----------------------------------------------------------------------------

    void testAppendTo() {
        assert callAppendTo(0) == "0.0";
        assert callAppendTo(1) == "1.0";
        assert callAppendTo(-1) == "-1.0";
        assert callAppendTo(2) == "2.0";
    }

    String callAppendTo(Float16 n) {
        StringBuffer buf = new StringBuffer();
        n.appendTo(buf);
        return buf.toString();
    }
}
