const Int64
        extends IntNumber
        default(0) {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an Int64.
     */
    static IntLiteral MinValue = -0x8000_0000_0000_0000;

    /**
     * The maximum value for an Int64.
     */
    static IntLiteral MaxValue =  0x7FFF_FFFF_FFFF_FFFF;


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength() {
        return True, 64;
    }

    @Override
    static Int64 zero() {
        return 0;
    }

    @Override
    static Int64 one() {
        return 1;
    }

    @Override
    static conditional Range<Int64> range() {
        return True, MinValue..MaxValue;
    }


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 64-bit signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits) {
        assert bits.size == 64;
        super(bits);
    }

    /**
     * Construct a 64-bit signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes) {
        assert bytes.size == 8;
        super(bytes);
    }

    /**
     * Construct a 64-bit signed integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text) {
        construct Int64(new IntLiteral(text).toInt64().bits);
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
    UInt64 magnitude.get() {
        return toInt128().abs().toUInt64();
    }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("-#")
    Int64 neg() {
        return ~this + 1;
    }

    @Override
    @Op("+")
    Int64 add(Int64! n) {
        return this + n;
    }

    @Override
    @Op("-")
    Int64 sub(Int64! n) {
        return this + ~n + 1;
    }

    @Override
    @Op("*")
    Int64 mul(Int64! n) {
        return this * n;
    }

    @Override
    @Op("/")
    Int64 div(Int64! n) {
        return this / n;
    }

    @Override
    @Op("%")
    Int64 mod(Int64! n) {
        return this % n;
    }

    @Override
    Int64 abs() {
        return this < 0 ? -this : this;
    }

    @Override
    Int64 pow(Int64! n) {
        Int64 result = 1;

        while (n-- > 0) {
            result *= this;
        }

        return result;
    }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional Int64 next() {
        if (this < MaxValue) {
            return True, this + 1;
        }

        return False;
    }

    @Override
    conditional Int64 prev() {
        if (this > MinValue) {
            return True, this - 1;
        }

        return False;
    }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    Int8 toInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= Int8.MinValue && this <= Int8.MaxValue;
        return new Int8(bits[bitLength-8 ..< bitLength]);
    }

    @Override
    Int16 toInt16(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= Int16.MinValue && this <= Int16.MaxValue;
        return new Int16(bits[bitLength-16 ..< bitLength]);
    }

    @Override
    Int32 toInt32(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= Int32.MinValue && this <= Int32.MaxValue;
        return new Int32(bits[bitLength-32 ..< bitLength]);
    }

    @Override
    Int64 toInt64(Boolean checkBounds = False) = this;

    @Auto
    @Override
    Int128 toInt128(Boolean checkBounds = False) = new Int128(new Bit[128](i -> bits[i < 128-bitLength ? 0 : i]));

    @Override
    UInt8 toUInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= UInt8.MinValue && this <= UInt8.MaxValue;
        return new UInt8(bits[bitLength-8 ..< bitLength]);
    }

    @Override
    UInt16 toUInt16(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= UInt16.MinValue && this <= UInt16.MaxValue;
        return new UInt16(bits[bitLength-16 ..< bitLength]);
    }

    @Override
    UInt32 toUInt32(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= UInt32.MinValue && this <= UInt32.MaxValue;
        return new UInt32(bits[bitLength-32 ..< bitLength]);
    }

    @Override
    UInt64 toUInt64(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= 0;
        return new UInt64(bits);
    }

    @Override
    UInt128 toUInt128(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= 0;
        return new UInt128(new Bit[128](i -> (i < 128-bitLength ? 0 : bits[i])));
    }


    // ----- Hashable functions --------------------------------------------------------------------

    @Override
    static <CompileType extends Int64> Int64 hashCode(CompileType value) {
        return value;
    }

    @Override
    static <CompileType extends Int64> Boolean equals(CompileType value1, CompileType value2) {
        return value1.bits == value2.bits;
    }
}