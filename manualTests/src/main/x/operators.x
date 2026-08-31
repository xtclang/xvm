/**
 * Operator coverage for the natively-implemented types.
 *
 * Every operator here is implemented in Java by a template overriding the `OpSupport` protocol, and
 * those implementations are typed by their handle class. That typing is only as good as the
 * assumption behind it, and the assumption is not always right: `String + Int` reaches
 * `xString`'s `+` with a numeric handle, not a String one, so a template that declared its argument
 * as a String would fail on it. Nothing in the Java unit suite exercises these paths - only
 * running Ecstasy code does - and `numbers.x` only covers the numeric types.
 *
 * The mixed-type cases at the end are the point of this module; the rest is the matrix they sit in.
 */
module TestOperators {
    @Inject ecstasy.io.Console console;

    void run() {
        testIntegers();
        testInt128();
        testUnbounded();
        testFloatingPoint();
        testBitAndBoolean();
        testCharAndString();
        testMixedOperands();
        console.print("All operator tests passed.");
    }

    void testIntegers() {
        Int64 a = 17;
        Int64 b = 5;
        assert a + b == 22;
        assert a - b == 12;
        assert a * b == 85;
        assert a / b == 3;
        assert a % b == 2;
        assert a & b == 1;
        assert (a | b) == 21;
        assert a ^ b == 20;
        assert -a == -17;
        assert ~a == -18;
        assert a << 2 == 68;
        assert a >> 2 == 4;

        // the remainder of a negative dividend follows the sign of the divisor
        Int64 n = -47;
        assert n % 5 == 3;
        assert n / 5 == -9;

        // narrower widths wrap rather than promote
        Int8 c = 100;
        Int8 d = 7;
        assert c + d == 107;
        assert c - d == 93;
        assert -c == -100;

        // an unsigned right shift masks to the constrained width first: Int8 -1 is all ones as a
        // Java long, so an unmasked >>> would still read as -1 instead of 63
        Int8 e = -1;
        assert e >>> 2 == 63;

        UInt8 u = 200;
        UInt8 v = 55;
        assert u + v == 255;
        assert u - v == 145;
    }

    void testInt128() {
        Int128 a = 12345678901234567890;
        assert a + 1 == 12345678901234567891;
        assert a - 1 == 12345678901234567889;
        assert a * 2 == 24691357802469135780;
        assert a / 3 == 4115226300411522630;
        assert a % 7 == 1;
        assert -a == -12345678901234567890;
        // the shift count is an Int even though the value is an Int128
        assert a << 2 == 49382715604938271560;
        assert a >> 3 == 1543209862654320986;

        UInt128 u = 340282366920938463463374607431768211455;
        assert u / 3 == 113427455640312821154458202477256070485;
        assert u % 7 == 3;
    }

    void testUnbounded() {
        IntLiteral lit = 123456789012345678901234567890;
        assert lit + 1 == 123456789012345678901234567891;
        assert lit * 2 == 246913578024691357802469135780;
        assert lit / 3 == 41152263004115226300411522630;
        assert lit << 1 == 246913578024691357802469135780;
    }

    void testFloatingPoint() {
        Float64 f = 3.5;
        Float64 g = 1.25;
        assert f + g == 4.75;
        assert f - g == 2.25;
        assert f * g == 4.375;
        assert -f == -3.5;

        Dec64 d = 10.5;
        Dec64 e = 2.5;
        assert d + e == 13.0;
        assert d - e == 8.0;
        assert d * e == 26.25;
        assert d / e == 4.2;
        assert -d == -10.5;
    }

    void testBitAndBoolean() {
        Bit b0 = 0;
        Bit b1 = 1;
        assert (b0 & b1) == 0;
        assert (b0 | b1) == 1;
        assert (b0 ^ b1) == 1;
        assert ~b1 == 0;

        assert (True & False) == False;
        assert (True | False) == True;
        assert (True ^ False) == True;
        assert !True == False;
    }

    void testCharAndString() {
        Char c = 'a';
        assert c.nextValue() == 'b';
        assert c.prevValue() == '`';

        assert "foo" + "bar" == "foobar";
    }

    /**
     * The cases where an operand is NOT the same type as its receiver. These are what a
     * handle-typed implementation can get wrong while every same-typed case still passes.
     */
    void testMixedOperands() {
        // String + anything stringifies the operand, so the argument is not a String handle
        assert "n=" + 42 == "n=42";
        assert "f=" + 1.5 == "f=1.5";
        assert "b=" + True == "b=True";
        assert "c=" + 'x' == "c=x";

        // a shift count is an Int regardless of what is being shifted
        Int128 big = 12345678901234567890;
        Int64  cnt = 2;
        assert big << cnt == 49382715604938271560;

        // ranges over enums and integers
        Int sum = 0;
        for (Int i : 1..5) {
            sum += i;
        }
        assert sum == 15;
    }
}
