const Int8
        extends IntNumber
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

    /**
     * Convert this Int8 to an Int8, which is effectively a no-op and returns this Int8.
     *
     * @param checkBounds  this parameter will be ignored
     *
     * @return  this Int8
     */
    @Auto
    @Override
    Int8 toInt8(Boolean checkBounds = False) = this;

    /**
     * Convert this Int8 to an Int16.
     *
     * Conversion is performed by sign extending this Int8 up to an Int16.
     * An Int8 will always fit within an Int16, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int16
     *
     * @return  an Int16 that is equivalent to this Int8
     */
    @Auto
    @Override
    Int16 toInt16(Boolean checkBounds = False) = new Int16(new Bit[16](i -> bits[i < 16-bitLength ? 0 : i]));

    /**
     * Convert this Int8 to an Int32.
     *
     * Conversion is performed by sign extending this Int8 up to an Int32.
     * An Int8 will always fit within an Int32, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int32
     *
     * @return  an Int32 that is equivalent to this Int8
     */
    @Auto
    @Override
    Int32 toInt32(Boolean checkBounds = False) = new Int32(new Bit[32](i -> bits[i < 32-bitLength ? 0 : i]));

    /**
     * Convert this Int8 to an Int64.
     *
     * Conversion is performed by sign extending this Int8 up to an Int64.
     * An Int8 will always fit within an Int64, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int64
     *
     * @return  an Int64 that is equivalent to this Int8
     */
    @Auto
    @Override
    Int64 toInt64(Boolean checkBounds = False) = new Int64(new Bit[64](i -> bits[i < 64-bitLength ? 0 : i]));

    /**
     * Convert this Int8 to an Int128.
     *
     * Conversion is performed by sign extending this Int8 up to an Int128.
     * An Int8 will always fit within an Int128, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int128
     *
     * @return  an Int128 that is equivalent to this Int8
     */
    @Auto
    @Override
    Int128 toInt128(Boolean checkBounds = False) = new Int128(new Bit[128](i -> bits[i < 128-bitLength ? 0 : i]));

    /**
     * Convert this Int8 to a UInt8.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the bit pattern
     * of this Int8, the high-order bit loses its function as a sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt8
     *
     * @return  a UInt8 that is equivalent to this Int8
     *
     * @throws OutOfBounds if this Int8 is negative and checkBounds is True
     */
    @Override
    UInt8 toUInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= 0;
        return new UInt8(bits);
    }

    /**
     * Convert this Int8 to a UInt16.
     *
     * Conversion is performed after optionally checking the bounds, by sign-extending this Int8
     * to an Int16 converting to a UInt16, the high-order bit loses its function as a sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an UInt16
     *
     * @return  a UInt16 that is equivalent to this Int8
     *
     * @throws OutOfBounds if this Int8 is negative and checkBounds is True
     */
    @Override
    UInt16 toUInt16(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= 0;
        return new UInt16(new Bit[16](i -> i < 16-bitLength ? 0 : bits[i]));
    }

    /**
     * Convert this Int8 to a UInt32.
     *
     * Conversion is performed after optionally checking the bounds, by sign-extending this Int8
     * to an Int32 converting to a UInt32, the high-order bit loses its function as a sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an UInt32
     *
     * @return  a UInt32 that is equivalent to this Int8
     *
     * @throws OutOfBounds if this Int8 is negative and checkBounds is True
     */
    @Override
    UInt32 toUInt32(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= 0;
        return new UInt32(new Bit[32](i -> i < 32-bitLength ? 0 : bits[i]));
    }

    /**
     * Convert this Int8 to a UInt64.
     *
     * Conversion is performed after optionally checking the bounds, by sign-extending this Int8
     * to an Int64 converting to a UInt64, the high-order bit loses its function as a sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an UInt64
     *
     * @return  a UInt64 that is equivalent to this Int8
     *
     * @throws OutOfBounds if this Int8 is negative and checkBounds is True
     */
    @Override
    UInt64 toUInt64(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= 0;
        return new UInt64(new Bit[64](i -> i < 64-bitLength ? 0 : bits[i]));
    }

    /**
     * Convert this Int8 to a UInt128.
     *
     * Conversion is performed after optionally checking the bounds, by sign-extending this Int8
     * to an Int121 converting to a UInt128, the high-order bit loses its function as a sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an UInt128
     *
     * @return  a UInt128 that is equivalent to this Int8
     *
     * @throws OutOfBounds if this Int8 is negative and checkBounds is True
     */
    @Override
    UInt128 toUInt128(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= 0;
        return new UInt128(new Bit[128](i -> i < 128-bitLength ? 0 : bits[i]));
    }
}