const UInt128
        extends UIntNumber
        incorporates Bitwise
        default(0) {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an UInt128.
     */
    static IntLiteral MinValue = 0;

    /**
     * The maximum value for an UInt128.
     */
    static IntLiteral MaxValue =  0xFFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF;


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength() {
        return True, 128;
    }

    @Override
    static UInt128 zero() {
        return 0;
    }

    @Override
    static UInt128 one() {
        return 1;
    }

    @Override
    static conditional Range<UInt128> range() {
        return True, MinValue..MaxValue;
    }


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 128-bit unsigned integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits) {
        assert bits.size == 128;
        super(bits);
    }

    /**
     * Construct a 128-bit unsigned integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes) {
        assert bytes.size == 16;
        super(bytes);
    }

    /**
     * Construct a 128-bit unsigned integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text) {
        construct UInt128(new IntLiteral(text).toUInt128().bits);
    }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get() {
        return this == 0 ? Zero : Positive;
    }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("+")
    UInt128 add(UInt128! n) {
        return this + n;
    }

    @Override
    @Op("-")
    UInt128 sub(UInt128! n) {
        return this - n;
    }

    @Override
    @Op("*")
    UInt128 mul(UInt128! n) {
        return this * n;
    }

    @Override
    @Op("/")
    UInt128 div(UInt128! n) {
        return this / n;
    }

    @Override
    @Op("%")
    UInt128 mod(UInt128! n) {
        return this % n;
    }

    @Override
    UInt128 pow(UInt128! n) {
        UInt128 result = 1;

        while (n-- > 0) {
            result *= this;
        }

        return result;
    }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional UInt128 next() {
        if (this < MaxValue) {
            return True, this + 1;
        }

        return False;
    }

    @Override
    conditional UInt128 prev() {
        if (this > MinValue) {
            return True, this - 1;
        }

        return False;
    }


    // ----- conversions ---------------------------------------------------------------------------

    @Auto
    @Override
    Int toInt(Boolean truncate = False, Rounding direction = TowardZero);

    @Auto
    @Override
    UInt toUInt(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    Int8 toInt8(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this <= Int8.MaxValue;
        return new Int8(bits[bitLength-8 ..< bitLength]);
    }

    @Override
    Int16 toInt16(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this <= Int16.MaxValue;
        return new Int16(bits[bitLength-16 ..< bitLength]);
    }

    @Override
    Int32 toInt32(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this <= Int32.MaxValue;
        return new Int32(bits[bitLength-32 ..< bitLength]);
    }

    @Override
    Int64 toInt64(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this <= Int64.MaxValue;
        return new Int64(bits[bitLength-64 ..< bitLength]);
    }

    @Override
    Int128 toInt128(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this <= Int128.MaxValue;
        return new Int128(bits);
    }

    @Auto
    @Override
    IntN toIntN(Rounding direction = TowardZero) {
        return bits[0] == 0 ? new IntN(bits) : toUIntN().toIntN();
    }

    @Override
    UInt8 toUInt8(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this <= UInt8.MaxValue;
        return new UInt8(bits[bitLength-8 ..< bitLength]);
    }

    @Override
    UInt16 toUInt16(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this <= UInt16.MaxValue;
        return new UInt16(bits[bitLength-16 ..< bitLength]);
    }

    @Override
    UInt32 toUInt32(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this <= UInt32.MaxValue;
        return new UInt32(bits[bitLength-32 ..< bitLength]);
    }

    @Override
    UInt64 toUInt64(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this <= UInt64.MaxValue;
        return new UInt64(bits[bitLength-64 ..< bitLength]);
    }

    @Override
    UInt128 toUInt128(Boolean truncate = False, Rounding direction = TowardZero) {
        return this;
    }

    @Auto
    @Override
    UIntN toUIntN(Rounding direction = TowardZero) {
        return new UIntN(bits);
    }

    @Auto
    @Override
    BFloat16 toBFloat16();

    @Auto
    @Override
    Float16 toFloat16();

    @Auto
    @Override
    Float32 toFloat32();

    @Auto
    @Override
    Float64 toFloat64();

    @Auto
    @Override
    Float128 toFloat128();

    @Auto
    @Override
    FloatN toFloatN() {
        return toIntLiteral().toFloatN();
    }

    @Auto
    @Override
    Dec32 toDec32();

    @Auto
    @Override
    Dec64 toDec64();

    @Auto
    @Override
    Dec128 toDec128();

    @Auto
    @Override
    DecN toDecN() {
        return toIntLiteral().toDecN();
    }


    // ----- Stringable implementation -------------------------------------------------------------

    @Override
    Int estimateStringLength() {
        return calculateStringSize(this, sizeArray);
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        if (this == 0) {
            buf.add('0');
        } else {
            (UInt128 left, UInt128 digit) = this /% 10;
            if (left != 0) {
                left.appendTo(buf);
            }
            buf.add(DIGITS[digit.toInt64()]);
        }
        return buf;
    }

    // MaxValue = 340_282_366_920_938_463_463_374_607_431_768_211_455 (39 digits)
    private static UInt128[] sizeArray =
         [
         9, 99, 999, 9_999, 99_999, 999_999,
         9_999_999, 99_999_999, 999_999_999, 9_999_999_999, 99_999_999_999, 999_999_999_999,
         9_999_999_999_999, 99_999_999_999_999, 999_999_999_999_999,
         9_999_999_999_999_999, 99_999_999_999_999_999, 999_999_999_999_999_999,
         9_999_999_999_999_999_999, 99_999_999_999_999_999_999, 999_999_999_999_999_999_999,
           9_999_999_999_999_999_999_999,
          99_999_999_999_999_999_999_999,
         999_999_999_999_999_999_999_999,
           9_999_999_999_999_999_999_999_999,
          99_999_999_999_999_999_999_999_999,
         999_999_999_999_999_999_999_999_999,
           9_999_999_999_999_999_999_999_999_999,
          99_999_999_999_999_999_999_999_999_999,
         999_999_999_999_999_999_999_999_999_999,
           9_999_999_999_999_999_999_999_999_999_999,
          99_999_999_999_999_999_999_999_999_999_999,
         999_999_999_999_999_999_999_999_999_999_999,
           9_999_999_999_999_999_999_999_999_999_999_999,
          99_999_999_999_999_999_999_999_999_999_999_999,
         999_999_999_999_999_999_999_999_999_999_999_999,
           9_999_999_999_999_999_999_999_999_999_999_999_999,
          99_999_999_999_999_999_999_999_999_999_999_999_999,
         340_282_366_920_938_463_463_374_607_431_768_211_455
         ];
}