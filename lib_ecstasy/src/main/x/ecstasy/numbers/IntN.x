/**
 * A signed integer (using twos-complement) with a variable number of bytes.
 */
const IntN
        extends IntNumber
        default(0) {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a variable-length signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits) {
        assert bits.size >= 8 && bits.size.bitCount == 1;
        super(bits);
    }

    /**
     * Construct a variable-length signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes) {
        assert bytes.size >= 1;
        super(bytes);
    }

    /**
     * Construct a variable-sized signed integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text) {
        construct IntN(new IntLiteral(text).toIntN().bits);
    }


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static IntN zero() {
        return 0;
    }

    @Override
    static IntN one() {
        return 1;
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
    UIntN magnitude.get() {
        return abs().toUIntN();
    }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("-#")
    IntN neg() {
        return ~this + 1;
    }

    @Override
    @Op("+")
    IntN add(IntN! n) {
        return this + n;
    }

    @Override
    @Op("-")
    IntN sub(IntN! n) {
        return this + ~n + 1;
    }

    @Override
    @Op("*")
    IntN mul(IntN! n) {
        return this * n;
    }

    @Override
    @Op("/")
    IntN div(IntN! n) {
        return this / n;
    }

    @Override
    @Op("%")
    IntN mod(IntN! n) {
        return this % n;
    }

    @Override
    IntN abs() {
        return this < 0 ? -this : this;
    }

    @Override
    IntN pow(IntN! n) {
        IntN result = 1;

        while (n-- > 0) {
            result *= this;
        }

        return result;
    }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional IntN next() {
        return True, this + 1;
    }

    @Override
    conditional IntN prev() {
        return True, this - 1;
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
        assert:bounds this >= Int32.MinValue && this <= Int32.MaxValue;
        return new Int32(bits[bitLength-32 ..< bitLength]);
    }

    @Override
    Int64 toInt64(Boolean checkBounds = False) {
        assert:bounds this >= Int64.MinValue && this <= Int64.MaxValue;
        return new Int64(bits[bitLength-64 ..< bitLength]);
    }

    @Override
    Int128 toInt128(Boolean checkBounds = False) {
        assert:bounds this >= Int128.MinValue && this <= Int128.MaxValue;
        return new Int128(bits[bitLength-128 ..< bitLength]);
    }

    @Override
    IntN toIntN() {
        return this;
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
        assert:bounds this >= UInt32.MinValue && this <= UInt32.MaxValue;
        return new UInt32(bits[bitLength-32 ..< bitLength]);
    }

    @Override
    UInt64 toUInt64(Boolean checkBounds = False) {
        assert:bounds this >= UInt64.MinValue && this <= UInt64.MaxValue;
        return new UInt64(bits[bitLength-64 ..< bitLength]);
    }

    @Override
    UInt128 toUInt128(Boolean checkBounds = False) {
        assert:bounds this >= UInt128.MinValue && this <= UInt128.MaxValue;
        return new UInt128(bits[bitLength-128 ..< bitLength]);
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