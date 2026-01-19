/**
 * Tests for Int16 conversions to other numeric types.
 */
class Int16ConvertTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Int16 Conversion tests >>>>");
        // Int16.MinValue is 0x8000 the lowest byte is zero
        testInt16ToInt8(Int16.MinValue, 0);
        testInt16ToInt8(-128, Int8.MinValue);
        testInt16ToInt8(-100, -100);
        testInt16ToInt8(0, 0);
        testInt16ToInt8(100, 100);
        testInt16ToInt8(127, Int8.MaxValue);
        // Int16.MinValue is 0x7FFF the lowest byte is 0xFF == -1
        testInt16ToInt8(Int16.MaxValue, -1);

        testInt16ToInt8WithBoundsCheck(Int16.MinValue, 0, True);
        testInt16ToInt8WithBoundsCheck(-128, Int8.MinValue, False);
        testInt16ToInt8WithBoundsCheck(-100, -100, False);
        testInt16ToInt8WithBoundsCheck(0, 0, False);
        testInt16ToInt8WithBoundsCheck(100, 100, False);
        testInt16ToInt8WithBoundsCheck(127, Int8.MaxValue, False);
        testInt16ToInt8WithBoundsCheck(Int16.MaxValue, 0, True);

        testInt16ToInt16(Int16.MinValue, -32768);
        testInt16ToInt16(-100, -100);
        testInt16ToInt16(0, 0);
        testInt16ToInt16(100, 100);
        testInt16ToInt16(Int16.MaxValue, 32767);

        testInt16ToInt32(Int16.MinValue, -32768);
        testInt16ToInt32(-100, -100);
        testInt16ToInt32(0, 0);
        testInt16ToInt32(100, 100);
        testInt16ToInt32(Int16.MaxValue, 32767);

        testInt16ToInt64(Int16.MinValue, -32768);
        testInt16ToInt64(-100, -100);
        testInt16ToInt64(0, 0);
        testInt16ToInt64(100, 100);
        testInt16ToInt64(Int16.MaxValue, 32767);

        // Int16.MinValue is 0x8000 the lowest byte is zero
        testInt16ToUInt8(Int16.MinValue, 0);
        testInt16ToUInt8(-128, 128);
        testInt16ToUInt8(-1, UInt8.MaxValue);
        testInt16ToUInt8(-100, 156);
        testInt16ToUInt8(0, 0);
        testInt16ToUInt8(100, 100);
        testInt16ToUInt8(127, 127);
        // Int16.MinValue is 0x7FFF the lowest byte is 0xFF == UInt8.MaxValue
        testInt16ToUInt8(Int16.MaxValue, UInt8.MaxValue);

        testInt16ToUInt8WithBoundsCheck(Int16.MinValue, 0, True);
        testInt16ToUInt8WithBoundsCheck(-128, 0, True);
        testInt16ToUInt8WithBoundsCheck(-1, 0, True);
        testInt16ToUInt8WithBoundsCheck(-100, 0, True);
        testInt16ToUInt8WithBoundsCheck(0, 0, False);
        testInt16ToUInt8WithBoundsCheck(100, 100, False);
        testInt16ToUInt8WithBoundsCheck(127, 127, False);
        testInt16ToUInt8WithBoundsCheck(255, 255, False);
        testInt16ToUInt8WithBoundsCheck(Int16.MaxValue, 0, True);

        testInt16ToUInt16(Int16.MinValue, 32768);
        testInt16ToUInt16(-1, 65535);
        testInt16ToUInt16(-100, 65436);
        testInt16ToUInt16(0, 0);
        testInt16ToUInt16(100, 100);
        testInt16ToUInt16(Int16.MaxValue, 32767);

        testInt16ToUInt16WithBoundsCheck(Int16.MinValue, 0, True);
        testInt16ToUInt16WithBoundsCheck(-1, 0, True);
        testInt16ToUInt16WithBoundsCheck(-100, 0, True);
        testInt16ToUInt16WithBoundsCheck(0, 0, False);
        testInt16ToUInt16WithBoundsCheck(100, 100, False);
        testInt16ToUInt16WithBoundsCheck(Int16.MaxValue, 32767, False);

        testInt16ToUInt32(Int16.MinValue, 4294934528);
        testInt16ToUInt32(-1, 4294967295);
        testInt16ToUInt32(-100, 4294967196);
        testInt16ToUInt32(0, 0);
        testInt16ToUInt32(100, 100);
        testInt16ToUInt32(Int16.MaxValue, 32767);

        testInt16ToUInt32WithBoundsCheck(Int16.MinValue, 0, True);
        testInt16ToUInt32WithBoundsCheck(-1, 0, True);
        testInt16ToUInt32WithBoundsCheck(-100, 0, True);
        testInt16ToUInt32WithBoundsCheck(0, 0, False);
        testInt16ToUInt32WithBoundsCheck(100, 100, False);
        testInt16ToUInt32WithBoundsCheck(Int16.MaxValue, 32767, False);

        testInt16ToUInt64(Int16.MinValue, 18446744073709518848);
        testInt16ToUInt64(-1, 18446744073709551615);
        testInt16ToUInt64(-100, 18446744073709551516);
        testInt16ToUInt64(0, 0);
        testInt16ToUInt64(100, 100);
        testInt16ToUInt64(Int16.MaxValue, 32767);

        testInt16ToUInt64WithBoundsCheck(Int16.MinValue, 0, True);
        testInt16ToUInt64WithBoundsCheck(-1, 0, True);
        testInt16ToUInt64WithBoundsCheck(-100, 0, True);
        testInt16ToUInt64WithBoundsCheck(0, 0, False);
        testInt16ToUInt64WithBoundsCheck(100, 100, False);
        testInt16ToUInt64WithBoundsCheck(Int16.MaxValue, 32767, False);

        console.print("<<<<< Finished Int16 Conversion tests >>><<");
    }

    void testInt16ToInt8(Int16 a, Int8 expected) {
        console.print("Test Int16 ", True);
        console.print(a, True);
        console.print(" to Int8 expected=", True);
        console.print(expected, True);
        Int8 b = a.toInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt16ToInt8WithBoundsCheck(Int16 a, Int8 expected, Boolean oob) {
        console.print("Test Int16 ", True);
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

    void testInt16ToInt16(Int16 a, Int16 expected) {
        console.print("Test Int16 ", True);
        console.print(a, True);
        console.print(" to Int16 expected=", True);
        console.print(expected, True);
        Int16 b = a;
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt16ToInt32(Int16 a, Int32 expected) {
        console.print("Test Int16 ", True);
        console.print(a, True);
        console.print(" to Int32 expected=", True);
        console.print(expected, True);
        Int32 b = a;
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt16ToInt64(Int16 a, Int64 expected) {
        console.print("Test Int16 ", True);
        console.print(a, True);
        console.print(" to Int64 expected=", True);
        console.print(expected, True);
        Int64 b = a;
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt16ToUInt8(Int16 a, UInt8 expected) {
        console.print("Test Int16 ", True);
        console.print(a, True);
        console.print(" to UInt8 expected=", True);
        console.print(expected, True);
        UInt8 b = a.toUInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt16ToUInt8WithBoundsCheck(Int16 a, UInt8 expected, Boolean oob) {
        console.print("Test Int16 ", True);
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

    void testInt16ToUInt16(Int16 a, UInt16 expected) {
        console.print("Test Int16 ", True);
        console.print(a, True);
        console.print(" to UInt16 expected=", True);
        console.print(expected, True);
        UInt16 b = a.toUInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt16ToUInt16WithBoundsCheck(Int16 a, UInt16 expected, Boolean oob) {
        console.print("Test Int16 ", True);
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

    void testInt16ToUInt32(Int16 a, UInt32 expected) {
        console.print("Test Int16 ", True);
        console.print(a, True);
        console.print(" to UInt32 expected=", True);
        console.print(expected, True);
        UInt32 b = a.toUInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt16ToUInt32WithBoundsCheck(Int16 a, UInt32 expected, Boolean oob) {
        console.print("Test Int16 ", True);
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

    void testInt16ToUInt64(Int16 a, UInt64 expected) {
        console.print("Test Int16 ", True);
        console.print(a, True);
        console.print(" to UInt64 expected=", True);
        console.print(expected, True);
        UInt64 b = a.toUInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testInt16ToUInt64WithBoundsCheck(Int16 a, UInt64 expected, Boolean oob) {
        console.print("Test Int16 ", True);
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
}
