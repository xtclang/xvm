/**
 * Tests for Dec32 conversions to other numeric types.
 */
class Dec32ConvertTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Dec32 Conversion tests >>>>");

        testDec32ToDec32(-1000.456, -1000.456);
        testDec32ToDec32(1000.456, 1000.456);
        testDec32ToDec64(-1000.456, -1000.456);
        testDec32ToDec64(1000.456, 1000.456);
        testDec32ToDec128(-1000.456, -1000.456);
        testDec32ToDec128(1000.456, 1000.456);

        testDec32ToInt8(-200, 56);
        testDec32ToInt8(-128.4, -128);
        testDec32ToInt8(-100, -100);
        testDec32ToInt8(0, 0);
        testDec32ToInt8(100, 100);
        testDec32ToInt8(256.4, 0); // rounds to 256 == 0x100 converted to Int8 == 0
        testDec32ToInt8Rounding(10.4, 10, TowardZero); // rounds towards zero
        testDec32ToInt8Rounding(10.5, 10, TowardZero);
        testDec32ToInt8Rounding(10.9, 10, TowardZero);
        testDec32ToInt8Rounding(-10.4, -10, TowardZero);
        testDec32ToInt8Rounding(-10.5, -10, TowardZero);
        testDec32ToInt8Rounding(-10.9, -10, TowardZero);
        testDec32ToInt8Rounding(10.1, 10, TiesToEven); // .5 rounds to the even number up or down
        testDec32ToInt8Rounding(10.5, 10, TiesToEven);
        testDec32ToInt8Rounding(10.9, 11, TiesToEven);
        testDec32ToInt8Rounding(11.1, 11, TiesToEven);
        testDec32ToInt8Rounding(11.5, 12, TiesToEven);
        testDec32ToInt8Rounding(11.9, 12, TiesToEven);
        testDec32ToInt8Rounding(-10.1, -10, TiesToEven);
        testDec32ToInt8Rounding(-10.5, -10, TiesToEven);
        testDec32ToInt8Rounding(-10.9, -11, TiesToEven);
        testDec32ToInt8Rounding(-11.1, -11, TiesToEven);
        testDec32ToInt8Rounding(-11.5, -12, TiesToEven);
        testDec32ToInt8Rounding(-11.9, -12, TiesToEven);
        testDec32ToInt8Rounding(10.1, 11, TiesToAway); // rounds up
        testDec32ToInt8Rounding(10.5, 11, TiesToAway);
        testDec32ToInt8Rounding(10.9, 11, TiesToAway);
        testDec32ToInt8Rounding(-10.1, -11, TiesToAway);
        testDec32ToInt8Rounding(-10.5, -11, TiesToAway);
        testDec32ToInt8Rounding(-10.9, -11, TiesToAway);
        testDec32ToInt8Rounding(10.4, 11, TowardPositive); // rounds towards +ve infinity
        testDec32ToInt8Rounding(10.5, 11, TowardPositive);
        testDec32ToInt8Rounding(10.9, 11, TowardPositive);
        testDec32ToInt8Rounding(-10.4, -10, TowardPositive);
        testDec32ToInt8Rounding(-10.5, -10, TowardPositive);
        testDec32ToInt8Rounding(-10.9, -10, TowardPositive);
        testDec32ToInt8Rounding(10.4, 10, TowardNegative); // rounds towards -ve infinity
        testDec32ToInt8Rounding(10.5, 10, TowardNegative);
        testDec32ToInt8Rounding(10.9, 10, TowardNegative);
        testDec32ToInt8Rounding(-10.4, -11, TowardNegative);
        testDec32ToInt8Rounding(-10.5, -11, TowardNegative);
        testDec32ToInt8Rounding(-10.9, -11, TowardNegative);
        testDec32ToInt8WithBoundsCheck(-129.3, 0, True);
        testDec32ToInt8WithBoundsCheck(-128.9, Int8.MinValue, False);
        testDec32ToInt8WithBoundsCheck(-128.4, Int8.MinValue, False);
        testDec32ToInt8WithBoundsCheck(-100, -100, False);
        testDec32ToInt8WithBoundsCheck(0, 0, False);
        testDec32ToInt8WithBoundsCheck(100, 100, False);
        testDec32ToInt8WithBoundsCheck(127.4, 127, False);
        testDec32ToInt8WithBoundsCheck(128.4, 0, True);

        testDec32ToInt16(-33768.9, 31768);
        testDec32ToInt16(-32768.4, -32768);
        testDec32ToInt16(-100, -100);
        testDec32ToInt16(0, 0);
        testDec32ToInt16(100, 100);
        testDec32ToInt16(65536.4, 0); // rounds to 65536 == 0x10000 converted to Int16 == 0
        testDec32ToInt16Rounding(10.4, 10, TowardZero); // rounds towards zero
        testDec32ToInt16Rounding(10.5, 10, TowardZero);
        testDec32ToInt16Rounding(10.9, 10, TowardZero);
        testDec32ToInt16Rounding(-10.4, -10, TowardZero);
        testDec32ToInt16Rounding(-10.5, -10, TowardZero);
        testDec32ToInt16Rounding(-10.9, -10, TowardZero);
        testDec32ToInt16Rounding(10.1, 10, TiesToEven); // .5 rounds to the even number up or down
        testDec32ToInt16Rounding(10.5, 10, TiesToEven);
        testDec32ToInt16Rounding(10.9, 11, TiesToEven);
        testDec32ToInt16Rounding(11.1, 11, TiesToEven);
        testDec32ToInt16Rounding(11.5, 12, TiesToEven);
        testDec32ToInt16Rounding(11.9, 12, TiesToEven);
        testDec32ToInt16Rounding(-10.1, -10, TiesToEven);
        testDec32ToInt16Rounding(-10.5, -10, TiesToEven);
        testDec32ToInt16Rounding(-10.9, -11, TiesToEven);
        testDec32ToInt16Rounding(-11.1, -11, TiesToEven);
        testDec32ToInt16Rounding(-11.5, -12, TiesToEven);
        testDec32ToInt16Rounding(-11.9, -12, TiesToEven);
        testDec32ToInt16Rounding(10.1, 11, TiesToAway); // rounds up
        testDec32ToInt16Rounding(10.5, 11, TiesToAway);
        testDec32ToInt16Rounding(10.9, 11, TiesToAway);
        testDec32ToInt16Rounding(-10.1, -11, TiesToAway);
        testDec32ToInt16Rounding(-10.5, -11, TiesToAway);
        testDec32ToInt16Rounding(-10.9, -11, TiesToAway);
        testDec32ToInt16Rounding(10.4, 11, TowardPositive); // rounds towards +ve infinity
        testDec32ToInt16Rounding(10.5, 11, TowardPositive);
        testDec32ToInt16Rounding(10.9, 11, TowardPositive);
        testDec32ToInt16Rounding(-10.4, -10, TowardPositive);
        testDec32ToInt16Rounding(-10.5, -10, TowardPositive);
        testDec32ToInt16Rounding(-10.9, -10, TowardPositive);
        testDec32ToInt16Rounding(10.4, 10, TowardNegative); // rounds towards -ve infinity
        testDec32ToInt16Rounding(10.5, 10, TowardNegative);
        testDec32ToInt16Rounding(10.9, 10, TowardNegative);
        testDec32ToInt16Rounding(-10.4, -11, TowardNegative);
        testDec32ToInt16Rounding(-10.5, -11, TowardNegative);
        testDec32ToInt16Rounding(-10.9, -11, TowardNegative);
        testDec32ToInt16WithBoundsCheck(-32769.1, 0, True);
        testDec32ToInt16WithBoundsCheck(-32768.9, Int16.MinValue, False);
        testDec32ToInt16WithBoundsCheck(-32768.4, Int16.MinValue, False);
        testDec32ToInt16WithBoundsCheck(-100, -100, False);
        testDec32ToInt16WithBoundsCheck(0, 0, False);
        testDec32ToInt16WithBoundsCheck(100, 100, False);
        testDec32ToInt16WithBoundsCheck(32767.7, 32767, False);
        testDec32ToInt16WithBoundsCheck(32768.1, 0, True);

        testDec32ToInt32(-2147487648.9, 2147479296);
        testDec32ToInt32(-100, -100);
        testDec32ToInt32(0, 0);
        testDec32ToInt32(100, 100);
        testDec32ToInt32(2147483647.1, -2147483296);
        testDec32ToInt32Rounding(10.4, 10, TowardZero); // rounds towards zero
        testDec32ToInt32Rounding(10.5, 10, TowardZero);
        testDec32ToInt32Rounding(10.9, 10, TowardZero);
        testDec32ToInt32Rounding(-10.4, -10, TowardZero);
        testDec32ToInt32Rounding(-10.5, -10, TowardZero);
        testDec32ToInt32Rounding(-10.9, -10, TowardZero);
        testDec32ToInt32Rounding(10.1, 10, TiesToEven); // .5 rounds to the even number up or down
        testDec32ToInt32Rounding(10.5, 10, TiesToEven);
        testDec32ToInt32Rounding(10.9, 11, TiesToEven);
        testDec32ToInt32Rounding(11.1, 11, TiesToEven);
        testDec32ToInt32Rounding(11.5, 12, TiesToEven);
        testDec32ToInt32Rounding(11.9, 12, TiesToEven);
        testDec32ToInt32Rounding(-10.1, -10, TiesToEven);
        testDec32ToInt32Rounding(-10.5, -10, TiesToEven);
        testDec32ToInt32Rounding(-10.9, -11, TiesToEven);
        testDec32ToInt32Rounding(-11.1, -11, TiesToEven);
        testDec32ToInt32Rounding(-11.5, -12, TiesToEven);
        testDec32ToInt32Rounding(-11.9, -12, TiesToEven);
        testDec32ToInt32Rounding(10.1, 11, TiesToAway); // rounds up
        testDec32ToInt32Rounding(10.5, 11, TiesToAway);
        testDec32ToInt32Rounding(10.9, 11, TiesToAway);
        testDec32ToInt32Rounding(-10.1, -11, TiesToAway);
        testDec32ToInt32Rounding(-10.5, -11, TiesToAway);
        testDec32ToInt32Rounding(-10.9, -11, TiesToAway);
        testDec32ToInt32Rounding(10.4, 11, TowardPositive); // rounds towards +ve infinity
        testDec32ToInt32Rounding(10.5, 11, TowardPositive);
        testDec32ToInt32Rounding(10.9, 11, TowardPositive);
        testDec32ToInt32Rounding(-10.4, -10, TowardPositive);
        testDec32ToInt32Rounding(-10.5, -10, TowardPositive);
        testDec32ToInt32Rounding(-10.9, -10, TowardPositive);
        testDec32ToInt32Rounding(10.4, 10, TowardNegative); // rounds towards -ve infinity
        testDec32ToInt32Rounding(10.5, 10, TowardNegative);
        testDec32ToInt32Rounding(10.9, 10, TowardNegative);
        testDec32ToInt32Rounding(-10.4, -11, TowardNegative);
        testDec32ToInt32Rounding(-10.5, -11, TowardNegative);
        testDec32ToInt32Rounding(-10.9, -11, TowardNegative);
        testDec32ToInt32WithBoundsCheck(-2147487649.1, 0, True);
        testDec32ToInt32WithBoundsCheck(-100, -100, False);
        testDec32ToInt32WithBoundsCheck(0, 0, False);
        testDec32ToInt32WithBoundsCheck(100, 100, False);
        testDec32ToInt32WithBoundsCheck(2147483648.1, 0, True);

        testDec32ToInt64(-100, -100);
        testDec32ToInt64(0, 0);
        testDec32ToInt64(100, 100);
        testDec32ToInt64Rounding(10.5, 10, TowardZero);
        testDec32ToInt64Rounding(10.5, 10, TiesToEven);
        testDec32ToInt64Rounding(10.5, 11, TiesToAway);
        testDec32ToInt64Rounding(10.5, 11, TowardPositive);
        testDec32ToInt64Rounding(10.5, 10, TowardNegative);
        testDec32ToInt64WithBoundsCheck(-100, -100, False);
        testDec32ToInt64WithBoundsCheck(0, 0, False);
        testDec32ToInt64WithBoundsCheck(100, 100, False);

        testDec32ToInt128(-100, -100);
        testDec32ToInt128(0, 0);
        testDec32ToInt128(100, 100);
        testDec32ToInt128Rounding(10.5, 10, TowardZero);
        testDec32ToInt128Rounding(10.5, 10, TiesToEven);
        testDec32ToInt128Rounding(10.5, 11, TiesToAway);
        testDec32ToInt128Rounding(10.5, 11, TowardPositive);
        testDec32ToInt128Rounding(10.5, 10, TowardNegative);
        testDec32ToInt128WithBoundsCheck(-100, -100, False);
        testDec32ToInt128WithBoundsCheck(0, 0, False);
        testDec32ToInt128WithBoundsCheck(100, 100, False);

        testDec32ToUInt8(0, 0);
        testDec32ToUInt8(100, 100);
        testDec32ToUInt8(256.4, 0);
        testDec32ToUInt8Rounding(10.5, 10, TowardZero);
        testDec32ToUInt8Rounding(10.5, 10, TiesToEven);
        testDec32ToUInt8Rounding(10.5, 11, TiesToAway);
        testDec32ToUInt8Rounding(10.5, 11, TowardPositive);
        testDec32ToUInt8Rounding(10.5, 10, TowardNegative);
        testDec32ToUInt8WithBoundsCheck(-1, 0, True);
        testDec32ToUInt8WithBoundsCheck(0, 0, False);
        testDec32ToUInt8WithBoundsCheck(255, 255, False);
        testDec32ToUInt8WithBoundsCheck(256, 0, True);

        testDec32ToUInt16(0, 0);
        testDec32ToUInt16(100, 100);
        testDec32ToUInt16(65536.4, 0);
        testDec32ToUInt16Rounding(10.5, 10, TowardZero);
        testDec32ToUInt16Rounding(10.5, 10, TiesToEven);
        testDec32ToUInt16Rounding(10.5, 11, TiesToAway);
        testDec32ToUInt16Rounding(10.5, 11, TowardPositive);
        testDec32ToUInt16Rounding(10.5, 10, TowardNegative);
        testDec32ToUInt16WithBoundsCheck(-1, 0, True);
        testDec32ToUInt16WithBoundsCheck(0, 0, False);
        testDec32ToUInt16WithBoundsCheck(65535, 65535, False);
        testDec32ToUInt16WithBoundsCheck(65536, 0, True);

        testDec32ToUInt32(0, 0);
        testDec32ToUInt32(100, 100);
        testDec32ToUInt32Rounding(10.5, 10, TowardZero);
        testDec32ToUInt32Rounding(10.5, 10, TiesToEven);
        testDec32ToUInt32Rounding(10.5, 11, TiesToAway);
        testDec32ToUInt32Rounding(10.5, 11, TowardPositive);
        testDec32ToUInt32Rounding(10.5, 10, TowardNegative);
        testDec32ToUInt32WithBoundsCheck(-1, 0, True);
        testDec32ToUInt32WithBoundsCheck(0, 0, False);
        testDec32ToUInt32WithBoundsCheck(100, 100, False);
        testDec32ToUInt32WithBoundsCheck(4294967296, 4294967000, False); // lost precision

        testDec32ToUInt64(0, 0);
        testDec32ToUInt64(100, 100);
        testDec32ToUInt64Rounding(10.5, 10, TowardZero);
        testDec32ToUInt64Rounding(10.5, 10, TiesToEven);
        testDec32ToUInt64Rounding(10.5, 11, TiesToAway);
        testDec32ToUInt64Rounding(10.5, 11, TowardPositive);
        testDec32ToUInt64Rounding(10.5, 10, TowardNegative);
        testDec32ToUInt64WithBoundsCheck(-1, 0, True);
        testDec32ToUInt64WithBoundsCheck(0, 0, False);
        testDec32ToUInt64WithBoundsCheck(100, 100, False);

        testDec32ToUInt128(0, 0);
        testDec32ToUInt128(100, 100);
        testDec32ToUInt128Rounding(10.5, 10, TowardZero);
        testDec32ToUInt128Rounding(10.5, 10, TiesToEven);
        testDec32ToUInt128Rounding(10.5, 11, TiesToAway);
        testDec32ToUInt128Rounding(10.5, 11, TowardPositive);
        testDec32ToUInt128Rounding(10.5, 10, TowardNegative);
        testDec32ToUInt128WithBoundsCheck(-1, 0, True);
        testDec32ToUInt128WithBoundsCheck(0, 0, False);
        testDec32ToUInt128WithBoundsCheck(100, 100, False);

        console.print(">>>> Finished Dec32 Conversion tests >>>>");
    }


    void testDec32ToDec32(Dec32 a, Dec32 expected){
        console.print("Test Dec32 ", True);
        console.print(a, True);
        console.print(" to Dec32 expected=", True);
        console.print(expected, True);
        Dec32 b = a.toDec32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec32ToDec64(Dec32 a, Dec64 expected){
        console.print("Test Dec32 ", True);
        console.print(a, True);
        console.print(" to Dec64 expected=", True);
        console.print(expected, True);
        Dec64 b = a.toDec64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec32ToDec128(Dec32 a, Dec128 expected){
        console.print("Test Dec32 ", True);
        console.print(a, True);
        console.print(" to Dec128 expected=", True);
        console.print(expected, True);
        Dec128 b = a.toDec128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec32ToInt8(Dec32 a, Int8 expected){
        console.print("Test Dec32 ", True);
        console.print(a, True);
        console.print(" to Int8 expected=", True);
        console.print(expected, True);
        Int8 b = a.toInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec32ToInt8Rounding(Dec32 a, Int8 expected, Rounding direction){
        console.print("Test Dec32 ", True);
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

    void testDec32ToInt8WithBoundsCheck(Dec32 a, Int8 expected, Boolean oob){
        console.print("Test Dec32 ", True);
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

    void testDec32ToInt16(Dec32 a, Int16 expected){
        console.print("Test Dec32 ", True);
        console.print(a, True);
        console.print(" to Int16 expected=", True);
        console.print(expected, True);
        Int16 b = a.toInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec32ToInt16Rounding(Dec32 a, Int16 expected, Rounding direction){
        console.print("Test Dec32 ", True);
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

    void testDec32ToInt16WithBoundsCheck(Dec32 a, Int16 expected, Boolean oob){
        console.print("Test Dec32 ", True);
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

    void testDec32ToInt32(Dec32 a, Int32 expected){
        console.print("Test Dec32 ", True);
        console.print(a, True);
        console.print(" to Int32 expected=", True);
        console.print(expected, True);
        Int32 b = a.toInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec32ToInt32Rounding(Dec32 a, Int32 expected, Rounding direction){
        console.print("Test Dec32 ", True);
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

    void testDec32ToInt32WithBoundsCheck(Dec32 a, Int32 expected, Boolean oob){
        console.print("Test Dec32 ", True);
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

    void testDec32ToInt64(Dec32 a, Int64 expected){
        console.print("Test Dec32 ", True);
        console.print(a, True);
        console.print(" to Int64 expected=", True);
        console.print(expected, True);
        Int64 b = a.toInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec32ToInt64Rounding(Dec32 a, Int64 expected, Rounding direction){
        console.print("Test Dec32 ", True);
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

    void testDec32ToInt64WithBoundsCheck(Dec32 a, Int64 expected, Boolean oob){
        console.print("Test Dec32 ", True);
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

    void testDec32ToInt128(Dec32 a, Int128 expected){
        console.print("Test Dec32 ", True);
        console.print(a, True);
        console.print(" to Int128 expected=", True);
        console.print(expected, True);
        Int128 b = a.toInt128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec32ToInt128Rounding(Dec32 a, Int128 expected, Rounding direction){
        console.print("Test Dec32 ", True);
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

    void testDec32ToInt128WithBoundsCheck(Dec32 a, Int128 expected, Boolean oob){
        console.print("Test Dec32 ", True);
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

    void testDec32ToUInt8(Dec32 a, UInt8 expected){
        console.print("Test Dec32 ", True);
        console.print(a, True);
        console.print(" to UInt8 expected=", True);
        console.print(expected, True);
        UInt8 b = a.toUInt8();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec32ToUInt8Rounding(Dec32 a, UInt8 expected, Rounding direction){
        console.print("Test Dec32 ", True);
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

    void testDec32ToUInt8WithBoundsCheck(Dec32 a, UInt8 expected, Boolean oob){
        console.print("Test Dec32 ", True);
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

    void testDec32ToUInt16(Dec32 a, UInt16 expected){
        console.print("Test Dec32 ", True);
        console.print(a, True);
        console.print(" to UInt16 expected=", True);
        console.print(expected, True);
        UInt16 b = a.toUInt16();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec32ToUInt16Rounding(Dec32 a, UInt16 expected, Rounding direction){
        console.print("Test Dec32 ", True);
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

    void testDec32ToUInt16WithBoundsCheck(Dec32 a, UInt16 expected, Boolean oob){
        console.print("Test Dec32 ", True);
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

    void testDec32ToUInt32(Dec32 a, UInt32 expected){
        console.print("Test Dec32 ", True);
        console.print(a, True);
        console.print(" to UInt32 expected=", True);
        console.print(expected, True);
        UInt32 b = a.toUInt32();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec32ToUInt32Rounding(Dec32 a, UInt32 expected, Rounding direction){
        console.print("Test Dec32 ", True);
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

    void testDec32ToUInt32WithBoundsCheck(Dec32 a, UInt32 expected, Boolean oob){
        console.print("Test Dec32 ", True);
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

    void testDec32ToUInt64(Dec32 a, UInt64 expected){
        console.print("Test Dec32 ", True);
        console.print(a, True);
        console.print(" to UInt64 expected=", True);
        console.print(expected, True);
        UInt64 b = a.toUInt64();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec32ToUInt64Rounding(Dec32 a, UInt64 expected, Rounding direction){
        console.print("Test Dec32 ", True);
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

    void testDec32ToUInt64WithBoundsCheck(Dec32 a, UInt64 expected, Boolean oob){
        console.print("Test Dec32 ", True);
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

    void testDec32ToUInt128(Dec32 a, UInt128 expected){
        console.print("Test Dec32 ", True);
        console.print(a, True);
        console.print(" to UInt128 expected=", True);
        console.print(expected, True);
        UInt128 b = a.toUInt128();
        console.print(" actual=", True);
        console.print(b);
        assert b == expected;
    }

    void testDec32ToUInt128Rounding(Dec32 a, UInt128 expected, Rounding direction){
        console.print("Test Dec32 ", True);
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

    void testDec32ToUInt128WithBoundsCheck(Dec32 a, UInt128 expected, Boolean oob){
        console.print("Test Dec32 ", True);
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
