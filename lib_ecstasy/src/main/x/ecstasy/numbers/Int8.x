const Int8
        extends IntNumber
        incorporates Bitwise
        default(0) {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an Int8.
     */
    static IntLiteral MinValue = -128;

    /**
     * The maximum value for an Int8.
     */
    static IntLiteral MaxValue = 127;


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength() {
        return True, 8;
    }

    @Override
    static Int8 zero() {
        return 0;
    }

    @Override
    static Int8 one() {
        return 1;
    }

    @Override
    static conditional Range<Int8> range() {
        return True, MinValue..MaxValue;
    }


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an 8-bit signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits) {
        assert bits.size == 8;
        super(bits);
    }

    /**
     * Construct an 8-bit signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes) {
        assert bytes.size == 1;
        super(bytes);
    }

    /**
     * Construct an 8-bit signed integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text) {
        construct Int8(new IntLiteral(text).toInt8().bits);
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
    UInt8 magnitude.get() {
        return toInt16().abs().toUInt8();
    }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("-#")
    Int8 neg() {
        return ~this + 1;
    }

    @Override
    @Op("+")
    Int8 add(Int8! n) {
        return this + n;
    }

    @Override
    @Op("-")
    Int8 sub(Int8! n) {
        return this + ~n + 1;
    }

    @Override
    @Op("*")
    Int8 mul(Int8! n) {
        return this * n;
    }

    @Override
    @Op("/")
    Int8 div(Int8! n) {
        return this / n;
    }

    @Override
    @Op("%")
    Int8 mod(Int8! n) {
        return this % n;
    }

    @Override
    Int8 abs() {
        return this < 0 ? -this : this;
    }

    @Override
    Int8 pow(Int8! n) {
        Int8 result = 1;

        while (n-- > 0) {
            result *= this;
        }

        return result;
    }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional Int8 next() {
        if (this < MaxValue) {
            return True, this + 1;
        }

        return False;
    }

    @Override
    conditional Int8 prev() {
        if (this > MinValue) {
            return True, this - 1;
        }

        return False;
    }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    (Int8 - Unchecked) toChecked() {
        return this.is(Unchecked) ? new Int8(bits) : this;
    }

    @Override
    @Unchecked Int8 toUnchecked() {
        return this.is(Unchecked) ? this : new @Unchecked Int8(bits);
    }

    @Auto
    @Override
    Int toInt(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    UInt toUInt(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    Int8 toInt8(Boolean truncate = False, Rounding direction = TowardZero) {
        return this;
    }

    @Auto
    @Override
    Int16 toInt16(Boolean truncate = False, Rounding direction = TowardZero) {
        return new Int16(new Bit[16](i -> bits[i < 16-bitLength ? 0 : i]));
    }

    @Auto
    @Override
    Int32 toInt32(Boolean truncate = False, Rounding direction = TowardZero) {
        return new Int32(new Bit[32](i -> bits[i < 32-bitLength ? 0 : i]));
    }

    @Auto
    @Override
    Int64 toInt64(Boolean truncate = False, Rounding direction = TowardZero) {
        return new Int64(new Bit[64](i -> bits[i < 64-bitLength ? 0 : i]));
    }

    @Auto
    @Override
    Int128 toInt128(Boolean truncate = False, Rounding direction = TowardZero) {
        return new Int128(new Bit[128](i -> bits[i < 128-bitLength ? 0 : i]));
    }

    @Auto
    @Override
    IntN toIntN(Rounding direction = TowardZero) {
        return new IntN(bits);
    }

    @Override
    UInt8 toUInt8(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this >= 0;
        return new UInt8(bits);
    }

    @Override
    UInt16 toUInt16(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this >= 0;
        return new UInt16(new Bit[16](i -> i < 16-bitLength ? 0 : bits[i]));
    }

    @Override
    UInt32 toUInt32(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this >= 0;
        return new UInt32(new Bit[32](i -> i < 32-bitLength ? 0 : bits[i]));
    }

    @Override
    UInt64 toUInt64(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this >= 0;
        return new UInt64(new Bit[64](i -> i < 64-bitLength ? 0 : bits[i]));
    }

    @Override
    UInt128 toUInt128(Boolean truncate = False, Rounding direction = TowardZero) {
        assert:bounds this >= 0;
        return new UInt128(new Bit[128](i -> i < 128-bitLength ? 0 : bits[i]));
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