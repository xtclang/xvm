import ecstasy.numbers.BinaryFPNumber;
import ecstasy.numbers.DecimalFPNumber;

class FPNumberTests {

    void run() {
        testExponentBitLength();
        testPrecision();
        testRadix();
        testSignificandBitLength();
        testEMax();
        testEMin();
        testBias();
    }

    void test(Number n) {
        Int32 i2 = n.toInt32();
        assert i2 == 100;
    }

    void testExponentBitLength() {
        assert Dec32.one().exponentBitLength == 6;
        testExponentBitLengthDecimalFPNumber(Dec32.one(), 6);
        assert Dec64.one().exponentBitLength == 8;
        testExponentBitLengthDecimalFPNumber(Dec64.one(), 8);
        assert Dec128.one().exponentBitLength == 12;
        testExponentBitLengthDecimalFPNumber(Dec128.one(), 12);

        assert Float16.one().exponentBitLength == 5;
        testExponentBitLengthBinaryFPNumber(Float16.one(), 5);
        assert Float32.one().exponentBitLength == 8;
        testExponentBitLengthBinaryFPNumber(Float32.one(), 8);
        assert Float64.one().exponentBitLength == 11;
        testExponentBitLengthBinaryFPNumber(Float64.one(), 11);
    }

    void testExponentBitLengthFPNumber(FPNumber n, Int expected) {
        assert n.exponentBitLength == expected;
    }

    void testExponentBitLengthBinaryFPNumber(BinaryFPNumber n, Int expected) {
        assert n.exponentBitLength == expected;
        testExponentBitLengthFPNumber(n, expected);
    }

    void testExponentBitLengthDecimalFPNumber(DecimalFPNumber n, Int expected) {
        assert n.exponentBitLength == expected;
        testExponentBitLengthFPNumber(n, expected);
    }

    void testPrecision() {
        assert Dec32.one().precision == 26;
        testPrecisionDecimalFPNumber(Dec32.one(), 26);
        assert Dec64.one().precision == 56;
        testPrecisionDecimalFPNumber(Dec64.one(), 56);
        assert Dec128.one().precision == 116;
        testPrecisionDecimalFPNumber(Dec128.one(), 116);

        assert Float16.one().precision == 11;
        testPrecisionBinaryFPNumber(Float16.one(), 11);
        assert Float32.one().precision == 24;
        testPrecisionBinaryFPNumber(Float32.one(), 24);
        assert Float64.one().precision == 53;
        testPrecisionBinaryFPNumber(Float64.one(), 53);
    }

    void testPrecisionFPNumber(FPNumber n, Int expected) {
        assert n.precision == expected;
    }

    void testPrecisionBinaryFPNumber(BinaryFPNumber n, Int expected) {
        assert n.precision == expected;
        testPrecisionFPNumber(n, expected);
    }

    void testPrecisionDecimalFPNumber(DecimalFPNumber n, Int expected) {
        assert n.precision == expected;
        testPrecisionFPNumber(n, expected);
    }

    void testRadix() {
        assert Dec32.one().radix == 10;
        testRadixDecimalFPNumber(Dec32.one(), 10);
        assert Dec64.one().radix == 10;
        testRadixDecimalFPNumber(Dec64.one(), 10);
        assert Dec128.one().radix == 10;
        testRadixDecimalFPNumber(Dec128.one(), 10);

        assert Float16.one().radix == 2;
        testRadixBinaryFPNumber(Float16.one(), 2);
        assert Float32.one().radix == 2;
        testRadixBinaryFPNumber(Float32.one(), 2);
        assert Float64.one().radix == 2;
        testRadixBinaryFPNumber(Float64.one(), 2);
    }

    void testRadixFPNumber(FPNumber n, Int expected) {
        assert n.radix == expected;
    }

    void testRadixBinaryFPNumber(BinaryFPNumber n, Int expected) {
        assert n.radix == expected;
        testRadixFPNumber(n, expected);
    }

    void testRadixDecimalFPNumber(DecimalFPNumber n, Int expected) {
        assert n.radix == expected;
        testRadixFPNumber(n, expected);
    }

    void testSignificandBitLength() {
        assert Dec32.one().significandBitLength == 25;
        testSignificandBitLengthDecimalFPNumber(Dec32.one(), 25);
        assert Dec64.one().significandBitLength == 55;
        testSignificandBitLengthDecimalFPNumber(Dec64.one(), 55);
        assert Dec128.one().significandBitLength == 115;
        testSignificandBitLengthDecimalFPNumber(Dec128.one(), 115);

        assert Float16.one().significandBitLength == 10;
        testSignificandBitLengthBinaryFPNumber(Float16.one(), 10);
        assert Float32.one().significandBitLength == 23;
        testSignificandBitLengthBinaryFPNumber(Float32.one(), 23);
        assert Float64.one().significandBitLength == 52;
        testSignificandBitLengthBinaryFPNumber(Float64.one(), 52);
    }

    void testSignificandBitLengthFPNumber(FPNumber n, Int expected) {
        assert n.significandBitLength == expected;
    }

    void testSignificandBitLengthBinaryFPNumber(BinaryFPNumber n, Int expected) {
        assert n.significandBitLength == expected;
        testSignificandBitLengthFPNumber(n, expected);
    }

    void testSignificandBitLengthDecimalFPNumber(DecimalFPNumber n, Int expected) {
        assert n.significandBitLength == expected;
        testSignificandBitLengthFPNumber(n, expected);
    }

    void testEMax() {
        assert Dec32.one().emax == 96;
        testEMaxDecimalFPNumber(Dec32.one(), 96);
        assert Dec64.one().emax == 384;
        testEMaxDecimalFPNumber(Dec64.one(), 384);
        assert Dec128.one().emax == 6144;
        testEMaxDecimalFPNumber(Dec128.one(), 6144);

        assert Float16.one().emax == 15;
        testEMaxBinaryFPNumber(Float16.one(), 15);
        assert Float32.one().emax == 127;
        testEMaxBinaryFPNumber(Float32.one(), 127);
        assert Float64.one().emax == 1023;
        testEMaxBinaryFPNumber(Float64.one(), 1023);
    }

    void testEMaxFPNumber(FPNumber n, Int expected) {
// TODO Fails due to missing "capped" property accessor
//        assert n.emax.toInt() == expected;
    }

    void testEMaxBinaryFPNumber(BinaryFPNumber n, Int expected) {
// TODO Fails due to missing "capped" property accessor
//        assert n.emax.toInt() == expected;
        testEMaxFPNumber(n, expected);
    }

    void testEMaxDecimalFPNumber(DecimalFPNumber n, Int expected) {
// TODO Fails due to missing "capped" property accessor
//        assert n.emax.toInt() == expected;
        testEMaxFPNumber(n, expected);
    }

    void testEMin() {
        assert Dec32.one().emin == -95;
        testEMinDecimalFPNumber(Dec32.one(), -95);
        assert Dec64.one().emin == -383;
        testEMinDecimalFPNumber(Dec64.one(), -383);
        assert Dec128.one().emin == -6143;
        testEMinDecimalFPNumber(Dec128.one(), -6144);

        assert Float16.one().emin == -14;
        testEMinBinaryFPNumber(Float16.one(), -14);
        assert Float32.one().emin == -126;
        testEMinBinaryFPNumber(Float32.one(), -126);
        assert Float64.one().emin == -1022;
        testEMinBinaryFPNumber(Float64.one(), -1022);
    }

    void testEMinFPNumber(FPNumber n, Int expected) {
// TODO Fails due to missing "capped" property accessor
//        assert n.emin.toInt() == expected;
    }

    void testEMinBinaryFPNumber(BinaryFPNumber n, Int expected) {
// TODO Fails due to missing "capped" property accessor
//        assert n.emin.toInt() == expected;
        testEMinFPNumber(n, expected);
    }

    void testEMinDecimalFPNumber(DecimalFPNumber n, Int expected) {
// TODO Fails due to missing "capped" property accessor
//        assert n.emin.toInt() == expected;
        testEMinFPNumber(n, expected);
    }

    void testBias() {
        assert Dec32.one().bias == 101;
        testBiasDecimalFPNumber(Dec32.one(), 101);
        assert Dec64.one().bias == 398;
        testBiasDecimalFPNumber(Dec64.one(), 398);
        assert Dec128.one().bias == 6176;
        testBiasDecimalFPNumber(Dec128.one(), 6176);

        assert Float16.one().bias == 15;
        testBiasBinaryFPNumber(Float16.one(), 15);
        assert Float32.one().bias == 127;
        testBiasBinaryFPNumber(Float32.one(), 127);
        assert Float64.one().bias == 1023;
        testBiasBinaryFPNumber(Float64.one(), 1023);
    }

    void testBiasFPNumber(FPNumber n, Int expected) {
// TODO Fails due to missing "capped" property accessor
//        assert n.bias.toInt() == expected;
    }

    void testBiasBinaryFPNumber(BinaryFPNumber n, Int expected) {
// TODO Fails due to missing "capped" property accessor
//        assert n.bias.toInt() == expected;
        testBiasFPNumber(n, expected);
    }

    void testBiasDecimalFPNumber(DecimalFPNumber n, Int expected) {
// TODO Fails due to missing "capped" property accessor
//        assert n.bias.toInt() == expected;
        testBiasFPNumber(n, expected);
    }

}
