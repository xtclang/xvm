/**
 * Tests for Dec64 conversions to other numeric types.
 */
class Dec64ConvertTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Dec64 Conversion tests >>>>");

        testDec64ToDec32(-1000.456, -1000.456);
        testDec64ToDec32(1000.456, 1000.456);
        testDec64ToDec64(-1000.456, -1000.456);
        testDec64ToDec64(1000.456, 1000.456);
        testDec64ToDec128(-1000.456, -1000.456);
        testDec64ToDec128(1000.456, 1000.456);

        testDec64ToInt8(-200, 56);
        testDec64ToInt8(-128.4, -128);
        testDec64ToInt8(-100, -100);
        testDec64ToInt8(0, 0);
        testDec64ToInt8(100, 100);
        testDec64ToInt8(256.4, 0); // rounds to 256 == 0x100 converted to Int8 == 0
        testDec64ToInt8Rounding(10.4, 10, TowardZero); // rounds towards zero
        testDec64ToInt8Rounding(10.5, 10, TowardZero);
        testDec64ToInt8Rounding(10.9, 10, TowardZero);
        testDec64ToInt8Rounding(-10.4, -10, TowardZero);
        testDec64ToInt8Rounding(-10.5, -10, TowardZero);
        testDec64ToInt8Rounding(-10.9, -10, TowardZero);
        testDec64ToInt8Rounding(10.1, 10, TiesToEven); // .5 rounds to the even number up or down
        testDec64ToInt8Rounding(10.5, 10, TiesToEven);
        testDec64ToInt8Rounding(10.9, 11, TiesToEven);
        testDec64ToInt8Rounding(11.1, 11, TiesToEven);
        testDec64ToInt8Rounding(11.5, 12, TiesToEven);
        testDec64ToInt8Rounding(11.9, 12, TiesToEven);
        testDec64ToInt8Rounding(-10.1, -10, TiesToEven);
        testDec64ToInt8Rounding(-10.5, -10, TiesToEven);
        testDec64ToInt8Rounding(-10.9, -11, TiesToEven);
        testDec64ToInt8Rounding(-11.1, -11, TiesToEven);
        testDec64ToInt8Rounding(-11.5, -12, TiesToEven);
        testDec64ToInt8Rounding(-11.9, -12, TiesToEven);
        testDec64ToInt8Rounding(10.1, 11, TiesToAway); // rounds up
        testDec64ToInt8Rounding(10.5, 11, TiesToAway);
        testDec64ToInt8Rounding(10.9, 11, TiesToAway);
        testDec64ToInt8Rounding(-10.1, -11, TiesToAway);
        testDec64ToInt8Rounding(-10.5, -11, TiesToAway);
        testDec64ToInt8Rounding(-10.9, -11, TiesToAway);
        testDec64ToInt8Rounding(10.4, 11, TowardPositive); // rounds towards +ve infinity
        testDec64ToInt8Rounding(10.5, 11, TowardPositive);
        testDec64ToInt8Rounding(10.9, 11, TowardPositive);
        testDec64ToInt8Rounding(-10.4, -10, TowardPositive);
        testDec64ToInt8Rounding(-10.5, -10, TowardPositive);
        testDec64ToInt8Rounding(-10.9, -10, TowardPositive);
        testDec64ToInt8Rounding(10.4, 10, TowardNegative); // rounds towards -ve infinity
        testDec64ToInt8Rounding(10.5, 10, TowardNegative);
        testDec64ToInt8Rounding(10.9, 10, TowardNegative);
        testDec64ToInt8Rounding(-10.4, -11, TowardNegative);
        testDec64ToInt8Rounding(-10.5, -11, TowardNegative);
        testDec64ToInt8Rounding(-10.9, -11, TowardNegative);
        testDec64ToInt8WithBoundsCheck(-129.3, 0, True);
        testDec64ToInt8WithBoundsCheck(-128.9, Int8.MinValue, False);
        testDec64ToInt8WithBoundsCheck(-128.4, Int8.MinValue, False);
        testDec64ToInt8WithBoundsCheck(-100, -100, False);
        testDec64ToInt8WithBoundsCheck(0, 0, False);
        testDec64ToInt8WithBoundsCheck(100, 100, False);
        testDec64ToInt8WithBoundsCheck(127.4, 127, False);
        testDec64ToInt8WithBoundsCheck(128.4, 0, True);

        testDec64ToInt16(-33768.9, 31768);
        testDec64ToInt16(-32768.4, -32768);
        testDec64ToInt16(-100, -100);
        testDec64ToInt16(0, 0);
        testDec64ToInt16(100, 100);
        testDec64ToInt16(65536.4, 0); // rounds to 65536 == 0x10000 converted to Int16 == 0
        testDec64ToInt16Rounding(10.4, 10, TowardZero); // rounds towards zero
        testDec64ToInt16Rounding(10.5, 10, TowardZero);
        testDec64ToInt16Rounding(10.9, 10, TowardZero);
        testDec64ToInt16Rounding(-10.4, -10, TowardZero);
        testDec64ToInt16Rounding(-10.5, -10, TowardZero);
        testDec64ToInt16Rounding(-10.9, -10, TowardZero);
        testDec64ToInt16Rounding(10.1, 10, TiesToEven); // .5 rounds to the even number up or down
        testDec64ToInt16Rounding(10.5, 10, TiesToEven);
        testDec64ToInt16Rounding(10.9, 11, TiesToEven);
        testDec64ToInt16Rounding(11.1, 11, TiesToEven);
        testDec64ToInt16Rounding(11.5, 12, TiesToEven);
        testDec64ToInt16Rounding(11.9, 12, TiesToEven);
        testDec64ToInt16Rounding(-10.1, -10, TiesToEven);
        testDec64ToInt16Rounding(-10.5, -10, TiesToEven);
        testDec64ToInt16Rounding(-10.9, -11, TiesToEven);
        testDec64ToInt16Rounding(-11.1, -11, TiesToEven);
        testDec64ToInt16Rounding(-11.5, -12, TiesToEven);
        testDec64ToInt16Rounding(-11.9, -12, TiesToEven);
        testDec64ToInt16Rounding(10.1, 11, TiesToAway); // rounds up
        testDec64ToInt16Rounding(10.5, 11, TiesToAway);
        testDec64ToInt16Rounding(10.9, 11, TiesToAway);
        testDec64ToInt16Rounding(-10.1, -11, TiesToAway);
        testDec64ToInt16Rounding(-10.5, -11, TiesToAway);
        testDec64ToInt16Rounding(-10.9, -11, TiesToAway);
        testDec64ToInt16Rounding(10.4, 11, TowardPositive); // rounds towards +ve infinity
        testDec64ToInt16Rounding(10.5, 11, TowardPositive);
        testDec64ToInt16Rounding(10.9, 11, TowardPositive);
        testDec64ToInt16Rounding(-10.4, -10, TowardPositive);
        testDec64ToInt16Rounding(-10.5, -10, TowardPositive);
        testDec64ToInt16Rounding(-10.9, -10, TowardPositive);
        testDec64ToInt16Rounding(10.4, 10, TowardNegative); // rounds towards -ve infinity
        testDec64ToInt16Rounding(10.5, 10, TowardNegative);
        testDec64ToInt16Rounding(10.9, 10, TowardNegative);
        testDec64ToInt16Rounding(-10.4, -11, TowardNegative);
        testDec64ToInt16Rounding(-10.5, -11, TowardNegative);
        testDec64ToInt16Rounding(-10.9, -11, TowardNegative);
        testDec64ToInt16WithBoundsCheck(-32769.1, 0, True);
        testDec64ToInt16WithBoundsCheck(-32768.9, Int16.MinValue, False);
        testDec64ToInt16WithBoundsCheck(-32768.4, Int16.MinValue, False);
        testDec64ToInt16WithBoundsCheck(-100, -100, False);
        testDec64ToInt16WithBoundsCheck(0, 0, False);
        testDec64ToInt16WithBoundsCheck(100, 100, False);
        testDec64ToInt16WithBoundsCheck(32767.7, 32767, False);
        testDec64ToInt16WithBoundsCheck(32768.1, 0, True);

        testDec64ToInt32(-2147483649.1, 2147483647);
        testDec64ToInt32(-100, -100);
        testDec64ToInt32(0, 0);
        testDec64ToInt32(100, 100);
        testDec64ToInt32(2147483648.1, -2147483648);
        testDec64ToInt32Rounding(10.4, 10, TowardZero); // rounds towards zero
        testDec64ToInt32Rounding(10.5, 10, TowardZero);
        testDec64ToInt32Rounding(10.9, 10, TowardZero);
        testDec64ToInt32Rounding(-10.4, -10, TowardZero);
        testDec64ToInt32Rounding(-10.5, -10, TowardZero);
        testDec64ToInt32Rounding(-10.9, -10, TowardZero);
        testDec64ToInt32Rounding(10.1, 10, TiesToEven); // .5 rounds to the even number up or down
        testDec64ToInt32Rounding(10.5, 10, TiesToEven);
        testDec64ToInt32Rounding(10.9, 11, TiesToEven);
        testDec64ToInt32Rounding(11.1, 11, TiesToEven);
        testDec64ToInt32Rounding(11.5, 12, TiesToEven);
        testDec64ToInt32Rounding(11.9, 12, TiesToEven);
        testDec64ToInt32Rounding(-10.1, -10, TiesToEven);
        testDec64ToInt32Rounding(-10.5, -10, TiesToEven);
        testDec64ToInt32Rounding(-10.9, -11, TiesToEven);
        testDec64ToInt32Rounding(-11.1, -11, TiesToEven);
        testDec64ToInt32Rounding(-11.5, -12, TiesToEven);
        testDec64ToInt32Rounding(-11.9, -12, TiesToEven);
        testDec64ToInt32Rounding(10.1, 11, TiesToAway); // rounds up
        testDec64ToInt32Rounding(10.5, 11, TiesToAway);
        testDec64ToInt32Rounding(10.9, 11, TiesToAway);
        testDec64ToInt32Rounding(-10.1, -11, TiesToAway);
        testDec64ToInt32Rounding(-10.5, -11, TiesToAway);
        testDec64ToInt32Rounding(-10.9, -11, TiesToAway);
        testDec64ToInt32Rounding(10.4, 11, TowardPositive); // rounds towards +ve infinity
        testDec64ToInt32Rounding(10.5, 11, TowardPositive);
        testDec64ToInt32Rounding(10.9, 11, TowardPositive);
        testDec64ToInt32Rounding(-10.4, -10, TowardPositive);
        testDec64ToInt32Rounding(-10.5, -10, TowardPositive);
        testDec64ToInt32Rounding(-10.9, -10, TowardPositive);
        testDec64ToInt32Rounding(10.4, 10, TowardNegative); // rounds towards -ve infinity
        testDec64ToInt32Rounding(10.5, 10, TowardNegative);
        testDec64ToInt32Rounding(10.9, 10, TowardNegative);
        testDec64ToInt32Rounding(-10.4, -11, TowardNegative);
        testDec64ToInt32Rounding(-10.5, -11, TowardNegative);
        testDec64ToInt32Rounding(-10.9, -11, TowardNegative);
        testDec64ToInt32WithBoundsCheck(-2147483649.1, 0, True);
        testDec64ToInt32WithBoundsCheck(-100, -100, False);
        testDec64ToInt32WithBoundsCheck(0, 0, False);
        testDec64ToInt32WithBoundsCheck(100, 100, False);
        testDec64ToInt32WithBoundsCheck(2147483648.1, 0, True);

        testDec64ToInt64(-100, -100);
        testDec64ToInt64(0, 0);
        testDec64ToInt64(100, 100);
        testDec64ToInt64Rounding(10.5, 10, TowardZero);
        testDec64ToInt64Rounding(10.5, 10, TiesToEven);
        testDec64ToInt64Rounding(10.5, 11, TiesToAway);
        testDec64ToInt64Rounding(10.5, 11, TowardPositive);
        testDec64ToInt64Rounding(10.5, 10, TowardNegative);
        testDec64ToInt64WithBoundsCheck(-100, -100, False);
        testDec64ToInt64WithBoundsCheck(0, 0, False);
        testDec64ToInt64WithBoundsCheck(100, 100, False);

        testDec64ToInt128(-100, -100);
        testDec64ToInt128(0, 0);
        testDec64ToInt128(100, 100);
        testDec64ToInt128Rounding(10.5, 10, TowardZero);
        testDec64ToInt128Rounding(10.5, 10, TiesToEven);
        testDec64ToInt128Rounding(10.5, 11, TiesToAway);
        testDec64ToInt128Rounding(10.5, 11, TowardPositive);
        testDec64ToInt128Rounding(10.5, 10, TowardNegative);
        testDec64ToInt128WithBoundsCheck(-100, -100, False);
        testDec64ToInt128WithBoundsCheck(0, 0, False);
        testDec64ToInt128WithBoundsCheck(100, 100, False);

        testDec64ToUInt8(0, 0);
        testDec64ToUInt8(100, 100);
        testDec64ToUInt8(256.4, 0);
        testDec64ToUInt8Rounding(10.5, 10, TowardZero);
        testDec64ToUInt8Rounding(10.5, 10, TiesToEven);
        testDec64ToUInt8Rounding(10.5, 11, TiesToAway);
        testDec64ToUInt8Rounding(10.5, 11, TowardPositive);
        testDec64ToUInt8Rounding(10.5, 10, TowardNegative);
        testDec64ToUInt8WithBoundsCheck(-1, 0, True);
        testDec64ToUInt8WithBoundsCheck(0, 0, False);
        testDec64ToUInt8WithBoundsCheck(255, 255, False);
        testDec64ToUInt8WithBoundsCheck(256, 0, True);

        testDec64ToUInt16(0, 0);
        testDec64ToUInt16(100, 100);
        testDec64ToUInt16(65536.4, 0);
        testDec64ToUInt16Rounding(10.5, 10, TowardZero);
        testDec64ToUInt16Rounding(10.5, 10, TiesToEven);
        testDec64ToUInt16Rounding(10.5, 11, TiesToAway);
        testDec64ToUInt16Rounding(10.5, 11, TowardPositive);
        testDec64ToUInt16Rounding(10.5, 10, TowardNegative);
        testDec64ToUInt16WithBoundsCheck(-1, 0, True);
        testDec64ToUInt16WithBoundsCheck(0, 0, False);
        testDec64ToUInt16WithBoundsCheck(65535, 65535, False);
        testDec64ToUInt16WithBoundsCheck(65536, 0, True);

        testDec64ToUInt32(0, 0);
        testDec64ToUInt32(100, 100);
        testDec64ToUInt32Rounding(10.5, 10, TowardZero);
        testDec64ToUInt32Rounding(10.5, 10, TiesToEven);
        testDec64ToUInt32Rounding(10.5, 11, TiesToAway);
        testDec64ToUInt32Rounding(10.5, 11, TowardPositive);
        testDec64ToUInt32Rounding(10.5, 10, TowardNegative);
        testDec64ToUInt32WithBoundsCheck(-1, 0, True);
        testDec64ToUInt32WithBoundsCheck(0, 0, False);
        testDec64ToUInt32WithBoundsCheck(100, 100, False);
        testDec64ToUInt32WithBoundsCheck(4294967296, 0, True);

        testDec64ToUInt64(0, 0);
        testDec64ToUInt64(100, 100);
        testDec64ToUInt64Rounding(10.5, 10, TowardZero);
        testDec64ToUInt64Rounding(10.5, 10, TiesToEven);
        testDec64ToUInt64Rounding(10.5, 11, TiesToAway);
        testDec64ToUInt64Rounding(10.5, 11, TowardPositive);
        testDec64ToUInt64Rounding(10.5, 10, TowardNegative);
        testDec64ToUInt64WithBoundsCheck(-1, 0, True);
        testDec64ToUInt64WithBoundsCheck(0, 0, False);
        testDec64ToUInt64WithBoundsCheck(100, 100, False);

        testDec64ToUInt128(0, 0);
        testDec64ToUInt128(100, 100);
        testDec64ToUInt128Rounding(10.5, 10, TowardZero);
        testDec64ToUInt128Rounding(10.5, 10, TiesToEven);
        testDec64ToUInt128Rounding(10.5, 11, TiesToAway);
        testDec64ToUInt128Rounding(10.5, 11, TowardPositive);
        testDec64ToUInt128Rounding(10.5, 10, TowardNegative);
        testDec64ToUInt128WithBoundsCheck(-1, 0, True);
        testDec64ToUInt128WithBoundsCheck(0, 0, False);
        testDec64ToUInt128WithBoundsCheck(100, 100, False);

        console.print(">>>> Finished Dec64 Conversion tests >>>>");
    }

    void testDec64ToDec32(Dec64 a, Dec32 expected){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to Dec32 expected=", True);
        console.print(expected, True);
        Dec32 b = a.toDec32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToDec64(Dec64 a, Dec64 expected){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to Dec64 expected=", True);
        console.print(expected, True);
        Dec64 b = a.toDec64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToDec128(Dec64 a, Dec128 expected){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to Dec128 expected=", True);
        console.print(expected, True);
        Dec128 b = a.toDec128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToInt8(Dec64 a, Int8 expected){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to Int8 expected=", True);
        console.print(expected, True);
        Int8 b = a.toInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToInt8Rounding(Dec64 a, Int8 expected, Rounding direction){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to Int8 direction=", True);
        console.print(direction, True);
        console.print("  expected=", True);
        console.print(expected, True);
        Int8 b = a.toInt8(False, direction);
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToInt8WithBoundsCheck(Dec64 a, Int8 expected, Boolean oob){
        console.print("Test Dec64 ", True);
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

    void testDec64ToInt16(Dec64 a, Int16 expected){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to Int16 expected=", True);
        console.print(expected, True);
        Int16 b = a.toInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToInt16Rounding(Dec64 a, Int16 expected, Rounding direction){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to Int16 direction=", True);
        console.print(direction, True);
        console.print("  expected=", True);
        console.print(expected, True);
        Int16 b = a.toInt16(False, direction);
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToInt16WithBoundsCheck(Dec64 a, Int16 expected, Boolean oob){
        console.print("Test Dec64 ", True);
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

    void testDec64ToInt32(Dec64 a, Int32 expected){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to Int32 expected=", True);
        console.print(expected, True);
        Int32 b = a.toInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToInt32Rounding(Dec64 a, Int32 expected, Rounding direction){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to Int32 direction=", True);
        console.print(direction, True);
        console.print("  expected=", True);
        console.print(expected, True);
        Int32 b = a.toInt32(False, direction);
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToInt32WithBoundsCheck(Dec64 a, Int32 expected, Boolean oob){
        console.print("Test Dec64 ", True);
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

    void testDec64ToInt64(Dec64 a, Int64 expected){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to Int64 expected=", True);
        console.print(expected, True);
        Int64 b = a.toInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToInt64Rounding(Dec64 a, Int64 expected, Rounding direction){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to Int64 direction=", True);
        console.print(direction, True);
        console.print("  expected=", True);
        console.print(expected, True);
        Int64 b = a.toInt64(False, direction);
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToInt64WithBoundsCheck(Dec64 a, Int64 expected, Boolean oob){
        console.print("Test Dec64 ", True);
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

    void testDec64ToInt128(Dec64 a, Int128 expected){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to Int128 expected=", True);
        console.print(expected, True);
        Int128 b = a.toInt128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToInt128Rounding(Dec64 a, Int128 expected, Rounding direction){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to Int128 direction=", True);
        console.print(direction, True);
        console.print("  expected=", True);
        console.print(expected, True);
        Int128 b = a.toInt128(False, direction);
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToInt128WithBoundsCheck(Dec64 a, Int128 expected, Boolean oob){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        if (oob) {
            console.print(" to Int128 throws OutOfBounds");
            try {
                a.toInt128(True);
                assert as "Expected OutOfBounds to be thrown";
            } catch (OutOfBounds e) {
                // expected
            }
        } else {
            console.print(" to Int128 succeeds");
            Int128 b = a.toInt128(True);
            assert b == expected;
        }
    }

    void testDec64ToUInt8(Dec64 a, UInt8 expected){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to UInt8 expected=", True);
        console.print(expected, True);
        UInt8 b = a.toUInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToUInt8Rounding(Dec64 a, UInt8 expected, Rounding direction){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to UInt8 direction=", True);
        console.print(direction, True);
        console.print("  expected=", True);
        console.print(expected, True);
        UInt8 b = a.toUInt8(False, direction);
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToUInt8WithBoundsCheck(Dec64 a, UInt8 expected, Boolean oob){
        console.print("Test Dec64 ", True);
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
            console.print(" to UInt8 succeeds, expected=", False);
            console.print(expected);
            console.print(" actual=", False);
            UInt8 b = a.toUInt8(True);
            console.print(b);
            assert b == expected;
        }
    }

    void testDec64ToUInt16(Dec64 a, UInt16 expected){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to UInt16 expected=", True);
        console.print(expected, True);
        UInt16 b = a.toUInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToUInt16Rounding(Dec64 a, UInt16 expected, Rounding direction){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to UInt16 direction=", True);
        console.print(direction, True);
        console.print("  expected=", True);
        console.print(expected, True);
        UInt16 b = a.toUInt16(False, direction);
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToUInt16WithBoundsCheck(Dec64 a, UInt16 expected, Boolean oob){
        console.print("Test Dec64 ", True);
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
            console.print(" to UInt16 succeeds expected=", False);
            console.print(expected, False);
            UInt16 b = a.toUInt16(True);
            console.print(" actual=", False);
            console.print(b);
            assert b == expected;
        }
    }

    void testDec64ToUInt32(Dec64 a, UInt32 expected){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to UInt32 expected=", True);
        console.print(expected, True);
        UInt32 b = a.toUInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToUInt32Rounding(Dec64 a, UInt32 expected, Rounding direction){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to UInt32 direction=", True);
        console.print(direction, True);
        console.print("  expected=", True);
        console.print(expected, True);
        UInt32 b = a.toUInt32(False, direction);
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToUInt32WithBoundsCheck(Dec64 a, UInt32 expected, Boolean oob){
        console.print("Test Dec64 ", True);
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

    void testDec64ToUInt64(Dec64 a, UInt64 expected){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to UInt64 expected=", True);
        console.print(expected, True);
        UInt64 b = a.toUInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToUInt64Rounding(Dec64 a, UInt64 expected, Rounding direction){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to UInt64 direction=", True);
        console.print(direction, True);
        console.print("  expected=", True);
        console.print(expected, True);
        UInt64 b = a.toUInt64(False, direction);
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToUInt64WithBoundsCheck(Dec64 a, UInt64 expected, Boolean oob){
        console.print("Test Dec64 ", True);
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

    void testDec64ToUInt128(Dec64 a, UInt128 expected){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to UInt128 expected=", True);
        console.print(expected, True);
        UInt128 b = a.toUInt128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToUInt128Rounding(Dec64 a, UInt128 expected, Rounding direction){
        console.print("Test Dec64 ", True);
        console.print(a, True);
        console.print(" to UInt128 direction=", True);
        console.print(direction, True);
        console.print("  expected=", True);
        console.print(expected, True);
        UInt128 b = a.toUInt128(False, direction);
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec64ToUInt128WithBoundsCheck(Dec64 a, UInt128 expected, Boolean oob){
        console.print("Test Dec64 ", True);
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
