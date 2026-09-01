import ecstasy.numbers.BinaryFPNumber;
import ecstasy.numbers.DecimalFPNumber;
import ecstasy.numbers.UIntNumber;

/**
 * Tests for converting Bit arrays to Numbers and vice-versa.
 */
class BitsToNumberConversionTests {

    @Inject Console console;

    Byte[] Empty = [];

    void run() {
        testInt8();
        testInt16();
        testInt32();
        testInt64();
        testInt128();
        testNibble();
        testUInt8();
        testUInt16();
        testUInt32();
        testUInt64();
        testUInt128();
        testIntN();
        testUIntN();

        testDec32();
        testDec64();
        testDec128();
    }

    void testInt8() {
        testInt8(new Int8(#4F), 0x4F);
        testInt8(new Int8(#00), 0);
        testInt8(new Int8(#7F), Int8.MaxValue);
        testInt8(new Int8(#80), Int8.MinValue);

        testInt8InvalidBits(Empty); // empty (too short)
        testInt8InvalidBits(#FFFF); // too long

        testBitsToInt8([0,1,1,0,1,1,0,0], 0x6C);
        testBitsToInt8([0], 0);
        testBitsToInt8([0,1,1,0], 0x06);
        testBitsToInt8([1,1,1,0], -2);   // 0xE sign extended to 0xFE == -2

        // valid 8-bits
        testBitsToInt8WithBoundsCheck([0,1,1,0,1,1,0,0], 0x6C);
        // valid less than 8-bits
        testBitsToInt8WithBoundsCheck([0,1,1,0,], 0x06);
        // extra bits are all zero
        testBitsToInt8WithBoundsCheck([0,0,0,0,0,1,1,0,1,1,0,0], 0x6C);
        // extra bits all 1, 0xFFE == -2
        testBitsToInt8WithBoundsCheck([1,1,1,1,1,1,1,1,1,1,1,0], -2);
        // bounds check False
        testBitsToInt8WithBoundsCheck([1,0,1,1,0,0,0,0,0,0,0,0], 0);
        // bounds check True
        testBitsToInt8WithBoundsCheck([1,0,1,1,0,0,0,0,0,0,0,0]);

    }

    void testInt8(Int8 n1, Int8 expected) {
        assert n1 == expected;
        Int8  n2 = new Int8(n1.bits);
        assert n2 == n1;
        Int8 n3 = n1.bits.toInt8();
        assert n3 == n1;
    }

    void testInt8InvalidBits(Byte[] bytes) {
        try {
            new Int8(bytes);
            assert as "expected IllegalState to be thrown";
        } catch (IllegalState _) {
            // expected
        }
    }

    void testBitsToInt8(Bit[] bits, Int8 expected) {
        Int8 n1 = bits.toInt8();
        assert n1 == expected;
    }

    void testBitsToInt8WithBoundsCheck(Bit[] bits, Int8? expected = Null) {
        if (expected.is(Int8)) {
            Int8 n1 = bits.toInt8(False);
            assert n1 == expected;
        } else {
            try {
                bits.toInt8(True);
                assert as "expected OutOfBounds to be thrown";
            } catch (OutOfBounds _) {
                // expected
            }
        }
    }

    void testInt16() {
        testInt16(new Int16(#4F6C), 0x4F6C);
        testInt16(new Int16(#0000), 0);
        testInt16(new Int16(#7FFF), Int16.MaxValue);
        testInt16(new Int16(#8000), Int16.MinValue);

        testInt16InvalidBits(Empty);     // empty (too short)
        testInt16InvalidBits(#FF);       // too short
        testInt16InvalidBits(#FFFFFF);   // too long

        testBitsToInt16([0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        testBitsToInt16([0], 0);
        testBitsToInt16([0,1,1,0], 0x06);
        testBitsToInt16([1,1,1,0], -2);   // 0xE sign extended to 0xFFFE == -2

        // valid 16-bits
        testBitsToInt16WithBoundsCheck([0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        // valid less than 16-bits
        testBitsToInt16WithBoundsCheck([0,1,1,0], 0x06);
        // extra bits are all zero
        testBitsToInt16WithBoundsCheck([0,0,0,0,0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        // extra bits all 1, 0xFFFFE == -2
        testBitsToInt16WithBoundsCheck([1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0], -2);
        // bounds check False
        testBitsToInt16WithBoundsCheck([1,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0], 0);
        // bounds check True
        testBitsToInt16WithBoundsCheck([1,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]);
    }

    void testInt16(Int16 n1, Int16 expected) {
        assert n1 == expected;
        Int16 n2 = new Int16(n1.bits);
        assert n2 == n1;
        Int16 n3 = n1.bits.toInt16();
        assert n3 == n1;
    }

    void testInt16InvalidBits(Byte[] bytes) {
        try {
            new Int16(bytes);
            assert as "expected IllegalState to be thrown";
        } catch (IllegalState _) {
            // expected
        }
    }

    void testBitsToInt16(Bit[] bits, Int16 expected) {
        Int16 n1 = bits.toInt16();
        assert n1 == expected;
    }

    void testBitsToInt16WithBoundsCheck(Bit[] bits, Int16? expected = Null) {
        if (expected.is(Int16)) {
            Int16 n1 = bits.toInt16(False);
            assert n1 == expected;
        } else {
            try {
                bits.toInt16(True);
                assert as "expected OutOfBounds to be thrown";
            } catch (OutOfBounds _) {
                // expected
            }
        }
    }

    void testInt32() {
        testInt32(new Int32(#4F6C0102), 0x4F6C0102);
        testInt32(new Int32(#00000000), 0);
        testInt32(new Int32(#7FFFFFFF), Int32.MaxValue);
        testInt32(new Int32(#80000000), Int32.MinValue);

        testInt32InvalidBits(Empty);         // empty (too short)
        testInt32InvalidBits(#FFFFFF);       // too short (3 bytes)
        testInt32InvalidBits(#FFFFFFFFFF);   // too long (5 bytes)

        testBitsToInt32([0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        testBitsToInt32([0], 0);
        testBitsToInt32([0,1,1,0], 0x06);
        testBitsToInt32([1,1,1,0], -2);   // 0xE sign extended to 0xFFFFFFFE == -2

        // valid 16-bits in Int32
        testBitsToInt32WithBoundsCheck([0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        // valid less than 32-bits
        testBitsToInt32WithBoundsCheck([0,1,1,0], 0x06);
        // extra bits are all zero
        testBitsToInt32WithBoundsCheck([0,0,0,0,0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        // extra bits all 1, 0xFFFFFFE == -2
        testBitsToInt32WithBoundsCheck([1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0], -2);
        // bounds check False
        testBitsToInt32WithBoundsCheck([1,0,1,1,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0], 0);
        // bounds check True
        testBitsToInt32WithBoundsCheck([1,0,1,1,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0]);
    }

    void testInt32(Int32 n1, Int32 expected) {
        assert n1 == expected;
        Int32 n2 = new Int32(n1.bits);
        assert n2 == n1;
        Int32 n3 = n1.bits.toInt32();
        assert n3 == n1;
    }

    void testInt32InvalidBits(Byte[] bytes) {
        try {
            new Int32(bytes);
            assert as "expected IllegalState to be thrown";
        } catch (IllegalState _) {
            // expected
        }
    }

    void testBitsToInt32(Bit[] bits, Int32 expected) {
        Int32 n1 = bits.toInt32();
        assert n1 == expected;
    }

    void testBitsToInt32WithBoundsCheck(Bit[] bits, Int32? expected = Null) {
        if (expected.is(Int32)) {
            Int32 n1 = bits.toInt32(False);
            assert n1 == expected;
        } else {
            try {
                bits.toInt32(True);
                assert as "expected OutOfBounds to be thrown";
            } catch (OutOfBounds _) {
                // expected
            }
        }
    }

    void testInt64() {
        testInt64(new Int64(#4F6C010203040506), 0x4F6C010203040506);
        testInt64(new Int64(#0000000000000000), 0);
        testInt64(new Int64(#7FFFFFFFFFFFFFFF), Int64.MaxValue);
        testInt64(new Int64(#8000000000000000), Int64.MinValue);

        testInt64InvalidBits(Empty);                 // empty (too short)
        testInt64InvalidBits(#FFFFFFFFFFFFFF);       // too short (7 bytes)
        testInt64InvalidBits(#FFFFFFFFFFFFFFFFFF);   // too long (9 bytes)

        testBitsToInt64([0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        testBitsToInt64([0], 0);
        testBitsToInt64([0,1,1,0], 0x06);
        testBitsToInt64([1,1,1,0], -2);   // 0xE sign extended to 0xFFFFFFFFFFFFFFFE == -2

        // valid 16-bits in Int64
        testBitsToInt64WithBoundsCheck([0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        // valid less than 64-bits
        testBitsToInt64WithBoundsCheck([0,1,1,0], 0x06);
        // extra bits are all zero
        testBitsToInt64WithBoundsCheck([0,0,0,0,0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        // extra bits all 1, 0xFFFFFFE == -2
        testBitsToInt64WithBoundsCheck([1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0], -2);
        // bounds check False (68 bits)
        testBitsToInt64WithBoundsCheck([1,0,1,1,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0], 0);
        // bounds check True (68 bits)
        testBitsToInt64WithBoundsCheck([1,0,1,1,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0]);
    }

    void testInt64(Int64 n1, Int64 expected) {
        assert n1 == expected;
        Int64 n2 = new Int64(n1.bits);
        assert n2 == n1;
        Int64 n3 = n1.bits.toInt64();
        assert n3 == n1;
    }

    void testInt64InvalidBits(Byte[] bytes) {
        try {
            new Int64(bytes);
            assert as "expected IllegalState to be thrown";
        } catch (IllegalState _) {
            // expected
        }
    }

    void testBitsToInt64(Bit[] bits, Int64 expected) {
        Int64 n1 = bits.toInt64();
        assert n1 == expected;
    }

    void testBitsToInt64WithBoundsCheck(Bit[] bits, Int64? expected = Null) {
        if (expected.is(Int64)) {
            Int64 n1 = bits.toInt64(False);
            assert n1 == expected;
        } else {
            try {
                bits.toInt64(True);
                assert as "expected OutOfBounds to be thrown";
            } catch (OutOfBounds _) {
                // expected
            }
        }
    }

    void testInt128() {
        testInt128(new Int128(#4F6C0102030405060708090A0B0C0D0E), 0x4F6C0102030405060708090A0B0C0D0E);
        testInt128(new Int128(#00000000000000000000000000000000), 0);
        testInt128(new Int128(#7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF), Int128.MaxValue);
        testInt128(new Int128(#80000000000000000000000000000000), Int128.MinValue);

        testInt128InvalidBits(Empty);                                         // empty (too short)
        testInt128InvalidBits(#FFFFFFFFFFFFFFFFFFFFFFFFFFFFFF);               // too short (15 bytes)
        testInt128InvalidBits(#FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF);           // too long (17 bytes)

        testBitsToInt128([0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        testBitsToInt128([0], 0);
        testBitsToInt128([0,1,1,0], 0x06);
        testBitsToInt128([1,1,1,0], -2);   // 0xE sign extended to -2

        // valid 16-bits in Int128
        testBitsToInt128WithBoundsCheck([0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        // valid less than 128-bits
        testBitsToInt128WithBoundsCheck([0,1,1,0], 0x06);
        // extra bits are all zero
        testBitsToInt128WithBoundsCheck([0,0,0,0,0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        // extra bits all 1, 0xFFFFFFE == -2
        testBitsToInt128WithBoundsCheck([1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0], -2);
        // bounds check False (132 bits: 4 bits + 128 zeros)
        testBitsToInt128WithBoundsCheck([1,0,1,1,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0], 0);
        // bounds check True (132 bits: 4 bits + 128 zeros)
        testBitsToInt128WithBoundsCheck([1,0,1,1,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0,0,0,0,0,
                                        0,0,0,0]);
    }

    void testInt128(Int128 n1, Int128 expected) {
        assert n1 == expected;
        Int128 n2 = new Int128(n1.bits);
        assert n2 == n1;
        Int128 n3 = n1.bits.toInt128();
        assert n3 == n1;
    }

    void testInt128InvalidBits(Byte[] bytes) {
        try {
            new Int128(bytes);
            assert as "expected IllegalState to be thrown";
        } catch (IllegalState _) {
            // expected
        }
    }

    void testBitsToInt128(Bit[] bits, Int128 expected) {
        Int128 n1 = bits.toInt128();
        assert n1 == expected;
    }

    void testBitsToInt128WithBoundsCheck(Bit[] bits, Int128? expected = Null) {
        if (expected.is(Int128)) {
            Int128 n1 = bits.toInt128(False);
            assert n1 == expected;
        } else {
            try {
                bits.toInt128(True);
                assert as "expected OutOfBounds to be thrown";
            } catch (OutOfBounds _) {
                // expected
            }
        }
    }

    void testNibble() {
        testNibble(new Nibble(#4), 0x4);
        testNibble(new Nibble(#0), 0);
        testNibble(new Nibble(#7), 7);
        testNibble(new Nibble(#F), Nibble.MaxValue);

        testNibbleInvalidBits(Empty); // empty (too short)
        testNibbleInvalidBits(#FF); // too long

        testBitsToNibble([0,1,1,0], 0x6);
        testBitsToNibble([0], 0);
        testBitsToNibble([0,1,1,0], 0x6);
        testBitsToNibble([1,1,0], 0x6); // unsigned: leading bits are 0-extended -> 0x6

        // valid 8-bits
        testBitsToNibbleWithBoundsCheck([0,1,1,0], 0x6);
        // valid less than 8-bits
        testBitsToNibbleWithBoundsCheck([0,1,1,0], 0x6);
        // extra bits are all zero
        testBitsToNibbleWithBoundsCheck([0,0,0,0,0,1,1,0], 0x6);
        // bounds check False (extra 1-bits discarded)
        testBitsToNibbleWithBoundsCheck([1,0,1,1,0,0,0,0], 0);
        // bounds check True (extra 1-bits throw OutOfBounds)
        testBitsToNibbleWithBoundsCheck([1,0,1,1,0,0,0]);
    }

    void testNibble(Nibble n1, Nibble expected) {
        assert n1 == expected;
        Nibble n2 = new Nibble(n1.bits);
        assert n2 == n1;
        Nibble n3 = n1.bits.toNibble();
        assert n3 == n1;
    }

    void testNibbleInvalidBits(Byte[] bytes) {
        try {
            new Nibble(bytes);
            assert as "expected IllegalState to be thrown";
        } catch (IllegalState _) {
            // expected
        }
    }

    void testBitsToNibble(Bit[] bits, Nibble expected) {
        Nibble n1 = bits.toNibble();
        assert n1 == expected;
    }

    void testBitsToNibbleWithBoundsCheck(Bit[] bits, Nibble? expected = Null) {
        if (expected.is(Nibble)) {
            Nibble n1 = bits.toNibble(False);
            assert n1 == expected;
        } else {
            try {
                bits.toNibble(True);
                assert as "expected OutOfBounds to be thrown";
            } catch (OutOfBounds _) {
                // expected
            }
        }
    }

    void testUInt8() {
        testUInt8(new UInt8(#4F), 0x4F);
        testUInt8(new UInt8(#00), 0);
        testUInt8(new UInt8(#7F), 127);
        testUInt8(new UInt8(#FF), UInt8.MaxValue);

        testUInt8InvalidBits(Empty); // empty (too short)
        testUInt8InvalidBits(#FFFF); // too long

        testBitsToUInt8([0,1,1,0,1,1,0,0], 0x6C);
        testBitsToUInt8([0], 0);
        testBitsToUInt8([0,1,1,0], 0x06);
        testBitsToUInt8([1,1,1,0], 0x0E); // unsigned: leading bits are 0-extended -> 0x0E

        // valid 8-bits
        testBitsToUInt8WithBoundsCheck([0,1,1,0,1,1,0,0], 0x6C);
        // valid less than 8-bits
        testBitsToUInt8WithBoundsCheck([0,1,1,0], 0x06);
        // extra bits are all zero
        testBitsToUInt8WithBoundsCheck([0,0,0,0,0,1,1,0,1,1,0,0], 0x6C);
        // bounds check False (extra 1-bits discarded)
        testBitsToUInt8WithBoundsCheck([1,0,1,1,0,0,0,0,0,0,0,0], 0);
        // bounds check True (extra 1-bits throw OutOfBounds)
        testBitsToUInt8WithBoundsCheck([1,0,1,1,0,0,0,0,0,0,0,0]);
    }

    void testUInt8(UInt8 n1, UInt8 expected) {
        assert n1 == expected;
        UInt8 n2 = new UInt8(n1.bits);
        assert n2 == n1;
        UInt8 n3 = n1.bits.toUInt8();
        assert n3 == n1;
    }

    void testUInt8InvalidBits(Byte[] bytes) {
        try {
            new UInt8(bytes);
            assert as "expected IllegalState to be thrown";
        } catch (IllegalState _) {
            // expected
        }
    }

    void testBitsToUInt8(Bit[] bits, UInt8 expected) {
        UInt8 n1 = bits.toUInt8();
        assert n1 == expected;
    }

    void testBitsToUInt8WithBoundsCheck(Bit[] bits, UInt8? expected = Null) {
        if (expected.is(UInt8)) {
            UInt8 n1 = bits.toUInt8(False);
            assert n1 == expected;
        } else {
            try {
                bits.toUInt8(True);
                assert as "expected OutOfBounds to be thrown";
            } catch (OutOfBounds _) {
                // expected
            }
        }
    }

    void testUInt16() {
        testUInt16(new UInt16(#4F6C), 0x4F6C);
        testUInt16(new UInt16(#0000), 0);
        testUInt16(new UInt16(#7FFF), 0x7FFF);
        testUInt16(new UInt16(#FFFF), UInt16.MaxValue);

        testUInt16InvalidBits(Empty);     // empty (too short)
        testUInt16InvalidBits(#FF);       // too short
        testUInt16InvalidBits(#FFFFFF);   // too long

        testBitsToUInt16([0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        testBitsToUInt16([0], 0);
        testBitsToUInt16([0,1,1,0], 0x06);
        testBitsToUInt16([1,1,1,0], 0x0E); // unsigned: 0-extended to 0x000E

        // valid 16-bits
        testBitsToUInt16WithBoundsCheck([0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        // valid less than 16-bits
        testBitsToUInt16WithBoundsCheck([0,1,1,0], 0x06);
        // extra bits are all zero
        testBitsToUInt16WithBoundsCheck([0,0,0,0,0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        // bounds check False
        testBitsToUInt16WithBoundsCheck([1,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0], 0);
        // bounds check True
        testBitsToUInt16WithBoundsCheck([1,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]);
    }

    void testUInt16(UInt16 n1, UInt16 expected) {
        assert n1 == expected;
        UInt16 n2 = new UInt16(n1.bits);
        assert n2 == n1;
        UInt16 n3 = n1.bits.toUInt16();
        assert n3 == n1;
    }

    void testUInt16InvalidBits(Byte[] bytes) {
        try {
            new UInt16(bytes);
            assert as "expected IllegalState to be thrown";
        } catch (IllegalState _) {
            // expected
        }
    }

    void testBitsToUInt16(Bit[] bits, UInt16 expected) {
        UInt16 n1 = bits.toUInt16();
        assert n1 == expected;
    }

    void testBitsToUInt16WithBoundsCheck(Bit[] bits, UInt16? expected = Null) {
        if (expected.is(UInt16)) {
            UInt16 n1 = bits.toUInt16(False);
            assert n1 == expected;
        } else {
            try {
                bits.toUInt16(True);
                assert as "expected OutOfBounds to be thrown";
            } catch (OutOfBounds _) {
                // expected
            }
        }
    }

    void testUInt32() {
        testUInt32(new UInt32(#4F6C0102), 0x4F6C0102);
        testUInt32(new UInt32(#00000000), 0);
        testUInt32(new UInt32(#7FFFFFFF), 0x7FFFFFFF);
        testUInt32(new UInt32(#FFFFFFFF), UInt32.MaxValue);

        testUInt32InvalidBits(Empty);         // empty (too short)
        testUInt32InvalidBits(#FFFFFF);       // too short (3 bytes)
        testUInt32InvalidBits(#FFFFFFFFFF);   // too long (5 bytes)

        testBitsToUInt32([0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        testBitsToUInt32([0], 0);
        testBitsToUInt32([0,1,1,0], 0x06);
        testBitsToUInt32([1,1,1,0], 0x0E); // unsigned: 0-extended to 0x0000000E

        // valid 16-bits in UInt32
        testBitsToUInt32WithBoundsCheck([0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        // valid less than 32-bits
        testBitsToUInt32WithBoundsCheck([0,1,1,0], 0x06);
        // extra bits are all zero
        testBitsToUInt32WithBoundsCheck([0,0,0,0,0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        // bounds check False
        testBitsToUInt32WithBoundsCheck([1,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,
                                         0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                         0,0,0,0], 0);
        // bounds check True
        testBitsToUInt32WithBoundsCheck([1,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,
                                         0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                         0,0,0,0]);
    }

    void testUInt32(UInt32 n1, UInt32 expected) {
        assert n1 == expected;
        UInt32 n2 = new UInt32(n1.bits);
        assert n2 == n1;
        UInt32 n3 = n1.bits.toUInt32();
        assert n3 == n1;
    }

    void testUInt32InvalidBits(Byte[] bytes) {
        try {
            new UInt32(bytes);
            assert as "expected IllegalState to be thrown";
        } catch (IllegalState _) {
            // expected
        }
    }

    void testBitsToUInt32(Bit[] bits, UInt32 expected) {
        UInt32 n1 = bits.toUInt32();
        assert n1 == expected;
    }

    void testBitsToUInt32WithBoundsCheck(Bit[] bits, UInt32? expected = Null) {
        if (expected.is(UInt32)) {
            UInt32 n1 = bits.toUInt32(False);
            assert n1 == expected;
        } else {
            try {
                bits.toUInt32(True);
                assert as "expected OutOfBounds to be thrown";
            } catch (OutOfBounds _) {
                // expected
            }
        }
    }

    void testUInt64() {
        testUInt64(new UInt64(#4F6C010203040506), 0x4F6C010203040506);
        testUInt64(new UInt64(#0000000000000000), 0);
        testUInt64(new UInt64(#7FFFFFFFFFFFFFFF), 0x7FFFFFFFFFFFFFFF);
        testUInt64(new UInt64(#FFFFFFFFFFFFFFFF), UInt64.MaxValue);

        testUInt64InvalidBits(Empty);                 // empty (too short)
        testUInt64InvalidBits(#FFFFFFFFFFFFFF);       // too short (7 bytes)
        testUInt64InvalidBits(#FFFFFFFFFFFFFFFFFF);   // too long (9 bytes)

        testBitsToUInt64([0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        testBitsToUInt64([0], 0);
        testBitsToUInt64([0,1,1,0], 0x06);
        testBitsToUInt64([1,1,1,0], 0x0E); // unsigned: 0-extended to 0x000000000000000E

        // valid 16-bits in UInt64
        testBitsToUInt64WithBoundsCheck([0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        // valid less than 64-bits
        testBitsToUInt64WithBoundsCheck([0,1,1,0], 0x06);
        // extra bits are all zero
        testBitsToUInt64WithBoundsCheck([0,0,0,0,0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        // bounds check False (68 bits)

        testBitsToUInt64WithBoundsCheck([1,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,
                                         0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                         0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                         0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                         0,0,0,0], 0);
        // bounds check True (68 bits)
        testBitsToUInt64WithBoundsCheck([1,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,
                                         0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                         0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                         0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                         0,0,0,0]);
    }

    void testUInt64(UInt64 n1, UInt64 expected) {
        assert n1 == expected;
        UInt64 n2 = new UInt64(n1.bits);
        assert n2 == n1;
        UInt64 n3 = n1.bits.toUInt64();
        assert n3 == n1;
    }

    void testUInt64InvalidBits(Byte[] bytes) {
        try {
            new UInt64(bytes);
            assert as "expected IllegalState to be thrown";
        } catch (IllegalState _) {
            // expected
        }
    }

    void testBitsToUInt64(Bit[] bits, UInt64 expected) {
        UInt64 n1 = bits.toUInt64();
        assert n1 == expected;
    }

    void testBitsToUInt64WithBoundsCheck(Bit[] bits, UInt64? expected = Null) {
        if (expected.is(UInt64)) {
            UInt64 n1 = bits.toUInt64(False);
            assert n1 == expected;
        } else {
            try {
                bits.toUInt64(True);
                assert as "expected OutOfBounds to be thrown";
            } catch (OutOfBounds _) {
                // expected
            }
        }
    }

    void testUInt128() {
        testUInt128(new UInt128(#4F6C0102030405060708090A0B0C0D0E), 0x4F6C0102030405060708090A0B0C0D0E);
        testUInt128(new UInt128(#00000000000000000000000000000000), 0);
        testUInt128(new UInt128(#7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF), 0x7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF);
        testUInt128(new UInt128(#FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF), UInt128.MaxValue);

        testUInt128InvalidBits(Empty);                                         // empty (too short)
        testUInt128InvalidBits(#FFFFFFFFFFFFFFFFFFFFFFFFFFFFFF);               // too short (15 bytes)
        testUInt128InvalidBits(#FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF);           // too long (17 bytes)

        testBitsToUInt128([0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        testBitsToUInt128([0], 0);
        testBitsToUInt128([0,1,1,0], 0x06);
        testBitsToUInt128([1,1,1,0], 0x0E); // unsigned: 0-extended to 0x0E

        // valid 16-bits in UInt128
        testBitsToUInt128WithBoundsCheck([0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        // valid less than 128-bits
        testBitsToUInt128WithBoundsCheck([0,1,1,0], 0x06);
        // extra bits are all zero
        testBitsToUInt128WithBoundsCheck([0,0,0,0,0,1,0,0,1,1,1,1,0,1,1,0,1,1,0,0], 0x4F6C);
        // bounds check False (132 bits: 4 bits + 128 zeros)
        testBitsToUInt128WithBoundsCheck([1,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,
                                          0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                          0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                          0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                          0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                          0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                          0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                          0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                          0,0,0,0], 0);
        // bounds check True (132 bits: 4 bits + 128 zeros)
        testBitsToUInt128WithBoundsCheck([1,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,
                                          0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                          0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                          0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                          0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                          0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                          0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                          0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
                                          0,0,0,0]);
    }

    void testUInt128(UInt128 n1, UInt128 expected) {
        assert n1 == expected;
        UInt128 n2 = new UInt128(n1.bits);
        assert n2 == n1;
        UInt128 n3 = n1.bits.toUInt128();
        assert n3 == n1;
    }

    void testUInt128InvalidBits(Byte[] bytes) {
        try {
            new UInt128(bytes);
            assert as "expected IllegalState to be thrown";
        } catch (IllegalState _) {
            // expected
        }
    }

    void testBitsToUInt128(Bit[] bits, UInt128 expected) {
        UInt128 n1 = bits.toUInt128();
        assert n1 == expected;
    }

    void testBitsToUInt128WithBoundsCheck(Bit[] bits, UInt128? expected = Null) {
        if (expected.is(UInt128)) {
            UInt128 n1 = bits.toUInt128(False);
            assert n1 == expected;
        } else {
            try {
                bits.toUInt128(True);
                assert as "expected OutOfBounds to be thrown";
            } catch (OutOfBounds _) {
                // expected
            }
        }
    }

    void testIntN() {
        // 0X098_7654_321A_BCDE_F123_4567_89FE_DCBA_FFFF
        // 51880205844660248555030106951628520882175
        Bit[] bits1 = [0, 0, 0, 0,
                       1, 0, 0, 1, 1, 0, 0, 0,
                       0, 1, 1, 1, 0, 1, 1, 0,
                       0, 1, 0, 1, 0, 1, 0, 0,
                       0, 0, 1, 1, 0, 0, 1, 0,
                       0, 0, 0, 1, 1, 0, 1, 0,
                       1, 0, 1, 1, 1, 1, 0, 0,
                       1, 1, 0, 1, 1, 1, 1, 0,
                       1, 1, 1, 1, 0, 0, 0, 1,
                       0, 0, 1, 0, 0, 0, 1, 1,
                       0, 1, 0, 0, 0, 1, 0, 1,
                       0, 1, 1, 0, 0, 1, 1, 1,
                       1, 0, 0, 0, 1, 0, 0, 1,
                       1, 1, 1, 1, 1, 1, 1, 0,
                       1, 1, 0, 1, 1, 1, 0, 0,
                       1, 0, 1, 1, 1, 0, 1, 0,
                       1, 1, 1, 1, 1, 1, 1, 1,
                       1, 1, 1, 1, 1, 1, 1, 1];

        // 0X98_7654_321A_BCDE_F123_4567_89FE_DCBA_FFFF
        // sign bit set 35232080087099998091593792550904141250561
        Bit[] bits2 = [1, 0, 0, 1, 1, 0, 0, 0,
                       0, 1, 1, 1, 0, 1, 1, 0,
                       0, 1, 0, 1, 0, 1, 0, 0,
                       0, 0, 1, 1, 0, 0, 1, 0,
                       0, 0, 0, 1, 1, 0, 1, 0,
                       1, 0, 1, 1, 1, 1, 0, 0,
                       1, 1, 0, 1, 1, 1, 1, 0,
                       1, 1, 1, 1, 0, 0, 0, 1,
                       0, 0, 1, 0, 0, 0, 1, 1,
                       0, 1, 0, 0, 0, 1, 0, 1,
                       0, 1, 1, 0, 0, 1, 1, 1,
                       1, 0, 0, 0, 1, 0, 0, 1,
                       1, 1, 1, 1, 1, 1, 1, 0,
                       1, 1, 0, 1, 1, 1, 0, 0,
                       1, 0, 1, 1, 1, 0, 1, 0,
                       1, 1, 1, 1, 1, 1, 1, 1,
                       1, 1, 1, 1, 1, 1, 1, 1];

        // Zero
        Bit[] bits3 = [0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0];

        testIntN(bits1, 51880205844660248555030106951628520882175, Positive);
        testIntN(bits2, -35232080087099998091593792550904141250561, Negative);
        testIntN(bits3, 0, Zero);
    }

    void testIntN(Bit[] bits, IntN expected, Number.Signum sign) {
        IntN n2 = new IntN(bits);
        assert n2 == expected;
        assert n2.sign == sign;
        IntN n3 = new IntN(expected.bits);
        assert n3 == expected;
        IntN n4 = new IntN(n2.bits);
        assert n4 == expected;
        IntN n5 = bits.toIntN();
        assert n5 == expected;
    }

    void testUIntN() {
        // 0X098_7654_321A_BCDE_F123_4567_89FE_DCBA_FFFF
        // 51880205844660248555030106951628520882175
        Bit[] bits1 = [0, 0, 0, 0,
                       1, 0, 0, 1, 1, 0, 0, 0,
                       0, 1, 1, 1, 0, 1, 1, 0,
                       0, 1, 0, 1, 0, 1, 0, 0,
                       0, 0, 1, 1, 0, 0, 1, 0,
                       0, 0, 0, 1, 1, 0, 1, 0,
                       1, 0, 1, 1, 1, 1, 0, 0,
                       1, 1, 0, 1, 1, 1, 1, 0,
                       1, 1, 1, 1, 0, 0, 0, 1,
                       0, 0, 1, 0, 0, 0, 1, 1,
                       0, 1, 0, 0, 0, 1, 0, 1,
                       0, 1, 1, 0, 0, 1, 1, 1,
                       1, 0, 0, 0, 1, 0, 0, 1,
                       1, 1, 1, 1, 1, 1, 1, 0,
                       1, 1, 0, 1, 1, 1, 0, 0,
                       1, 0, 1, 1, 1, 0, 1, 0,
                       1, 1, 1, 1, 1, 1, 1, 1,
                       1, 1, 1, 1, 1, 1, 1, 1];

        // 0X98_7654_321A_BCDE_F123_4567_89FE_DCBA_FFFF
        // sign bit set but this is a UIntN so this is the same value as bits1
        // 51880205844660248555030106951628520882175
        Bit[] bits2 = [1, 0, 0, 1, 1, 0, 0, 0,
                       0, 1, 1, 1, 0, 1, 1, 0,
                       0, 1, 0, 1, 0, 1, 0, 0,
                       0, 0, 1, 1, 0, 0, 1, 0,
                       0, 0, 0, 1, 1, 0, 1, 0,
                       1, 0, 1, 1, 1, 1, 0, 0,
                       1, 1, 0, 1, 1, 1, 1, 0,
                       1, 1, 1, 1, 0, 0, 0, 1,
                       0, 0, 1, 0, 0, 0, 1, 1,
                       0, 1, 0, 0, 0, 1, 0, 1,
                       0, 1, 1, 0, 0, 1, 1, 1,
                       1, 0, 0, 0, 1, 0, 0, 1,
                       1, 1, 1, 1, 1, 1, 1, 0,
                       1, 1, 0, 1, 1, 1, 0, 0,
                       1, 0, 1, 1, 1, 0, 1, 0,
                       1, 1, 1, 1, 1, 1, 1, 1,
                       1, 1, 1, 1, 1, 1, 1, 1];

        // Zero
        Bit[] bits3 = [0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0,
                       0, 0, 0, 0, 0, 0, 0, 0];

        testUIntN(bits1, 51880205844660248555030106951628520882175, Positive);
        testUIntN(bits2, 51880205844660248555030106951628520882175, Positive);
        testUIntN(bits3, 0, Zero);
    }

    void testUIntN(Bit[] bits, UIntN expected, Number.Signum sign) {
        UIntN n2 = new UIntN(bits);
        assert n2 == expected;
        assert n2.sign == sign;
        UIntN n3 = new UIntN(expected.bits);
        assert n3 == expected;
        UIntN n4 = new UIntN(n2.bits);
        assert n4 == expected;
        UIntN n5 = bits.toUIntN();
        assert n5 == expected;
    }

    void testDec32() {
        testDec32(0, 0);
        testDec32(1234.567, 1234.567);

        testBitsToDec32([0,1,1,1, 1,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0],
                        Dec32.PositiveInfinity);
        testBitsToDec32([1,1,1,1, 1,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0],
                        Dec32.NegativeInfinity);

        testDec32InvalidBits(Empty);           // empty
        testDec32InvalidBits(#7F_ABCD);        // too short
        testDec32InvalidBits(#7FAB_CDEF_0123); // too long
    }

    void testDec32(Dec32 n1, Dec32 expected) {
        assert n1 == expected;
        Dec32  n2 = new Dec32(n1.bits);
        assert n2 == n1;
        Dec32  n3 = n1.bits.toDec32();
        assert n3 == n1;
    }

    void testBitsToDec32(Bit[] bits, Dec32 expected) {
        Dec32 n1 = bits.toDec32();
        assert n1 == expected;
    }

    void testDec32InvalidBits(Byte[] bytes) {
        try {
            new Dec32(bytes);
            assert as "expected OutOfBounds to be thrown";
        } catch (OutOfBounds _) {
            // expected
        }
    }

    void testDec64() {
        testDec64(0, 0);
        testDec64(1234.567, 1234.567);

        testBitsToDec64([0,1,1,1, 1,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0,
                         0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0],
                        Dec64.PositiveInfinity);
        testBitsToDec64([1,1,1,1, 1,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0,
                         0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0],
                        Dec64.NegativeInfinity);

        testDec64InvalidBits(Empty);                   // empty
        testDec64InvalidBits(#7FAB_CDEF_0123);         // too short
        testDec64InvalidBits(#7FAB_CDEF_0123_9876_54); // too long
    }

    void testDec64(Dec64 n1, Dec64 expected) {
        assert n1 == expected;
        Dec64  n2 = new Dec64(n1.bits);
        assert n2 == n1;
        Dec64  n3 = n1.bits.toDec64();
        assert n3 == n1;
    }

    void testBitsToDec64(Bit[] bits, Dec64 expected) {
        Dec64 n1 = bits.toDec64();
        assert n1 == expected;
    }

    void testDec64InvalidBits(Byte[] bytes) {
        try {
            new Dec64(bytes);
            assert as "expected OutOfBounds to be thrown";
        } catch (OutOfBounds _) {
            // expected
        }
    }

    void testDec128() {
        testDec128(0, 0);
        testDec128(1234.567, 1234.567);

        testBitsToDec128([0,1,1,1, 1,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0,
                          0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0,
                          0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0,
                          0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0],
                        Dec128.PositiveInfinity);
        testBitsToDec128([1,1,1,1, 1,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0,
                          0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0,
                          0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0,
                          0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0],
                        Dec128.NegativeInfinity);

        testDec128InvalidBits(Empty);                                       // empty
        testDec128InvalidBits(#7FAB_CDEF_0123);                             // too short
        testDec128InvalidBits(#7FAB_CDEF_0123_9876_7FAB_CDEF_0123_9876_54); // too long
    }

    void testDec128(Dec128 n1, Dec128 expected) {
        assert n1 == expected;
        Dec128  n2 = new Dec128(n1.bits);
        assert n2 == n1;
        Dec128  n3 = n1.bits.toDec128();
        assert n3 == n1;
    }

    void testBitsToDec128(Bit[] bits, Dec128 expected) {
        Dec128 n1 = bits.toDec128();
        assert n1 == expected;
    }

    void testDec128InvalidBits(Byte[] bytes) {
        try {
            new Dec128(bytes);
            assert as "expected OutOfBounds to be thrown";
        } catch (OutOfBounds _) {
            // expected
        }
    }
}