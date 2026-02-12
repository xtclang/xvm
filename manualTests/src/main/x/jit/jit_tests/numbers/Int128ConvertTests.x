/**
 * Tests for Int128 conversions to other numeric types.
 */
class Int128ConvertTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Int128 Conversion tests >>>>");

        testInt128ToInt8(Int128.MinValue, 0);
        testInt128ToInt8(-0x8000, 0);
        testInt128ToInt8(-128, Int8.MinValue);
        testInt128ToInt8(-100, -100);
        testInt128ToInt8(0, 0);
        testInt128ToInt8(100, 100);
        testInt128ToInt8(127, Int8.MaxValue);
        testInt128ToInt8(0x7FFF, -1);
        testInt128ToInt8(Int128.MaxValue, -1);

        testInt128ToInt8WithBoundsCheck(Int128.MinValue, 0, True);
        testInt128ToInt8WithBoundsCheck(-0x8000, 0, True);
        testInt128ToInt8WithBoundsCheck(-128, Int8.MinValue, False);
        testInt128ToInt8WithBoundsCheck(-100, -100, False);
        testInt128ToInt8WithBoundsCheck(0, 0, False);
        testInt128ToInt8WithBoundsCheck(100, 100, False);
        testInt128ToInt8WithBoundsCheck(127, Int8.MaxValue, False);
        testInt128ToInt8WithBoundsCheck(0x7FFF, 0, True);
        testInt128ToInt8WithBoundsCheck(Int128.MaxValue, 0, True);

        testInt128ToInt16(Int128.MinValue, 0);
        testInt128ToInt16(0x8000, Int16.MinValue);
        testInt128ToInt16(-100, -100);
        testInt128ToInt16(0, 0);
        testInt128ToInt16(100, 100);
        testInt128ToInt16(0x7FFF, Int16.MaxValue);
        testInt128ToInt16(Int128.MaxValue, -1);

        testInt128ToInt16WithBoundsCheck(Int128.MinValue, 0, True);
        testInt128ToInt16WithBoundsCheck(-0x8000, Int16.MinValue, False);
        testInt128ToInt16WithBoundsCheck(-100, -100, False);
        testInt128ToInt16WithBoundsCheck(0, 0, False);
        testInt128ToInt16WithBoundsCheck(100, 100, False);
        testInt128ToInt16WithBoundsCheck(0x7FFF, Int16.MaxValue, False);
        testInt128ToInt16WithBoundsCheck(Int128.MaxValue, 0, True);

        testInt128ToInt32(Int128.MinValue, 0);
        testInt128ToInt32(-0x80000000, Int32.MinValue);
        testInt128ToInt32(-0x8000, Int16.MinValue);
        testInt128ToInt32(-100, -100);
        testInt128ToInt32(0, 0);
        testInt128ToInt32(100, 100);
        testInt128ToInt32(0x7FFFFFFF, Int32.MaxValue);
        testInt128ToInt32(Int128.MaxValue, -1);

        testInt128ToInt32WithBoundsCheck(Int128.MinValue, 0, True);
        testInt128ToInt32WithBoundsCheck(-0x80000000, Int32.MinValue, False);
        testInt128ToInt32WithBoundsCheck(-0x8000, -0x8000, False);
        testInt128ToInt32WithBoundsCheck(-100, -100, False);
        testInt128ToInt32WithBoundsCheck(0, 0, False);
        testInt128ToInt32WithBoundsCheck(100, 100, False);
        testInt128ToInt32WithBoundsCheck(0x7FFFFFFF, Int32.MaxValue, False);
        testInt128ToInt32WithBoundsCheck(Int128.MaxValue, 0, True);

        testInt128ToInt64(Int128.MinValue, Int128.MinValue);
        testInt128ToInt64(-0x80000000, -0x80000000);
        testInt128ToInt64(-100, -100);
        testInt128ToInt64(0, 0);
        testInt128ToInt64(100, 100);
        testInt128ToInt64(0x7FFFFFFF, 0x7FFFFFFF);
        testInt128ToInt64(Int128.MaxValue, Int128.MaxValue);

        testInt128ToInt128(Int128.MinValue, Int128.MinValue);
        testInt128ToInt128(-0x80000000, -0x80000000);
        testInt128ToInt128(-100, -100);
        testInt128ToInt128(0, 0);
        testInt128ToInt128(100, 100);
        testInt128ToInt128(0x7FFFFFFF, 0x7FFFFFFF);
        testInt128ToInt128(Int128.MaxValue, Int128.MaxValue);

        testInt128ToUInt8(Int128.MinValue, 0);
        testInt128ToInt8(-0x8000, 0);
        testInt128ToUInt8(-1, 255);
        testInt128ToUInt8(-100, 156);
        testInt128ToUInt8(0, 0);
        testInt128ToUInt8(100, 100);
        testInt128ToUInt8(127, 127);
        testInt128ToUInt8(Int128.MaxValue, 255);

        testInt128ToUInt8WithBoundsCheck(Int128.MinValue, 0, True);
        testInt128ToInt8WithBoundsCheck(-0x8000, 0, True);
        testInt128ToUInt8WithBoundsCheck(-1, 0, True);
        testInt128ToUInt8WithBoundsCheck(-100, 0, True);
        testInt128ToUInt8WithBoundsCheck(0, 0, False);
        testInt128ToUInt8WithBoundsCheck(100, 100, False);
        testInt128ToUInt8WithBoundsCheck(127, 127, False);
        testInt128ToUInt8WithBoundsCheck(Int128.MaxValue, 0, True);

        testInt128ToUInt16(Int128.MinValue, 0);
        testInt128ToUInt16(-1, 65535);
        testInt128ToUInt16(-100, 65436);
        testInt128ToUInt16(0, 0);
        testInt128ToUInt16(100, 100);
        testInt128ToUInt16(Int128.MaxValue, UInt16.MaxValue);

        testInt128ToUInt16WithBoundsCheck(Int128.MinValue, 0, True);
        testInt128ToUInt16WithBoundsCheck(-1, 0, True);
        testInt128ToUInt16WithBoundsCheck(-100, 0, True);
        testInt128ToUInt16WithBoundsCheck(0, 0, False);
        testInt128ToUInt16WithBoundsCheck(100, 100, False);
        testInt128ToUInt16WithBoundsCheck(Int128.MaxValue, 0, True);

        testInt128ToUInt32(Int128.MinValue, 0);
        testInt128ToUInt32(-1, 4294967295);
        testInt128ToUInt32(-100, 4294967196);
        testInt128ToUInt32(0, 0);
        testInt128ToUInt32(100, 100);
        testInt128ToUInt32(Int128.MaxValue, UInt32.MaxValue);

        testInt128ToUInt32WithBoundsCheck(Int128.MinValue, 0, True);
        testInt128ToUInt32WithBoundsCheck(-1, 0, True);
        testInt128ToUInt32WithBoundsCheck(-100, 0, True);
        testInt128ToUInt32WithBoundsCheck(0, 0, False);
        testInt128ToUInt32WithBoundsCheck(100, 100, False);
        testInt128ToUInt32WithBoundsCheck(Int128.MaxValue, 0, True);

        testInt128ToUInt64(Int128.MinValue, 0x8000_0000_0000_0000);
        testInt128ToUInt64(-1, 18446744073709551615);
        testInt128ToUInt64(-100, 18446744073709551516);
        testInt128ToUInt64(0, 0);
        testInt128ToUInt64(100, 100);
        testInt128ToUInt64(Int128.MaxValue, 0x7FFF_FFFF_FFFF_FFFF);

        testInt128ToUInt64WithBoundsCheck(Int128.MinValue, 0, True);
        testInt128ToUInt64WithBoundsCheck(-1, 0, True);
        testInt128ToUInt64WithBoundsCheck(-100, 0, True);
        testInt128ToUInt64WithBoundsCheck(0, 0, False);
        testInt128ToUInt64WithBoundsCheck(100, 100, False);
        testInt128ToUInt64WithBoundsCheck(Int128.MaxValue, 0x7FFF_FFFF_FFFF_FFFF, False);

        testInt128ToUInt128(Int128.MinValue, 0x8000_0000_0000_0000);
        testInt128ToUInt128(-1, 18446744073709551615);
        testInt128ToUInt128(-100, 18446744073709551516);
        testInt128ToUInt128(0, 0);
        testInt128ToUInt128(100, 100);
        testInt128ToUInt128(Int128.MaxValue, 0x7FFF_FFFF_FFFF_FFFF);

        testInt128ToUInt128WithBoundsCheck(Int128.MinValue, 0, True);
        testInt128ToUInt128WithBoundsCheck(-1, 0, True);
        testInt128ToUInt128WithBoundsCheck(-100, 0, True);
        testInt128ToUInt128WithBoundsCheck(0, 0, False);
        testInt128ToUInt128WithBoundsCheck(100, 100, False);
        testInt128ToUInt128WithBoundsCheck(Int128.MaxValue, 0x7FFF_FFFF_FFFF_FFFF, False);

        console.print("<<<<< Finished Int128 Conversion tests >>><<");
    }

    void testInt128ToInt8(Int128 a, Int8 expected) {
        console.print("Test Int128 ", True);
        console.print(a, True);
        console.print(" to Int8 expected=", True);
        console.print(expected, True);
        Int8 b = a.toInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt128ToInt8WithBoundsCheck(Int128 a, Int8 expected, Boolean oob) {
        console.print("Test Int128 ", True);
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

    void testInt128ToInt16(Int128 a, Int16 expected) {
        console.print("Test Int128 ", True);
        console.print(a, True);
        console.print(" to Int16 expected=", True);
        console.print(expected, True);
        Int16 b = a.toInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt128ToInt16WithBoundsCheck(Int128 a, Int16 expected, Boolean oob) {
        console.print("Test Int128 ", True);
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

    void testInt128ToInt32(Int128 a, Int32 expected) {
        console.print("Test Int128 ", True);
        console.print(a, True);
        console.print(" to Int32 expected=", True);
        console.print(expected, True);
        Int32 b = a.toInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt128ToInt32WithBoundsCheck(Int128 a, Int32 expected, Boolean oob) {
        console.print("Test Int128 ", True);
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

    void testInt128ToInt64(Int128 a, Int128 expected) {
        console.print("Test Int128 ", True);
        console.print(a, True);
        console.print(" to Int128 expected=", True);
        console.print(expected, True);
        Int128 b = a;
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt128ToInt128(Int128 a, Int128 expected) {
        console.print("Test Int128 ", True);
        console.print(a, True);
        console.print(" to Int128 expected=", True);
        console.print(expected, True);
        Int128 b = a;
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt128ToUInt8(Int128 a, UInt8 expected) {
        console.print("Test Int128 ", True);
        console.print(a, True);
        console.print(" to UInt8 expected=", True);
        console.print(expected, True);
        UInt8 b = a.toUInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt128ToUInt8WithBoundsCheck(Int128 a, UInt8 expected, Boolean oob) {
        console.print("Test Int128 ", True);
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

    void testInt128ToUInt16(Int128 a, UInt16 expected) {
        console.print("Test Int128 ", True);
        console.print(a, True);
        console.print(" to UInt16 expected=", True);
        console.print(expected, True);
        UInt16 b = a.toUInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt128ToUInt16WithBoundsCheck(Int128 a, UInt16 expected, Boolean oob) {
        console.print("Test Int128 ", True);
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

    void testInt128ToUInt32(Int128 a, UInt32 expected) {
        console.print("Test Int128 ", True);
        console.print(a, True);
        console.print(" to UInt32 expected=", True);
        console.print(expected, True);
        UInt32 b = a.toUInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt128ToUInt32WithBoundsCheck(Int128 a, UInt32 expected, Boolean oob) {
        console.print("Test Int128 ", True);
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

    void testInt128ToUInt64(Int128 a, UInt64 expected) {
        console.print("Test Int128 ", True);
        console.print(a, True);
        console.print(" to UInt64 expected=", True);
        console.print(expected, True);
        UInt64 b = a.toUInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt128ToUInt64WithBoundsCheck(Int128 a, UInt64 expected, Boolean oob) {
        console.print("Test Int128 ", True);
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

    void testInt128ToUInt128(Int128 a, UInt128 expected) {
        console.print("Test Int128 ", True);
        console.print(a, True);
        console.print(" to UInt128 expected=", True);
        console.print(expected, True);
        UInt128 b = a.toUInt128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt128ToUInt128WithBoundsCheck(Int128 a, UInt128 expected, Boolean oob) {
        console.print("Test Int128 ", True);
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
