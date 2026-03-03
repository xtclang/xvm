/**
 * Tests for UInt8 conversions to other numeric types.
 */
class UInt8ConvertTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running UInt8 Conversion tests >>>>");
        testUInt8ToInt8(0, 0);
        testUInt8ToInt8(100, 100);
        testUInt8ToInt8(127, 127);
        testUInt8ToInt8(200, -56);
        testUInt8ToInt8(UInt8.MaxValue, -1);

        testUInt8ToInt8WithBoundsCheck(0, 0, False);
        testUInt8ToInt8WithBoundsCheck(100, 100, False);
        testUInt8ToInt8WithBoundsCheck(127, 127, False);
        testUInt8ToInt8WithBoundsCheck(200, 0, True);
        testUInt8ToInt8WithBoundsCheck(UInt8.MaxValue, 0, True);

        testUInt8ToInt16(0, 0);
        testUInt8ToInt16(100, 100);
        testUInt8ToInt16(Int8.MaxValue, 127);

        testUInt8ToInt32(0, 0);
        testUInt8ToInt32(100, 100);
        testUInt8ToInt32(Int8.MaxValue, 127);

        testUInt8ToInt64(0, 0);
        testUInt8ToInt64(100, 100);
        testUInt8ToInt64(Int8.MaxValue, 127);

        testUInt8ToInt128(0, 0);
        testUInt8ToInt128(100, 100);
        testUInt8ToInt128(Int8.MaxValue, 127);

        testUInt8ToUInt8(0, 0);
        testUInt8ToUInt8(100, 100);
        testUInt8ToUInt8(Int8.MaxValue, 127);

        testUInt8ToUInt16(0, 0);
        testUInt8ToUInt16(100, 100);
        testUInt8ToUInt16(Int8.MaxValue, 127);

        testUInt8ToUInt32(0, 0);
        testUInt8ToUInt32(100, 100);
        testUInt8ToUInt32(Int8.MaxValue, 127);

        testUInt8ToUInt64(0, 0);
        testUInt8ToUInt64(100, 100);
        testUInt8ToUInt64(Int8.MaxValue, 127);

        testUInt8ToUInt128(0, 0);
        testUInt8ToUInt128(100, 100);
        testUInt8ToUInt128(Int8.MaxValue, 127);

        console.print("<<<<< Finished UInt8 Conversion tests >>>>>");
    }

    void testUInt8ToInt8(UInt8 a, Int8 expected) {
        console.print("Test UInt8 ", True);
        console.print(a, True);
        console.print(" to Int8: ", True);
        console.print(expected);
        Int8 b = a.toInt8();
        console.print(b);
        assert b == expected;
    }

    void testUInt8ToInt8WithBoundsCheck(UInt8 a, UInt8 expected, Boolean oob) {
        console.print("Test UInt8 ", True);
        console.print(a, True);
        if (oob) {
            console.print(" to UInt8 throws OutOfBounds");
            try {
                a.toInt8(True);
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

    void testUInt8ToInt16(UInt8 a, Int16 expected) {
        console.print("Test UInt8 ", True);
        console.print(a, True);
        console.print(" to Int16: ", True);
        console.print(expected);
        Int16 b = a.toInt16();
        assert b == expected;
    }

    void testUInt8ToInt32(UInt8 a, Int32 expected) {
        console.print("Test UInt8 ", True);
        console.print(a, True);
        console.print(" to Int32: ", True);
        console.print(expected);
        Int32 b = a.toInt32();
        assert b == expected;
    }

    void testUInt8ToInt64(UInt8 a, Int64 expected) {
        console.print("Test UInt8 ", True);
        console.print(a, True);
        console.print(" to Int64: ", True);
        console.print(expected);
        Int64 b = a.toInt64();
        assert b == expected;
    }

    void testUInt8ToInt128(UInt8 a, Int128 expected) {
        console.print("Test UInt8 ", True);
        console.print(a, True);
        console.print(" to Int128: ", True);
        console.print(expected);
        Int128 b = a.toInt128();
        assert b == expected;
    }

    void testUInt8ToUInt8(UInt8 a, UInt8 expected) {
        console.print("Test UInt8 ", True);
        console.print(a, True);
        console.print(" to UInt8: ", True);
        console.print(expected);
        UInt8 b = a.toUInt8();
        console.print(b);
        assert b == expected;
    }

    void testUInt8ToUInt16(UInt8 a, UInt16 expected) {
        console.print("Test UInt8 ", True);
        console.print(a, True);
        console.print(" to UInt16: ", True);
        console.print(expected);
        UInt16 b = a.toUInt16();
        console.print(b);
        assert b == expected;
    }

    void testUInt8ToUInt32(UInt8 a, UInt32 expected) {
        console.print("Test UInt8 ", True);
        console.print(a, True);
        console.print(" to UInt32: ", True);
        console.print(expected);
        UInt32 b = a.toUInt32();
        console.print(b);
        assert b == expected;
    }

    void testUInt8ToUInt64(UInt8 a, UInt64 expected) {
        console.print("Test UInt8 ", True);
        console.print(a, True);
        console.print(" to UInt64: ", True);
        console.print(expected);
        UInt64 b = a.toUInt64();
        console.print(b);
        assert b == expected;
    }

    void testUInt8ToUInt128(UInt8 a, UInt128 expected) {
        console.print("Test UInt8 ", True);
        console.print(a, True);
        console.print(" to UInt128: ", True);
        console.print(expected);
        UInt128 b = a.toUInt128();
        console.print(b);
        assert b == expected;
    }
}