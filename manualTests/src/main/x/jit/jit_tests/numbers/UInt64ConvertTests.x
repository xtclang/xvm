/**
 * Tests for UInt64 conversions to other numeric types.
 */
class UInt64ConvertTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running UInt64 Conversion tests >>>>");

        testUInt64ToInt8(0, 0);
        testUInt64ToInt8(100, 100);
        testUInt64ToInt8(127, Int8.MaxValue);
        testUInt64ToInt8(0x7FFF, -1);
        // UInt64.MaxValue is 0xFFFF_FFFF_FFFF_FFFF the lowest byte is 0xFF == -1
        testUInt64ToInt8(UInt64.MaxValue, -1);

        testUInt64ToInt8WithBoundsCheck(0, 0, False);
        testUInt64ToInt8WithBoundsCheck(100, 100, False);
        testUInt64ToInt8WithBoundsCheck(127, Int8.MaxValue, False);
        testUInt64ToInt8WithBoundsCheck(0x7FFF, 0, True);
        testUInt64ToInt8WithBoundsCheck(UInt64.MaxValue, 0, True);

        testUInt64ToInt16(0, 0);
        testUInt64ToInt16(100, 100);
        testUInt64ToInt16(0x7FFF, Int16.MaxValue);
        // UInt64.MaxValue is 0xFFFF_FFFF_FFFF_FFFF the lowest bytes are 0xFFFF == -1
        testUInt64ToInt16(UInt64.MaxValue, -1);

        testUInt64ToInt16WithBoundsCheck(0, 0, False);
        testUInt64ToInt16WithBoundsCheck(100, 100, False);
        testUInt64ToInt16WithBoundsCheck(0x7FFF, Int16.MaxValue, False);
        testUInt64ToInt16WithBoundsCheck(UInt64.MaxValue, 0, True);

        testUInt64ToInt32(0, 0);
        testUInt64ToInt32(100, 100);
        testUInt64ToInt32(0x7FFFFFFF, Int32.MaxValue);
        // UInt64.MaxValue is 0xFFFF_FFFF_FFFF_FFFF the lowest bytes are 0xFFFF == -1
        testUInt64ToInt32(UInt64.MaxValue, -1);

        testUInt64ToInt32WithBoundsCheck(0, 0, False);
        testUInt64ToInt32WithBoundsCheck(100, 100, False);
        testUInt64ToInt32WithBoundsCheck(0x7FFFFFFF, Int32.MaxValue, False);
        testUInt64ToInt32WithBoundsCheck(UInt64.MaxValue, 0, True);

        testUInt64ToInt64(0, 0);
        testUInt64ToInt64(100, 100);
        testUInt64ToInt64(0x7FFFFFFF, 0x7FFFFFFF);
        testUInt64ToInt64(UInt64.MaxValue, -1);

        testUInt64ToInt64WithBoundsCheck(0, 0, False);
        testUInt64ToInt64WithBoundsCheck(100, 100, False);
        testUInt64ToInt64WithBoundsCheck(0x7FFFFFFF, 0x7FFFFFFF, False);
        testUInt64ToInt64WithBoundsCheck(0x7FFF_FFFF_FFFF_FFFF, 0x7FFF_FFFF_FFFF_FFFF, False);
        testUInt64ToInt64WithBoundsCheck(UInt64.MaxValue, 0, True);

        testUInt64ToInt128(0, 0);
        testUInt64ToInt128(100, 100);
        testUInt64ToInt128(0x7FFFFFFF, 0x7FFFFFFF);
        testUInt64ToInt128(UInt64.MaxValue, 18446744073709551615);

        testUInt64ToUInt8(0, 0);
        testUInt64ToUInt8(100, 100);
        testUInt64ToUInt8(127, 127);
        // UInt64.MaxValue is 0xFFFF_FFFF_FFFF_FFFF the lowest byte is 0xFF == 255
        testUInt64ToUInt8(UInt64.MaxValue, 0xFF);

        testUInt64ToUInt8WithBoundsCheck(0, 0, False);
        testUInt64ToUInt8WithBoundsCheck(100, 100, False);
        testUInt64ToUInt8WithBoundsCheck(127, 127, False);
        testUInt64ToUInt8WithBoundsCheck(UInt64.MaxValue, 0, True);

        testUInt64ToUInt16(0, 0);
        testUInt64ToUInt16(100, 100);
        // UInt64.MaxValue is 0xFFFF_FFFF_FFFF_FFFF the lowest bytes are 0xFFFF == 32767
        testUInt64ToUInt16(UInt64.MaxValue, UInt16.MaxValue);

        testUInt64ToUInt16WithBoundsCheck(0, 0, False);
        testUInt64ToUInt16WithBoundsCheck(100, 100, False);
        testUInt64ToUInt16WithBoundsCheck(UInt64.MaxValue, 0, True);

        testUInt64ToUInt32(0, 0);
        testUInt64ToUInt32(100, 100);
        testUInt64ToUInt32(UInt64.MaxValue, 4294967295);

        testUInt64ToUInt32WithBoundsCheck(0, 0, False);
        testUInt64ToUInt32WithBoundsCheck(100, 100, False);
        testUInt64ToUInt32WithBoundsCheck(UInt64.MaxValue, 0, True);

        testUInt64ToUInt64(0, 0);
        testUInt64ToUInt64(100, 100);
        testUInt64ToUInt64(UInt64.MaxValue, 18446744073709551615);

        testUInt64ToUInt64WithBoundsCheck(0, 0, False);
        testUInt64ToUInt64WithBoundsCheck(100, 100, False);
        testUInt64ToUInt64WithBoundsCheck(0x7FFFFFFF, 0x7FFFFFFF, False);
        testUInt64ToUInt64WithBoundsCheck(UInt64.MaxValue, UInt64.MaxValue, False);

        testUInt64ToUInt128(0, 0);
        testUInt64ToUInt128(100, 100);
        testUInt64ToUInt128(UInt64.MaxValue, 18446744073709551615);

        testUInt64ToUInt128WithBoundsCheck(0, 0, False);
        testUInt64ToUInt128WithBoundsCheck(100, 100, False);
        testUInt64ToUInt128WithBoundsCheck(0x7FFFFFFF, 0x7FFFFFFF, False);
        testUInt64ToUInt128WithBoundsCheck(UInt64.MaxValue, UInt64.MaxValue, False);

        console.print("<<<<< Finished UInt64 Conversion tests >>><<");
    }

    void testUInt64ToInt8(UInt64 a, Int8 expected) {
        console.print("Test UInt64 ", True);
        console.print(a, True);
        console.print(" to Int8 expected=", True);
        console.print(expected, True);
        Int8 b = a.toInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt64ToInt8WithBoundsCheck(UInt64 a, Int8 expected, Boolean oob) {
        console.print("Test UInt64 ", True);
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

    void testUInt64ToInt16(UInt64 a, Int16 expected) {
        console.print("Test UInt64 ", True);
        console.print(a, True);
        console.print(" to Int16 expected=", True);
        console.print(expected, True);
        Int16 b = a.toInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt64ToInt16WithBoundsCheck(UInt64 a, Int16 expected, Boolean oob) {
        console.print("Test UInt64 ", True);
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

    void testUInt64ToInt32(UInt64 a, Int32 expected) {
        console.print("Test UInt64 ", True);
        console.print(a, True);
        console.print(" to Int32 expected=", True);
        console.print(expected, True);
        Int32 b = a.toInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt64ToInt32WithBoundsCheck(UInt64 a, Int32 expected, Boolean oob) {
        console.print("Test UInt64 ", True);
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

    void testUInt64ToInt64(UInt64 a, Int64 expected) {
        console.print("Test UInt64 ", True);
        console.print(a, True);
        console.print(" to Int64 expected=", True);
        console.print(expected, True);
        Int64 b = a.toInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt64ToInt64WithBoundsCheck(UInt64 a, Int64 expected, Boolean oob) {
        console.print("Test UInt64 ", True);
        console.print(a, True);
        if (oob) {
            console.print(" to Int64 throws OutOfBounds");
            try {
                a.toInt64(True);
                assert as "Expected OutOfBounds to be thrown";
            } catch (OutOfBounds e) {
                // expected
            }
        } else {
            console.print(" to Int64 succeeds");
            Int64 b = a.toInt64(True);
            assert b == expected;
        }
    }

    void testUInt64ToInt128(UInt64 a, Int128 expected) {
        console.print("Test UInt64 ", True);
        console.print(a, True);
        console.print(" to Int128 expected=", True);
        console.print(expected, True);
        Int128 b = a.toInt128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt64ToUInt8(UInt64 a, UInt8 expected) {
        console.print("Test UInt64 ", True);
        console.print(a, True);
        console.print(" to UInt8 expected=", True);
        console.print(expected, True);
        UInt8 b = a.toUInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt64ToUInt8WithBoundsCheck(UInt64 a, UInt8 expected, Boolean oob) {
        console.print("Test UInt64 ", True);
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

    void testUInt64ToUInt16(UInt64 a, UInt16 expected) {
        console.print("Test UInt64 ", True);
        console.print(a, True);
        console.print(" to UInt16 expected=", True);
        console.print(expected, True);
        UInt16 b = a.toUInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt64ToUInt16WithBoundsCheck(UInt64 a, UInt16 expected, Boolean oob) {
        console.print("Test UInt64 ", True);
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

    void testUInt64ToUInt32(UInt64 a, UInt32 expected) {
        console.print("Test UInt64 ", True);
        console.print(a, True);
        console.print(" to UInt32 expected=", True);
        console.print(expected, True);
        UInt32 b = a.toUInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt64ToUInt32WithBoundsCheck(UInt64 a, UInt32 expected, Boolean oob) {
        console.print("Test UInt64 ", True);
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

    void testUInt64ToUInt64(UInt64 a, UInt64 expected) {
        console.print("Test UInt64 ", True);
        console.print(a, True);
        console.print(" to UInt64 expected=", True);
        console.print(expected, True);
        UInt64 b = a.toUInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt64ToUInt64WithBoundsCheck(UInt64 a, UInt64 expected, Boolean oob) {
        console.print("Test UInt64 ", True);
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

    void testUInt64ToUInt128(UInt64 a, UInt128 expected) {
        console.print("Test UInt64 ", True);
        console.print(a, True);
        console.print(" to UInt128 expected=", True);
        console.print(expected, True);
        UInt128 b = a.toUInt128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testUInt64ToUInt128WithBoundsCheck(UInt64 a, UInt128 expected, Boolean oob) {
        console.print("Test UInt64 ", True);
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
