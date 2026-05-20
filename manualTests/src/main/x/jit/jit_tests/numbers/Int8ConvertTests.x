/**
 * Tests for Int8 conversions to other numeric types.
 */
class Int8ConvertTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Int8 Conversion tests >>>>");
        testInt8ToInt8(Int8.MinValue, -128);
        testInt8ToInt8(-100, -100);
        testInt8ToInt8(0, 0);
        testInt8ToInt8(100, 100);
        testInt8ToInt8(Int8.MaxValue, 127);

        testInt8ToInt16(Int8.MinValue, -128);
        testInt8ToInt16(-100, -100);
        testInt8ToInt16(0, 0);
        testInt8ToInt16(100, 100);
        testInt8ToInt16(Int8.MaxValue, 127);

        testInt8ToInt32(Int8.MinValue, -128);
        testInt8ToInt32(-100, -100);
        testInt8ToInt32(0, 0);
        testInt8ToInt32(100, 100);
        testInt8ToInt32(Int8.MaxValue, 127);

        testInt8ToInt64(Int8.MinValue, -128);
        testInt8ToInt64(-100, -100);
        testInt8ToInt64(0, 0);
        testInt8ToInt64(100, 100);
        testInt8ToInt64(Int8.MaxValue, 127);

        testInt8ToInt128(Int8.MinValue, -128);
        testInt8ToInt128(-100, -100);
        testInt8ToInt128(0, 0);
        testInt8ToInt128(100, 100);
        testInt8ToInt128(Int8.MaxValue, 127);

        testInt8ToUInt8(Int8.MinValue, 128);
        testInt8ToUInt8(-1, 255);
        testInt8ToUInt8(-100, 156);
        testInt8ToUInt8(0, 0);
        testInt8ToUInt8(100, 100);
        testInt8ToUInt8(Int8.MaxValue, 127);

        testInt8ToUInt8WithBoundsCheck(Int8.MinValue, 0, True);
        testInt8ToUInt8WithBoundsCheck(-1, 0, True);
        testInt8ToUInt8WithBoundsCheck(-100, 0, True);
        testInt8ToUInt8WithBoundsCheck(0, 0, False);
        testInt8ToUInt8WithBoundsCheck(100, 100, False);
        testInt8ToUInt8WithBoundsCheck(Int8.MaxValue, 127, False);

        testInt8ToUInt16(Int8.MinValue, 65408);
        testInt8ToUInt16(-1, 65535);
        testInt8ToUInt16(-100, 65436);
        testInt8ToUInt16(0, 0);
        testInt8ToUInt16(100, 100);
        testInt8ToUInt16(Int8.MaxValue, 127);

        testInt8ToUInt16WithBoundsCheck(Int8.MinValue, 0, True);
        testInt8ToUInt16WithBoundsCheck(-1, 0, True);
        testInt8ToUInt16WithBoundsCheck(-100, 0, True);
        testInt8ToUInt16WithBoundsCheck(0, 0, False);
        testInt8ToUInt16WithBoundsCheck(100, 100, False);
        testInt8ToUInt16WithBoundsCheck(Int8.MaxValue, 127, False);

        testInt8ToUInt32(Int8.MinValue, 4294967168);
        testInt8ToUInt32(-1, 4294967295);
        testInt8ToUInt32(-100, 4294967196);
        testInt8ToUInt32(0, 0);
        testInt8ToUInt32(100, 100);
        testInt8ToUInt32(Int8.MaxValue, 127);

        testInt8ToUInt32WithBoundsCheck(Int8.MinValue, 0, True);
        testInt8ToUInt32WithBoundsCheck(-1, 0, True);
        testInt8ToUInt32WithBoundsCheck(-100, 0, True);
        testInt8ToUInt32WithBoundsCheck(0, 0, False);
        testInt8ToUInt32WithBoundsCheck(100, 100, False);
        testInt8ToUInt32WithBoundsCheck(Int8.MaxValue, 127, False);

        testInt8ToUInt64(Int8.MinValue, 18446744073709551488);
        testInt8ToUInt64(-1, 18446744073709551615);
        testInt8ToUInt64(-100, 18446744073709551516);
        testInt8ToUInt64(0, 0);
        testInt8ToUInt64(100, 100);
        testInt8ToUInt64(Int8.MaxValue, 127);

        testInt8ToUInt64WithBoundsCheck(Int8.MinValue, 0, True);
        testInt8ToUInt64WithBoundsCheck(-1, 0, True);
        testInt8ToUInt64WithBoundsCheck(-100, 0, True);
        testInt8ToUInt64WithBoundsCheck(0, 0, False);
        testInt8ToUInt64WithBoundsCheck(100, 100, False);
        testInt8ToUInt64WithBoundsCheck(Int8.MaxValue, 127, False);

        testInt8ToUInt128(Int8.MinValue, 340282366920938463463374607431768211328);
        testInt8ToUInt128(-1, 340282366920938463463374607431768211455);
        testInt8ToUInt128(-100, 340282366920938463463374607431768211356);
        testInt8ToUInt128(0, 0);
        testInt8ToUInt128(100, 100);
        testInt8ToUInt128(Int8.MaxValue, 127);

        testInt8ToUInt128WithBoundsCheck(Int8.MinValue, 0, True);
        testInt8ToUInt128WithBoundsCheck(-1, 0, True);
        testInt8ToUInt128WithBoundsCheck(-100, 0, True);
        testInt8ToUInt128WithBoundsCheck(0, 0, False);
        testInt8ToUInt128WithBoundsCheck(100, 100, False);
        testInt8ToUInt128WithBoundsCheck(Int8.MaxValue, 127, False);

        console.print("<<<<< Finished Int8 Conversion tests >>>>>");
    }

    void testInt8ToInt8(Int8 a, Int8 expected) {
        Int8 b = a;
        assert b == expected;
    }

    void testInt8ToInt16(Int8 a, Int16 expected) {
        Int16 b = a;
        assert b == expected;
    }

    void testInt8ToInt32(Int8 a, Int32 expected) {
        Int32 b = a;
        assert b == expected;
    }

    void testInt8ToInt64(Int8 a, Int64 expected) {
        Int64 b = a;
        assert b == expected;
    }

    void testInt8ToInt128(Int8 a, Int128 expected) {
        Int128 b = a;
        assert b == expected;
    }

    void testInt8ToUInt8(Int8 a, UInt8 expected) {
        UInt8 b = a.toUInt8();
        assert b == expected;
    }

    void testInt8ToUInt8WithBoundsCheck(Int8 a, UInt8 expected, Boolean oob) {
        if (oob) {
            try {
                a.toUInt8(True);
                assert as "Expected OutOfBounds to be thrown";
            } catch (OutOfBounds e) {
                // expected
            }
        } else {
            UInt8 b = a.toUInt8(True);
            assert b == expected;
        }
    }

    void testInt8ToUInt16(Int8 a, UInt16 expected) {
        UInt16 b = a.toUInt16();
        assert b == expected;
    }

    void testInt8ToUInt16WithBoundsCheck(Int8 a, UInt16 expected, Boolean oob) {
        if (oob) {
            try {
                a.toUInt16(True);
                assert as "Expected OutOfBounds to be thrown";
            } catch (OutOfBounds e) {
                // expected
            }
        } else {
            UInt16 b = a.toUInt16(True);
            assert b == expected;
        }
    }

    void testInt8ToUInt32(Int8 a, UInt32 expected) {
        UInt32 b = a.toUInt32();
        assert b == expected;
    }

    void testInt8ToUInt32WithBoundsCheck(Int8 a, UInt32 expected, Boolean oob) {
        if (oob) {
            try {
                a.toUInt32(True);
                assert as "Expected OutOfBounds to be thrown";
            } catch (OutOfBounds e) {
                // expected
            }
        } else {
            UInt32 b = a.toUInt32(True);
            assert b == expected;
        }
    }

    void testInt8ToUInt64(Int8 a, UInt64 expected) {
        UInt64 b = a.toUInt64();
        assert b == expected;
    }

    void testInt8ToUInt64WithBoundsCheck(Int8 a, UInt64 expected, Boolean oob) {
        if (oob) {
            try {
                a.toUInt64(True);
                assert as "Expected OutOfBounds to be thrown";
            } catch (OutOfBounds e) {
                // expected
            }
        } else {
            UInt64 b = a.toUInt64(True);
            assert b == expected;
        }
    }

    void testInt8ToUInt128(Int8 a, UInt128 expected) {
        UInt128 b = a.toUInt128();
        assert b == expected;
    }

    void testInt8ToUInt128WithBoundsCheck(Int8 a, UInt128 expected, Boolean oob) {
        if (oob) {
            try {
                a.toUInt128(True);
                assert as "Expected OutOfBounds to be thrown";
            } catch (OutOfBounds e) {
                // expected
            }
        } else {
            UInt128 b = a.toUInt128(True);
            assert b == expected;
        }
    }
}