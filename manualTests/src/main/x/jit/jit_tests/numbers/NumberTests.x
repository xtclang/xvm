import ecstasy.numbers.BinaryFPNumber;
import ecstasy.numbers.DecimalFPNumber;
import ecstasy.numbers.UIntNumber;

class NumberTests {

    void run() {
        testBitLength();
        testBits();
        testByteLength();
        testSigned();
        testSign();
        testNegative();
        testFinite();
        testInfinity();
        testNaN();
        testMagnitude();
// TODO: JIT calls $hasOptMethod() with a null nFunction context
//        testConverterFor();
    }

//    void testConverterFor() {
//        function Byte(Int) toByte = Number.converterFor(Int, Byte);
//        assert toByte(3) == 3;
//        assert toByte(45) == 45;
//
//        function Float64(Int) toFloat64 = Number.converterFor(Int, Float64);
//        assert toFloat64(42) == 42.0;
//    }

    void testBitLength() {
        assert Dec32.one().bitLength == 32;
        testBitLengthDecimalFPNumber(Dec32.one(), 32);
        assert Dec64.one().bitLength == 64;
        testBitLengthDecimalFPNumber(Dec64.one(), 64);
        assert Dec128.one().bitLength == 128;
        testBitLengthDecimalFPNumber(Dec128.one(), 128);

        assert Float16.one().bitLength == 16;
        testBitLengthBinaryFPNumber(Float16.one(), 16);
        assert Float32.one().bitLength == 32;
        testBitLengthBinaryFPNumber(Float32.one(), 32);
        assert Float64.one().bitLength == 64;
        testBitLengthBinaryFPNumber(Float64.one(), 64);

        assert Nibble.one().bitLength == 4;
        testBitLengthUIntNumber(Nibble.one(), 4);

        assert Int8.one().bitLength == 8;
        testBitLengthIntNumber(Int8.one(), 8);
        assert Int16.one().bitLength == 16;
        testBitLengthIntNumber(Int16.one(), 16);
        assert Int32.one().bitLength == 32;
        testBitLengthIntNumber(Int32.one(), 32);
        assert Int64.one().bitLength == 64;
        testBitLengthIntNumber(Int64.one(), 64);
        assert Int128.one().bitLength == 128;
        testBitLengthIntNumber(Int128.one(), 128);

        assert UInt8.one().bitLength == 8;
        testBitLengthUIntNumber(UInt8.one(), 8);
        assert UInt16.one().bitLength == 16;
        testBitLengthUIntNumber(UInt16.one(), 16);
        assert UInt32.one().bitLength == 32;
        testBitLengthUIntNumber(UInt32.one(), 32);
        assert UInt64.one().bitLength == 64;
        testBitLengthUIntNumber(UInt64.one(), 64);
        assert UInt128.one().bitLength == 128;
        testBitLengthUIntNumber(UInt128.one(), 128);

        assert IntN.one().bitLength == 1;
        testBitLengthIntNumber(IntN.one(), 1);
        assert UIntN.one().bitLength == 1;
        testBitLengthUIntNumber(UIntN.one(), 1);
    }

    void testBitLengthNumber(Number n, Int expected) {
        assert n.bitLength == expected;
    }

    void testBitLengthFPNumber(FPNumber n, Int expected) {
        assert n.bitLength == expected;
        testBitLengthNumber(n, expected);
        }

    void testBitLengthBinaryFPNumber(BinaryFPNumber n, Int expected) {
        assert n.bitLength == expected;
        testBitLengthFPNumber(n, expected);
    }

    void testBitLengthDecimalFPNumber(DecimalFPNumber n, Int expected) {
        assert n.bitLength == expected;
        testBitLengthFPNumber(n, expected);
    }

    void testBitLengthIntNumber(IntNumber n, Int expected) {
        assert n.bitLength == expected;
        testBitLengthNumber(n, expected);
    }

    void testBitLengthUIntNumber(UIntNumber n, Int expected) {
        assert n.bitLength == expected;
        testBitLengthIntNumber(n, expected);
    }

    void testBits() {
        assertBitArray(Dec32.one().bits, 32);
        testBitsDecimalFPNumber(Dec32.one(), 32);
        assertBitArray(Dec64.one().bits, 64);
        testBitsDecimalFPNumber(Dec64.one(), 64);
        assertBitArray(Dec128.one().bits, 128);
        testBitsDecimalFPNumber(Dec128.one(), 128);

        assertBitArray(Float16.one().bits, 16);
        testBitsBinaryFPNumber(Float16.one(), 16);
        assertBitArray(Float32.one().bits, 32);
        testBitsBinaryFPNumber(Float32.one(), 32);
        assertBitArray(Float64.one().bits, 64);
        testBitsBinaryFPNumber(Float64.one(), 64);

        assertBitArray(Nibble.one().bits, 4);
        testBitsUIntNumber(Nibble.one(), 4);

        assertBitArray(Int8.one().bits, 8);
        testBitsIntNumber(Int8.one(), 8);
        assertBitArray(Int16.one().bits, 16);
        testBitsIntNumber(Int16.one(), 16);
        assertBitArray(Int32.one().bits, 32);
        testBitsIntNumber(Int32.one(), 32);
        assertBitArray(Int64.one().bits, 64);
        testBitsIntNumber(Int64.one(), 64);
        assertBitArray(Int128.one().bits, 128);
        testBitsIntNumber(Int128.one(), 128);

        assertBitArray(UInt8.one().bits, 8);
        testBitsUIntNumber(UInt8.one(), 8);
        assertBitArray(UInt16.one().bits, 16);
        testBitsUIntNumber(UInt16.one(), 16);
        assertBitArray(UInt32.one().bits, 32);
        testBitsUIntNumber(UInt32.one(), 32);
        assertBitArray(UInt64.one().bits, 64);
        testBitsUIntNumber(UInt64.one(), 64);
        assertBitArray(UInt128.one().bits, 128);
        testBitsUIntNumber(UInt128.one(), 128);

        assertBitArray(IntN.one().bits, 1);
        testBitsIntNumber(IntN.one(), 1);
        assertBitArray(UIntN.one().bits, 1);
        testBitsUIntNumber(UIntN.one(), 1);
    }

    void assertBitArray(Bit[] bits, Int size) {
        assert bits.size == size;
    }

    void testBitsNumber(Number n, Int expected) {
        assertBitArray(n.bits, expected);
    }

    void testBitsFPNumber(FPNumber n, Int expected) {
        assertBitArray(n.bits, expected);
        testBitsNumber(n, expected);
        }

    void testBitsBinaryFPNumber(BinaryFPNumber n, Int expected) {
        assertBitArray(n.bits, expected);
        testBitsFPNumber(n, expected);
    }

    void testBitsDecimalFPNumber(DecimalFPNumber n, Int expected) {
        assertBitArray(n.bits, expected);
        testBitsFPNumber(n, expected);
    }

    void testBitsIntNumber(IntNumber n, Int expected) {
        assertBitArray(n.bits, expected);
        testBitsNumber(n, expected);
    }

    void testBitsUIntNumber(UIntNumber n, Int expected) {
        assertBitArray(n.bits, expected);
        testBitsIntNumber(n, expected);
    }

    void testByteLength() {
        assert Dec32.one().byteLength == 4;
        testByteLengthDecimalFPNumber(Dec32.one(), 4);
        assert Dec64.one().byteLength == 8;
        testByteLengthDecimalFPNumber(Dec64.one(), 8);
        assert Dec128.one().byteLength == 16;
        testByteLengthDecimalFPNumber(Dec128.one(), 16);

        assert Float16.one().byteLength == 2;
        testByteLengthBinaryFPNumber(Float16.one(), 2);
        assert Float32.one().byteLength == 4;
        testByteLengthBinaryFPNumber(Float32.one(), 4);
        assert Float64.one().byteLength == 8;
        testByteLengthBinaryFPNumber(Float64.one(), 8);

        assert Nibble.one().byteLength == 1;
        testByteLengthUIntNumber(Nibble.one(), 1);

        assert Int8.one().byteLength == 1;
        testByteLengthIntNumber(Int8.one(), 1);
        assert Int16.one().byteLength == 2;
        testByteLengthIntNumber(Int16.one(), 2);
        assert Int32.one().byteLength == 4;
        testByteLengthIntNumber(Int32.one(), 4);
        assert Int64.one().byteLength == 8;
        testByteLengthIntNumber(Int64.one(), 8);
        assert Int128.one().byteLength == 16;
        testByteLengthIntNumber(Int128.one(), 16);

        assert UInt8.one().byteLength == 1;
        testByteLengthUIntNumber(UInt8.one(), 1);
        assert UInt16.one().byteLength == 2;
        testByteLengthUIntNumber(UInt16.one(), 2);
        assert UInt32.one().byteLength == 4;
        testByteLengthUIntNumber(UInt32.one(), 4);
        assert UInt64.one().byteLength == 8;
        testByteLengthUIntNumber(UInt64.one(), 8);
        assert UInt128.one().byteLength == 16;
        testByteLengthUIntNumber(UInt128.one(), 16);

        assert IntN.one().byteLength == 1;
        testByteLengthIntNumber(IntN.one(), 1);
        assert UIntN.one().byteLength == 1;
        testByteLengthUIntNumber(UIntN.one(), 1);
    }

    void testByteLengthNumber(Number n, Int expected) {
        assert n.byteLength == expected;
    }

    void testByteLengthFPNumber(FPNumber n, Int expected) {
        assert n.byteLength == expected;
        testByteLengthNumber(n, expected);
    }

    void testByteLengthBinaryFPNumber(BinaryFPNumber n, Int expected) {
        assert n.byteLength == expected;
        testByteLengthFPNumber(n, expected);
    }

    void testByteLengthDecimalFPNumber(DecimalFPNumber n, Int expected) {
        assert n.byteLength == expected;
        testByteLengthFPNumber(n, expected);
    }

    void testByteLengthIntNumber(IntNumber n, Int expected) {
        assert n.byteLength == expected;
        testByteLengthNumber(n, expected);
    }

    void testByteLengthUIntNumber(UIntNumber n, Int expected) {
        assert n.byteLength == expected;
        testByteLengthIntNumber(n, expected);
    }

    void testSigned() {
        assert Dec32.one().signed == True;
        testSignedDecimalFPNumber(Dec32.one(), True);
        assert Dec64.one().signed == True;
        testSignedDecimalFPNumber(Dec64.one(), True);
        assert Dec128.one().signed == True;
        testSignedDecimalFPNumber(Dec128.one(), True);

        assert Float16.one().signed == True;
        testSignedBinaryFPNumber(Float16.one(), True);
        assert Float32.one().signed == True;
        testSignedBinaryFPNumber(Float32.one(), True);
        assert Float64.one().signed == True;
        testSignedBinaryFPNumber(Float64.one(), True);

        assert Nibble.one().signed == False;
        testSignedUIntNumber(Nibble.one(), False);

        assert Int8.one().signed == True;
        testSignedIntNumber(Int8.one(), True);
        assert Int16.one().signed == True;
        testSignedIntNumber(Int16.one(), True);
        assert Int32.one().signed == True;
        testSignedIntNumber(Int32.one(), True);
        assert Int64.one().signed == True;
        testSignedIntNumber(Int64.one(), True);
        assert Int128.one().signed == True;
        testSignedIntNumber(Int128.one(), True);

        assert UInt8.one().signed == False;
        testSignedUIntNumber(UInt8.one(), False);
        assert UInt16.one().signed == False;
        testSignedUIntNumber(UInt16.one(), False);
        assert UInt32.one().signed == False;
        testSignedUIntNumber(UInt32.one(), False);
        assert UInt64.one().signed == False;
        testSignedUIntNumber(UInt64.one(), False);
        assert UInt128.one().signed == False;
        testSignedUIntNumber(UInt128.one(), False);

        assert IntN.one().signed == True;
        testSignedIntNumber(IntN.one(), True);
        assert UIntN.one().signed == False;
        testSignedUIntNumber(UIntN.one(), False);
    }

    void testSignedNumber(Number n, Boolean expected) {
        assert n.signed == expected;
    }

    void testSignedFPNumber(FPNumber n, Boolean expected) {
        assert n.signed == expected;
        testSignedNumber(n, expected);
    }

    void testSignedBinaryFPNumber(BinaryFPNumber n, Boolean expected) {
        assert n.signed == expected;
        testSignedFPNumber(n, expected);
    }

    void testSignedDecimalFPNumber(DecimalFPNumber n, Boolean expected) {
        assert n.signed == expected;
        testSignedFPNumber(n, expected);
    }

    void testSignedIntNumber(IntNumber n, Boolean expected) {
        assert n.signed == expected;
        testSignedNumber(n, expected);
    }

    void testSignedUIntNumber(UIntNumber n, Boolean expected) {
        assert n.signed == expected;
        testSignedIntNumber(n, expected);
    }

    void testSign() {
        // Decimals
        assert Dec32.one().sign == Positive;
        testSignDecimalFPNumber(Dec32.one(), Positive);
        // TODO - test for negative zero
        assert Dec32.zero().sign == Positive;
        testSignDecimalFPNumber(Dec32.zero(), Positive);
        assert (-Dec32.one()).sign == Negative;
        testSignDecimalFPNumber(-Dec32.one(), Negative);
        assert (Dec32.PositiveInfinity).sign == Positive;
        testSignDecimalFPNumber(Dec32.PositiveInfinity, Positive);
        assert (Dec32.NegativeInfinity).sign == Negative;
        testSignDecimalFPNumber(Dec32.NegativeInfinity, Negative);
        assert (Dec32.PositiveNaN).sign == Positive;
        testSignDecimalFPNumber(Dec32.PositiveNaN, Positive);
        assert (Dec32.NegativeNaN).sign == Negative;
        testSignDecimalFPNumber(Dec32.NegativeNaN, Negative);

        assert Dec64.one().sign == Positive;
        testSignDecimalFPNumber(Dec64.one(), Positive);
        // TODO - test for negative zero
        assert Dec64.zero().sign == Positive;
        testSignDecimalFPNumber(Dec64.zero(), Positive);
        assert (-Dec64.one()).sign == Negative;
        testSignDecimalFPNumber(-Dec64.one(), Negative);
        assert Dec64.PositiveInfinity.sign == Positive;
        testSignDecimalFPNumber(Dec64.PositiveInfinity, Positive);
        assert (Dec64.NegativeInfinity).sign == Negative;
        testSignDecimalFPNumber(Dec64.NegativeInfinity, Negative);
        assert (Dec64.PositiveNaN).sign == Positive;
        testSignDecimalFPNumber(Dec64.PositiveNaN, Positive);
        assert (Dec64.NegativeNaN).sign == Negative;
        testSignDecimalFPNumber(Dec64.NegativeNaN, Negative);

        assert Dec128.one().sign == Positive;
        testSignDecimalFPNumber(Dec128.one(), Positive);
        // TODO - test for negative zero
        assert Dec128.zero().sign == Positive;
        testSignDecimalFPNumber(Dec128.zero(), Positive);
        assert (-Dec128.one()).sign == Negative;
        testSignDecimalFPNumber(-Dec128.one(), Negative);
        assert Dec128.PositiveInfinity.sign == Positive;
        testSignDecimalFPNumber(Dec128.PositiveInfinity, Positive);
        assert (Dec128.NegativeInfinity).sign == Negative;
        testSignDecimalFPNumber(Dec128.NegativeInfinity, Negative);
        assert (Dec128.PositiveNaN).sign == Positive;
        testSignDecimalFPNumber(Dec128.PositiveNaN, Positive);
        assert (Dec128.NegativeNaN).sign == Negative;
        testSignDecimalFPNumber(Dec128.NegativeNaN, Negative);

        // Floats
        assert Float16.one().sign == Positive;
        testSignBinaryFPNumber(Float16.one(), Positive);
        // TODO - test for negative zero
        assert Float16.zero().sign == Positive;
        testSignBinaryFPNumber(Float16.zero(), Positive);
        assert (-Float16.one()).sign == Negative;
        testSignBinaryFPNumber(-Float16.one(), Negative);
        assert Float16.PositiveInfinity.sign == Positive;
        testSignBinaryFPNumber(Float16.PositiveInfinity, Positive);
        assert (Float16.NegativeInfinity).sign == Negative;
        testSignBinaryFPNumber(Float16.NegativeInfinity, Negative);
        assert Float16.PositiveNaN.sign == Positive;
        testSignBinaryFPNumber(Float16.PositiveNaN, Positive);
// TODO Fails because Java float cannot do -NaN but the Ecstasy interpreter can
//        assert Float16.NegativeNaN.sign == Negative;
//        testSignBinaryFPNumber(Float16.NegativeNaN, Negative);

        assert Float32.one().sign == Positive;
        testSignBinaryFPNumber(Float32.one(), Positive);
        // TODO - test for negative zero
        assert Float32.zero().sign == Positive;
        testSignBinaryFPNumber(Float32.zero(), Positive);
        assert (-Float32.one()).sign == Negative;
        testSignBinaryFPNumber(-Float32.one(), Negative);
        assert Float32.PositiveInfinity.sign == Positive;
        testSignBinaryFPNumber(Float32.PositiveInfinity, Positive);
        assert (Float32.NegativeInfinity).sign == Negative;
        testSignBinaryFPNumber(Float32.NegativeInfinity, Negative);
        assert Float32.PositiveNaN.sign == Positive;
        testSignBinaryFPNumber(Float32.PositiveNaN, Positive);
// TODO Fails because Java float cannot do -NaN but the Ecstasy interpreter can
//        assert Float32.NegativeNaN.sign == Negative;
//        testSignBinaryFPNumber(Float32.NegativeNaN, Negative);

        assert Float64.one().sign == Positive;
        testSignBinaryFPNumber(Float64.one(), Positive);
        // TODO - test for negative zero
        assert Float64.zero().sign == Positive;
        testSignBinaryFPNumber(Float64.zero(), Positive);
        assert (-Float64.one()).sign == Negative;
        testSignBinaryFPNumber(-Float64.one(), Negative);
        assert Float64.PositiveInfinity.sign == Positive;
        testSignBinaryFPNumber(Float64.PositiveInfinity, Positive);
        assert (Float64.NegativeInfinity).sign == Negative;
        testSignBinaryFPNumber(Float64.NegativeInfinity, Negative);
        assert Float64.PositiveNaN.sign == Positive;
        testSignBinaryFPNumber(Float64.PositiveNaN, Positive);
// TODO Fails because Java double cannot do -NaN but the Ecstasy interpreter can
//        assert Float64.NegativeNaN.sign == Negative;
//        testSignBinaryFPNumber(Float64.NegativeNaN, Negative);

        // Nibble
        assert Nibble.one().sign == Positive;
        testSignUIntNumber(Nibble.one(), Positive);
        assert Nibble.zero().sign == Zero;
        testSignUIntNumber(Nibble.zero(), Zero);

        // Integers
        assert Int8.one().sign == Positive;
        testSignIntNumber(Int8.one(), Positive);
        assert Int8.zero().sign == Zero;
        testSignIntNumber(Int8.zero(), Zero);
        assert (-Int8.one()).sign == Negative;
        testSignIntNumber(-Int8.one(), Negative);

        assert Int16.one().sign == Positive;
        testSignIntNumber(Int16.one(), Positive);
        assert Int16.zero().sign == Zero;
        testSignIntNumber(Int16.zero(), Zero);
        assert (-Int16.one()).sign == Negative;
        testSignIntNumber(-Int16.one(), Negative);

        assert Int32.one().sign == Positive;
        testSignIntNumber(Int32.one(), Positive);
        assert Int32.zero().sign == Zero;
        testSignIntNumber(Int32.zero(), Zero);
        assert (-Int32.one()).sign == Negative;
        testSignIntNumber(-Int32.one(), Negative);

        assert Int64.one().sign == Positive;
        testSignIntNumber(Int64.one(), Positive);
        assert Int64.zero().sign == Zero;
        testSignIntNumber(Int64.zero(), Zero);
        assert (-Int64.one()).sign == Negative;
        testSignIntNumber(-Int64.one(), Negative);

        assert Int128.one().sign == Positive;
        testSignIntNumber(Int128.one(), Positive);
        assert Int128.zero().sign == Zero;
        testSignIntNumber(Int128.zero(), Zero);
        assert (-Int128.one()).sign == Negative;
        testSignIntNumber(-Int128.one(), Negative);

        assert IntN.one().sign == Positive;
        testSignIntNumber(IntN.one(), Positive);
        assert IntN.zero().sign == Zero;
        testSignIntNumber(IntN.zero(), Zero);
        assert (-IntN.one()).sign == Negative;
        testSignIntNumber(-IntN.one(), Negative);

        // Unsigned Integers
        assert UInt8.one().sign == Positive;
        testSignUIntNumber(UInt8.one(), Positive);
        assert UInt8.zero().sign == Zero;
        testSignUIntNumber(UInt8.zero(), Zero);

        assert UInt16.one().sign == Positive;
        testSignUIntNumber(UInt16.one(), Positive);
        assert UInt16.zero().sign == Zero;
        testSignUIntNumber(UInt16.zero(), Zero);

        assert UInt32.one().sign == Positive;
        testSignUIntNumber(UInt32.one(), Positive);
        assert UInt32.zero().sign == Zero;
        testSignUIntNumber(UInt32.zero(), Zero);

        assert UInt64.one().sign == Positive;
        testSignUIntNumber(UInt64.one(), Positive);
        assert UInt64.zero().sign == Zero;
        testSignUIntNumber(UInt64.zero(), Zero);

        assert UInt128.one().sign == Positive;
        testSignIntNumber(UInt128.one(), Positive);
        assert UInt128.zero().sign == Zero;
        testSignUIntNumber(UInt128.zero(), Zero);

        assert UIntN.one().sign == Positive;
        testSignIntNumber(UIntN.one(), Positive);
        assert UIntN.zero().sign == Zero;
        testSignUIntNumber(UIntN.zero(), Zero);
    }

    void testSignNumber(Number n, Signum expected) {
        assert n.sign == expected;
    }

    void testSignFPNumber(FPNumber n, Signum expected) {
        assert n.sign == expected;
        testSignNumber(n, expected);
    }

    void testSignBinaryFPNumber(BinaryFPNumber n, Signum expected) {
        assert n.sign == expected;
        testSignFPNumber(n, expected);
    }

    void testSignDecimalFPNumber(DecimalFPNumber n, Signum expected) {
        assert n.sign == expected;
        testSignFPNumber(n, expected);
    }

    void testSignIntNumber(IntNumber n, Signum expected) {
        assert n.sign == expected;
        testSignNumber(n, expected);
    }

    void testSignUIntNumber(UIntNumber n, Signum expected) {
        assert n.sign == expected;
        testSignIntNumber(n, expected);
    }

    void testNegative() {
        // Decimals
        assert Dec32.one().negative == False;
        testNegativeDecimalFPNumber(Dec32.one(), False);
        assert Dec32.zero().negative == False;
        testNegativeDecimalFPNumber(Dec32.zero(), False);
        assert (-Dec32.one()).negative == True;
        testNegativeDecimalFPNumber(-Dec32.one(), True);
        assert Dec32.PositiveInfinity.negative == False;
        testNegativeDecimalFPNumber(Dec32.PositiveInfinity, False);
        assert Dec32.NegativeInfinity.negative == True;
        testNegativeDecimalFPNumber(Dec32.NegativeInfinity, True);
        assert Dec32.PositiveNaN.negative == False;
        testNegativeDecimalFPNumber(Dec32.PositiveNaN, False);
        assert Dec32.NegativeNaN.negative == True;
        testNegativeDecimalFPNumber(Dec32.NegativeNaN, True);

        assert Dec64.one().negative == False;
        testNegativeDecimalFPNumber(Dec64.one(), False);
        assert Dec64.zero().negative == False;
        testNegativeDecimalFPNumber(Dec64.zero(), False);
        assert (-Dec64.one()).negative == True;
        testNegativeDecimalFPNumber(-Dec64.one(), True);
        assert Dec64.PositiveInfinity.negative == False;
        testNegativeDecimalFPNumber(Dec64.PositiveInfinity, False);
        assert Dec64.NegativeInfinity.negative == True;
        testNegativeDecimalFPNumber(Dec64.NegativeInfinity, True);
        assert Dec64.PositiveNaN.negative == False;
        testNegativeDecimalFPNumber(Dec64.PositiveNaN, False);
        assert Dec64.NegativeNaN.negative == True;
        testNegativeDecimalFPNumber(Dec64.NegativeNaN, True);

        assert Dec128.one().negative == False;
        testNegativeDecimalFPNumber(Dec128.one(), False);
        assert Dec128.zero().negative == False;
        testNegativeDecimalFPNumber(Dec128.zero(), False);
        assert (-Dec128.one()).negative == True;
        testNegativeDecimalFPNumber(-Dec128.one(), True);
        assert Dec128.PositiveInfinity.negative == False;
        testNegativeDecimalFPNumber(Dec128.PositiveInfinity, False);
        assert Dec128.NegativeInfinity.negative == True;
        testNegativeDecimalFPNumber(Dec128.NegativeInfinity, True);
        assert Dec128.PositiveNaN.negative == False;
        testNegativeDecimalFPNumber(Dec128.PositiveNaN, False);
        assert Dec128.NegativeNaN.negative == True;
        testNegativeDecimalFPNumber(Dec128.NegativeNaN, True);

        // Floats
        assert Float16.one().negative == False;
        testNegativeBinaryFPNumber(Float16.one(), False);
        assert Float16.zero().negative == False;
        testNegativeBinaryFPNumber(Float16.zero(), False);
        assert (-Float16.one()).negative == True;
        testNegativeBinaryFPNumber(-Float16.one(), True);
        assert Float16.PositiveInfinity.negative == False;
        testNegativeBinaryFPNumber(Float16.PositiveInfinity, False);
        assert Float16.NegativeInfinity.negative == True;
        testNegativeBinaryFPNumber(Float16.NegativeInfinity, True);
        assert Float16.PositiveNaN.negative == False;
        testNegativeBinaryFPNumber(Float16.PositiveNaN, False);
// TODO Fails because Java float cannot do -NaN but the Ecstasy interpreter can
//        assert Float16.NegativeNaN.negative == True;
//        testNegativeBinaryFPNumber(Float16.NegativeNaN, True);

        assert Float32.one().negative == False;
        testNegativeBinaryFPNumber(Float32.one(), False);
        assert Float32.zero().negative == False;
        testNegativeBinaryFPNumber(Float32.zero(), False);
        assert (-Float32.one()).negative == True;
        testNegativeBinaryFPNumber(-Float32.one(), True);
        assert Float32.PositiveInfinity.negative == False;
        testNegativeBinaryFPNumber(Float32.PositiveInfinity, False);
        assert Float32.NegativeInfinity.negative == True;
        testNegativeBinaryFPNumber(Float32.NegativeInfinity, True);
        assert Float32.PositiveNaN.negative == False;
        testNegativeBinaryFPNumber(Float32.PositiveNaN, False);
// TODO Fails because Java float cannot do -NaN but the Ecstasy interpreter can
//        assert Float32.NegativeNaN.negative == True;
//        testNegativeBinaryFPNumber(Float32.NegativeNaN, True);

        assert Float64.one().negative == False;
        testNegativeBinaryFPNumber(Float64.one(), False);
        assert Float64.zero().negative == False;
        testNegativeBinaryFPNumber(Float64.zero(), False);
        assert (-Float64.one()).negative == True;
        testNegativeBinaryFPNumber(-Float64.one(), True);
        assert Float64.PositiveInfinity.negative == False;
        testNegativeBinaryFPNumber(Float64.PositiveInfinity, False);
        assert Float64.NegativeInfinity.negative == True;
        testNegativeBinaryFPNumber(Float64.NegativeInfinity, True);
        assert Float64.PositiveNaN.negative == False;
        testNegativeBinaryFPNumber(Float64.PositiveNaN, False);
// TODO Fails because Java float cannot do -NaN but the Ecstasy interpreter can
//        assert Float64.NegativeNaN.negative == True;
//        testNegativeBinaryFPNumber(Float64.NegativeNaN, True);

        // Nibble
        assert Nibble.one().negative == False;
        testNegativeUIntNumber(Nibble.one(), False);
        assert Nibble.zero().negative == False;
        testNegativeUIntNumber(Nibble.zero(), False);

        // Integers
        assert Int8.one().negative == False;
        testNegativeIntNumber(Int8.one(), False);
        assert Int8.zero().negative == False;
        testNegativeIntNumber(Int8.zero(), False);
        assert (-Int8.one()).negative == True;
        testNegativeIntNumber(-Int8.one(), True);

        assert Int16.one().negative == False;
        testNegativeIntNumber(Int16.one(), False);
        assert Int16.zero().negative == False;
        testNegativeIntNumber(Int16.zero(), False);
        assert (-Int16.one()).negative == True;
        testNegativeIntNumber(-Int16.one(), True);

        assert Int32.one().negative == False;
        testNegativeIntNumber(Int32.one(), False);
        assert Int32.zero().negative == False;
        testNegativeIntNumber(Int32.zero(), False);
        assert (-Int32.one()).negative == True;
        testNegativeIntNumber(-Int32.one(), True);

        assert Int64.one().negative == False;
        testNegativeIntNumber(Int64.one(), False);
        assert Int64.zero().negative == False;
        testNegativeIntNumber(Int64.zero(), False);
        assert (-Int64.one()).negative == True;
        testNegativeIntNumber(-Int64.one(), True);

        assert Int128.one().negative == False;
        testNegativeIntNumber(Int128.one(), False);
        assert Int128.zero().negative == False;
        testNegativeIntNumber(Int128.zero(), False);
        assert (-Int128.one()).negative == True;
        testNegativeIntNumber(-Int128.one(), True);

        assert IntN.one().negative == False;
        testNegativeIntNumber(IntN.one(), False);
        assert IntN.zero().negative == False;
        testNegativeIntNumber(IntN.zero(), False);
        assert (-IntN.one()).negative == True;
        testNegativeIntNumber(-IntN.one(), True);

        // Unsigned Integers
        assert UInt8.one().negative == False;
        testNegativeUIntNumber(UInt8.one(), False);
        assert UInt8.zero().negative == False;
        testNegativeUIntNumber(UInt8.zero(), False);

        assert UInt16.one().negative == False;
        testNegativeUIntNumber(UInt16.one(), False);
        assert UInt16.zero().negative == False;
        testNegativeUIntNumber(UInt16.zero(), False);

        assert UInt32.one().negative == False;
        testNegativeUIntNumber(UInt32.one(), False);
        assert UInt32.zero().negative == False;
        testNegativeUIntNumber(UInt32.zero(), False);

        assert UInt64.one().negative == False;
        testNegativeUIntNumber(UInt64.one(), False);
        assert UInt64.zero().negative == False;
        testNegativeUIntNumber(UInt64.zero(), False);

        assert UInt128.one().negative == False;
        testNegativeUIntNumber(UInt128.one(), False);
        assert UInt128.zero().negative == False;
        testNegativeUIntNumber(UInt128.zero(), False);

        assert UIntN.one().negative == False;
        testNegativeUIntNumber(UIntN.one(), False);
        assert UIntN.zero().negative == False;
        testNegativeUIntNumber(UIntN.zero(), False);
    }

    void testNegativeNumber(Number n, Boolean expected) {
        assert n.negative == expected;
    }

    void testNegativeFPNumber(FPNumber n, Boolean expected) {
        assert n.negative == expected;
        testNegativeNumber(n, expected);
    }

    void testNegativeBinaryFPNumber(BinaryFPNumber n, Boolean expected) {
        assert n.negative == expected;
        testNegativeFPNumber(n, expected);
    }

    void testNegativeDecimalFPNumber(DecimalFPNumber n, Boolean expected) {
        assert n.negative == expected;
        testNegativeFPNumber(n, expected);
    }

    void testNegativeIntNumber(IntNumber n, Boolean expected) {
        assert n.negative == expected;
        testNegativeNumber(n, expected);
    }

    void testNegativeUIntNumber(UIntNumber n, Boolean expected) {
        assert n.negative == expected;
        testNegativeIntNumber(n, expected);
    }

    void testFinite() {
        // Decimals
        assert Dec32.one().finite == True;
        testFiniteDecimalFPNumber(Dec32.one(), True);
        assert Dec32.zero().finite == True;
        testFiniteDecimalFPNumber(Dec32.zero(), True);
        assert (-Dec32.one()).finite == True;
        testFiniteDecimalFPNumber(-Dec32.one(), True);
        assert Dec32.PositiveInfinity.finite == False;
        testFiniteDecimalFPNumber(Dec32.PositiveInfinity, False);
        assert Dec32.NegativeInfinity.finite == False;
        testFiniteDecimalFPNumber(Dec32.NegativeInfinity, False);
        assert Dec32.PositiveNaN.finite == False;
        testFiniteDecimalFPNumber(Dec32.PositiveNaN, False);
        assert Dec32.NegativeNaN.finite == False;
        testFiniteDecimalFPNumber(Dec32.NegativeNaN, False);

        assert Dec64.one().finite == True;
        testFiniteDecimalFPNumber(Dec64.one(), True);
        assert Dec64.zero().finite == True;
        testFiniteDecimalFPNumber(Dec64.zero(), True);
        assert (-Dec64.one()).finite == True;
        testFiniteDecimalFPNumber(-Dec64.one(), True);
        assert Dec64.PositiveInfinity.finite == False;
        testFiniteDecimalFPNumber(Dec64.PositiveInfinity, False);
        assert Dec64.NegativeInfinity.finite == False;
        testFiniteDecimalFPNumber(Dec64.NegativeInfinity, False);
        assert Dec64.PositiveNaN.finite == False;
        testFiniteDecimalFPNumber(Dec64.PositiveNaN, False);
        assert Dec64.NegativeNaN.finite == False;
        testFiniteDecimalFPNumber(Dec64.NegativeNaN, False);

        assert Dec128.one().finite == True;
        testFiniteDecimalFPNumber(Dec128.one(), True);
        assert Dec128.zero().finite == True;
        testFiniteDecimalFPNumber(Dec128.zero(), True);
        assert (-Dec128.one()).finite == True;
        testFiniteDecimalFPNumber(-Dec128.one(), True);
        assert Dec128.PositiveInfinity.finite == False;
        testFiniteDecimalFPNumber(Dec128.PositiveInfinity, False);
        assert Dec128.NegativeInfinity.finite == False;
        testFiniteDecimalFPNumber(Dec128.NegativeInfinity, False);
        assert Dec128.PositiveNaN.finite == False;
        testFiniteDecimalFPNumber(Dec128.PositiveNaN, False);
        assert Dec128.NegativeNaN.finite == False;
        testFiniteDecimalFPNumber(Dec128.NegativeNaN, False);

        // Floats
        assert Float16.one().finite == True;
        testFiniteBinaryFPNumber(Float16.one(), True);
        assert Float16.zero().finite == True;
        testFiniteBinaryFPNumber(Float16.zero(), True);
        assert (-Float16.one()).finite == True;
        testFiniteBinaryFPNumber(-Float16.one(), True);
        assert Float16.PositiveInfinity.finite == False;
        testFiniteBinaryFPNumber(Float16.PositiveInfinity, False);
        assert Float16.NegativeInfinity.finite == False;
        testFiniteBinaryFPNumber(Float16.NegativeInfinity, False);
        assert Float16.PositiveNaN.finite == False;
        testFiniteBinaryFPNumber(Float16.PositiveNaN, False);
        assert Float16.NegativeNaN.finite == False;
        testFiniteBinaryFPNumber(Float16.NegativeNaN, False);


        assert Float32.one().finite == True;
        testFiniteBinaryFPNumber(Float32.one(), True);
        assert Float32.zero().finite == True;
        testFiniteBinaryFPNumber(Float32.zero(), True);
        assert (-Float32.one()).finite == True;
        testFiniteBinaryFPNumber(-Float32.one(), True);
        assert Float32.PositiveInfinity.finite == False;
        testFiniteBinaryFPNumber(Float32.PositiveInfinity, False);
        assert Float32.NegativeInfinity.finite == False;
        testFiniteBinaryFPNumber(Float32.NegativeInfinity, False);
        assert Float32.PositiveNaN.finite == False;
        testFiniteBinaryFPNumber(Float32.PositiveNaN, False);
        assert Float32.NegativeNaN.finite == False;
        testFiniteBinaryFPNumber(Float32.NegativeNaN, False);

        assert Float64.one().finite == True;
        testFiniteBinaryFPNumber(Float64.one(), True);
        assert Float64.zero().finite == True;
        testFiniteBinaryFPNumber(Float64.zero(), True);
        assert (-Float64.one()).finite == True;
        testFiniteBinaryFPNumber(-Float64.one(), True);
        assert Float64.PositiveInfinity.finite == False;
        testFiniteBinaryFPNumber(Float64.PositiveInfinity, False);
        assert Float64.NegativeInfinity.finite == False;
        testFiniteBinaryFPNumber(Float64.NegativeInfinity, False);
        assert Float64.PositiveNaN.finite == False;
        testFiniteBinaryFPNumber(Float64.PositiveNaN, False);
        assert Float64.NegativeNaN.finite == False;
        testFiniteBinaryFPNumber(Float64.NegativeNaN, False);

        // Nibble
        assert Nibble.one().finite == True;
        testFiniteUIntNumber(Nibble.one(), True);

        // Integers
        assert Int8.one().finite == True;
        testFiniteIntNumber(Int8.one(), True);

        assert Int16.one().finite == True;
        testFiniteIntNumber(Int16.one(), True);

        assert Int32.one().finite == True;
        testFiniteIntNumber(Int32.one(), True);

        assert Int64.one().finite == True;
        testFiniteIntNumber(Int64.one(), True);

        assert Int128.one().finite == True;
        testFiniteIntNumber(Int128.one(), True);

        assert IntN.one().finite == True;
        testFiniteIntNumber(IntN.one(), True);

        // Unsigned Integers
        assert UInt8.one().finite == True;
        testFiniteUIntNumber(UInt8.one(), True);

        assert UInt16.one().finite == True;
        testFiniteUIntNumber(UInt16.one(), True);

        assert UInt32.one().finite == True;
        testFiniteUIntNumber(UInt32.one(), True);

        assert UInt64.one().finite == True;
        testFiniteUIntNumber(UInt64.one(), True);

        assert UInt128.one().finite == True;
        testFiniteUIntNumber(UInt128.one(), True);

        assert UIntN.one().finite == True;
        testFiniteUIntNumber(UIntN.one(), True);
    }

    void testFiniteNumber(Number n, Boolean expected) {
        assert n.finite == expected;
    }

    void testFiniteFPNumber(FPNumber n, Boolean expected) {
        assert n.finite == expected;
        testFiniteNumber(n, expected);
    }

    void testFiniteBinaryFPNumber(BinaryFPNumber n, Boolean expected) {
        assert n.finite == expected;
        testFiniteFPNumber(n, expected);
    }

    void testFiniteDecimalFPNumber(DecimalFPNumber n, Boolean expected) {
        assert n.finite == expected;
        testFiniteFPNumber(n, expected);
    }

    void testFiniteIntNumber(IntNumber n, Boolean expected) {
        assert n.finite == expected;
        testFiniteNumber(n, expected);
    }

    void testFiniteUIntNumber(UIntNumber n, Boolean expected) {
        assert n.finite == expected;
        testFiniteIntNumber(n, expected);
    }

    void testInfinity() {
        // Decimals
        assert Dec32.one().infinity == False;
        testInfinityDecimalFPNumber(Dec32.one(), False);
        assert Dec32.PositiveInfinity.infinity == True;
        testInfinityDecimalFPNumber(Dec32.PositiveInfinity, True);
        assert Dec32.NegativeInfinity.infinity == True;
        testInfinityDecimalFPNumber(Dec32.NegativeInfinity, True);
        assert Dec32.PositiveNaN.infinity == False;
        testInfinityDecimalFPNumber(Dec32.PositiveNaN, False);
        assert Dec32.NegativeNaN.infinity == False;
        testInfinityDecimalFPNumber(Dec32.NegativeNaN, False);

        assert Dec64.one().infinity == False;
        testInfinityDecimalFPNumber(Dec64.one(), False);
        assert Dec64.PositiveInfinity.infinity == True;
        testInfinityDecimalFPNumber(Dec64.PositiveInfinity, True);
        assert Dec64.NegativeInfinity.infinity == True;
        testInfinityDecimalFPNumber(Dec64.NegativeInfinity, True);
        assert Dec64.PositiveNaN.infinity == False;
        testInfinityDecimalFPNumber(Dec64.PositiveNaN, False);
        assert Dec64.NegativeNaN.infinity == False;
        testInfinityDecimalFPNumber(Dec64.NegativeNaN, False);

        assert Dec128.one().infinity == False;
        testInfinityDecimalFPNumber(Dec128.one(), False);
        assert Dec128.PositiveInfinity.infinity == True;
        testInfinityDecimalFPNumber(Dec128.PositiveInfinity, True);
        assert Dec128.NegativeInfinity.infinity == True;
        testInfinityDecimalFPNumber(Dec128.NegativeInfinity, True);
        assert Dec128.PositiveNaN.infinity == False;
        testInfinityDecimalFPNumber(Dec128.PositiveNaN, False);
        assert Dec128.NegativeNaN.infinity == False;
        testInfinityDecimalFPNumber(Dec128.NegativeNaN, False);

        // Floats
        assert Float16.one().infinity == False;
        testInfinityBinaryFPNumber(Float16.one(), False);
        assert Float16.PositiveInfinity.infinity == True;
        testInfinityBinaryFPNumber(Float16.PositiveInfinity, True);
        assert Float16.NegativeInfinity.infinity == True;
        testInfinityBinaryFPNumber(Float16.NegativeInfinity, True);
        assert Float16.PositiveNaN.infinity == False;
        testInfinityBinaryFPNumber(Float16.PositiveNaN, False);
        assert Float16.NegativeNaN.infinity == False;
        testInfinityBinaryFPNumber(Float16.NegativeNaN, False);

        assert Float32.one().infinity == False;
        testInfinityBinaryFPNumber(Float32.one(), False);
        assert Float32.PositiveInfinity.infinity == True;
        testInfinityBinaryFPNumber(Float32.PositiveInfinity, True);
        assert Float32.NegativeInfinity.infinity == True;
        testInfinityBinaryFPNumber(Float32.NegativeInfinity, True);
        assert Float32.PositiveNaN.infinity == False;
        testInfinityBinaryFPNumber(Float32.PositiveNaN, False);
        assert Float32.NegativeNaN.infinity == False;
        testInfinityBinaryFPNumber(Float32.NegativeNaN, False);

        assert Float64.one().infinity == False;
        testInfinityBinaryFPNumber(Float64.one(), False);
        assert Float64.PositiveInfinity.infinity == True;
        testInfinityBinaryFPNumber(Float64.PositiveInfinity, True);
        assert Float64.NegativeInfinity.infinity == True;
        testInfinityBinaryFPNumber(Float64.NegativeInfinity, True);
        assert Float64.PositiveNaN.infinity == False;
        testInfinityBinaryFPNumber(Float64.PositiveNaN, False);
        assert Float64.NegativeNaN.infinity == False;
        testInfinityBinaryFPNumber(Float64.NegativeNaN, False);

        // Nibble
        assert Nibble.one().infinity == False;
        testInfinityUIntNumber(Nibble.one(), False);

        // Integers
        assert Int8.one().infinity == False;
        testInfinityIntNumber(Int8.one(), False);

        assert Int16.one().infinity == False;
        testInfinityIntNumber(Int16.one(), False);

        assert Int32.one().infinity == False;
        testInfinityIntNumber(Int32.one(), False);

        assert Int64.one().infinity == False;
        testInfinityIntNumber(Int64.one(), False);

        assert Int128.one().infinity == False;
        testInfinityIntNumber(Int128.one(), False);

        assert IntN.one().infinity == False;
        testInfinityIntNumber(IntN.one(), False);

        // Unsigned Integers
        assert UInt8.one().infinity == False;
        testInfinityUIntNumber(UInt8.one(), False);

        assert UInt16.one().infinity == False;
        testInfinityUIntNumber(UInt16.one(), False);

        assert UInt32.one().infinity == False;
        testInfinityUIntNumber(UInt32.one(), False);

        assert UInt64.one().infinity == False;
        testInfinityUIntNumber(UInt64.one(), False);

        assert UInt128.one().infinity == False;
        testInfinityUIntNumber(UInt128.one(), False);

        assert UIntN.one().infinity == False;
        testInfinityUIntNumber(UIntN.one(), False);
    }

    void testInfinityNumber(Number n, Boolean expected) {
        assert n.infinity == expected;
    }

    void testInfinityFPNumber(FPNumber n, Boolean expected) {
        assert n.infinity == expected;
        testInfinityNumber(n, expected);
    }

    void testInfinityBinaryFPNumber(BinaryFPNumber n, Boolean expected) {
        assert n.infinity == expected;
        testInfinityFPNumber(n, expected);
    }

    void testInfinityDecimalFPNumber(DecimalFPNumber n, Boolean expected) {
        assert n.infinity == expected;
        testInfinityFPNumber(n, expected);
    }

    void testInfinityIntNumber(IntNumber n, Boolean expected) {
        assert n.infinity == expected;
        testInfinityNumber(n, expected);
    }

    void testInfinityUIntNumber(UIntNumber n, Boolean expected) {
        assert n.infinity == expected;
        testInfinityIntNumber(n, expected);
    }

    void testNaN() {
        // Decimals
        assert Dec32.one().NaN == False;
        testNaNDecimalFPNumber(Dec32.one(), False);
        assert Dec32.PositiveInfinity.NaN == False;
        testNaNDecimalFPNumber(Dec32.PositiveInfinity, False);
        assert Dec32.NegativeInfinity.NaN == False;
        testNaNDecimalFPNumber(Dec32.NegativeInfinity, False);
        assert Dec32.PositiveNaN.NaN == True;
        testNaNDecimalFPNumber(Dec32.PositiveNaN, True);
        assert Dec32.NegativeNaN.NaN == True;
        testNaNDecimalFPNumber(Dec32.NegativeNaN, True);

        assert Dec64.one().NaN == False;
        testNaNDecimalFPNumber(Dec64.one(), False);
        assert Dec64.PositiveInfinity.NaN == False;
        testNaNDecimalFPNumber(Dec64.PositiveInfinity, False);
        assert Dec64.NegativeInfinity.NaN == False;
        testNaNDecimalFPNumber(Dec64.NegativeInfinity, False);
        assert Dec64.PositiveNaN.NaN == True;
        testNaNDecimalFPNumber(Dec64.PositiveNaN, True);
        assert Dec64.NegativeNaN.NaN == True;
        testNaNDecimalFPNumber(Dec64.NegativeNaN, True);

        assert Dec128.one().NaN == False;
        testNaNDecimalFPNumber(Dec128.one(), False);
        assert Dec128.PositiveInfinity.NaN == False;
        testNaNDecimalFPNumber(Dec128.PositiveInfinity, False);
        assert Dec128.NegativeInfinity.NaN == False;
        testNaNDecimalFPNumber(Dec128.NegativeInfinity, False);
        assert Dec128.PositiveNaN.NaN == True;
        testNaNDecimalFPNumber(Dec128.PositiveNaN, True);
        assert Dec128.NegativeNaN.NaN == True;
        testNaNDecimalFPNumber(Dec128.NegativeNaN, True);

        // Floats
        assert Float16.one().NaN == False;
        testNaNBinaryFPNumber(Float16.one(), False);
        assert Float16.PositiveInfinity.NaN == False;
        testNaNBinaryFPNumber(Float16.PositiveInfinity, False);
        assert Float16.NegativeInfinity.NaN == False;
        testNaNBinaryFPNumber(Float16.NegativeInfinity, False);
        assert Float16.PositiveNaN.NaN == True;
        testNaNBinaryFPNumber(Float16.PositiveNaN, True);
        assert Float16.NegativeNaN.NaN == True;
        testNaNBinaryFPNumber(Float16.NegativeNaN, True);

        assert Float32.one().NaN == False;
        testNaNBinaryFPNumber(Float32.one(), False);
        assert Float32.PositiveInfinity.NaN == False;
        testNaNBinaryFPNumber(Float32.PositiveInfinity, False);
        assert Float32.NegativeInfinity.NaN == False;
        testNaNBinaryFPNumber(Float32.NegativeInfinity, False);
        assert Float32.PositiveNaN.NaN == True;
        testNaNBinaryFPNumber(Float32.PositiveNaN, True);
        assert Float32.NegativeNaN.NaN == True;
        testNaNBinaryFPNumber(Float32.NegativeNaN, True);

        assert Float64.one().NaN == False;
        testNaNBinaryFPNumber(Float64.one(), False);
        assert Float64.PositiveInfinity.NaN == False;
        testNaNBinaryFPNumber(Float64.PositiveInfinity, False);
        assert Float64.NegativeInfinity.NaN == False;
        testNaNBinaryFPNumber(Float64.NegativeInfinity, False);
        assert Float64.PositiveNaN.NaN == True;
        testNaNBinaryFPNumber(Float64.PositiveNaN, True);
        assert Float64.NegativeNaN.NaN == True;
        testNaNBinaryFPNumber(Float64.NegativeNaN, True);

        // Nibble
        assert Nibble.one().NaN == False;
        testNaNUIntNumber(Nibble.one(), False);

        // Integers
        assert Int8.one().NaN == False;
        testNaNIntNumber(Int8.one(), False);

        assert Int16.one().NaN == False;
        testNaNIntNumber(Int16.one(), False);

        assert Int32.one().NaN == False;
        testNaNIntNumber(Int32.one(), False);

        assert Int64.one().NaN == False;
        testNaNIntNumber(Int64.one(), False);

        assert Int128.one().NaN == False;
        testNaNIntNumber(Int128.one(), False);

        assert IntN.one().NaN == False;
        testNaNIntNumber(IntN.one(), False);

        // Unsigned Integers
        assert UInt8.one().NaN == False;
        testNaNUIntNumber(UInt8.one(), False);

        assert UInt16.one().NaN == False;
        testNaNUIntNumber(UInt16.one(), False);

        assert UInt32.one().NaN == False;
        testNaNUIntNumber(UInt32.one(), False);

        assert UInt64.one().NaN == False;
        testNaNUIntNumber(UInt64.one(), False);

        assert UInt128.one().NaN == False;
        testNaNUIntNumber(UInt128.one(), False);

        assert UIntN.one().NaN == False;
        testNaNUIntNumber(UIntN.one(), False);
    }

    void testNaNNumber(Number n, Boolean expected) {
        assert n.NaN == expected;
    }

    void testNaNFPNumber(FPNumber n, Boolean expected) {
        assert n.NaN == expected;
        testNaNNumber(n, expected);
    }

    void testNaNBinaryFPNumber(BinaryFPNumber n, Boolean expected) {
        assert n.NaN == expected;
        testNaNFPNumber(n, expected);
    }

    void testNaNDecimalFPNumber(DecimalFPNumber n, Boolean expected) {
        assert n.NaN == expected;
        testNaNFPNumber(n, expected);
    }

    void testNaNIntNumber(IntNumber n, Boolean expected) {
        assert n.NaN == expected;
        testNaNNumber(n, expected);
    }

    void testNaNUIntNumber(UIntNumber n, Boolean expected) {
        assert n.NaN == expected;
        testNaNIntNumber(n, expected);
    }

    void testMagnitude() {
// TODO these tests fail due to either call targeting the wrong type or not generating all the
// "capped" property methods
//        // Decimals
//        assert Dec32.one().magnitude == Dec32.one();
//        testMagnitudeDecimalFPNumber(Dec32.one(), Dec32.one());
//        assert Dec32.PositiveInfinity.magnitude == Dec32.PositiveInfinity;
//        testMagnitudeDecimalFPNumber(Dec32.PositiveInfinity, Dec32.PositiveInfinity);
//        assert Dec32.NegativeInfinity.magnitude == Dec32.NegativeInfinity;
//        testMagnitudeDecimalFPNumber(Dec32.NegativeInfinity, Dec32.PositiveInfinity);
//        assert Dec32.PositiveNaN.magnitude == Dec32.PositiveNaN;
//        testMagnitudeDecimalFPNumber(Dec32.PositiveNaN, Dec32.PositiveNaN);
//        assert Dec32.NegativeNaN.magnitude == Dec32.PositiveNaN;
//        testMagnitudeDecimalFPNumber(Dec32.NegativeNaN, Dec32.PositiveNaN);
//
//        assert Dec64.one().magnitude == Dec64.one();
//        testMagnitudeDecimalFPNumber(Dec64.one(), Dec64.one());
//        assert Dec64.PositiveInfinity.magnitude == Dec64.PositiveInfinity;
//        testMagnitudeDecimalFPNumber(Dec64.PositiveInfinity, Dec64.PositiveInfinity);
//        assert Dec64.NegativeInfinity.magnitude == Dec64.NegativeInfinity;
//        testMagnitudeDecimalFPNumber(Dec64.NegativeInfinity, Dec64.PositiveInfinity);
//        assert Dec64.PositiveNaN.magnitude == Dec64.PositiveNaN;
//        testMagnitudeDecimalFPNumber(Dec64.PositiveNaN, Dec64.PositiveNaN);
//        assert Dec64.NegativeNaN.magnitude == Dec64.PositiveNaN;
//        testMagnitudeDecimalFPNumber(Dec64.NegativeNaN, Dec64.PositiveNaN);
//
//        assert Dec128.one().magnitude == Dec128.one();
//        testMagnitudeDecimalFPNumber(Dec128.one(), Dec128.one());
//        assert Dec128.PositiveInfinity.magnitude == Dec128.PositiveInfinity;
//        testMagnitudeDecimalFPNumber(Dec128.PositiveInfinity, Dec128.PositiveInfinity);
//        assert Dec128.NegativeInfinity.magnitude == Dec128.NegativeInfinity;
//        testMagnitudeDecimalFPNumber(Dec128.NegativeInfinity, Dec128.PositiveInfinity);
//        assert Dec128.PositiveNaN.magnitude == Dec128.PositiveNaN;
//        testMagnitudeDecimalFPNumber(Dec128.PositiveNaN, Dec128.PositiveNaN);
//        assert Dec128.NegativeNaN.magnitude == Dec128.PositiveNaN;
//        testMagnitudeDecimalFPNumber(Dec128.NegativeNaN, Dec128.PositiveNaN);
//
//        // Floats
//        assert Float16.one().magnitude == Float16.one();
//        testMagnitudeBinaryFPNumber(Float16.one(), Float16.one());
//        assert Float16.PositiveInfinity.magnitude == Float16.PositiveInfinity;
//        testMagnitudeBinaryFPNumber(Float16.PositiveInfinity, Float16.PositiveInfinity);
//        assert Float16.NegativeInfinity.magnitude == Float16.PositiveInfinity;
//        testMagnitudeBinaryFPNumber(Float16.NegativeInfinity, Float16.PositiveInfinity);
//        assert Float16.PositiveNaN.magnitude == Float16.PositiveNaN;
//        testMagnitudeBinaryFPNumber(Float16.PositiveNaN, Float16.PositiveNaN);
//        assert Float16.NegativeNaN.magnitude == Float16.PositiveNaN;
//        testMagnitudeBinaryFPNumber(Float16.NegativeNaN, Float16.PositiveNaN);
//
//        assert Float32.one().magnitude == Float32.one();
//        testMagnitudeBinaryFPNumber(Float32.one(), Float32.one());
//        assert Float32.PositiveInfinity.magnitude == Float32.PositiveInfinity;
//        testMagnitudeBinaryFPNumber(Float32.PositiveInfinity, Float32.PositiveInfinity);
//        assert Float32.NegativeInfinity.magnitude == Float32.PositiveInfinity;
//        testMagnitudeBinaryFPNumber(Float32.NegativeInfinity, Float32.PositiveInfinity);
//        assert Float32.PositiveNaN.magnitude == Float32.PositiveNaN;
//        testMagnitudeBinaryFPNumber(Float32.PositiveNaN, Float32.PositiveNaN);
//        assert Float32.NegativeNaN.magnitude == Float32.PositiveNaN;
//        testMagnitudeBinaryFPNumber(Float32.NegativeNaN, Float32.PositiveNaN);
//
//        assert Float64.one().magnitude == Float64.one();
//        testMagnitudeBinaryFPNumber(Float64.one(), Float64.one());
//        assert Float64.PositiveInfinity.magnitude == Float64.PositiveInfinity;
//        testMagnitudeBinaryFPNumber(Float64.PositiveInfinity, Float64.PositiveInfinity);
//        assert Float64.NegativeInfinity.magnitude == Float64.PositiveInfinity;
//        testMagnitudeBinaryFPNumber(Float64.NegativeInfinity, Float64.PositiveInfinity);
//        assert Float64.PositiveNaN.magnitude == Float64.PositiveNaN;
//        testMagnitudeBinaryFPNumber(Float64.PositiveNaN, Float64.PositiveNaN);
//        assert Float64.NegativeNaN.magnitude == Float64.PositiveNaN;
//        testMagnitudeBinaryFPNumber(Float64.NegativeNaN, Float64.PositiveNaN);
//
//        // Nibble
//        assert Nibble.one().magnitude == Nibble.one();
//        testMagnitudeUIntNumber(Nibble.one(), Nibble.one());
//
//        // Integers
//        assert Int8.one().magnitude == UInt8.one();
//        testMagnitudeIntNumber(Int8.one(), UInt8.one());
//
//        assert Int16.one().magnitude == UInt16.one();
//        testMagnitudeIntNumber(Int16.one(), UInt16.one());
//
//        assert Int32.one().magnitude == UInt32.one();
//        testMagnitudeIntNumber(Int32.one(), UInt32.one());
//
//        assert Int64.one().magnitude == UInt64.one();
//        testMagnitudeIntNumber(Int64.one(), UInt64.one());
//
//        assert Int128.one().magnitude == UInt128.one();
//        testMagnitudeIntNumber(Int128.one(), UInt128.one());
//
//        assert IntN.one().magnitude == UIntN.one();
//        testMagnitudeIntNumber(IntN.one(), UIntN.one());
//
//        // Unsigned Integers
//        assert UInt8.one().magnitude == UInt8.one();
//        testMagnitudeUIntNumber(UInt8.one(), UInt8.one());
//
//        assert UInt16.one().magnitude == UInt16.one();
//        testMagnitudeUIntNumber(UInt16.one(), UInt16.one());
//
//        assert UInt32.one().magnitude == UInt32.one();
//        testMagnitudeUIntNumber(UInt32.one(), UInt32.one());
//
//        assert UInt64.one().magnitude == UInt64.one();
//        testMagnitudeUIntNumber(UInt64.one(), UInt64.one());
//
//        assert UInt128.one().magnitude == UInt128.one();
//        testMagnitudeUIntNumber(UInt128.one(), UInt128.one());
//
//        assert UIntN.one().magnitude == UIntN.one();
//        testMagnitudeUIntNumber(UIntN.one(), UIntN.one());
//    }
//
//    void testMagnitudeNumber(Number n, Number expected) {
//        assert n.magnitude == expected;
//    }
//
//    void testMagnitudeFPNumber(FPNumber n, FPNumber expected) {
//        assert n.magnitude == expected;
//        testMagnitudeNumber(n, expected);
//    }
//
//    void testMagnitudeBinaryFPNumber(BinaryFPNumber n, BinaryFPNumber expected) {
//        assert n.magnitude == expected;
//        testMagnitudeFPNumber(n, expected);
//    }
//
//    void testMagnitudeDecimalFPNumber(DecimalFPNumber n, DecimalFPNumber expected) {
//        assert n.magnitude == expected;
//        testMagnitudeFPNumber(n, expected);
//    }
//
//    void testMagnitudeIntNumber(IntNumber n, UIntNumber expected) {
//        assert n.magnitude == expected;
//        testMagnitudeNumber(n, expected);
//    }
//
//    void testMagnitudeUIntNumber(UIntNumber n, UIntNumber expected) {
//        assert n.magnitude == expected;
//        testMagnitudeIntNumber(n, expected);
    }
}