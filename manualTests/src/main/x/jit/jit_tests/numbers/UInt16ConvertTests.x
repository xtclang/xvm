/**
 * Tests for Int16 conversions to other numeric types.
 */
class UInt16ConvertTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running UInt16 Conversion tests >>>>");
        testUInt16ToInt8(0, 0);
        testUInt16ToInt8(100, 100);
        testUInt16ToInt8(127, Int8.MaxValue);
        testUInt16ToInt8(200, -56);
        // Int16.MaxValue is 0xFFFF the lowest byte is 0xFF == -1
        testUInt16ToInt8(UInt16.MaxValue, -1);

        testUInt16ToInt8WithBoundsCheck(0, 0, False);
        testUInt16ToInt8WithBoundsCheck(100, 100, False);
        testUInt16ToInt8WithBoundsCheck(127, Int8.MaxValue, False);
        testUInt16ToInt8WithBoundsCheck(200, 0, True);
        testUInt16ToInt8WithBoundsCheck(UInt16.MaxValue, 0, True);

        testUInt16ToInt16(0, 0);
        testUInt16ToInt16(500, 500);
        testUInt16ToInt16(50000, -15536);
        testUInt16ToInt16(Int16.MaxValue, 32767);

        testUInt16ToInt16WithBoundsCheck(0, 0, False);
        testUInt16ToInt16WithBoundsCheck(500, 500, False);
        testUInt16ToInt16WithBoundsCheck(0x7FFF, 0x7FFF, False);
        testUInt16ToInt16WithBoundsCheck(50000, 0, True);
        testUInt16ToInt16WithBoundsCheck(UInt16.MaxValue, 0, True);

        testUInt16ToInt32(0, 0);
        testUInt16ToInt32(500, 500);
        testUInt16ToInt32(Int16.MaxValue, 32767);

        testUInt16ToInt64(0, 0);
        testUInt16ToInt64(500, 500);
        testUInt16ToInt64(Int16.MaxValue, 32767);

        testUInt16ToUInt8(0, 0);
        testUInt16ToUInt8(500, 244);
        testUInt16ToUInt8(127, 127);
        // Int16.MaxValue is 0x7FFF the lowest byte is 0xFF == UInt8.MaxValue
        testUInt16ToUInt8(Int16.MaxValue, UInt8.MaxValue);

        testUInt16ToUInt8WithBoundsCheck(0, 0, False);
        testUInt16ToUInt8WithBoundsCheck(100, 100, False);
        testUInt16ToUInt8WithBoundsCheck(127, 127, False);
        testUInt16ToUInt8WithBoundsCheck(255, 255, False);
        testUInt16ToUInt8WithBoundsCheck(500, 0, True);
        testUInt16ToUInt8WithBoundsCheck(Int16.MaxValue, 0, True);

        testUInt16ToUInt16(0, 0);
        testUInt16ToUInt16(500, 500);
        testUInt16ToUInt16(Int16.MaxValue, 32767);

        testUInt16ToUInt32(0, 0);
        testUInt16ToUInt32(500, 500);
        testUInt16ToUInt32(Int16.MaxValue, 32767);

        testUInt16ToUInt64(0, 0);
        testUInt16ToUInt64(500, 500);
        testUInt16ToUInt64(Int16.MaxValue, 32767);

        console.print("<<<<< Finished UInt16 Conversion tests >>><<");
    }

    void testUInt16ToInt8(UInt16 a, Int8 expected) {
        console.print("Test Int16 ", True);
        console.print(a, True);
        console.print(" to Int8 expected=", True);
        console.print(expected, True);
        Int8 b = a.toInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt16ToInt8WithBoundsCheck(UInt16 a, Int8 expected, Boolean oob) {
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

    void testUInt16ToInt16(UInt16 a, Int16 expected) {
        console.print("Test Int16 ", True);
        console.print(a, True);
        console.print(" to Int16 expected=", True);
        console.print(expected, True);
        Int16 b = a.toInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt16ToInt16WithBoundsCheck(UInt16 a, Int16 expected, Boolean oob) {
        console.print("Test Int16 ", True);
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

    void testUInt16ToInt32(UInt16 a, Int32 expected) {
        console.print("Test Int16 ", True);
        console.print(a, True);
        console.print(" to Int32 expected=", True);
        console.print(expected, True);
        Int32 b = a.toInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt16ToInt64(UInt16 a, Int64 expected) {
        console.print("Test Int16 ", True);
        console.print(a, True);
        console.print(" to Int64 expected=", True);
        console.print(expected, True);
        Int64 b = a.toInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt16ToUInt8(UInt16 a, UInt8 expected) {
        console.print("Test Int16 ", True);
        console.print(a, True);
        console.print(" to UInt8 expected=", True);
        console.print(expected, True);
        UInt8 b = a.toUInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt16ToUInt8WithBoundsCheck(UInt16 a, UInt8 expected, Boolean oob) {
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

    void testUInt16ToUInt16(UInt16 a, UInt16 expected) {
        console.print("Test Int16 ", True);
        console.print(a, True);
        console.print(" to UInt16 expected=", True);
        console.print(expected, True);
        UInt16 b = a.toUInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt16ToUInt16WithBoundsCheck(UInt16 a, UInt16 expected, Boolean oob) {
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

    void testUInt16ToUInt32(UInt16 a, UInt32 expected) {
        console.print("Test Int16 ", True);
        console.print(a, True);
        console.print(" to UInt32 expected=", True);
        console.print(expected, True);
        UInt32 b = a.toUInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt16ToUInt64(UInt16 a, UInt64 expected) {
        console.print("Test Int16 ", True);
        console.print(a, True);
        console.print(" to UInt64 expected=", True);
        console.print(expected, True);
        UInt64 b = a.toUInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }
}
