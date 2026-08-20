/**
 * Tests for fixed-length integer conversions to UIntN.
 */
class UIntNConvertTests {

    void run() {
        testBit();
        testNibble();
        testSignedIntegers();
        testUnsignedIntegers();
    }

    void testBit() {
        Bit bit = 1;
        assert bit.toUIntN() == 1;
    }

    void testNibble() {
        Nibble nibble = Nibble.MaxValue;
        assert nibble.toUIntN() == 15;
    }

    void testSignedIntegers() {
        Int128 positive = 18446744073709551617;
        UIntN expected = 18446744073709551617;
        assert positive.toUIntN() == expected;

        Int128 negative = -18446744073709551617;
        try {
            negative.toUIntN();
            assert as "Expected OutOfBounds to be thrown";
        } catch (OutOfBounds e) {
            // expected
        }
    }

    void testUnsignedIntegers() {
        UInt8 uint8 = UInt8.MaxValue;
        assert uint8.toUIntN() == 255;

        UInt16 uint16 = UInt16.MaxValue;
        assert uint16.toUIntN() == 65535;

        UInt32 uint32 = UInt32.MaxValue;
        assert uint32.toUIntN() == 4294967295;

        UInt64 uint64 = UInt64.MaxValue;
        UIntN uint64Expected = 18446744073709551615;
        assert uint64.toUIntN() == uint64Expected;

        UInt128 uint128 = UInt128.MaxValue;
        UIntN uint128Expected = 340282366920938463463374607431768211455;
        assert uint128.toUIntN() == uint128Expected;
    }
}
