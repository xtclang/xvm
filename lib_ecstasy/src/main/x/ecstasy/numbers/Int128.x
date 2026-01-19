const Int128
        extends IntNumber
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

    /**
     * Convert this Int128 to an Int8.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order 8
     * bits of this Int128.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int8
     *
     * @return  an Int8 that is equivalent to this Int128
     *
     * @throws OutOfBounds if this Int128 if checkBounds is True and this Int128 does not fit within
     *         the bounds of an Int8
     */
    @Override
    Int8 toInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= Int8.MinValue && this <= Int8.MaxValue;
        return new Int8(bits[bitLength-8 ..< bitLength]);
    }

    /**
     * Convert this Int128 to an Int16.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order 16
     * bits of this Int128.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int16
     *
     * @return  an Int16 that is equivalent to this Int128
     *
     * @throws OutOfBounds if this Int128 if checkBounds is True and this Int128 does not fit within
     *         the bounds of an Int16
     */
    @Override
    Int16 toInt16(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= Int16.MinValue && this <= Int16.MaxValue;
        return new Int16(bits[bitLength-16 ..< bitLength]);
    }

    /**
     * Convert this Int128 to an Int32.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order 32
     * bits of this Int128.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int32
     *
     * @return  an Int32 that is equivalent to this Int128
     *
     * @throws OutOfBounds if this Int128 if checkBounds is True and this Int128 does not fit within
     *         the bounds of an Int32
     */
    @Override
    Int32 toInt32(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= Int32.MinValue && this <= Int32.MaxValue;
        return new Int32(bits[bitLength-32 ..< bitLength]);
    }

    /**
     * Convert this Int128 to an Int64.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order 64
     * bits of this Int128.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int64
     *
     * @return  an Int64 that is equivalent to this Int128
     *
     * @throws OutOfBounds if this Int128 if checkBounds is True and this Int128 does not fit within
     *         the bounds of an Int64
     */
    @Override
    Int64 toInt64(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= Int64.MinValue && this <= Int64.MaxValue;
        return new Int64(bits[bitLength-64 ..< bitLength]);
    }

    /**
     * Convert this Int128 to an Int128, which is effectively a no-op and returns this Int128.
     *
     * @param checkBounds  this parameter will be ignored
     *
     * @return  this Int128
     */
    @Override
    Int128 toInt128(Boolean checkBounds = False) = this;

    /**
     * Convert this Int128 to a UInt8.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order 8
     * bits of this Int128.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt8
     *
     * @return  a UInt8 that is equivalent to this Int128
     *
     * @throws OutOfBounds if checkBounds is True and this Int128 is negative, or does not fit within
     *         the bounds of a UInt8
     */
    @Override
    UInt8 toUInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= UInt8.MinValue && this <= UInt8.MaxValue;
        return new UInt8(bits[bitLength-8 ..< bitLength]);
    }

    /**
     * Convert this Int128 to a UInt16.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order 16
     * bits of this Int128.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt16
     *
     * @return  a UInt16 that is equivalent to this Int128
     *
     * @throws OutOfBounds if checkBounds is True and this Int128 is negative, or does not fit within
     *         the bounds of a UInt16
     */
    @Override
    UInt16 toUInt16(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= UInt16.MinValue && this <= UInt16.MaxValue;
        return new UInt16(bits[bitLength-16 ..< bitLength]);
    }

    /**
     * Convert this Int128 to a UInt32.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order 32
     * bits of this Int128.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt32
     *
     * @return  a UInt32 that is equivalent to this Int128
     *
     * @throws OutOfBounds if checkBounds is True and this Int128 is negative, or does not fit within
     *         the bounds of a UInt32
     */
    @Override
    UInt32 toUInt32(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= UInt32.MinValue && this <= UInt32.MaxValue;
        return new UInt32(bits[bitLength-32 ..< bitLength]);
    }

    /**
     * Convert this Int128 to a UInt64.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order 64
     * bits of this Int128.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt64
     *
     * @return  a UInt64 that is equivalent to this Int128
     *
     * @throws OutOfBounds if checkBounds is True and this Int128 is negative, or does not fit within
     *         the bounds of a UInt64
     */
    @Override
    UInt64 toUInt64(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= UInt64.MinValue && this <= UInt64.MaxValue;
        return new UInt64(bits[bitLength-64 ..< bitLength]);
    }

    /**
     * Convert this Int128 to a UInt128.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the bit pattern
     * of this Int128, the high-order bit loses its function as a sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt128
     *
     * @return  a UInt128 that is equivalent to this Int128
     *
     * @throws OutOfBounds if this Int128 is negative and checkBounds is True
     */
    @Override
    UInt128 toUInt128(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= 0;
        return new UInt128(bits);
    }
}