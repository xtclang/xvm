/**
 * Tests for Float32 conversions to other numeric types.
 */
class Float32ConvertTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Float32 Conversion tests >>>>");

        testFloat32ToFloat32(-1000.456, -1000.456);
        testFloat32ToFloat32(1000.456, 1000.456);
        testFloat32ToFloat64(-1000.456, -1000.456);
        testFloat32ToFloat64(1000.456, 1000.456);

        testFloat32ToDec32(-1000.456, -1000.456);
        testFloat32ToDec32(1000.456, 1000.456);
        testFloat32ToDec64(-1000.456, -1000.456);
        testFloat32ToDec64(1000.456, 1000.456);
        testFloat32ToDec128(-1000.456, -1000.456);
        testFloat32ToDec128(1000.456, 1000.456);

        testFloat32ToInt8(-200, 56);
        testFloat32ToInt8(-128.4, -128);
        testFloat32ToInt8(-100, -100);
        testFloat32ToInt8(0, 0);
        testFloat32ToInt8(100, 100);
        testFloat32ToInt8(256.4, 0); // rounds to 256 == 0x100 converted to Int8 == 0
        testFloat32ToInt8Rounding(10.4, 10, TowardZero); // rounds towards zero
        testFloat32ToInt8Rounding(10.5, 10, TowardZero);
        testFloat32ToInt8Rounding(10.9, 10, TowardZero);
        testFloat32ToInt8Rounding(-10.4, -10, TowardZero);
        testFloat32ToInt8Rounding(-10.5, -10, TowardZero);
        testFloat32ToInt8Rounding(-10.9, -10, TowardZero);
        testFloat32ToInt8Rounding(10.1, 10, TiesToEven); // .5 rounds to the even number up or down
        testFloat32ToInt8Rounding(10.5, 10, TiesToEven);
        testFloat32ToInt8Rounding(10.9, 11, TiesToEven);
        testFloat32ToInt8Rounding(11.1, 11, TiesToEven);
        testFloat32ToInt8Rounding(11.5, 12, TiesToEven);
        testFloat32ToInt8Rounding(11.9, 12, TiesToEven);
        testFloat32ToInt8Rounding(-10.1, -10, TiesToEven);
        testFloat32ToInt8Rounding(-10.5, -10, TiesToEven);
        testFloat32ToInt8Rounding(-10.9, -11, TiesToEven);
        testFloat32ToInt8Rounding(-11.1, -11, TiesToEven);
        testFloat32ToInt8Rounding(-11.5, -12, TiesToEven);
        testFloat32ToInt8Rounding(-11.9, -12, TiesToEven);
        testFloat32ToInt8Rounding(10.1, 11, TiesToAway); // rounds up
        testFloat32ToInt8Rounding(10.5, 11, TiesToAway);
        testFloat32ToInt8Rounding(10.9, 11, TiesToAway);
        testFloat32ToInt8Rounding(-10.1, -11, TiesToAway);
        testFloat32ToInt8Rounding(-10.5, -11, TiesToAway);
        testFloat32ToInt8Rounding(-10.9, -11, TiesToAway);
        testFloat32ToInt8Rounding(10.4, 11, TowardPositive); // rounds towards +ve infinity
        testFloat32ToInt8Rounding(10.5, 11, TowardPositive);
        testFloat32ToInt8Rounding(10.9, 11, TowardPositive);
        testFloat32ToInt8Rounding(-10.4, -10, TowardPositive);
        testFloat32ToInt8Rounding(-10.5, -10, TowardPositive);
        testFloat32ToInt8Rounding(-10.9, -10, TowardPositive);
        testFloat32ToInt8Rounding(10.4, 10, TowardNegative); // rounds towards -ve infinity
        testFloat32ToInt8Rounding(10.5, 10, TowardNegative);
        testFloat32ToInt8Rounding(10.9, 10, TowardNegative);
        testFloat32ToInt8Rounding(-10.4, -11, TowardNegative);
        testFloat32ToInt8Rounding(-10.5, -11, TowardNegative);
        testFloat32ToInt8Rounding(-10.9, -11, TowardNegative);
        testFloat32ToInt8WithBoundsCheck(-129.3, 0, True);
        testFloat32ToInt8WithBoundsCheck(-128.9, Int8.MinValue, False);
        testFloat32ToInt8WithBoundsCheck(-128.4, Int8.MinValue, False);
        testFloat32ToInt8WithBoundsCheck(-100, -100, False);
        testFloat32ToInt8WithBoundsCheck(0, 0, False);
        testFloat32ToInt8WithBoundsCheck(100, 100, False);
        testFloat32ToInt8WithBoundsCheck(127.4, 127, False);
        testFloat32ToInt8WithBoundsCheck(128.4, 0, True);

        testFloat32ToInt16(-33768.9, 31768);
        testFloat32ToInt16(-32768.4, -32768);
        testFloat32ToInt16(-100, -100);
        testFloat32ToInt16(0, 0);
        testFloat32ToInt16(100, 100);
        testFloat32ToInt16(65536.4, 0); // rounds to 65536 == 0x10000 converted to Int16 == 0
        testFloat32ToInt16Rounding(10.4, 10, TowardZero); // rounds towards zero
        testFloat32ToInt16Rounding(10.5, 10, TowardZero);
        testFloat32ToInt16Rounding(10.9, 10, TowardZero);
        testFloat32ToInt16Rounding(-10.4, -10, TowardZero);
        testFloat32ToInt16Rounding(-10.5, -10, TowardZero);
        testFloat32ToInt16Rounding(-10.9, -10, TowardZero);
        testFloat32ToInt16Rounding(10.1, 10, TiesToEven); // .5 rounds to the even number up or down
        testFloat32ToInt16Rounding(10.5, 10, TiesToEven);
        testFloat32ToInt16Rounding(10.9, 11, TiesToEven);
        testFloat32ToInt16Rounding(11.1, 11, TiesToEven);
        testFloat32ToInt16Rounding(11.5, 12, TiesToEven);
        testFloat32ToInt16Rounding(11.9, 12, TiesToEven);
        testFloat32ToInt16Rounding(-10.1, -10, TiesToEven);
        testFloat32ToInt16Rounding(-10.5, -10, TiesToEven);
        testFloat32ToInt16Rounding(-10.9, -11, TiesToEven);
        testFloat32ToInt16Rounding(-11.1, -11, TiesToEven);
        testFloat32ToInt16Rounding(-11.5, -12, TiesToEven);
        testFloat32ToInt16Rounding(-11.9, -12, TiesToEven);
        testFloat32ToInt16Rounding(10.1, 11, TiesToAway); // rounds up
        testFloat32ToInt16Rounding(10.5, 11, TiesToAway);
        testFloat32ToInt16Rounding(10.9, 11, TiesToAway);
        testFloat32ToInt16Rounding(-10.1, -11, TiesToAway);
        testFloat32ToInt16Rounding(-10.5, -11, TiesToAway);
        testFloat32ToInt16Rounding(-10.9, -11, TiesToAway);
        testFloat32ToInt16Rounding(10.4, 11, TowardPositive); // rounds towards +ve infinity
        testFloat32ToInt16Rounding(10.5, 11, TowardPositive);
        testFloat32ToInt16Rounding(10.9, 11, TowardPositive);
        testFloat32ToInt16Rounding(-10.4, -10, TowardPositive);
        testFloat32ToInt16Rounding(-10.5, -10, TowardPositive);
        testFloat32ToInt16Rounding(-10.9, -10, TowardPositive);
        testFloat32ToInt16Rounding(10.4, 10, TowardNegative); // rounds towards -ve infinity
        testFloat32ToInt16Rounding(10.5, 10, TowardNegative);
        testFloat32ToInt16Rounding(10.9, 10, TowardNegative);
        testFloat32ToInt16Rounding(-10.4, -11, TowardNegative);
        testFloat32ToInt16Rounding(-10.5, -11, TowardNegative);
        testFloat32ToInt16Rounding(-10.9, -11, TowardNegative);
        testFloat32ToInt16WithBoundsCheck(-32769.1, 0, True);
        testFloat32ToInt16WithBoundsCheck(-32768.9, Int16.MinValue, False);
        testFloat32ToInt16WithBoundsCheck(-32768.4, Int16.MinValue, False);
        testFloat32ToInt16WithBoundsCheck(-100, -100, False);
        testFloat32ToInt16WithBoundsCheck(0, 0, False);
        testFloat32ToInt16WithBoundsCheck(100, 100, False);
        testFloat32ToInt16WithBoundsCheck(32767.7, 32767, False);
        testFloat32ToInt16WithBoundsCheck(32768.1, 0, True);

        testFloat32ToInt32(-2147483649.1, 2147483296);
        testFloat32ToInt32(-100, -100);
        testFloat32ToInt32(0, 0);
        testFloat32ToInt32(100, 100);
        testFloat32ToInt32(2147483648.1, -2147483296);
        testFloat32ToInt32Rounding(10.4, 10, TowardZero); // rounds towards zero
        testFloat32ToInt32Rounding(10.5, 10, TowardZero);
        testFloat32ToInt32Rounding(10.9, 10, TowardZero);
        testFloat32ToInt32Rounding(-10.4, -10, TowardZero);
        testFloat32ToInt32Rounding(-10.5, -10, TowardZero);
        testFloat32ToInt32Rounding(-10.9, -10, TowardZero);
        testFloat32ToInt32Rounding(10.1, 10, TiesToEven); // .5 rounds to the even number up or down
        testFloat32ToInt32Rounding(10.5, 10, TiesToEven);
        testFloat32ToInt32Rounding(10.9, 11, TiesToEven);
        testFloat32ToInt32Rounding(11.1, 11, TiesToEven);
        testFloat32ToInt32Rounding(11.5, 12, TiesToEven);
        testFloat32ToInt32Rounding(11.9, 12, TiesToEven);
        testFloat32ToInt32Rounding(-10.1, -10, TiesToEven);
        testFloat32ToInt32Rounding(-10.5, -10, TiesToEven);
        testFloat32ToInt32Rounding(-10.9, -11, TiesToEven);
        testFloat32ToInt32Rounding(-11.1, -11, TiesToEven);
        testFloat32ToInt32Rounding(-11.5, -12, TiesToEven);
        testFloat32ToInt32Rounding(-11.9, -12, TiesToEven);
        testFloat32ToInt32Rounding(10.1, 11, TiesToAway); // rounds up
        testFloat32ToInt32Rounding(10.5, 11, TiesToAway);
        testFloat32ToInt32Rounding(10.9, 11, TiesToAway);
        testFloat32ToInt32Rounding(-10.1, -11, TiesToAway);
        testFloat32ToInt32Rounding(-10.5, -11, TiesToAway);
        testFloat32ToInt32Rounding(-10.9, -11, TiesToAway);
        testFloat32ToInt32Rounding(10.4, 11, TowardPositive); // rounds towards +ve infinity
        testFloat32ToInt32Rounding(10.5, 11, TowardPositive);
        testFloat32ToInt32Rounding(10.9, 11, TowardPositive);
        testFloat32ToInt32Rounding(-10.4, -10, TowardPositive);
        testFloat32ToInt32Rounding(-10.5, -10, TowardPositive);
        testFloat32ToInt32Rounding(-10.9, -10, TowardPositive);
        testFloat32ToInt32Rounding(10.4, 10, TowardNegative); // rounds towards -ve infinity
        testFloat32ToInt32Rounding(10.5, 10, TowardNegative);
        testFloat32ToInt32Rounding(10.9, 10, TowardNegative);
        testFloat32ToInt32Rounding(-10.4, -11, TowardNegative);
        testFloat32ToInt32Rounding(-10.5, -11, TowardNegative);
        testFloat32ToInt32Rounding(-10.9, -11, TowardNegative);
        testFloat32ToInt32WithBoundsCheck(-2147483649.1, 0, True);
        testFloat32ToInt32WithBoundsCheck(-100, -100, False);
        testFloat32ToInt32WithBoundsCheck(0, 0, False);
        testFloat32ToInt32WithBoundsCheck(100, 100, False);
        testFloat32ToInt32WithBoundsCheck(2147483648.1, 0, True);

        testFloat32ToInt64(-100, -100);
        testFloat32ToInt64(0, 0);
        testFloat32ToInt64(100, 100);
        testFloat32ToInt64Rounding(10.5, 10, TowardZero);
        testFloat32ToInt64Rounding(10.5, 10, TiesToEven);
        testFloat32ToInt64Rounding(10.5, 11, TiesToAway);
        testFloat32ToInt64Rounding(10.5, 11, TowardPositive);
        testFloat32ToInt64Rounding(10.5, 10, TowardNegative);
        testFloat32ToInt64WithBoundsCheck(-100, -100, False);
        testFloat32ToInt64WithBoundsCheck(0, 0, False);
        testFloat32ToInt64WithBoundsCheck(100, 100, False);

        testFloat32ToInt128(-100, -100);
        testFloat32ToInt128(0, 0);
        testFloat32ToInt128(100, 100);
        testFloat32ToInt128Rounding(10.5, 10, TowardZero);
        testFloat32ToInt128Rounding(10.5, 10, TiesToEven);
        testFloat32ToInt128Rounding(10.5, 11, TiesToAway);
        testFloat32ToInt128Rounding(10.5, 11, TowardPositive);
        testFloat32ToInt128Rounding(10.5, 10, TowardNegative);
        testFloat32ToInt128WithBoundsCheck(-100, -100, False);
        testFloat32ToInt128WithBoundsCheck(0, 0, False);
        testFloat32ToInt128WithBoundsCheck(100, 100, False);

        testFloat32ToUInt8(0, 0);
        testFloat32ToUInt8(100, 100);
        testFloat32ToUInt8(256.4, 0);
        testFloat32ToUInt8Rounding(10.5, 10, TowardZero);
        testFloat32ToUInt8Rounding(10.5, 10, TiesToEven);
        testFloat32ToUInt8Rounding(10.5, 11, TiesToAway);
        testFloat32ToUInt8Rounding(10.5, 11, TowardPositive);
        testFloat32ToUInt8Rounding(10.5, 10, TowardNegative);
        testFloat32ToUInt8WithBoundsCheck(-1, 0, True);
        testFloat32ToUInt8WithBoundsCheck(0, 0, False);
        testFloat32ToUInt8WithBoundsCheck(255, 255, False);
        testFloat32ToUInt8WithBoundsCheck(256, 0, True);

        testFloat32ToUInt16(0, 0);
        testFloat32ToUInt16(100, 100);
        testFloat32ToUInt16(65536.4, 0);
        testFloat32ToUInt16Rounding(10.5, 10, TowardZero);
        testFloat32ToUInt16Rounding(10.5, 10, TiesToEven);
        testFloat32ToUInt16Rounding(10.5, 11, TiesToAway);
        testFloat32ToUInt16Rounding(10.5, 11, TowardPositive);
        testFloat32ToUInt16Rounding(10.5, 10, TowardNegative);
        testFloat32ToUInt16WithBoundsCheck(-1, 0, True);
        testFloat32ToUInt16WithBoundsCheck(0, 0, False);
        testFloat32ToUInt16WithBoundsCheck(65535, 65535, False);
        testFloat32ToUInt16WithBoundsCheck(65536, 0, True);

        testFloat32ToUInt32(0, 0);
        testFloat32ToUInt32(100, 100);
        testFloat32ToUInt32Rounding(10.5, 10, TowardZero);
        testFloat32ToUInt32Rounding(10.5, 10, TiesToEven);
        testFloat32ToUInt32Rounding(10.5, 11, TiesToAway);
        testFloat32ToUInt32Rounding(10.5, 11, TowardPositive);
        testFloat32ToUInt32Rounding(10.5, 10, TowardNegative);
        testFloat32ToUInt32WithBoundsCheck(-1, 0, True);
        testFloat32ToUInt32WithBoundsCheck(0, 0, False);
        testFloat32ToUInt32WithBoundsCheck(100, 100, False);
        testFloat32ToUInt32WithBoundsCheck(42949679898.9296, 0, True);

        testFloat32ToUInt64(0, 0);
        testFloat32ToUInt64(100, 100);
        testFloat32ToUInt64Rounding(10.5, 10, TowardZero);
        testFloat32ToUInt64Rounding(10.5, 10, TiesToEven);
        testFloat32ToUInt64Rounding(10.5, 11, TiesToAway);
        testFloat32ToUInt64Rounding(10.5, 11, TowardPositive);
        testFloat32ToUInt64Rounding(10.5, 10, TowardNegative);
        testFloat32ToUInt64WithBoundsCheck(-1, 0, True);
        testFloat32ToUInt64WithBoundsCheck(0, 0, False);
        testFloat32ToUInt64WithBoundsCheck(100, 100, False);

        testFloat32ToUInt128(0, 0);
        testFloat32ToUInt128(100, 100);
        testFloat32ToUInt128Rounding(10.5, 10, TowardZero);
        testFloat32ToUInt128Rounding(10.5, 10, TiesToEven);
        testFloat32ToUInt128Rounding(10.5, 11, TiesToAway);
        testFloat32ToUInt128Rounding(10.5, 11, TowardPositive);
        testFloat32ToUInt128Rounding(10.5, 10, TowardNegative);
        testFloat32ToUInt128WithBoundsCheck(-1, 0, True);
        testFloat32ToUInt128WithBoundsCheck(0, 0, False);
        testFloat32ToUInt128WithBoundsCheck(100, 100, False);

        console.print(">>>> Finished Float32 Conversion tests >>>>");
    }

    void testFloat32ToFloat32(Float32 a, Float32 expected){
        console.print("Test Float32 ", True);
        console.print(a, True);
        console.print(" to Float32 expected=", True);
        console.print(expected, True);
        Float32 b = a.toFloat32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat32ToFloat64(Float32 a, Float64 expected){
        console.print("Test Float32 ", True);
        console.print(a, True);
        console.print(" to Float64 expected=", True);
        console.print(expected, True);
        Float64 b = a.toFloat64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat32ToDec32(Float32 a, Dec32 expected){
        console.print("Test Float32 ", True);
        console.print(a, True);
        console.print(" to Dec32 expected=", True);
        console.print(expected, True);
        Dec32 b = a.toDec32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat32ToDec64(Float32 a, Dec64 expected){
        console.print("Test Float32 ", True);
        console.print(a, True);
        console.print(" to Dec64 expected=", True);
        console.print(expected, True);
        Dec64 b = a.toDec64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat32ToDec128(Float32 a, Dec128 expected){
        console.print("Test Float32 ", True);
        console.print(a, True);
        console.print(" to Dec128 expected=", True);
        console.print(expected, True);
        Dec128 b = a.toDec128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat32ToInt8(Float32 a, Int8 expected){
        console.print("Test Float32 ", True);
        console.print(a, True);
        console.print(" to Int8 expected=", True);
        console.print(expected, True);
        Int8 b = a.toInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat32ToInt8Rounding(Float32 a, Int8 expected, Rounding direction){
        console.print("Test Float32 ", True);
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

    void testFloat32ToInt8WithBoundsCheck(Float32 a, Int8 expected, Boolean oob){
        console.print("Test Float32 ", True);
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

    void testFloat32ToInt16(Float32 a, Int16 expected){
        console.print("Test Float32 ", True);
        console.print(a, True);
        console.print(" to Int16 expected=", True);
        console.print(expected, True);
        Int16 b = a.toInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat32ToInt16Rounding(Float32 a, Int16 expected, Rounding direction){
        console.print("Test Float32 ", True);
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

    void testFloat32ToInt16WithBoundsCheck(Float32 a, Int16 expected, Boolean oob){
        console.print("Test Float32 ", True);
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

    void testFloat32ToInt32(Float32 a, Int32 expected){
        console.print("Test Float32 ", True);
        console.print(a, True);
        console.print(" to Int32 expected=", True);
        console.print(expected, True);
        Int32 b = a.toInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat32ToInt32Rounding(Float32 a, Int32 expected, Rounding direction){
        console.print("Test Float32 ", True);
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

    void testFloat32ToInt32WithBoundsCheck(Float32 a, Int32 expected, Boolean oob){
        console.print("Test Float32 ", True);
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

    void testFloat32ToInt64(Float32 a, Int64 expected){
        console.print("Test Float32 ", True);
        console.print(a, True);
        console.print(" to Int64 expected=", True);
        console.print(expected, True);
        Int64 b = a.toInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat32ToInt64Rounding(Float32 a, Int64 expected, Rounding direction){
        console.print("Test Float32 ", True);
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

    void testFloat32ToInt64WithBoundsCheck(Float32 a, Int64 expected, Boolean oob){
        console.print("Test Float32 ", True);
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

    void testFloat32ToInt128(Float32 a, Int128 expected){
        console.print("Test Float32 ", True);
        console.print(a, True);
        console.print(" to Int128 expected=", True);
        console.print(expected, True);
        Int128 b = a.toInt128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat32ToInt128Rounding(Float32 a, Int128 expected, Rounding direction){
        console.print("Test Float32 ", True);
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

    void testFloat32ToInt128WithBoundsCheck(Float32 a, Int128 expected, Boolean oob){
        console.print("Test Float32 ", True);
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

    void testFloat32ToUInt8(Float32 a, UInt8 expected){
        console.print("Test Float32 ", True);
        console.print(a, True);
        console.print(" to UInt8 expected=", True);
        console.print(expected, True);
        UInt8 b = a.toUInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat32ToUInt8Rounding(Float32 a, UInt8 expected, Rounding direction){
        console.print("Test Float32 ", True);
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

    void testFloat32ToUInt8WithBoundsCheck(Float32 a, UInt8 expected, Boolean oob){
        console.print("Test Float32 ", True);
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

    void testFloat32ToUInt16(Float32 a, UInt16 expected){
        console.print("Test Float32 ", True);
        console.print(a, True);
        console.print(" to UInt16 expected=", True);
        console.print(expected, True);
        UInt16 b = a.toUInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat32ToUInt16Rounding(Float32 a, UInt16 expected, Rounding direction){
        console.print("Test Float32 ", True);
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

    void testFloat32ToUInt16WithBoundsCheck(Float32 a, UInt16 expected, Boolean oob){
        console.print("Test Float32 ", True);
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

    void testFloat32ToUInt32(Float32 a, UInt32 expected){
        console.print("Test Float32 ", True);
        console.print(a, True);
        console.print(" to UInt32 expected=", True);
        console.print(expected, True);
        UInt32 b = a.toUInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat32ToUInt32Rounding(Float32 a, UInt32 expected, Rounding direction){
        console.print("Test Float32 ", True);
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

    void testFloat32ToUInt32WithBoundsCheck(Float32 a, UInt32 expected, Boolean oob){
        console.print("Test Float32 ", True);
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

    void testFloat32ToUInt64(Float32 a, UInt64 expected){
        console.print("Test Float32 ", True);
        console.print(a, True);
        console.print(" to UInt64 expected=", True);
        console.print(expected, True);
        UInt64 b = a.toUInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat32ToUInt64Rounding(Float32 a, UInt64 expected, Rounding direction){
        console.print("Test Float32 ", True);
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

    void testFloat32ToUInt64WithBoundsCheck(Float32 a, UInt64 expected, Boolean oob){
        console.print("Test Float32 ", True);
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

    void testFloat32ToUInt128(Float32 a, UInt128 expected){
        console.print("Test Float32 ", True);
        console.print(a, True);
        console.print(" to UInt128 expected=", True);
        console.print(expected, True);
        UInt128 b = a.toUInt128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testFloat32ToUInt128Rounding(Float32 a, UInt128 expected, Rounding direction){
        console.print("Test Float32 ", True);
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

    void testFloat32ToUInt128WithBoundsCheck(Float32 a, UInt128 expected, Boolean oob){
        console.print("Test Float32 ", True);
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
