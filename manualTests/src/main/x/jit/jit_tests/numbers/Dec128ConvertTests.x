/**
 * Tests for Dec128 conversions to other numeric types.
 */
class Dec128ConvertTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Dec128 Conversion tests >>>>");

        testDec128ToDec32(-1000.456, -1000.456);
        testDec128ToDec32(1000.456, 1000.456);
        testDec128ToDec64(-1000.456, -1000.456);
        testDec128ToDec64(1000.456, 1000.456);
        testDec128ToDec128(-1000.456, -1000.456);
        testDec128ToDec128(1000.456, 1000.456);

        testDec128ToInt8(-200, 56);
        testDec128ToInt8(-128.4, -128);
        testDec128ToInt8(-100, -100);
        testDec128ToInt8(0, 0);
        testDec128ToInt8(100, 100);
        testDec128ToInt8(256.4, 0); // rounds to 256 == 0x100 converted to Int8 == 0
        testDec128ToInt8Rounding(10.4, 10, TowardZero); // rounds towards zero
        testDec128ToInt8Rounding(10.5, 10, TowardZero);
        testDec128ToInt8Rounding(10.9, 10, TowardZero);
        testDec128ToInt8Rounding(-10.4, -10, TowardZero);
        testDec128ToInt8Rounding(-10.5, -10, TowardZero);
        testDec128ToInt8Rounding(-10.9, -10, TowardZero);
        testDec128ToInt8Rounding(10.1, 10, TiesToEven); // .5 rounds to the even number up or down
        testDec128ToInt8Rounding(10.5, 10, TiesToEven);
        testDec128ToInt8Rounding(10.9, 11, TiesToEven);
        testDec128ToInt8Rounding(11.1, 11, TiesToEven);
        testDec128ToInt8Rounding(11.5, 12, TiesToEven);
        testDec128ToInt8Rounding(11.9, 12, TiesToEven);
        testDec128ToInt8Rounding(-10.1, -10, TiesToEven);
        testDec128ToInt8Rounding(-10.5, -10, TiesToEven);
        testDec128ToInt8Rounding(-10.9, -11, TiesToEven);
        testDec128ToInt8Rounding(-11.1, -11, TiesToEven);
        testDec128ToInt8Rounding(-11.5, -12, TiesToEven);
        testDec128ToInt8Rounding(-11.9, -12, TiesToEven);
        testDec128ToInt8Rounding(10.1, 11, TiesToAway); // rounds up
        testDec128ToInt8Rounding(10.5, 11, TiesToAway);
        testDec128ToInt8Rounding(10.9, 11, TiesToAway);
        testDec128ToInt8Rounding(-10.1, -11, TiesToAway);
        testDec128ToInt8Rounding(-10.5, -11, TiesToAway);
        testDec128ToInt8Rounding(-10.9, -11, TiesToAway);
        testDec128ToInt8Rounding(10.4, 11, TowardPositive); // rounds towards +ve infinity
        testDec128ToInt8Rounding(10.5, 11, TowardPositive);
        testDec128ToInt8Rounding(10.9, 11, TowardPositive);
        testDec128ToInt8Rounding(-10.4, -10, TowardPositive);
        testDec128ToInt8Rounding(-10.5, -10, TowardPositive);
        testDec128ToInt8Rounding(-10.9, -10, TowardPositive);
        testDec128ToInt8Rounding(10.4, 10, TowardNegative); // rounds towards -ve infinity
        testDec128ToInt8Rounding(10.5, 10, TowardNegative);
        testDec128ToInt8Rounding(10.9, 10, TowardNegative);
        testDec128ToInt8Rounding(-10.4, -11, TowardNegative);
        testDec128ToInt8Rounding(-10.5, -11, TowardNegative);
        testDec128ToInt8Rounding(-10.9, -11, TowardNegative);
        testDec128ToInt8WithBoundsCheck(-129.3, 0, True);
        testDec128ToInt8WithBoundsCheck(-128.9, Int8.MinValue, False);
        testDec128ToInt8WithBoundsCheck(-128.4, Int8.MinValue, False);
        testDec128ToInt8WithBoundsCheck(-100, -100, False);
        testDec128ToInt8WithBoundsCheck(0, 0, False);
        testDec128ToInt8WithBoundsCheck(100, 100, False);
        testDec128ToInt8WithBoundsCheck(127.4, 127, False);
        testDec128ToInt8WithBoundsCheck(128.4, 0, True);

        testDec128ToInt16(-33768.9, 31768);
        testDec128ToInt16(-32768.4, -32768);
        testDec128ToInt16(-100, -100);
        testDec128ToInt16(0, 0);
        testDec128ToInt16(100, 100);
        testDec128ToInt16(65536.4, 0); // rounds to 65536 == 0x10000 converted to Int16 == 0
        testDec128ToInt16Rounding(10.4, 10, TowardZero); // rounds towards zero
        testDec128ToInt16Rounding(10.5, 10, TowardZero);
        testDec128ToInt16Rounding(10.9, 10, TowardZero);
        testDec128ToInt16Rounding(-10.4, -10, TowardZero);
        testDec128ToInt16Rounding(-10.5, -10, TowardZero);
        testDec128ToInt16Rounding(-10.9, -10, TowardZero);
        testDec128ToInt16Rounding(10.1, 10, TiesToEven); // .5 rounds to the even number up or down
        testDec128ToInt16Rounding(10.5, 10, TiesToEven);
        testDec128ToInt16Rounding(10.9, 11, TiesToEven);
        testDec128ToInt16Rounding(11.1, 11, TiesToEven);
        testDec128ToInt16Rounding(11.5, 12, TiesToEven);
        testDec128ToInt16Rounding(11.9, 12, TiesToEven);
        testDec128ToInt16Rounding(-10.1, -10, TiesToEven);
        testDec128ToInt16Rounding(-10.5, -10, TiesToEven);
        testDec128ToInt16Rounding(-10.9, -11, TiesToEven);
        testDec128ToInt16Rounding(-11.1, -11, TiesToEven);
        testDec128ToInt16Rounding(-11.5, -12, TiesToEven);
        testDec128ToInt16Rounding(-11.9, -12, TiesToEven);
        testDec128ToInt16Rounding(10.1, 11, TiesToAway); // rounds up
        testDec128ToInt16Rounding(10.5, 11, TiesToAway);
        testDec128ToInt16Rounding(10.9, 11, TiesToAway);
        testDec128ToInt16Rounding(-10.1, -11, TiesToAway);
        testDec128ToInt16Rounding(-10.5, -11, TiesToAway);
        testDec128ToInt16Rounding(-10.9, -11, TiesToAway);
        testDec128ToInt16Rounding(10.4, 11, TowardPositive); // rounds towards +ve infinity
        testDec128ToInt16Rounding(10.5, 11, TowardPositive);
        testDec128ToInt16Rounding(10.9, 11, TowardPositive);
        testDec128ToInt16Rounding(-10.4, -10, TowardPositive);
        testDec128ToInt16Rounding(-10.5, -10, TowardPositive);
        testDec128ToInt16Rounding(-10.9, -10, TowardPositive);
        testDec128ToInt16Rounding(10.4, 10, TowardNegative); // rounds towards -ve infinity
        testDec128ToInt16Rounding(10.5, 10, TowardNegative);
        testDec128ToInt16Rounding(10.9, 10, TowardNegative);
        testDec128ToInt16Rounding(-10.4, -11, TowardNegative);
        testDec128ToInt16Rounding(-10.5, -11, TowardNegative);
        testDec128ToInt16Rounding(-10.9, -11, TowardNegative);
        testDec128ToInt16WithBoundsCheck(-32769.1, 0, True);
        testDec128ToInt16WithBoundsCheck(-32768.9, Int16.MinValue, False);
        testDec128ToInt16WithBoundsCheck(-32768.4, Int16.MinValue, False);
        testDec128ToInt16WithBoundsCheck(-100, -100, False);
        testDec128ToInt16WithBoundsCheck(0, 0, False);
        testDec128ToInt16WithBoundsCheck(100, 100, False);
        testDec128ToInt16WithBoundsCheck(32767.7, 32767, False);
        testDec128ToInt16WithBoundsCheck(32768.1, 0, True);

        testDec128ToInt32(-2147483649.1, 2147483647);
        testDec128ToInt32(-100, -100);
        testDec128ToInt32(0, 0);
        testDec128ToInt32(100, 100);
        testDec128ToInt32(2147483648.1, -2147483648);
        testDec128ToInt32Rounding(10.4, 10, TowardZero); // rounds towards zero
        testDec128ToInt32Rounding(10.5, 10, TowardZero);
        testDec128ToInt32Rounding(10.9, 10, TowardZero);
        testDec128ToInt32Rounding(-10.4, -10, TowardZero);
        testDec128ToInt32Rounding(-10.5, -10, TowardZero);
        testDec128ToInt32Rounding(-10.9, -10, TowardZero);
        testDec128ToInt32Rounding(10.1, 10, TiesToEven); // .5 rounds to the even number up or down
        testDec128ToInt32Rounding(10.5, 10, TiesToEven);
        testDec128ToInt32Rounding(10.9, 11, TiesToEven);
        testDec128ToInt32Rounding(11.1, 11, TiesToEven);
        testDec128ToInt32Rounding(11.5, 12, TiesToEven);
        testDec128ToInt32Rounding(11.9, 12, TiesToEven);
        testDec128ToInt32Rounding(-10.1, -10, TiesToEven);
        testDec128ToInt32Rounding(-10.5, -10, TiesToEven);
        testDec128ToInt32Rounding(-10.9, -11, TiesToEven);
        testDec128ToInt32Rounding(-11.1, -11, TiesToEven);
        testDec128ToInt32Rounding(-11.5, -12, TiesToEven);
        testDec128ToInt32Rounding(-11.9, -12, TiesToEven);
        testDec128ToInt32Rounding(10.1, 11, TiesToAway); // rounds up
        testDec128ToInt32Rounding(10.5, 11, TiesToAway);
        testDec128ToInt32Rounding(10.9, 11, TiesToAway);
        testDec128ToInt32Rounding(-10.1, -11, TiesToAway);
        testDec128ToInt32Rounding(-10.5, -11, TiesToAway);
        testDec128ToInt32Rounding(-10.9, -11, TiesToAway);
        testDec128ToInt32Rounding(10.4, 11, TowardPositive); // rounds towards +ve infinity
        testDec128ToInt32Rounding(10.5, 11, TowardPositive);
        testDec128ToInt32Rounding(10.9, 11, TowardPositive);
        testDec128ToInt32Rounding(-10.4, -10, TowardPositive);
        testDec128ToInt32Rounding(-10.5, -10, TowardPositive);
        testDec128ToInt32Rounding(-10.9, -10, TowardPositive);
        testDec128ToInt32Rounding(10.4, 10, TowardNegative); // rounds towards -ve infinity
        testDec128ToInt32Rounding(10.5, 10, TowardNegative);
        testDec128ToInt32Rounding(10.9, 10, TowardNegative);
        testDec128ToInt32Rounding(-10.4, -11, TowardNegative);
        testDec128ToInt32Rounding(-10.5, -11, TowardNegative);
        testDec128ToInt32Rounding(-10.9, -11, TowardNegative);
        testDec128ToInt32WithBoundsCheck(-2147483649.1, 0, True);
        testDec128ToInt32WithBoundsCheck(-100, -100, False);
        testDec128ToInt32WithBoundsCheck(0, 0, False);
        testDec128ToInt32WithBoundsCheck(100, 100, False);
        testDec128ToInt32WithBoundsCheck(2147483648.1, 0, True);

        testDec128ToInt64(-100, -100);
        testDec128ToInt64(0, 0);
        testDec128ToInt64(100, 100);
        testDec128ToInt64Rounding(10.5, 10, TowardZero);
        testDec128ToInt64Rounding(10.5, 10, TiesToEven);
        testDec128ToInt64Rounding(10.5, 11, TiesToAway);
        testDec128ToInt64Rounding(10.5, 11, TowardPositive);
        testDec128ToInt64Rounding(10.5, 10, TowardNegative);
        testDec128ToInt64WithBoundsCheck(-100, -100, False);
        testDec128ToInt64WithBoundsCheck(0, 0, False);
        testDec128ToInt64WithBoundsCheck(100, 100, False);

        testDec128ToInt128(-100, -100);
        testDec128ToInt128(0, 0);
        testDec128ToInt128(100, 100);
        testDec128ToInt128Rounding(10.5, 10, TowardZero);
        testDec128ToInt128Rounding(10.5, 10, TiesToEven);
        testDec128ToInt128Rounding(10.5, 11, TiesToAway);
        testDec128ToInt128Rounding(10.5, 11, TowardPositive);
        testDec128ToInt128Rounding(10.5, 10, TowardNegative);
        testDec128ToInt128WithBoundsCheck(-100, -100, False);
        testDec128ToInt128WithBoundsCheck(0, 0, False);
        testDec128ToInt128WithBoundsCheck(100, 100, False);

        testDec128ToUInt8(0, 0);
        testDec128ToUInt8(100, 100);
        testDec128ToUInt8(256.4, 0);
        testDec128ToUInt8Rounding(10.5, 10, TowardZero);
        testDec128ToUInt8Rounding(10.5, 10, TiesToEven);
        testDec128ToUInt8Rounding(10.5, 11, TiesToAway);
        testDec128ToUInt8Rounding(10.5, 11, TowardPositive);
        testDec128ToUInt8Rounding(10.5, 10, TowardNegative);
        testDec128ToUInt8WithBoundsCheck(-1, 0, True);
        testDec128ToUInt8WithBoundsCheck(0, 0, False);
        testDec128ToUInt8WithBoundsCheck(255, 255, False);
        testDec128ToUInt8WithBoundsCheck(256, 0, True);

        testDec128ToUInt16(0, 0);
        testDec128ToUInt16(100, 100);
        testDec128ToUInt16(65536.4, 0);
        testDec128ToUInt16Rounding(10.5, 10, TowardZero);
        testDec128ToUInt16Rounding(10.5, 10, TiesToEven);
        testDec128ToUInt16Rounding(10.5, 11, TiesToAway);
        testDec128ToUInt16Rounding(10.5, 11, TowardPositive);
        testDec128ToUInt16Rounding(10.5, 10, TowardNegative);
        testDec128ToUInt16WithBoundsCheck(-1, 0, True);
        testDec128ToUInt16WithBoundsCheck(0, 0, False);
        testDec128ToUInt16WithBoundsCheck(65535, 65535, False);
        testDec128ToUInt16WithBoundsCheck(65536, 0, True);

        testDec128ToUInt32(0, 0);
        testDec128ToUInt32(100, 100);
        testDec128ToUInt32Rounding(10.5, 10, TowardZero);
        testDec128ToUInt32Rounding(10.5, 10, TiesToEven);
        testDec128ToUInt32Rounding(10.5, 11, TiesToAway);
        testDec128ToUInt32Rounding(10.5, 11, TowardPositive);
        testDec128ToUInt32Rounding(10.5, 10, TowardNegative);
        testDec128ToUInt32WithBoundsCheck(-1, 0, True);
        testDec128ToUInt32WithBoundsCheck(0, 0, False);
        testDec128ToUInt32WithBoundsCheck(100, 100, False);
        testDec128ToUInt32WithBoundsCheck(4294967296, 0, True);

        testDec128ToUInt64(0, 0);
        testDec128ToUInt64(100, 100);
        testDec128ToUInt64Rounding(10.5, 10, TowardZero);
        testDec128ToUInt64Rounding(10.5, 10, TiesToEven);
        testDec128ToUInt64Rounding(10.5, 11, TiesToAway);
        testDec128ToUInt64Rounding(10.5, 11, TowardPositive);
        testDec128ToUInt64Rounding(10.5, 10, TowardNegative);
        testDec128ToUInt64WithBoundsCheck(-1, 0, True);
        testDec128ToUInt64WithBoundsCheck(0, 0, False);
        testDec128ToUInt64WithBoundsCheck(100, 100, False);

        testDec128ToUInt128(0, 0);
        testDec128ToUInt128(100, 100);
        testDec128ToUInt128Rounding(10.5, 10, TowardZero);
        testDec128ToUInt128Rounding(10.5, 10, TiesToEven);
        testDec128ToUInt128Rounding(10.5, 11, TiesToAway);
        testDec128ToUInt128Rounding(10.5, 11, TowardPositive);
        testDec128ToUInt128Rounding(10.5, 10, TowardNegative);
        testDec128ToUInt128WithBoundsCheck(-1, 0, True);
        testDec128ToUInt128WithBoundsCheck(0, 0, False);
        testDec128ToUInt128WithBoundsCheck(100, 100, False);

        console.print(">>>> Finished Dec128 Conversion tests >>>>");
    }

    void testDec128ToDec32(Dec128 a, Dec32 expected){
        console.print("Test Dec128 ", True);
        console.print(a, True);
        console.print(" to Dec32 expected=", True);
        console.print(expected, True);
        Dec32 b = a.toDec32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec128ToDec64(Dec128 a, Dec64 expected){
        console.print("Test Dec128 ", True);
        console.print(a, True);
        console.print(" to Dec64 expected=", True);
        console.print(expected, True);
        Dec64 b = a.toDec64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec128ToDec128(Dec128 a, Dec128 expected){
        console.print("Test Dec128 ", True);
        console.print(a, True);
        console.print(" to Dec128 expected=", True);
        console.print(expected, True);
        Dec128 b = a.toDec128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec128ToInt8(Dec128 a, Int8 expected){
        console.print("Test Dec128 ", True);
        console.print(a, True);
        console.print(" to Int8 expected=", True);
        console.print(expected, True);
        Int8 b = a.toInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec128ToInt8Rounding(Dec128 a, Int8 expected, Rounding direction){
        console.print("Test Dec128 ", True);
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

    void testDec128ToInt8WithBoundsCheck(Dec128 a, Int8 expected, Boolean oob){
        console.print("Test Dec128 ", True);
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

    void testDec128ToInt16(Dec128 a, Int16 expected){
        console.print("Test Dec128 ", True);
        console.print(a, True);
        console.print(" to Int16 expected=", True);
        console.print(expected, True);
        Int16 b = a.toInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec128ToInt16Rounding(Dec128 a, Int16 expected, Rounding direction){
        console.print("Test Dec128 ", True);
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

    void testDec128ToInt16WithBoundsCheck(Dec128 a, Int16 expected, Boolean oob){
        console.print("Test Dec128 ", True);
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

    void testDec128ToInt32(Dec128 a, Int32 expected){
        console.print("Test Dec128 ", True);
        console.print(a, True);
        console.print(" to Int32 expected=", True);
        console.print(expected, True);
        Int32 b = a.toInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec128ToInt32Rounding(Dec128 a, Int32 expected, Rounding direction){
        console.print("Test Dec128 ", True);
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

    void testDec128ToInt32WithBoundsCheck(Dec128 a, Int32 expected, Boolean oob){
        console.print("Test Dec128 ", True);
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

    void testDec128ToInt64(Dec128 a, Int64 expected){
        console.print("Test Dec128 ", True);
        console.print(a, True);
        console.print(" to Int64 expected=", True);
        console.print(expected, True);
        Int64 b = a.toInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec128ToInt64Rounding(Dec128 a, Int64 expected, Rounding direction){
        console.print("Test Dec128 ", True);
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

    void testDec128ToInt64WithBoundsCheck(Dec128 a, Int64 expected, Boolean oob){
        console.print("Test Dec128 ", True);
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

    void testDec128ToInt128(Dec128 a, Int128 expected){
        console.print("Test Dec128 ", True);
        console.print(a, True);
        console.print(" to Int128 expected=", True);
        console.print(expected, True);
        Int128 b = a.toInt128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec128ToInt128Rounding(Dec128 a, Int128 expected, Rounding direction){
        console.print("Test Dec128 ", True);
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

    void testDec128ToInt128WithBoundsCheck(Dec128 a, Int128 expected, Boolean oob){
        console.print("Test Dec128 ", True);
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

    void testDec128ToUInt8(Dec128 a, UInt8 expected){
        console.print("Test Dec128 ", True);
        console.print(a, True);
        console.print(" to UInt8 expected=", True);
        console.print(expected, True);
        UInt8 b = a.toUInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec128ToUInt8Rounding(Dec128 a, UInt8 expected, Rounding direction){
        console.print("Test Dec128 ", True);
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

    void testDec128ToUInt8WithBoundsCheck(Dec128 a, UInt8 expected, Boolean oob){
        console.print("Test Dec128 ", True);
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

    void testDec128ToUInt16(Dec128 a, UInt16 expected){
        console.print("Test Dec128 ", True);
        console.print(a, True);
        console.print(" to UInt16 expected=", True);
        console.print(expected, True);
        UInt16 b = a.toUInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec128ToUInt16Rounding(Dec128 a, UInt16 expected, Rounding direction){
        console.print("Test Dec128 ", True);
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

    void testDec128ToUInt16WithBoundsCheck(Dec128 a, UInt16 expected, Boolean oob){
        console.print("Test Dec128 ", True);
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

    void testDec128ToUInt32(Dec128 a, UInt32 expected){
        console.print("Test Dec128 ", True);
        console.print(a, True);
        console.print(" to UInt32 expected=", True);
        console.print(expected, True);
        UInt32 b = a.toUInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec128ToUInt32Rounding(Dec128 a, UInt32 expected, Rounding direction){
        console.print("Test Dec128 ", True);
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

    void testDec128ToUInt32WithBoundsCheck(Dec128 a, UInt32 expected, Boolean oob){
        console.print("Test Dec128 ", True);
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

    void testDec128ToUInt64(Dec128 a, UInt64 expected){
        console.print("Test Dec128 ", True);
        console.print(a, True);
        console.print(" to UInt64 expected=", True);
        console.print(expected, True);
        UInt64 b = a.toUInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec128ToUInt64Rounding(Dec128 a, UInt64 expected, Rounding direction){
        console.print("Test Dec128 ", True);
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

    void testDec128ToUInt64WithBoundsCheck(Dec128 a, UInt64 expected, Boolean oob){
        console.print("Test Dec128 ", True);
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

    void testDec128ToUInt128(Dec128 a, UInt128 expected){
        console.print("Test Dec128 ", True);
        console.print(a, True);
        console.print(" to UInt128 expected=", True);
        console.print(expected, True);
        UInt128 b = a.toUInt128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec128ToUInt128Rounding(Dec128 a, UInt128 expected, Rounding direction){
        console.print("Test Dec128 ", True);
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

    void testDec128ToUInt128WithBoundsCheck(Dec128 a, UInt128 expected, Boolean oob){
        console.print("Test Dec128 ", True);
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
