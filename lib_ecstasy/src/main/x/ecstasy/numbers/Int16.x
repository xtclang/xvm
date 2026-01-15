const Int16
        extends IntNumber
        default(0) {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an Int16.
     */
    static IntLiteral MinValue = -0x8000;

    /**
     * The maximum value for an Int16.
     */
    static IntLiteral MaxValue = 0x7FFF;


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength() {
        return True, 16;
    }

    @Override
    static Int16 zero() {
        return 0;
    }

    @Override
    static Int16 one() {
        return 1;
    }

    @Override
    static conditional Range<Int16> range() {
        return True, MinValue..MaxValue;
    }


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 16-bit signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits) {
        assert bits.size == 16;
        super(bits);
    }

    /**
     * Construct a 16-bit signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes) {
        assert bytes.size == 2;
        super(bytes);
    }

    /**
     * Construct a 16-bit signed integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text) {
        construct Int16(new IntLiteral(text).toInt16().bits);
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
    UInt16 magnitude.get() {
        return toInt32().abs().toUInt16();
    }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("-#")
    Int16 neg() {
        return ~this + 1;
    }

    @Override
    @Op("+")
    Int16 add(Int16! n) {
        return this + n;
    }

    @Override
    @Op("-")
    Int16 sub(Int16! n) {
        return this + ~n + 1;
    }

    @Override
    @Op("*")
    Int16 mul(Int16! n) {
        return this * n;
    }

    @Override
    @Op("/")
    Int16 div(Int16! n) {
        return this / n;
    }

    @Override
    @Op("%")
    Int16 mod(Int16! n) {
        return this % n;
    }

    @Override
    Int16 abs() {
        return this < 0 ? -this : this;
    }

    @Override
    Int16 pow(Int16! n) {
        Int16 result = 1;

        while (n-- > 0) {
            result *= this;
        }

        return result;
    }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional Int16 next() {
        if (this < MaxValue) {
            return True, this + 1;
        }

        return False;
    }

    @Override
    conditional Int16 prev() {
        if (this > MinValue) {
            return True, this - 1;
        }

        return False;
    }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Convert this Int16 to an Int8.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order 8
     * bits of this Int16.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int8
     *
     * @return  an Int8 that is equivalent to this Int16
     *
     * @throws OutOfBounds if this Int16 if checkBounds is True and this Int16 does not fit within
     *         the bounds of an Int8
     */
    @Override
    Int8 toInt8(Boolean checkBounds = False) {
        assert:bounds this >= Int8.MinValue && this <= Int8.MaxValue;
        return new Int8(bits[bitLength-8 ..< bitLength]);
    }

    /**
     * Convert this Int16 to an Int16, which is effectively a no-op and returns this Int16.
     *
     * @param checkBounds  this parameter will be ignored
     *
     * @return  this Int16
     */
    @Override
    Int16 toInt16(Boolean checkBounds = False) = this;

    /**
     * Convert this Int16 to an Int32.
     *
     * Conversion is performed by sign extending this Int16 up to an Int32.
     * An Int16 will always fit within an Int32, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int32
     *
     * @return  an Int32 that is equivalent to this Int16
     */
    @Auto
    @Override
    Int32 toInt32(Boolean checkBounds = False) = new Int32(new Bit[32](i -> bits[i < 32-bitLength ? 0 : i]));

    /**
     * Convert this Int16 to an Int64.
     *
     * Conversion is performed by sign extending this Int16 up to an Int64.
     * An Int16 will always fit within an Int64, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int64
     *
     * @return  an Int64 that is equivalent to this Int16
     */
    @Auto
    @Override
    Int64 toInt64(Boolean checkBounds = False) = new Int64(new Bit[64](i -> bits[i < 64-bitLength ? 0 : i]));

    /**
     * Convert this Int16 to an Int128.
     *
     * Conversion is performed by sign extending this Int16 up to an Int128.
     * An Int16 will always fit within an Int128, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int128
     *
     * @return  an Int128 that is equivalent to this Int16
     */
    @Auto
    @Override
    Int128 toInt128(Boolean checkBounds = False) = new Int128(new Bit[128](i -> bits[i < 128-bitLength ? 0 : i]));

    /**
     * Convert this Int16 to a UInt8.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order 8
     * bits of this Int16.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt8
     *
     * @return  a UInt8 that is equivalent to this Int16
     *
     * @throws OutOfBounds if checkBounds is True and this Int16 is negative, or does not fit within
     *         the bounds of a UInt8
     */
    @Override
    UInt8 toUInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= UInt8.MinValue && this <= UInt8.MaxValue;
        return new UInt8(bits[bitLength-8 ..< bitLength]);
    }

    /**
     * Convert this Int16 to a UInt16.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the bit pattern
     * of this Int16, the high-order bit loses its function as a sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt16
     *
     * @return  a UInt16 that is equivalent to this Int16
     *
     * @throws OutOfBounds if this Int16 is negative and checkBounds is True
     */
    @Override
    UInt16 toUInt16(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= 0;
        return new UInt16(bits);
    }

    /**
     * Convert this Int16 to a UInt32.
     *
     * Conversion is performed after optionally checking the bounds, by sign-extending this Int16
     * to an Int32 converting to a UInt32, the high-order bit loses its function as a sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an UInt32
     *
     * @return  a UInt32 that is equivalent to this Int16
     *
     * @throws OutOfBounds if this Int16 is negative and checkBounds is True
     */
    @Override
    UInt32 toUInt32(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= 0;
        return new UInt32(new Bit[32](i -> (i < 32-bitLength ? 0 : bits[i])));
    }

    /**
     * Convert this Int16 to a UInt64.
     *
     * Conversion is performed after optionally checking the bounds, by sign-extending this Int16
     * to an Int64 converting to a UInt64, the high-order bit loses its function as a sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an UInt64
     *
     * @return  a UInt64 that is equivalent to this Int16
     *
     * @throws OutOfBounds if this Int16 is negative and checkBounds is True
     */
    @Override
    UInt64 toUInt64(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= 0;
        return new UInt64(new Bit[64](i -> (i < 64-bitLength ? 0 : bits[i])));
    }

    /**
     * Convert this Int16 to a UInt128.
     *
     * Conversion is performed after optionally checking the bounds, by sign-extending this Int16
     * to an Int128 converting to a UInt128, the high-order bit loses its function as a sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an UInt128
     *
     * @return  a UInt128 that is equivalent to this Int16
     *
     * @throws OutOfBounds if this Int16 is negative and checkBounds is True
     */
    @Override
    UInt128 toUInt128(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this >= 0;
        return new UInt128(new Bit[128](i -> (i < 128-bitLength ? 0 : bits[i])));
    }
}