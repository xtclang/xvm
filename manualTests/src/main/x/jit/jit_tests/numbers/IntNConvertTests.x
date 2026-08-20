/**
 * Tests for fixed-length integer conversions to IntN.
 */
class IntNConvertTests {

    void run() {
        testBit();
        testNibble();
        testSignedIntegers();
        testUnsignedIntegers();
    }

    void testBit() {
        Bit bit = 1;
        assert bit.toIntN() == 1;
    }

    void testNibble() {
        Nibble nibble = Nibble.MaxValue;
        assert nibble.toIntN() == 15;
    }

    void testSignedIntegers() {
        Int8 int8 = -2;
        assert int8.toIntN() == -2;
        Int16 int16 = -2;
        assert int16.toIntN() == -2;
        Int32 int32 = -2;
        assert int32.toIntN() == -2;
        Int64 int64 = -2;
        assert int64.toIntN() == -2;

        Int128 int128 = -18446744073709551617;
        IntN expected = -18446744073709551617;
        assert int128.toIntN() == expected;
    }

    void testUnsignedIntegers() {
        UInt8 uint8 = UInt8.MaxValue;
        assert uint8.toIntN() == 255;

        UInt16 uint16 = UInt16.MaxValue;
        assert uint16.toIntN() == 65535;

        UInt32 uint32 = UInt32.MaxValue;
        assert uint32.toIntN() == 4294967295;

        UInt64 uint64 = UInt64.MaxValue;
        IntN uint64Expected = 18446744073709551615;
        assert uint64.toIntN() == uint64Expected;

        UInt128 uint128 = UInt128.MaxValue;
        IntN uint128Expected = 340282366920938463463374607431768211455;
        assert uint128.toIntN() == uint128Expected;
    }
}
