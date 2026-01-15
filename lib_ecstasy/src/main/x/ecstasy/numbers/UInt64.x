const UInt64
        extends UIntNumber
        default(0) {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an UInt64.
     */
    static IntLiteral MinValue = 0;

    /**
     * The maximum value for an UInt64.
     */
    static IntLiteral MaxValue =  0xFFFF_FFFF_FFFF_FFFF;


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength() {
        return True, 64;
    }

    @Override
    static UInt64 zero() {
        return 0;
    }

    @Override
    static UInt64 one() {
        return 1;
    }

    @Override
    static conditional Range<UInt64> range() {
        return True, MinValue..MaxValue;
    }


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 64-bit unsigned integer number from its bitwise machine representation.
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
     * Construct a 64-bit unsigned integer number from its network-portable representation.
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
     * Construct a 64-bit unsigned integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text) {
        construct UInt64(new IntLiteral(text).toUInt64().bits);
    }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get() {
        return this == 0 ? Zero : Positive;
    }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("+")
    UInt64 add(UInt64! n) {
        return this + n;
    }

    @Override
    @Op("-")
    UInt64 sub(UInt64! n) {
        return this - n;
    }

    @Override
    @Op("*")
    UInt64 mul(UInt64! n) {
        return this * n;
    }

    @Override
    @Op("/")
    UInt64 div(UInt64! n) {
        return this / n;
    }

    @Override
    @Op("%")
    UInt64 mod(UInt64! n) {
        return this % n;
    }

    @Override
    UInt64 pow(UInt64! n) {
        UInt64 result = 1;

        while (n-- > 0) {
            result *= this;
        }

        return result;
    }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional UInt64 next() {
        if (this < MaxValue) {
            return True, this + 1;
        }

        return False;
    }

    @Override
    conditional UInt64 prev() {
        if (this > MinValue) {
            return True, this - 1;
        }

        return False;
    }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Convert this UInt64 to an Int8.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order
     * 8 bits bits of this UInt64. The high-order bit becomes the sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int8
     *
     * @return  an Int8 that has the same bit pattern as the low order 8 bits of this UInt64
     *
     * @throws OutOfBounds if checkBounds is True and this UInt64 is higher then Int8.MaxValue
     */
    @Override
    Int8 toInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= Int8.MaxValue;
        return new Int8(bits[bitLength-8 ..< bitLength]);
    }

    /**
     * Convert this UInt64 to an Int16.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order
     * 16 bits bits of this UInt64. The high-order bit becomes the sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int16
     *
     * @return  an Int16 that has the same bit pattern as the low order 16 bits of this UInt64
     *
     * @throws OutOfBounds if checkBounds is True and this UInt64 is higher then Int16.MaxValue
     */
    @Override
    Int16 toInt16(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= Int16.MaxValue;
        return new Int16(bits[bitLength-16 ..< bitLength]);
    }

    /**
     * Convert this UInt64 to an Int32.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order
     * 32 bits bits of this UInt64. The high-order bit becomes the sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int32
     *
     * @return  an Int32 that has the same bit pattern as the low order 32 bits of this UInt64
     *
     * @throws OutOfBounds if checkBounds is True and this UInt64 is higher then Int32.MaxValue
     */
    @Override
    Int32 toInt32(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= Int32.MaxValue;
        return new Int32(bits[bitLength-32 ..< bitLength]);
    }

    /**
     * Convert this UInt64 to an Int64.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the bit pattern
     * of this UInt64. The high-order bit becomes the sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int64
     *
     * @return  an Int64 that has the same bit pattern as this UInt64
     */
    @Override
    Int64 toInt64(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= Int64.MaxValue;
        return new Int64(bits);
    }

    /**
     * Convert this UInt64 to an Int128.
     *
     * Conversion is performed by zero-extending this UInt64 up to an Int128.
     * A UInt64 will always fit within an Int128, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int128
     *
     * @return  an Int128 that is equivalent to this UInt64
     */
    @Auto
    @Override
    Int128 toInt128(Boolean checkBounds = False) = new Int128(new Bit[128](i -> i < 128-bitLength ? 0 : bits[i]));

    /**
     * Convert this UInt64 to an UInt8.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order
     * 8 bits bits of this UInt64.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt8
     *
     * @return  an Int8 that has the same bit pattern as the low order 8 bits of this UInt64
     *
     * @throws OutOfBounds if checkBounds is True and this UInt64 is higher then Int8.MaxValue
     */
    @Override
    UInt8 toUInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= UInt8.MaxValue;
        return new UInt8(bits[bitLength-8 ..< bitLength]);
    }

    /**
     * Convert this UInt64 to an UInt16.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order
     * 16 bits bits of this UInt64.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt16
     *
     * @return  an Int16 that has the same bit pattern as the low order 16 bits of this UInt64
     *
     * @throws OutOfBounds if checkBounds is True and this UInt64 is higher then Int16.MaxValue
     */
    @Override
    UInt16 toUInt16(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= UInt16.MaxValue;
        return new UInt16(bits[bitLength-16 ..< bitLength]);
    }

    /**
     * Convert this UInt64 to an UInt32.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order
     * 32 bits bits of this UInt64.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt32
     *
     * @return  an Int32 that has the same bit pattern as the low order 32 bits of this UInt64
     *
     * @throws OutOfBounds if checkBounds is True and this UInt64 is higher then Int32.MaxValue
     */
    @Override
    UInt32 toUInt32(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= UInt32.MaxValue;
        return new UInt32(bits[bitLength-32 ..< bitLength]);
    }

    /**
     * Convert this UInt64 to an UInt64, which is effectively a no-op and returns this UInt64.
     *
     * @param checkBounds  this parameter will be ignored
     *
     * @return  this UInt64
     */
    @Override
    UInt64 toUInt64(Boolean checkBounds = False) = this;

    /**
     * Convert this UInt64 to an UInt128.
     *
     * Conversion is performed by sign extending this UInt64 up to an UInt128.
     * A UInt64 will always fit within an Int128, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt128
     *
     * @return  an UInt128 that is equivalent to this UInt64
     */
    @Auto
    @Override
    UInt128 toUInt128(Boolean checkBounds = False) = new UInt128(new Bit[128](i -> i < 128-bitLength ? 0 : bits[i]));


    // ----- Stringable implementation -------------------------------------------------------------

    @Override
    Int estimateStringLength() {
        return calculateStringSize(this, sizeArray);
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        if (sign == Zero) {
            buf.add('0');
        } else {
            (UInt64 left, UInt64 digit) = this /% 10;
            if (left.sign != Zero) {
                left.appendTo(buf);
            }
            buf.add(Digits[digit.toInt64()]);
        }
        return buf;
    }

    // MaxValue = 18_446_744_073_709_551_615 (20 digits)
    private static UInt64[] sizeArray =
         [
         9, 99, 999, 9_999, 99_999, 999_999,
         9_999_999, 99_999_999, 999_999_999, 9_999_999_999, 99_999_999_999, 999_999_999_999,
         9_999_999_999_999, 99_999_999_999_999, 999_999_999_999_999,
         9_999_999_999_999_999, 99_999_999_999_999_999, 999_999_999_999_999_999,
         9_999_999_999_999_999_999, 18_446_744_073_709_551_615
         ];
}