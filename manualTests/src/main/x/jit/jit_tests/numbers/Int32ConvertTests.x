/**
 * Tests for Int32 conversions to other numeric types.
 */
class Int32ConvertTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Int32 Conversion tests >>>>");

        // Int32.MinValue is 0x80000000 the lowest byte is 0x0000 == 0
        testInt32ToInt8(Int32.MinValue, 0);
        testInt32ToInt8(-0x8000, 0);
        testInt32ToInt8(-128, Int8.MinValue);
        testInt32ToInt8(-100, -100);
        testInt32ToInt8(0, 0);
        testInt32ToInt8(100, 100);
        testInt32ToInt8(127, Int8.MaxValue);
        testInt32ToInt8(0x7FFF, -1);
        // Int32.MaxValue is 0x7FFFFFFF the lowest byte is 0xFF == -1
        testInt32ToInt8(Int32.MaxValue, -1);

        testInt32ToInt8WithBoundsCheck(Int32.MinValue, 0, True);
        testInt32ToInt8WithBoundsCheck(-0x8000, 0, True);
        testInt32ToInt8WithBoundsCheck(-128, Int8.MinValue, False);
        testInt32ToInt8WithBoundsCheck(-100, -100, False);
        testInt32ToInt8WithBoundsCheck(0, 0, False);
        testInt32ToInt8WithBoundsCheck(100, 100, False);
        testInt32ToInt8WithBoundsCheck(127, Int8.MaxValue, False);
        testInt32ToInt8WithBoundsCheck(0x7FFF, 0, True);
        testInt32ToInt8WithBoundsCheck(Int32.MaxValue, 0, True);

        // Int32.MinValue is 0x80000000 the lowest byte is 0x0000 == 0
        testInt32ToInt16(Int32.MinValue, 0);
        testInt32ToInt16(-0x8000, Int16.MinValue);
        testInt32ToInt16(-100, -100);
        testInt32ToInt16(0, 0);
        testInt32ToInt16(100, 100);
        testInt32ToInt16(0x7FFF, Int16.MaxValue);
        // Int32.MaxValue is 0x7FFFFFFF the lowest bytes are 0xFFFF == -1
        testInt32ToInt16(Int32.MaxValue, -1);

        testInt32ToInt16WithBoundsCheck(Int32.MinValue, 0, True);
        testInt32ToInt16WithBoundsCheck(-0x8000, Int16.MinValue, False);
        testInt32ToInt16WithBoundsCheck(-100, -100, False);
        testInt32ToInt16WithBoundsCheck(0, 0, False);
        testInt32ToInt16WithBoundsCheck(100, 100, False);
        testInt32ToInt16WithBoundsCheck(0x7FFF, Int16.MaxValue, False);
        testInt32ToInt16WithBoundsCheck(Int32.MaxValue, 0, True);

        testInt32ToInt32(Int32.MinValue, Int32.MinValue);
        testInt32ToInt32(-100, -100);
        testInt32ToInt32(0, 0);
        testInt32ToInt32(100, 100);
        testInt32ToInt32(Int32.MaxValue, Int32.MaxValue);

        testInt32ToInt64(Int32.MinValue, -0x80000000);
        testInt32ToInt64(-100, -100);
        testInt32ToInt64(0, 0);
        testInt32ToInt64(100, 100);
        testInt32ToInt64(Int32.MaxValue, 0x7FFFFFFF);

        testInt32ToInt128(Int32.MinValue, -0x80000000);
        testInt32ToInt128(-100, -100);
        testInt32ToInt128(0, 0);
        testInt32ToInt128(100, 100);
        testInt32ToInt128(Int32.MaxValue, 0x7FFFFFFF);

        // Int32.MinValue is 0x80000000 the lowest byte is zero
        testInt32ToUInt8(Int32.MinValue, 0);
        testInt32ToInt8(-0x8000, 0);
        testInt32ToUInt8(-1, 255);
        testInt32ToUInt8(-100, 156);
        testInt32ToUInt8(0, 0);
        testInt32ToUInt8(100, 100);
        testInt32ToUInt8(127, 127);
        // Int32.MaxValue is 0x7FFFFFFF the lowest byte is 0xFF == 255
        testInt32ToUInt8(Int32.MaxValue, 255);

        testInt32ToUInt8WithBoundsCheck(Int32.MinValue, 0, True);
        testInt32ToInt8WithBoundsCheck(-0x8000, 0, True);
        testInt32ToUInt8WithBoundsCheck(-1, 0, True);
        testInt32ToUInt8WithBoundsCheck(-100, 0, True);
        testInt32ToUInt8WithBoundsCheck(0, 0, False);
        testInt32ToUInt8WithBoundsCheck(100, 100, False);
        testInt32ToUInt8WithBoundsCheck(127, 127, False);
        testInt32ToUInt8WithBoundsCheck(Int32.MaxValue, 0, True);

        // Int32.MinValue is 0x80000000 the lowest byte is zero
        testInt32ToUInt16(Int32.MinValue, 0);
        testInt32ToUInt16(-1, 65535);
        testInt32ToUInt16(-100, 65436);
        testInt32ToUInt16(0, 0);
        testInt32ToUInt16(100, 100);
        // Int32.MaxValue is 0x7FFFFFFF the lowest bytes are 0xFFFF == 32767
        testInt32ToUInt16(Int32.MaxValue, UInt16.MaxValue);

        testInt32ToUInt16WithBoundsCheck(Int32.MinValue, 0, True);
        testInt32ToUInt16WithBoundsCheck(-1, 0, True);
        testInt32ToUInt16WithBoundsCheck(-100, 0, True);
        testInt32ToUInt16WithBoundsCheck(0, 0, False);
        testInt32ToUInt16WithBoundsCheck(100, 100, False);
        testInt32ToUInt16WithBoundsCheck(Int32.MaxValue, 0, True);

        testInt32ToUInt32(Int32.MinValue, 2147483648);
        testInt32ToUInt32(-1, 4294967295);
        testInt32ToUInt32(-100, 4294967196);
        testInt32ToUInt32(0, 0);
        testInt32ToUInt32(100, 100);
        testInt32ToUInt32(Int32.MaxValue, 2147483647);

        testInt32ToUInt32WithBoundsCheck(Int32.MinValue, 0, True);
        testInt32ToUInt32WithBoundsCheck(-1, 0, True);
        testInt32ToUInt32WithBoundsCheck(-100, 0, True);
        testInt32ToUInt32WithBoundsCheck(0, 0, False);
        testInt32ToUInt32WithBoundsCheck(100, 100, False);
        testInt32ToUInt32WithBoundsCheck(Int32.MaxValue, 2147483647, False);

        testInt32ToUInt64(Int32.MinValue, 18446744071562067968);
        testInt32ToUInt64(-1, 18446744073709551615);
        testInt32ToUInt64(-100, 18446744073709551516);
        testInt32ToUInt64(0, 0);
        testInt32ToUInt64(100, 100);
        testInt32ToUInt64(Int32.MaxValue, 2147483647);

        testInt32ToUInt64WithBoundsCheck(Int32.MinValue, 0, True);
        testInt32ToUInt64WithBoundsCheck(-1, 0, True);
        testInt32ToUInt64WithBoundsCheck(-100, 0, True);
        testInt32ToUInt64WithBoundsCheck(0, 0, False);
        testInt32ToUInt64WithBoundsCheck(100, 100, False);
        testInt32ToUInt64WithBoundsCheck(Int32.MaxValue, 2147483647, False);

        testInt32ToUInt128(Int32.MinValue, 340282366920938463463374607429620727808);
        testInt32ToUInt128(-1, 340282366920938463463374607431768211455);
        testInt32ToUInt128(-100, 340282366920938463463374607431768211356);
        testInt32ToUInt128(0, 0);
        testInt32ToUInt128(100, 100);
        testInt32ToUInt128(Int32.MaxValue, 2147483647);

        testInt32ToUInt128WithBoundsCheck(Int32.MinValue, 0, True);
        testInt32ToUInt128WithBoundsCheck(-1, 0, True);
        testInt32ToUInt128WithBoundsCheck(-100, 0, True);
        testInt32ToUInt128WithBoundsCheck(0, 0, False);
        testInt32ToUInt128WithBoundsCheck(100, 100, False);
        testInt32ToUInt128WithBoundsCheck(Int32.MaxValue, 2147483647, False);

        console.print("<<<<< Finished Int32 Conversion tests >>><<");
    }

    void testInt32ToInt8(Int32 a, Int8 expected) {
        console.print("Test Int32 ", True);
        console.print(a, True);
        console.print(" to Int8 expected=", True);
        console.print(expected, True);
        Int8 b = a.toInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt32ToInt8WithBoundsCheck(Int32 a, Int8 expected, Boolean oob) {
        console.print("Test Int32 ", True);
        console.print(a, True);
        if (oob) {
            console.print(" to Int8 throws OutOfBounds");
            try {
                a.toInt8(True);
                assert as "Expected OutOfBounds to be thrown";
            } catch (OutOfBounds e) {
                // expected
            }
        } else {
            console.print(" to Int8 succeeds");
            Int8 b = a.toInt8(True);
            assert b == expected;
        }
    }

    void testInt32ToInt16(Int32 a, Int16 expected) {
        console.print("Test Int32 ", True);
        console.print(a, True);
        console.print(" to Int16 expected=", True);
        console.print(expected, True);
        Int16 b = a.toInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt32ToInt16WithBoundsCheck(Int32 a, Int16 expected, Boolean oob) {
        console.print("Test Int32 ", True);
        console.print(a, True);
        if (oob) {
            console.print(" to Int16 throws OutOfBounds");
            try {
                a.toInt16(True);
                assert as "Expected OutOfBounds to be thrown";
            } catch (OutOfBounds e) {
                // expected
            }
        } else {
            console.print(" to Int16 succeeds");
            Int16 b = a.toInt16(True);
            assert b == expected;
        }
    }

    void testInt32ToInt32(Int32 a, Int32 expected) {
        console.print("Test Int32 ", True);
        console.print(a, True);
        console.print(" to Int32 expected=", True);
        console.print(expected, True);
        Int32 b = a;
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt32ToInt64(Int32 a, Int64 expected) {
        console.print("Test Int32 ", True);
        console.print(a, True);
        console.print(" to Int64 expected=", True);
        console.print(expected, True);
        Int64 b = a;
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt32ToInt128(Int32 a, Int128 expected) {
        console.print("Test Int32 ", True);
        console.print(a, True);
        console.print(" to Int128 expected=", True);
        console.print(expected, True);
        Int128 b = a;
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt32ToUInt8(Int32 a, UInt8 expected) {
        console.print("Test Int32 ", True);
        console.print(a, True);
        console.print(" to UInt8 expected=", True);
        console.print(expected, True);
        UInt8 b = a.toUInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt32ToUInt8WithBoundsCheck(Int32 a, UInt8 expected, Boolean oob) {
        console.print("Test Int32 ", True);
        console.print(a, True);
        if (oob) {
            console.print(" to UInt8 throws OutOfBounds");
            try {
                a.toUInt8(True);
                assert as "Expected OutOfBounds to be thrown";
            } catch (OutOfBounds e) {
                // expected
            }
        } else {
            console.print(" to UInt8 succeeds");
            UInt8 b = a.toUInt8(True);
            assert b == expected;
        }
    }

    void testInt32ToUInt16(Int32 a, UInt16 expected) {
        console.print("Test Int32 ", True);
        console.print(a, True);
        console.print(" to UInt16 expected=", True);
        console.print(expected, True);
        UInt16 b = a.toUInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt32ToUInt16WithBoundsCheck(Int32 a, UInt16 expected, Boolean oob) {
        console.print("Test Int32 ", True);
        console.print(a, True);
        if (oob) {
            console.print(" to UInt16 throws OutOfBounds");
            try {
                a.toUInt16(True);
                assert as "Expected OutOfBounds to be thrown";
            } catch (OutOfBounds e) {
                // expected
            }
        } else {
            console.print(" to UInt16 succeeds");
            UInt16 b = a.toUInt16(True);
            assert b == expected;
        }
    }

    void testInt32ToUInt32(Int32 a, UInt32 expected) {
        console.print("Test Int32 ", True);
        console.print(a, True);
        console.print(" to UInt32 expected=", True);
        console.print(expected, True);
        UInt32 b = a.toUInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt32ToUInt32WithBoundsCheck(Int32 a, UInt32 expected, Boolean oob) {
        console.print("Test Int32 ", True);
        console.print(a, True);
        if (oob) {
            console.print(" to UInt32 throws OutOfBounds");
            try {
                a.toUInt32(True);
                assert as "Expected OutOfBounds to be thrown";
            } catch (OutOfBounds e) {
                // expected
            }
        } else {
            console.print(" to UInt32 succeeds");
            UInt32 b = a.toUInt32(True);
            assert b == expected;
        }
    }

    void testInt32ToUInt64(Int32 a, UInt64 expected) {
        console.print("Test Int32 ", True);
        console.print(a, True);
        console.print(" to UInt64 expected=", True);
        console.print(expected, True);
        UInt64 b = a.toUInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt32ToUInt64WithBoundsCheck(Int32 a, UInt64 expected, Boolean oob) {
        console.print("Test Int32 ", True);
        console.print(a, True);
        if (oob) {
            console.print(" to UInt64 throws OutOfBounds");
            try {
                a.toUInt64(True);
                assert as "Expected OutOfBounds to be thrown";
            } catch (OutOfBounds e) {
                // expected
            }
        } else {
            console.print(" to UInt64 succeeds");
            UInt64 b = a.toUInt64(True);
            assert b == expected;
        }
    }

    void testInt32ToUInt128(Int32 a, UInt128 expected) {
        console.print("Test Int32 ", True);
        console.print(a, True);
        console.print(" to UInt128 expected=", True);
        console.print(expected, True);
        UInt128 b = a.toUInt128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt32ToUInt128WithBoundsCheck(Int32 a, UInt128 expected, Boolean oob) {
        console.print("Test Int32 ", True);
        console.print(a, True);
        if (oob) {
            console.print(" to UInt128 throws OutOfBounds");
            try {
                a.toUInt128(True);
                assert as "Expected OutOfBounds to be thrown";
            } catch (OutOfBounds e) {
                // expected
            }
        } else {
            console.print(" to UInt128 succeeds");
            UInt128 b = a.toUInt128(True);
            assert b == expected;
        }
    }
}
