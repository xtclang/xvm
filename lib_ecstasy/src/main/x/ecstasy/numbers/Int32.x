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

    /**
     * Convert this Int32 to an Int8.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order 8
     * bits of this Int32.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int8
     *
     * @return  an Int8 that is equivalent to this Int32
     *
     * @throws OutOfBounds if this Int32 if checkBounds is True and this Int32 does not fit within
     *         the bounds of an Int8
     */
    @Override
    Int8 toInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= Int8.MinValue && this <= Int8.MaxValue;
        return new Int8(bits[bitLength-8 ..< bitLength]);
    }

    /**
     * Convert this Int32 to an Int16.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order 16
     * bits of this Int32.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int16
     *
     * @return  an Int16 that is equivalent to this Int32
     *
     * @throws OutOfBounds if this Int32 if checkBounds is True and this Int32 does not fit within
     *         the bounds of an Int16
     */
    @Override
    Int16 toInt16(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= Int16.MinValue && this <= Int16.MaxValue;
        return new Int16(bits[bitLength-16 ..< bitLength]);
    }

    /**
     * Convert this Int32 to an Int32, which is effectively a no-op and returns this Int32.
     *
     * @param checkBounds  this parameter will be ignored
     *
     * @return  this Int32
     */
    @Override
    Int32 toInt32(Boolean checkBounds = False) = this;

    /**
     * Convert this Int32 to an Int64.
     *
     * Conversion is performed by sign extending this Int32 up to an Int64.
     * An Int32 will always fit within an Int64, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int64
     *
     * @return  an Int64 that is equivalent to this Int32
     */
    @Auto
    @Override
    Int64 toInt64(Boolean checkBounds = False) = new Int64(new Bit[64](i -> bits[i < 64-bitLength ? 0 : i]));

    /**
     * Convert this Int32 to an Int128.
     *
     * Conversion is performed by sign extending this Int32 up to an Int128.
     * An Int32 will always fit within an Int128, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int128
     *
     * @return  an Int128 that is equivalent to this Int32
     */
    @Auto
    @Override
    Int128 toInt128(Boolean checkBounds = False) = new Int128(new Bit[128](i -> bits[i < 128-bitLength ? 0 : i]));

    /**
     * Convert this Int32 to a UInt8.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order 8
     * bits of this Int32.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt8
     *
     * @return  a UInt8 that is equivalent to this Int32
     *
     * @throws OutOfBounds if checkBounds is True and this Int32 is negative, or does not fit within
     *         the bounds of a UInt8
     */
    @Override
    UInt8 toUInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= UInt8.MinValue && this <= UInt8.MaxValue;
        return new UInt8(bits[bitLength-8 ..< bitLength]);
    }

    /**
     * Convert this Int32 to a UInt16.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order 16
     * bits of this Int32.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt16
     *
     * @return  a UInt16 that is equivalent to this Int32
     *
     * @throws OutOfBounds if checkBounds is True and this Int32 is negative, or does not fit within
     *         the bounds of a UInt16
     */
    @Override
    UInt16 toUInt16(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= UInt16.MinValue && this <= UInt16.MaxValue;
        return new UInt16(bits[bitLength-16 ..< bitLength]);
    }

    /**
     * Convert this Int32 to a UInt32.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the bit pattern
     * of this Int32, the high-order bit loses its function as a sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt32
     *
     * @return  a UInt32 that is equivalent to this Int32
     *
     * @throws OutOfBounds if this Int32 is negative and checkBounds is True
     */
    @Override
    UInt32 toUInt32(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= 0;
        return new UInt32(bits);
    }

    /**
     * Convert this Int32 to a UInt64.
     *
     * Conversion is performed after optionally checking the bounds, by sign-extending this Int32
     * to an Int64 converting to a UInt64, the high-order bit loses its function as a sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an UInt64
     *
     * @return  a UInt64 that is equivalent to this Int32
     *
     * @throws OutOfBounds if this Int32 is negative and checkBounds is True
     */
    @Override
    UInt64 toUInt64(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= 0;
        return new UInt64(new Bit[64](i -> (i < 64-bitLength ? 0 : bits[i])));
    }

    /**
     * Convert this Int32 to a UInt128.
     *
     * Conversion is performed after optionally checking the bounds, by sign-extending this Int32
     * to an Int128 converting to a UInt128, the high-order bit loses its function as a sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an UInt128
     *
     * @return  a UInt128 that is equivalent to this Int32
     *
     * @throws OutOfBounds if this Int32 is negative and checkBounds is True
     */
    @Override
    UInt128 toUInt128(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= 0;
        return new UInt128(new Bit[128](i -> (i < 128-bitLength ? 0 : bits[i])));
    }
}