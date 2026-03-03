/**
 * Tests for Int64 conversions to other numeric types.
 */
class Int64ConvertTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Int64 Conversion tests >>>>");

        // Int64.MinValue is -0x8000_0000_0000_0000 the lowest byte is 0x0000 == 0
        testInt64ToInt8(Int64.MinValue, 0);
        testInt64ToInt8(-0x8000, 0);
        testInt64ToInt8(-128, Int8.MinValue);
        testInt64ToInt8(-100, -100);
        testInt64ToInt8(0, 0);
        testInt64ToInt8(100, 100);
        testInt64ToInt8(127, Int8.MaxValue);
        testInt64ToInt8(0x7FFF, -1);
        // Int64.MaxValue is 0x7FFF_FFFF_FFFF_FFFF the lowest byte is 0xFF == -1
        testInt64ToInt8(Int64.MaxValue, -1);

        testInt64ToInt8WithBoundsCheck(Int64.MinValue, 0, True);
        testInt64ToInt8WithBoundsCheck(-0x8000, 0, True);
        testInt64ToInt8WithBoundsCheck(-128, Int8.MinValue, False);
        testInt64ToInt8WithBoundsCheck(-100, -100, False);
        testInt64ToInt8WithBoundsCheck(0, 0, False);
        testInt64ToInt8WithBoundsCheck(100, 100, False);
        testInt64ToInt8WithBoundsCheck(127, Int8.MaxValue, False);
        testInt64ToInt8WithBoundsCheck(0x7FFF, 0, True);
        testInt64ToInt8WithBoundsCheck(Int64.MaxValue, 0, True);

        // Int64.MinValue is -0x8000_0000_0000_0000 the lowest bytes are 0x0000 == 0
        testInt64ToInt16(Int64.MinValue, 0);
        testInt64ToInt16(0x8000, Int16.MinValue);
        testInt64ToInt16(-100, -100);
        testInt64ToInt16(0, 0);
        testInt64ToInt16(100, 100);
        testInt64ToInt16(0x7FFF, Int16.MaxValue);
        // Int64.MaxValue is 0x7FFF_FFFF_FFFF_FFFF the lowest bytes are 0xFFFF == -1
        testInt64ToInt16(Int64.MaxValue, -1);

        testInt64ToInt16WithBoundsCheck(Int64.MinValue, 0, True);
        testInt64ToInt16WithBoundsCheck(-0x8000, Int16.MinValue, False);
        testInt64ToInt16WithBoundsCheck(-100, -100, False);
        testInt64ToInt16WithBoundsCheck(0, 0, False);
        testInt64ToInt16WithBoundsCheck(100, 100, False);
        testInt64ToInt16WithBoundsCheck(0x7FFF, Int16.MaxValue, False);
        testInt64ToInt16WithBoundsCheck(Int64.MaxValue, 0, True);

        // Int64.MinValue is -0x8000_0000_0000_0000 the lowest bytes are 0x0000 == 0
        testInt64ToInt32(Int64.MinValue, 0);
        testInt64ToInt32(-0x80000000, Int32.MinValue);
        testInt64ToInt32(-0x8000, Int16.MinValue);
        testInt64ToInt32(-100, -100);
        testInt64ToInt32(0, 0);
        testInt64ToInt32(100, 100);
        testInt64ToInt32(0x7FFFFFFF, Int32.MaxValue);
        // Int64.MaxValue is 0x7FFF_FFFF_FFFF_FFFF the lowest bytes are 0xFFFF == -1
        testInt64ToInt32(Int64.MaxValue, -1);

        testInt64ToInt32WithBoundsCheck(Int64.MinValue, 0, True);
        testInt64ToInt32WithBoundsCheck(-0x80000000, Int32.MinValue, False);
        testInt64ToInt32WithBoundsCheck(-0x8000, -0x8000, False);
        testInt64ToInt32WithBoundsCheck(-100, -100, False);
        testInt64ToInt32WithBoundsCheck(0, 0, False);
        testInt64ToInt32WithBoundsCheck(100, 100, False);
        testInt64ToInt32WithBoundsCheck(0x7FFFFFFF, Int32.MaxValue, False);
        testInt64ToInt32WithBoundsCheck(Int64.MaxValue, 0, True);

        testInt64ToInt64(Int64.MinValue, Int64.MinValue);
        testInt64ToInt64(-0x80000000, -0x80000000);
        testInt64ToInt64(-100, -100);
        testInt64ToInt64(0, 0);
        testInt64ToInt64(100, 100);
        testInt64ToInt64(0x7FFFFFFF, 0x7FFFFFFF);
        testInt64ToInt64(Int64.MaxValue, Int64.MaxValue);

        testInt64ToInt128(Int64.MinValue, Int64.MinValue);
        testInt64ToInt128(-0x80000000, -0x80000000);
        testInt64ToInt128(-100, -100);
        testInt64ToInt128(0, 0);
        testInt64ToInt128(100, 100);
        testInt64ToInt128(0x7FFFFFFF, 0x7FFFFFFF);
        testInt64ToInt128(Int64.MaxValue, Int64.MaxValue);

        // Int64.MinValue is -0x8000_0000_0000_0000 the lowest byte is zero
        testInt64ToUInt8(Int64.MinValue, 0);
        testInt64ToInt8(-0x8000, 0);
        testInt64ToUInt8(-1, 255);
        testInt64ToUInt8(-100, 156);
        testInt64ToUInt8(0, 0);
        testInt64ToUInt8(100, 100);
        testInt64ToUInt8(127, 127);
        // Int64.MaxValue is 0x7FFF_FFFF_FFFF_FFFF the lowest byte is 0xFF == 255
        testInt64ToUInt8(Int64.MaxValue, 255);

        testInt64ToUInt8WithBoundsCheck(Int64.MinValue, 0, True);
        testInt64ToInt8WithBoundsCheck(-0x8000, 0, True);
        testInt64ToUInt8WithBoundsCheck(-1, 0, True);
        testInt64ToUInt8WithBoundsCheck(-100, 0, True);
        testInt64ToUInt8WithBoundsCheck(0, 0, False);
        testInt64ToUInt8WithBoundsCheck(100, 100, False);
        testInt64ToUInt8WithBoundsCheck(127, 127, False);
        testInt64ToUInt8WithBoundsCheck(Int64.MaxValue, 0, True);

        // Int64.MinValue is -0x8000_0000_0000_0000 the lowest byte is zero
        testInt64ToUInt16(Int64.MinValue, 0);
        testInt64ToUInt16(-1, 65535);
        testInt64ToUInt16(-100, 65436);
        testInt64ToUInt16(0, 0);
        testInt64ToUInt16(100, 100);
        // Int64.MaxValue is 0x7FFF_FFFF_FFFF_FFFF the lowest bytes are 0xFFFF == 32767
        testInt64ToUInt16(Int64.MaxValue, UInt16.MaxValue);

        testInt64ToUInt16WithBoundsCheck(Int64.MinValue, 0, True);
        testInt64ToUInt16WithBoundsCheck(-1, 0, True);
        testInt64ToUInt16WithBoundsCheck(-100, 0, True);
        testInt64ToUInt16WithBoundsCheck(0, 0, False);
        testInt64ToUInt16WithBoundsCheck(100, 100, False);
        testInt64ToUInt16WithBoundsCheck(Int64.MaxValue, 0, True);

        testInt64ToUInt32(Int64.MinValue, 0);
        testInt64ToUInt32(-1, 4294967295);
        testInt64ToUInt32(-100, 4294967196);
        testInt64ToUInt32(0, 0);
        testInt64ToUInt32(100, 100);
        testInt64ToUInt32(Int64.MaxValue, UInt32.MaxValue);

        testInt64ToUInt32WithBoundsCheck(Int64.MinValue, 0, True);
        testInt64ToUInt32WithBoundsCheck(-1, 0, True);
        testInt64ToUInt32WithBoundsCheck(-100, 0, True);
        testInt64ToUInt32WithBoundsCheck(0, 0, False);
        testInt64ToUInt32WithBoundsCheck(100, 100, False);
        testInt64ToUInt32WithBoundsCheck(Int64.MaxValue, 0, True);

        testInt64ToUInt64(Int64.MinValue, 0x8000_0000_0000_0000);
        testInt64ToUInt64(-1, 18446744073709551615);
        testInt64ToUInt64(-100, 18446744073709551516);
        testInt64ToUInt64(0, 0);
        testInt64ToUInt64(100, 100);
        testInt64ToUInt64(Int64.MaxValue, 0x7FFF_FFFF_FFFF_FFFF);

        testInt64ToUInt64WithBoundsCheck(Int64.MinValue, 0, True);
        testInt64ToUInt64WithBoundsCheck(-1, 0, True);
        testInt64ToUInt64WithBoundsCheck(-100, 0, True);
        testInt64ToUInt64WithBoundsCheck(0, 0, False);
        testInt64ToUInt64WithBoundsCheck(100, 100, False);
        testInt64ToUInt64WithBoundsCheck(Int64.MaxValue, 0x7FFF_FFFF_FFFF_FFFF, False);

        testInt64ToUInt128(Int64.MinValue, 340282366920938463454151235394913435648);
        testInt64ToUInt128(-1, 340282366920938463463374607431768211455);
        testInt64ToUInt128(-100, 340282366920938463463374607431768211356);
        testInt64ToUInt128(0, 0);
        testInt64ToUInt128(100, 100);
        testInt64ToUInt128(Int64.MaxValue, 0x7FFF_FFFF_FFFF_FFFF);

        testInt64ToUInt128WithBoundsCheck(Int64.MinValue, 0, True);
        testInt64ToUInt128WithBoundsCheck(-1, 0, True);
        testInt64ToUInt128WithBoundsCheck(-100, 0, True);
        testInt64ToUInt128WithBoundsCheck(0, 0, False);
        testInt64ToUInt128WithBoundsCheck(100, 100, False);
        testInt64ToUInt128WithBoundsCheck(Int64.MaxValue, 0x7FFF_FFFF_FFFF_FFFF, False);

        console.print("<<<<< Finished Int64 Conversion tests >>><<");
    }

    void testInt64ToInt8(Int64 a, Int8 expected) {
        console.print("Test Int64 ", True);
        console.print(a, True);
        console.print(" to Int8 expected=", True);
        console.print(expected, True);
        Int8 b = a.toInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt64ToInt8WithBoundsCheck(Int64 a, Int8 expected, Boolean oob) {
        console.print("Test Int64 ", True);
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

    void testInt64ToInt16(Int64 a, Int16 expected) {
        console.print("Test Int64 ", True);
        console.print(a, True);
        console.print(" to Int16 expected=", True);
        console.print(expected, True);
        Int16 b = a.toInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt64ToInt16WithBoundsCheck(Int64 a, Int16 expected, Boolean oob) {
        console.print("Test Int64 ", True);
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

    void testInt64ToInt32(Int64 a, Int32 expected) {
        console.print("Test Int64 ", True);
        console.print(a, True);
        console.print(" to Int32 expected=", True);
        console.print(expected, True);
        Int32 b = a.toInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt64ToInt32WithBoundsCheck(Int64 a, Int32 expected, Boolean oob) {
        console.print("Test Int64 ", True);
        console.print(a, True);
        if (oob) {
            console.print(" to Int32 throws OutOfBounds");
            try {
                a.toInt32(True);
                assert as "Expected OutOfBounds to be thrown";
            } catch (OutOfBounds e) {
                // expected
            }
        } else {
            console.print(" to Int32 succeeds");
            Int32 b = a.toInt32(True);
            assert b == expected;
        }
    }

    void testInt64ToInt64(Int64 a, Int64 expected) {
        console.print("Test Int64 ", True);
        console.print(a, True);
        console.print(" to Int64 expected=", True);
        console.print(expected, True);
        Int64 b = a;
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt64ToInt128(Int64 a, Int128 expected) {
        console.print("Test Int64 ", True);
        console.print(a, True);
        console.print(" to Int128 expected=", True);
        console.print(expected, True);
        Int128 b = a;
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt64ToUInt8(Int64 a, UInt8 expected) {
        console.print("Test Int64 ", True);
        console.print(a, True);
        console.print(" to UInt8 expected=", True);
        console.print(expected, True);
        UInt8 b = a.toUInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt64ToUInt8WithBoundsCheck(Int64 a, UInt8 expected, Boolean oob) {
        console.print("Test Int64 ", True);
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

    void testInt64ToUInt16(Int64 a, UInt16 expected) {
        console.print("Test Int64 ", True);
        console.print(a, True);
        console.print(" to UInt16 expected=", True);
        console.print(expected, True);
        UInt16 b = a.toUInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt64ToUInt16WithBoundsCheck(Int64 a, UInt16 expected, Boolean oob) {
        console.print("Test Int64 ", True);
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

    void testInt64ToUInt32(Int64 a, UInt32 expected) {
        console.print("Test Int64 ", True);
        console.print(a, True);
        console.print(" to UInt32 expected=", True);
        console.print(expected, True);
        UInt32 b = a.toUInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt64ToUInt32WithBoundsCheck(Int64 a, UInt32 expected, Boolean oob) {
        console.print("Test Int64 ", True);
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

    void testInt64ToUInt64(Int64 a, UInt64 expected) {
        console.print("Test Int64 ", True);
        console.print(a, True);
        console.print(" to UInt64 expected=", True);
        console.print(expected, True);
        UInt64 b = a.toUInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt64ToUInt64WithBoundsCheck(Int64 a, UInt64 expected, Boolean oob) {
        console.print("Test Int64 ", True);
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

    void testInt64ToUInt128(Int64 a, UInt128 expected) {
        console.print("Test Int64 ", True);
        console.print(a, True);
        console.print(" to UInt128 expected=", True);
        console.print(expected, True);
        UInt128 b = a.toUInt128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt64ToUInt128WithBoundsCheck(Int64 a, UInt128 expected, Boolean oob) {
        console.print("Test Int64 ", True);
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
