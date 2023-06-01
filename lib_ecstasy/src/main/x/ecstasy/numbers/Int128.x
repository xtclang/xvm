const Int128
        extends IntNumber
        incorporates Bitwise
        default(0) {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an Int128.
     */
    static IntLiteral MinValue = -0x8000_0000_0000_0000_0000_0000_0000_0000;

    /**
     * The maximum value for an Int128.
     */
    static IntLiteral MaxValue =  0x7FFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF;


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength() {
        return True, 128;
    }

    @Override
    static Int128 zero() {
        return 0;
    }

    @Override
    static Int128 one() {
        return 1;
    }

    @Override
    static conditional Range<Int128> range() {
        return True, MinValue..MaxValue;
    }


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 128-bit signed integer number from its bitwise machine representation.
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
     * Construct a 128-bit signed integer number from its network-portable representation.
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
     * Construct a 128-bit signed integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text) {
        construct Int128(new IntLiteral(text).toInt128().bits);
    }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get() {
        return switch (this <=> 0) {
            case Lesser : Negative;
            case Equal  : Zero;
            case Greater: Positive;
        };
    }

    @Override
    UInt128 magnitude.get() {
        return toIntN().abs().toUInt128();
    }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("-#")
    Int128 neg() {
        return ~this + 1;
    }

    @Override
    @Op("+")
    Int128 add(Int128! n) {
        return this + n;
    }

    @Override
    @Op("-")
    Int128 sub(Int128! n) {
        return this + ~n + 1;
    }

    @Override
    @Op("*")
    Int128 mul(Int128! n) {
        return this * n;
    }

    @Override
    @Op("/")
    Int128 div(Int128! n) {
        return this / n;
    }

    @Override
    @Op("%")
    Int128 mod(Int128! n) {
        return this % n;
    }

    @Override
    Int128 abs() {
        return this < 0 ? -this : this;
    }

    @Override
    Int128 pow(Int128! n) {
        Int128 result = 1;

        while (n-- > 0) {
            result *= this;
        }

        return result;
    }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional Int128 next() {
        if (this < MaxValue) {
            return True, this + 1;
        }

        return False;
    }

    @Override
    conditional Int128 prev() {
        if (this > MinValue) {
            return True, this - 1;
        }

        return False;
    }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    (Int128 - Unchecked) toChecked() {
        return this.is(Unchecked) ? new Int128(bits) : this;
    }

    @Override
    @Unchecked Int128 toUnchecked() {
        return this.is(Unchecked) ? this : new @Unchecked Int128(bits);
    }

    @Auto
    @Override
    Int toInt(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    UInt toUInt(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    Int8 toInt8(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this >= Int8.MinValue && this <= Int8.MaxValue;
        return new Int8(bits[bitLength-8 ..< bitLength]);
    }

    @Override
    Int16 toInt16(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this >= Int16.MinValue && this <= Int16.MaxValue;
        return new Int16(bits[bitLength-16 ..< bitLength]);
    }

    @Override
    Int32 toInt32(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this >= Int32.MinValue && this <= Int32.MaxValue;
        return new Int32(bits[bitLength-32 ..< bitLength]);
    }

    @Override
    Int64 toInt64(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this >= Int64.MinValue && this <= Int64.MaxValue;
        return new Int64(bits[bitLength-64 ..< bitLength]);
    }

    @Override
    Int128 toInt128(Boolean truncate = False, Rounding direction = TowardZero) {
        return this;
    }

    @Auto
    @Override
    IntN toIntN(Rounding direction = TowardZero) {
        return new IntN(bits);
    }

    @Override
    UInt8 toUInt8(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this >= UInt8.MinValue && this <= UInt8.MaxValue;
        return new UInt8(bits[bitLength-8 ..< bitLength]);
    }

    @Override
    UInt16 toUInt16(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this >= UInt16.MinValue && this <= UInt16.MaxValue;
        return new UInt16(bits[bitLength-16 ..< bitLength]);
    }

    @Override
    UInt32 toUInt32(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this >= UInt32.MinValue && this <= UInt32.MaxValue;
        return new UInt32(bits[bitLength-32 ..< bitLength]);
    }

    @Override
    UInt64 toUInt64(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this >= UInt64.MinValue && this <= UInt64.MaxValue;
        return new UInt64(bits[bitLength-64 ..< bitLength]);
    }

    @Override
    UInt128 toUInt128(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this >= 0;
        return new UInt128(bits);
    }

    @Override
    UIntN toUIntN(Rounding direction = TowardZero) {
        assert:bounds this >= 0;
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
}