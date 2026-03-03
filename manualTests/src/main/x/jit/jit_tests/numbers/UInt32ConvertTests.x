/**
 * Tests for UInt32 conversions to other numeric types.
 */
class UInt32ConvertTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running UInt32 Conversion tests >>>>");

        testUInt32ToInt8(0, 0);
        testUInt32ToInt8(100, 100);
        testUInt32ToInt8(127, Int8.MaxValue);
        testUInt32ToInt8(0x7FFF, -1);
        // UInt32.MaxValue is 0x7FFFFFFF the lowest byte is 0xFF == -1
        testUInt32ToInt8(UInt32.MaxValue, -1);

        testUInt32ToInt8WithBoundsCheck(0, 0, False);
        testUInt32ToInt8WithBoundsCheck(100, 100, False);
        testUInt32ToInt8WithBoundsCheck(127, Int8.MaxValue, False);
        testUInt32ToInt8WithBoundsCheck(0x7FFF, 0, True);
        testUInt32ToInt8WithBoundsCheck(UInt32.MaxValue, 0, True);

        testUInt32ToInt16(0, 0);
        testUInt32ToInt16(100, 100);
        testUInt32ToInt16(0x7FFF, Int16.MaxValue);
        // UInt32.MaxValue is 0x7FFFFFFF the lowest bytes are 0xFFFF == -1
        testUInt32ToInt16(UInt32.MaxValue, -1);

        testUInt32ToInt16WithBoundsCheck(0, 0, False);
        testUInt32ToInt16WithBoundsCheck(100, 100, False);
        testUInt32ToInt16WithBoundsCheck(0x7FFF, Int16.MaxValue, False);
        testUInt32ToInt16WithBoundsCheck(UInt32.MaxValue, 0, True);

        testUInt32ToInt32(0, 0);
        testUInt32ToInt32(100, 100);
        testUInt32ToInt32(UInt32.MaxValue, -1);

        testUInt32ToInt64(0, 0);
        testUInt32ToInt64(100, 100);
        testUInt32ToInt64(UInt32.MaxValue, 0xFFFFFFFF);

        testUInt32ToInt128(0, 0);
        testUInt32ToInt128(100, 100);
        testUInt32ToInt128(UInt32.MaxValue, 0xFFFFFFFF);

        testUInt32ToUInt8(0, 0);
        testUInt32ToUInt8(100, 100);
        testUInt32ToUInt8(127, 127);
        // UInt32.MaxValue is 0x7FFFFFFF the lowest byte is 0xFF == 255
        testUInt32ToUInt8(UInt32.MaxValue, 255);

        testUInt32ToUInt8WithBoundsCheck(0, 0, False);
        testUInt32ToUInt8WithBoundsCheck(100, 100, False);
        testUInt32ToUInt8WithBoundsCheck(127, 127, False);
        testUInt32ToUInt8WithBoundsCheck(UInt32.MaxValue, 0, True);

        testUInt32ToUInt16(0, 0);
        testUInt32ToUInt16(100, 100);
        // UInt32.MaxValue is 0x7FFFFFFF the lowest bytes are 0xFFFF == 32767
        testUInt32ToUInt16(UInt32.MaxValue, UInt16.MaxValue);

        testUInt32ToUInt16WithBoundsCheck(0, 0, False);
        testUInt32ToUInt16WithBoundsCheck(100, 100, False);
        testUInt32ToUInt16WithBoundsCheck(UInt32.MaxValue, 0, True);

        testUInt32ToUInt32(0, 0);
        testUInt32ToUInt32(100, 100);
        testUInt32ToUInt32(UInt32.MaxValue, UInt32.MaxValue);

        testUInt32ToUInt32WithBoundsCheck(0, 0, False);
        testUInt32ToUInt32WithBoundsCheck(100, 100, False);
        testUInt32ToUInt32WithBoundsCheck(UInt32.MaxValue, UInt32.MaxValue, False);

        testUInt32ToUInt64(0, 0);
        testUInt32ToUInt64(100, 100);
        testUInt32ToUInt64(UInt32.MaxValue, 0xFFFFFFFF);

        testUInt32ToUInt64WithBoundsCheck(0, 0, False);
        testUInt32ToUInt64WithBoundsCheck(100, 100, False);
        testUInt32ToUInt64WithBoundsCheck(UInt32.MaxValue, 0xFFFFFFFF, False);

        testUInt32ToUInt128(0, 0);
        testUInt32ToUInt128(100, 100);
        testUInt32ToUInt128(UInt32.MaxValue, 0xFFFFFFFF);

        testUInt32ToUInt128WithBoundsCheck(0, 0, False);
        testUInt32ToUInt128WithBoundsCheck(100, 100, False);
        testUInt32ToUInt128WithBoundsCheck(UInt32.MaxValue, 0xFFFFFFFF, False);

        console.print("<<<<< Finished UInt32 Conversion tests >>><<");
    }

    void testUInt32ToInt8(UInt32 a, Int8 expected) {
        console.print("Test UInt32 ", True);
        console.print(a, True);
        console.print(" to Int8 expected=", True);
        console.print(expected, True);
        Int8 b = a.toInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt32ToInt8WithBoundsCheck(UInt32 a, Int8 expected, Boolean oob) {
        console.print("Test UInt32 ", True);
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

    void testUInt32ToInt16(UInt32 a, Int16 expected) {
        console.print("Test UInt32 ", True);
        console.print(a, True);
        console.print(" to Int16 expected=", True);
        console.print(expected, True);
        Int16 b = a.toInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt32ToInt16WithBoundsCheck(UInt32 a, Int16 expected, Boolean oob) {
        console.print("Test UInt32 ", True);
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

    void testUInt32ToInt32(UInt32 a, Int32 expected) {
        console.print("Test UInt32 ", True);
        console.print(a, True);
        console.print(" to Int32 expected=", True);
        console.print(expected, True);
        Int32 b = a.toInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt32ToInt64(UInt32 a, Int64 expected) {
        console.print("Test UInt32 ", True);
        console.print(a, True);
        console.print(" to Int64 expected=", True);
        console.print(expected, True);
        Int64 b = a.toInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt32ToInt128(UInt32 a, Int128 expected) {
        console.print("Test UInt32 ", True);
        console.print(a, True);
        console.print(" to Int128 expected=", True);
        console.print(expected, True);
        Int128 b = a.toInt128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt32ToUInt8(UInt32 a, UInt8 expected) {
        console.print("Test UInt32 ", True);
        console.print(a, True);
        console.print(" to UInt8 expected=", True);
        console.print(expected, True);
        UInt8 b = a.toUInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt32ToUInt8WithBoundsCheck(UInt32 a, UInt8 expected, Boolean oob) {
        console.print("Test UInt32 ", True);
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

    void testUInt32ToUInt16(UInt32 a, UInt16 expected) {
        console.print("Test UInt32 ", True);
        console.print(a, True);
        console.print(" to UInt16 expected=", True);
        console.print(expected, True);
        UInt16 b = a.toUInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt32ToUInt16WithBoundsCheck(UInt32 a, UInt16 expected, Boolean oob) {
        console.print("Test UInt32 ", True);
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

    void testUInt32ToUInt32(UInt32 a, UInt32 expected) {
        console.print("Test UInt32 ", True);
        console.print(a, True);
        console.print(" to UInt32 expected=", True);
        console.print(expected, True);
        UInt32 b = a.toUInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt32ToUInt32WithBoundsCheck(UInt32 a, UInt32 expected, Boolean oob) {
        console.print("Test UInt32 ", True);
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

    void testUInt32ToUInt64(UInt32 a, UInt64 expected) {
        console.print("Test UInt32 ", True);
        console.print(a, True);
        console.print(" to UInt64 expected=", True);
        console.print(expected, True);
        UInt64 b = a.toUInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt32ToUInt64WithBoundsCheck(UInt32 a, UInt64 expected, Boolean oob) {
        console.print("Test UInt32 ", True);
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

    void testUInt32ToUInt128(UInt32 a, UInt128 expected) {
        console.print("Test UInt32 ", True);
        console.print(a, True);
        console.print(" to UInt128 expected=", True);
        console.print(expected, True);
        UInt128 b = a.toUInt128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt32ToUInt128WithBoundsCheck(UInt32 a, UInt128 expected, Boolean oob) {
        console.print("Test UInt32 ", True);
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
