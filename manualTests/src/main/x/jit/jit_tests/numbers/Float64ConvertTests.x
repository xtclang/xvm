/**
 * Tests for Float64 conversions to other numeric types.
 */
class Float64ConvertTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Float64 Conversion tests >>>>");

        testFloat64ToFloat32(-1000.456, -1000.456);
        testFloat64ToFloat32(1000.456, 1000.456);
        testFloat64ToFloat64(-1000.456, -1000.456);
        testFloat64ToFloat64(1000.456, 1000.456);

        testFloat64ToDec32(-1000.456, -1000.456);
        testFloat64ToDec32(1000.456, 1000.456);
        testFloat64ToDec64(-1000.456, -1000.456);
        testFloat64ToDec64(1000.456, 1000.456);
        testFloat64ToDec128(-1000.456, -1000.456);
        testFloat64ToDec128(1000.456, 1000.456);

        testFloat64ToInt8(-200, 56);
        testFloat64ToInt8AsNumber(-200, 56);
        testFloat64ToInt8(-128.4, -128);
        testFloat64ToInt8(-100, -100);
        testFloat64ToInt8(0, 0);
        testFloat64ToInt8(100, 100);
        testFloat64ToInt8(256.4, 0); // rounds to 256 == 0x100 converted to Int8 == 0
        testFloat64ToInt8Rounding(10.4, 10, TowardZero); // rounds towards zero
        testFloat64ToInt8Rounding(10.5, 10, TowardZero);
        testFloat64ToInt8Rounding(10.9, 10, TowardZero);
        testFloat64ToInt8Rounding(-10.4, -10, TowardZero);
        testFloat64ToInt8Rounding(-10.5, -10, TowardZero);
        testFloat64ToInt8Rounding(-10.9, -10, TowardZero);
        testFloat64ToInt8Rounding(10.1, 10, TiesToEven); // .5 rounds to the even number up or down
        testFloat64ToInt8Rounding(10.5, 10, TiesToEven);
        testFloat64ToInt8Rounding(10.9, 11, TiesToEven);
        testFloat64ToInt8Rounding(11.1, 11, TiesToEven);
        testFloat64ToInt8Rounding(11.5, 12, TiesToEven);
        testFloat64ToInt8Rounding(11.9, 12, TiesToEven);
        testFloat64ToInt8Rounding(-10.1, -10, TiesToEven);
        testFloat64ToInt8Rounding(-10.5, -10, TiesToEven);
        testFloat64ToInt8Rounding(-10.9, -11, TiesToEven);
        testFloat64ToInt8Rounding(-11.1, -11, TiesToEven);
        testFloat64ToInt8Rounding(-11.5, -12, TiesToEven);
        testFloat64ToInt8Rounding(-11.9, -12, TiesToEven);
        testFloat64ToInt8Rounding(10.1, 11, TiesToAway); // rounds up
        testFloat64ToInt8Rounding(10.5, 11, TiesToAway);
        testFloat64ToInt8Rounding(10.9, 11, TiesToAway);
        testFloat64ToInt8Rounding(-10.1, -11, TiesToAway);
        testFloat64ToInt8Rounding(-10.5, -11, TiesToAway);
        testFloat64ToInt8Rounding(-10.9, -11, TiesToAway);
        testFloat64ToInt8Rounding(10.4, 11, TowardPositive); // rounds towards +ve infinity
        testFloat64ToInt8Rounding(10.5, 11, TowardPositive);
        testFloat64ToInt8Rounding(10.9, 11, TowardPositive);
        testFloat64ToInt8Rounding(-10.4, -10, TowardPositive);
        testFloat64ToInt8Rounding(-10.5, -10, TowardPositive);
        testFloat64ToInt8Rounding(-10.9, -10, TowardPositive);
        testFloat64ToInt8Rounding(10.4, 10, TowardNegative); // rounds towards -ve infinity
        testFloat64ToInt8Rounding(10.5, 10, TowardNegative);
        testFloat64ToInt8Rounding(10.9, 10, TowardNegative);
        testFloat64ToInt8Rounding(-10.4, -11, TowardNegative);
        testFloat64ToInt8Rounding(-10.5, -11, TowardNegative);
        testFloat64ToInt8Rounding(-10.9, -11, TowardNegative);
        testFloat64ToInt8WithBoundsCheck(-129.3, 0, True);
        testFloat64ToInt8WithBoundsCheck(-128.9, Int8.MinValue, False);
        testFloat64ToInt8WithBoundsCheck(-128.4, Int8.MinValue, False);
        testFloat64ToInt8WithBoundsCheck(-100, -100, False);
        testFloat64ToInt8WithBoundsCheck(0, 0, False);
        testFloat64ToInt8WithBoundsCheck(100, 100, False);
        testFloat64ToInt8WithBoundsCheck(127.4, 127, False);
        testFloat64ToInt8WithBoundsCheck(128.4, 0, True);

        testFloat64ToInt16(-33768.9, 31768);
        testFloat64ToInt16(-32768.4, -32768);
        testFloat64ToInt16(-100, -100);
        testFloat64ToInt16(0, 0);
        testFloat64ToInt16(100, 100);
        testFloat64ToInt16(65536.4, 0); // rounds to 65536 == 0x10000 converted to Int16 == 0
        testFloat64ToInt16Rounding(10.4, 10, TowardZero); // rounds towards zero
        testFloat64ToInt16Rounding(10.5, 10, TowardZero);
        testFloat64ToInt16Rounding(10.9, 10, TowardZero);
        testFloat64ToInt16Rounding(-10.4, -10, TowardZero);
        testFloat64ToInt16Rounding(-10.5, -10, TowardZero);
        testFloat64ToInt16Rounding(-10.9, -10, TowardZero);
        testFloat64ToInt16Rounding(10.1, 10, TiesToEven); // .5 rounds to the even number up or down
        testFloat64ToInt16Rounding(10.5, 10, TiesToEven);
        testFloat64ToInt16Rounding(10.9, 11, TiesToEven);
        testFloat64ToInt16Rounding(11.1, 11, TiesToEven);
        testFloat64ToInt16Rounding(11.5, 12, TiesToEven);
        testFloat64ToInt16Rounding(11.9, 12, TiesToEven);
        testFloat64ToInt16Rounding(-10.1, -10, TiesToEven);
        testFloat64ToInt16Rounding(-10.5, -10, TiesToEven);
        testFloat64ToInt16Rounding(-10.9, -11, TiesToEven);
        testFloat64ToInt16Rounding(-11.1, -11, TiesToEven);
        testFloat64ToInt16Rounding(-11.5, -12, TiesToEven);
        testFloat64ToInt16Rounding(-11.9, -12, TiesToEven);
        testFloat64ToInt16Rounding(10.1, 11, TiesToAway); // rounds up
        testFloat64ToInt16Rounding(10.5, 11, TiesToAway);
        testFloat64ToInt16Rounding(10.9, 11, TiesToAway);
        testFloat64ToInt16Rounding(-10.1, -11, TiesToAway);
        testFloat64ToInt16Rounding(-10.5, -11, TiesToAway);
        testFloat64ToInt16Rounding(-10.9, -11, TiesToAway);
        testFloat64ToInt16Rounding(10.4, 11, TowardPositive); // rounds towards +ve infinity
        testFloat64ToInt16Rounding(10.5, 11, TowardPositive);
        testFloat64ToInt16Rounding(10.9, 11, TowardPositive);
        testFloat64ToInt16Rounding(-10.4, -10, TowardPositive);
        testFloat64ToInt16Rounding(-10.5, -10, TowardPositive);
        testFloat64ToInt16Rounding(-10.9, -10, TowardPositive);
        testFloat64ToInt16Rounding(10.4, 10, TowardNegative); // rounds towards -ve infinity
        testFloat64ToInt16Rounding(10.5, 10, TowardNegative);
        testFloat64ToInt16Rounding(10.9, 10, TowardNegative);
        testFloat64ToInt16Rounding(-10.4, -11, TowardNegative);
        testFloat64ToInt16Rounding(-10.5, -11, TowardNegative);
        testFloat64ToInt16Rounding(-10.9, -11, TowardNegative);
        testFloat64ToInt16WithBoundsCheck(-32769.1, 0, True);
        testFloat64ToInt16WithBoundsCheck(-32768.9, Int16.MinValue, False);
        testFloat64ToInt16WithBoundsCheck(-32768.4, Int16.MinValue, False);
        testFloat64ToInt16WithBoundsCheck(-100, -100, False);
        testFloat64ToInt16WithBoundsCheck(0, 0, False);
        testFloat64ToInt16WithBoundsCheck(100, 100, False);
        testFloat64ToInt16WithBoundsCheck(32767.7, 32767, False);
        testFloat64ToInt16WithBoundsCheck(32768.1, 0, True);

        testFloat64ToInt32(-2147483649.1, 2147483647);
        testFloat64ToInt32(-100, -100);
        testFloat64ToInt32(0, 0);
        testFloat64ToInt32(100, 100);
        testFloat64ToInt32(2147483648.1, -2147483648);
        testFloat64ToInt32Rounding(10.4, 10, TowardZero); // rounds towards zero
        testFloat64ToInt32Rounding(10.5, 10, TowardZero);
        testFloat64ToInt32Rounding(10.9, 10, TowardZero);
        testFloat64ToInt32Rounding(-10.4, -10, TowardZero);
        testFloat64ToInt32Rounding(-10.5, -10, TowardZero);
        testFloat64ToInt32Rounding(-10.9, -10, TowardZero);
        testFloat64ToInt32Rounding(10.1, 10, TiesToEven); // .5 rounds to the even number up or down
        testFloat64ToInt32Rounding(10.5, 10, TiesToEven);
        testFloat64ToInt32Rounding(10.9, 11, TiesToEven);
        testFloat64ToInt32Rounding(11.1, 11, TiesToEven);
        testFloat64ToInt32Rounding(11.5, 12, TiesToEven);
        testFloat64ToInt32Rounding(11.9, 12, TiesToEven);
        testFloat64ToInt32Rounding(-10.1, -10, TiesToEven);
        testFloat64ToInt32Rounding(-10.5, -10, TiesToEven);
        testFloat64ToInt32Rounding(-10.9, -11, TiesToEven);
        testFloat64ToInt32Rounding(-11.1, -11, TiesToEven);
        testFloat64ToInt32Rounding(-11.5, -12, TiesToEven);
        testFloat64ToInt32Rounding(-11.9, -12, TiesToEven);
        testFloat64ToInt32Rounding(10.1, 11, TiesToAway); // rounds up
        testFloat64ToInt32Rounding(10.5, 11, TiesToAway);
        testFloat64ToInt32Rounding(10.9, 11, TiesToAway);
        testFloat64ToInt32Rounding(-10.1, -11, TiesToAway);
        testFloat64ToInt32Rounding(-10.5, -11, TiesToAway);
        testFloat64ToInt32Rounding(-10.9, -11, TiesToAway);
        testFloat64ToInt32Rounding(10.4, 11, TowardPositive); // rounds towards +ve infinity
        testFloat64ToInt32Rounding(10.5, 11, TowardPositive);
        testFloat64ToInt32Rounding(10.9, 11, TowardPositive);
        testFloat64ToInt32Rounding(-10.4, -10, TowardPositive);
        testFloat64ToInt32Rounding(-10.5, -10, TowardPositive);
        testFloat64ToInt32Rounding(-10.9, -10, TowardPositive);
        testFloat64ToInt32Rounding(10.4, 10, TowardNegative); // rounds towards -ve infinity
        testFloat64ToInt32Rounding(10.5, 10, TowardNegative);
        testFloat64ToInt32Rounding(10.9, 10, TowardNegative);
        testFloat64ToInt32Rounding(-10.4, -11, TowardNegative);
        testFloat64ToInt32Rounding(-10.5, -11, TowardNegative);
        testFloat64ToInt32Rounding(-10.9, -11, TowardNegative);
        testFloat64ToInt32WithBoundsCheck(-2147483649.1, 0, True);
        testFloat64ToInt32WithBoundsCheck(-100, -100, False);
        testFloat64ToInt32WithBoundsCheck(0, 0, False);
        testFloat64ToInt32WithBoundsCheck(100, 100, False);
        testFloat64ToInt32WithBoundsCheck(2147483648.1, 0, True);

        testFloat64ToInt64(-100, -100);
        testFloat64ToInt64(0, 0);
        testFloat64ToInt64(100, 100);
        testFloat64ToInt64Rounding(10.5, 10, TowardZero);
        testFloat64ToInt64Rounding(10.5, 10, TiesToEven);
        testFloat64ToInt64Rounding(10.5, 11, TiesToAway);
        testFloat64ToInt64Rounding(10.5, 11, TowardPositive);
        testFloat64ToInt64Rounding(10.5, 10, TowardNegative);
        testFloat64ToInt64WithBoundsCheck(-100, -100, False);
        testFloat64ToInt64WithBoundsCheck(0, 0, False);
        testFloat64ToInt64WithBoundsCheck(100, 100, False);

        testFloat64ToInt128(-100, -100);
        testFloat64ToInt128(0, 0);
        testFloat64ToInt128(100, 100);
        testFloat64ToInt128Rounding(10.5, 10, TowardZero);
        testFloat64ToInt128Rounding(10.5, 10, TiesToEven);
        testFloat64ToInt128Rounding(10.5, 11, TiesToAway);
        testFloat64ToInt128Rounding(10.5, 11, TowardPositive);
        testFloat64ToInt128Rounding(10.5, 10, TowardNegative);
        testFloat64ToInt128WithBoundsCheck(-100, -100, False);
        testFloat64ToInt128WithBoundsCheck(0, 0, False);
        testFloat64ToInt128WithBoundsCheck(100, 100, False);

        testFloat64ToUInt8(0, 0);
        testFloat64ToUInt8(100, 100);
        testFloat64ToUInt8(256.4, 0);
        testFloat64ToUInt8AsNumber(100, 100);
        testFloat64ToUInt8AsNumber(256.4, 0);
        testFloat64ToUInt8Rounding(10.5, 10, TowardZero);
        testFloat64ToUInt8Rounding(10.5, 10, TiesToEven);
        testFloat64ToUInt8Rounding(10.5, 11, TiesToAway);
        testFloat64ToUInt8Rounding(10.5, 11, TowardPositive);
        testFloat64ToUInt8Rounding(10.5, 10, TowardNegative);
        testFloat64ToUInt8WithBoundsCheck(-1, 0, True);
        testFloat64ToUInt8WithBoundsCheck(0, 0, False);
        testFloat64ToUInt8WithBoundsCheck(255, 255, False);
        testFloat64ToUInt8WithBoundsCheck(256, 0, True);

        testFloat64ToUInt16(0, 0);
        testFloat64ToUInt16(100, 100);
        testFloat64ToUInt16(65536.4, 0);
        testFloat64ToUInt16Rounding(10.5, 10, TowardZero);
        testFloat64ToUInt16Rounding(10.5, 10, TiesToEven);
        testFloat64ToUInt16Rounding(10.5, 11, TiesToAway);
        testFloat64ToUInt16Rounding(10.5, 11, TowardPositive);
        testFloat64ToUInt16Rounding(10.5, 10, TowardNegative);
        testFloat64ToUInt16WithBoundsCheck(-1, 0, True);
        testFloat64ToUInt16WithBoundsCheck(0, 0, False);
        testFloat64ToUInt16WithBoundsCheck(65535, 65535, False);
        testFloat64ToUInt16WithBoundsCheck(65536, 0, True);

        testFloat64ToUInt32(0, 0);
        testFloat64ToUInt32(100, 100);
        testFloat64ToUInt32Rounding(10.5, 10, TowardZero);
        testFloat64ToUInt32Rounding(10.5, 10, TiesToEven);
        testFloat64ToUInt32Rounding(10.5, 11, TiesToAway);
        testFloat64ToUInt32Rounding(10.5, 11, TowardPositive);
        testFloat64ToUInt32Rounding(10.5, 10, TowardNegative);
        testFloat64ToUInt32WithBoundsCheck(-1, 0, True);
        testFloat64ToUInt32WithBoundsCheck(0, 0, False);
        testFloat64ToUInt32WithBoundsCheck(100, 100, False);
        testFloat64ToUInt32WithBoundsCheck(4294967296, 0, True);

        testFloat64ToUInt64(0, 0);
        testFloat64ToUInt64(100, 100);
        testFloat64ToUInt64Rounding(10.5, 10, TowardZero);
        testFloat64ToUInt64Rounding(10.5, 10, TiesToEven);
        testFloat64ToUInt64Rounding(10.5, 11, TiesToAway);
        testFloat64ToUInt64Rounding(10.5, 11, TowardPositive);
        testFloat64ToUInt64Rounding(10.5, 10, TowardNegative);
        testFloat64ToUInt64WithBoundsCheck(-1, 0, True);
        testFloat64ToUInt64WithBoundsCheck(0, 0, False);
        testFloat64ToUInt64WithBoundsCheck(100, 100, False);

        testFloat64ToUInt128(0, 0);
        testFloat64ToUInt128(100, 100);
        testFloat64ToUInt128Rounding(10.5, 10, TowardZero);
        testFloat64ToUInt128Rounding(10.5, 10, TiesToEven);
        testFloat64ToUInt128Rounding(10.5, 11, TiesToAway);
        testFloat64ToUInt128Rounding(10.5, 11, TowardPositive);
        testFloat64ToUInt128Rounding(10.5, 10, TowardNegative);
        testFloat64ToUInt128WithBoundsCheck(-1, 0, True);
        testFloat64ToUInt128WithBoundsCheck(0, 0, False);
        testFloat64ToUInt128WithBoundsCheck(100, 100, False);

        console.print(">>>> Finished Float64 Conversion tests >>>>");
    }

    void testFloat64ToFloat32(Float64 a, Float32 expected){
        console.print("Test Float64 ", True);
        console.print(a, True);
        console.print(" to Float32 expected=", True);
        console.print(expected, True);
        Float32 b = a.toFloat32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat64ToFloat64(Float64 a, Float64 expected){
        console.print("Test Float64 ", True);
        console.print(a, True);
        console.print(" to Float64 expected=", True);
        console.print(expected, True);
        Float64 b = a.toFloat64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat64ToDec32(Float64 a, Dec32 expected){
        console.print("Test Float64 ", True);
        console.print(a, True);
        console.print(" to Dec32 expected=", True);
        console.print(expected, True);
        Dec32 b = a.toDec32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat64ToDec64(Float64 a, Dec64 expected){
        console.print("Test Float64 ", True);
        console.print(a, True);
        console.print(" to Dec64 expected=", True);
        console.print(expected, True);
        Dec64 b = a.toDec64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat64ToDec128(Float64 a, Dec128 expected){
        console.print("Test Float64 ", True);
        console.print(a, True);
        console.print(" to Dec128 expected=", True);
        console.print(expected, True);
        Dec128 b = a.toDec128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat64ToInt8(Float64 a, Int8 expected){
        console.print("Test Float64 ", True);
        console.print(a, True);
        console.print(" to Int8 expected=", True);
        console.print(expected, True);
        Int8 b = a.toInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat64ToInt8Rounding(Float64 a, Int8 expected, Rounding direction){
        console.print("Test Float64 ", True);
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

    void testFloat64ToInt8WithBoundsCheck(Float64 a, Int8 expected, Boolean oob){
        console.print("Test Float64 ", True);
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

    void testFloat64ToInt16(Float64 a, Int16 expected){
        console.print("Test Float64 ", True);
        console.print(a, True);
        console.print(" to Int16 expected=", True);
        console.print(expected, True);
        Int16 b = a.toInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat64ToInt16Rounding(Float64 a, Int16 expected, Rounding direction){
        console.print("Test Float64 ", True);
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

    void testFloat64ToInt16WithBoundsCheck(Float64 a, Int16 expected, Boolean oob){
        console.print("Test Float64 ", True);
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

    void testFloat64ToInt32(Float64 a, Int32 expected){
        console.print("Test Float64 ", True);
        console.print(a, True);
        console.print(" to Int32 expected=", True);
        console.print(expected, True);
        Int32 b = a.toInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat64ToInt32Rounding(Float64 a, Int32 expected, Rounding direction){
        console.print("Test Float64 ", True);
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

    void testFloat64ToInt32WithBoundsCheck(Float64 a, Int32 expected, Boolean oob){
        console.print("Test Float64 ", True);
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

    void testFloat64ToInt64(Float64 a, Int64 expected){
        console.print("Test Float64 ", True);
        console.print(a, True);
        console.print(" to Int64 expected=", True);
        console.print(expected, True);
        Int64 b = a.toInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat64ToInt64Rounding(Float64 a, Int64 expected, Rounding direction){
        console.print("Test Float64 ", True);
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

    void testFloat64ToInt64WithBoundsCheck(Float64 a, Int64 expected, Boolean oob){
        console.print("Test Float64 ", True);
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

    void testFloat64ToInt128(Float64 a, Int128 expected){
        console.print("Test Float64 ", True);
        console.print(a, True);
        console.print(" to Int128 expected=", True);
        console.print(expected, True);
        Int128 b = a.toInt128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat64ToInt128Rounding(Float64 a, Int128 expected, Rounding direction){
        console.print("Test Float64 ", True);
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

    void testFloat64ToInt128WithBoundsCheck(Float64 a, Int128 expected, Boolean oob){
        console.print("Test Float64 ", True);
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

    void testFloat64ToUInt8(Float64 a, UInt8 expected){
        console.print("Test Float64 ", True);
        console.print(a, True);
        console.print(" to UInt8 expected=", True);
        console.print(expected, True);
        UInt8 b = a.toUInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat64ToUInt8Rounding(Float64 a, UInt8 expected, Rounding direction){
        console.print("Test Float64 ", True);
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

    void testFloat64ToUInt8WithBoundsCheck(Float64 a, UInt8 expected, Boolean oob){
        console.print("Test Float64 ", True);
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

    void testFloat64ToUInt16(Float64 a, UInt16 expected){
        console.print("Test Float64 ", True);
        console.print(a, True);
        console.print(" to UInt16 expected=", True);
        console.print(expected, True);
        UInt16 b = a.toUInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat64ToUInt16Rounding(Float64 a, UInt16 expected, Rounding direction){
        console.print("Test Float64 ", True);
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

    void testFloat64ToUInt16WithBoundsCheck(Float64 a, UInt16 expected, Boolean oob){
        console.print("Test Float64 ", True);
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

    void testFloat64ToUInt32(Float64 a, UInt32 expected){
        console.print("Test Float64 ", True);
        console.print(a, True);
        console.print(" to UInt32 expected=", True);
        console.print(expected, True);
        UInt32 b = a.toUInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat64ToUInt32Rounding(Float64 a, UInt32 expected, Rounding direction){
        console.print("Test Float64 ", True);
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

    void testFloat64ToUInt32WithBoundsCheck(Float64 a, UInt32 expected, Boolean oob){
        console.print("Test Float64 ", True);
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

    void testFloat64ToUInt64(Float64 a, UInt64 expected){
        console.print("Test Float64 ", True);
        console.print(a, True);
        console.print(" to UInt64 expected=", True);
        console.print(expected, True);
        UInt64 b = a.toUInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat64ToUInt64Rounding(Float64 a, UInt64 expected, Rounding direction){
        console.print("Test Float64 ", True);
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

    void testFloat64ToUInt64WithBoundsCheck(Float64 a, UInt64 expected, Boolean oob){
        console.print("Test Float64 ", True);
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

    void testFloat64ToUInt128(Float64 a, UInt128 expected){
        console.print("Test Float64 ", True);
        console.print(a, True);
        console.print(" to UInt128 expected=", True);
        console.print(expected, True);
        UInt128 b = a.toUInt128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat64ToUInt128Rounding(Float64 a, UInt128 expected, Rounding direction){
        console.print("Test Float64 ", True);
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

    void testFloat64ToUInt128WithBoundsCheck(Float64 a, UInt128 expected, Boolean oob){
        console.print("Test Float64 ", True);
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

    void testFloat64ToInt8AsNumber(Float64 a, Int8 expected){
        console.print("Test Float64 ", True);
        console.print(a, True);
        console.print(" as Number to Int8 expected=", True);
        console.print(expected, True);
        Number b = asNumber(a).toInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b.as(Int8) == expected;
    }

    void testFloat64ToUInt8AsNumber(Float64 a, UInt8 expected){
        console.print("Test Float64 ", True);
        console.print(a, True);
        console.print(" as Number to UInt8 expected=", True);
        console.print(expected, True);
        Number b = asNumber(a).toUInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b.as(UInt8) == expected;
    }

    Number asNumber(Number n) = n;
}
