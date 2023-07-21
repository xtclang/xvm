const Int32
        extends IntNumber
        default(0) {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an Int32.
     */
    static IntLiteral MinValue = -0x80000000;

    /**
     * The maximum value for an Int32.
     */
    static IntLiteral MaxValue =  0x7FFFFFFF;


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength() {
        return True, 32;
    }

    @Override
    static Int32 zero() {
        return 0;
    }

    @Override
    static Int32 one() {
        return 1;
    }

    @Override
    static conditional Range<Int32> range() {
        return True, MinValue..MaxValue;
    }


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 32-bit signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits) {
        assert bits.size == 32;
        super(bits);
    }

    /**
     * Construct a 32-bit signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes) {
        assert bytes.size == 4;
        super(bytes);
    }

    /**
     * Construct a 32-bit signed integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text) {
        construct Int32(new IntLiteral(text).toInt32().bits);
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
    UInt32 magnitude.get() {
        return toInt64().abs().toUInt32();
    }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("-#")
    Int32 neg() {
        return ~this + 1;
    }

    @Override
    @Op("+")
    Int32 add(Int32! n) {
        return this + n;
    }

    @Override
    @Op("-")
    Int32 sub(Int32! n) {
        return this + ~n + 1;
    }

    @Override
    @Op("*")
    Int32 mul(Int32! n) {
        return this * n;
    }

    @Override
    @Op("/")
    Int32 div(Int32! n) {
        return this / n;
    }

    @Override
    @Op("%")
    Int32 mod(Int32! n) {
        return this % n;
    }

    @Override
    Int32 abs() {
        return this < 0 ? -this : this;
    }

    @Override
    Int32 pow(Int32! n) {
        Int32 result = 1;

        while (n-- > 0) {
            result *= this;
        }

        return result;
    }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional Int32 next() {
        if (this < MaxValue) {
            return True, this + 1;
        }

        return False;
    }

    @Override
    conditional Int32 prev() {
        if (this > MinValue) {
            return True, this - 1;
        }

        return False;
    }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    Int8 toInt8(Boolean checkBounds = False) {
        assert:bounds this >= Int8.MinValue && this <= Int8.MaxValue;
        return new Int8(bits[bitLength-8 ..< bitLength]);
    }

    @Override
    Int16 toInt16(Boolean checkBounds = False) {
        assert:bounds this >= Int16.MinValue && this <= Int16.MaxValue;
        return new Int16(bits[bitLength-16 ..< bitLength]);
    }

    @Override
    Int32 toInt32(Boolean checkBounds = False) {
        return this;
    }

    @Auto
    @Override
    Int64 toInt64(Boolean checkBounds = False) {
        return new Int64(new Bit[64](i -> bits[i < 64-bitLength ? 0 : i]));
    }

    @Auto
    @Override
    Int128 toInt128(Boolean checkBounds = False) {
        return new Int128(new Bit[128](i -> bits[i < 128-bitLength ? 0 : i]));
    }

    @Auto
    @Override
    IntN toIntN() {
        return new IntN(bits);
    }

    @Override
    UInt8 toUInt8(Boolean checkBounds = False) {
        assert:bounds this >= UInt8.MinValue && this <= UInt8.MaxValue;
        return new UInt8(bits[bitLength-8 ..< bitLength]);
    }

    @Override
    UInt16 toUInt16(Boolean checkBounds = False) {
        assert:bounds this >= UInt16.MinValue && this <= UInt16.MaxValue;
        return new UInt16(bits[bitLength-16 ..< bitLength]);
    }

    @Override
    UInt32 toUInt32(Boolean checkBounds = False) {
        assert:bounds this >= 0;
        return new UInt32(bits);
    }

    @Override
    UInt64 toUInt64(Boolean checkBounds = False) {
        assert:bounds this >= 0;
        return new UInt64(new Bit[64](i -> (i < 64-bitLength ? 0 : bits[i])));
    }

    @Override
    UInt128 toUInt128(Boolean checkBounds = False) {
        assert:bounds this >= 0;
        return new UInt128(new Bit[128](i -> (i < 128-bitLength ? 0 : bits[i])));
    }

    @Override
    UIntN toUIntN() {
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