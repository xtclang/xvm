const UInt128
        extends UIntNumber
        default(0) {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an UInt128.
     */
    static IntLiteral MinValue = 0;

    /**
     * The maximum value for an UInt128.
     */
    static IntLiteral MaxValue =  0xFFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF;


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength() {
        return True, 128;
    }

    @Override
    static UInt128 zero() {
        return 0;
    }

    @Override
    static UInt128 one() {
        return 1;
    }

    @Override
    static conditional Range<UInt128> range() {
        return True, MinValue..MaxValue;
    }


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 128-bit unsigned integer number from its bitwise machine representation.
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
     * Construct a 128-bit unsigned integer number from its network-portable representation.
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
     * Construct a 128-bit unsigned integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text) {
        construct UInt128(new IntLiteral(text).toUInt128().bits);
    }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get() {
        return this == 0 ? Zero : Positive;
    }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("+")
    UInt128 add(UInt128! n) {
        return this + n;
    }

    @Override
    @Op("-")
    UInt128 sub(UInt128! n) {
        return this - n;
    }

    @Override
    @Op("*")
    UInt128 mul(UInt128! n) {
        return this * n;
    }

    @Override
    @Op("/")
    UInt128 div(UInt128! n) {
        return this / n;
    }

    @Override
    @Op("%")
    UInt128 mod(UInt128! n) {
        return this % n;
    }

    @Override
    UInt128 pow(UInt128! n) {
        UInt128 result = 1;

        while (n-- > 0) {
            result *= this;
        }

        return result;
    }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional UInt128 next() {
        if (this < MaxValue) {
            return True, this + 1;
        }

        return False;
    }

    @Override
    conditional UInt128 prev() {
        if (this > MinValue) {
            return True, this - 1;
        }

        return False;
    }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Convert this UInt128 to an Int8.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order
     * 8 bits bits of this UInt128. The high-order bit becomes the sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int8
     *
     * @return  an Int8 that has the same bit pattern as the low order 8 bits of this UInt128
     *
     * @throws OutOfBounds if checkBounds is True and this UInt128 is higher then Int8.MaxValue
     */
    @Override
    Int8 toInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= Int8.MaxValue;
        return new Int8(bits[bitLength-8 ..< bitLength]);
    }

    /**
     * Convert this UInt128 to an Int16.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order
     * 16 bits bits of this UInt128. The high-order bit becomes the sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int16
     *
     * @return  an Int16 that has the same bit pattern as the low order 16 bits of this UInt128
     *
     * @throws OutOfBounds if checkBounds is True and this UInt128 is higher then Int16.MaxValue
     */
    @Override
    Int16 toInt16(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= Int16.MaxValue;
        return new Int16(bits[bitLength-16 ..< bitLength]);
    }

    /**
     * Convert this UInt128 to an Int32.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order
     * 32 bits bits of this UInt128. The high-order bit becomes the sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int32
     *
     * @return  an Int32 that has the same bit pattern as the low order 32 bits of this UInt128
     *
     * @throws OutOfBounds if checkBounds is True and this UInt128 is higher then Int32.MaxValue
     */
    @Override
    Int32 toInt32(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= Int32.MaxValue;
        return new Int32(bits[bitLength-32 ..< bitLength]);
    }

    /**
     * Convert this UInt128 to an Int64.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order
     * 64 bits bits of this UInt128. The high-order bit becomes the sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int64
     *
     * @return  an Int64 that has the same bit pattern as the low order 64 bits of this UInt128
     *
     * @throws OutOfBounds if checkBounds is True and this UInt128 is higher then Int64.MaxValue
     */
    @Override
    Int64 toInt64(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= Int64.MaxValue;
        return new Int64(bits[bitLength-64 ..< bitLength]);
    }

    /**
     * Convert this UInt128 to an Int128.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the bit pattern
     * of this UInt128. The high-order bit becomes the sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int128
     *
     * @return  an Int128 that has the same bit pattern as this UInt128
     */
    @Override
    Int128 toInt128(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= Int128.MaxValue;
        return new Int128(bits);
    }

    /**
     * Convert this UInt128 to an UInt8.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order
     * 8 bits bits of this UInt128.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt8
     *
     * @return  an Int8 that has the same bit pattern as the low order 8 bits of this UInt128
     *
     * @throws OutOfBounds if checkBounds is True and this UInt128 is higher then Int8.MaxValue
     */
    @Override
    UInt8 toUInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= UInt8.MaxValue;
        return new UInt8(bits[bitLength-8 ..< bitLength]);
    }

    /**
     * Convert this UInt128 to an UInt16.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order
     * 16 bits bits of this UInt128.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt16
     *
     * @return  an Int16 that has the same bit pattern as the low order 16 bits of this UInt128
     *
     * @throws OutOfBounds if checkBounds is True and this UInt128 is higher then Int16.MaxValue
     */
    @Override
    UInt16 toUInt16(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= UInt16.MaxValue;
        return new UInt16(bits[bitLength-16 ..< bitLength]);
    }

    /**
     * Convert this UInt128 to an UInt32.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order
     * 32 bits bits of this UInt128.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt32
     *
     * @return  an Int32 that has the same bit pattern as the low order 32 bits of this UInt128
     *
     * @throws OutOfBounds if checkBounds is True and this UInt128 is higher then Int32.MaxValue
     */
    @Override
    UInt32 toUInt32(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= UInt32.MaxValue;
        return new UInt32(bits[bitLength-32 ..< bitLength]);
    }

    /**
     * Convert this UInt128 to an UInt64.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the low-order
     * 64 bits bits of this UInt128.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt64
     *
     * @return  an Int64 that has the same bit pattern as the low order 64 bits of this UInt128
     *
     * @throws OutOfBounds if checkBounds is True and this UInt128 is higher then Int64.MaxValue
     */
    @Override
    UInt64 toUInt64(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= UInt64.MaxValue;
        return new UInt64(bits[bitLength-64 ..< bitLength]);
    }

    /**
     * Convert this UInt128 to an UInt128, which is effectively a no-op and returns this UInt128.
     *
     * @param checkBounds  this parameter will be ignored
     *
     * @return  this UInt128
     */
    @Override
    UInt128 toUInt128(Boolean checkBounds = False) = this;


    // ----- Stringable implementation -------------------------------------------------------------

    @Override
    Int estimateStringLength() {
        return calculateStringSize(this, sizeArray);
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        if (this == 0) {
            buf.add('0');
        } else {
            (UInt128 left, UInt128 digit) = this /% 10;
            if (left != 0) {
                left.appendTo(buf);
            }
            buf.add(Digits[digit.toInt64()]);
        }
        return buf;
    }

    // MaxValue = 340_282_366_920_938_463_463_374_607_431_768_211_455 (39 digits)
    private static UInt128[] sizeArray =
         [
         9, 99, 999, 9_999, 99_999, 999_999,
         9_999_999, 99_999_999, 999_999_999, 9_999_999_999, 99_999_999_999, 999_999_999_999,
         9_999_999_999_999, 99_999_999_999_999, 999_999_999_999_999,
         9_999_999_999_999_999, 99_999_999_999_999_999, 999_999_999_999_999_999,
         9_999_999_999_999_999_999, 99_999_999_999_999_999_999, 999_999_999_999_999_999_999,
           9_999_999_999_999_999_999_999,
          99_999_999_999_999_999_999_999,
         999_999_999_999_999_999_999_999,
           9_999_999_999_999_999_999_999_999,
          99_999_999_999_999_999_999_999_999,
         999_999_999_999_999_999_999_999_999,
           9_999_999_999_999_999_999_999_999_999,
          99_999_999_999_999_999_999_999_999_999,
         999_999_999_999_999_999_999_999_999_999,
           9_999_999_999_999_999_999_999_999_999_999,
          99_999_999_999_999_999_999_999_999_999_999,
         999_999_999_999_999_999_999_999_999_999_999,
           9_999_999_999_999_999_999_999_999_999_999_999,
          99_999_999_999_999_999_999_999_999_999_999_999,
         999_999_999_999_999_999_999_999_999_999_999_999,
           9_999_999_999_999_999_999_999_999_999_999_999_999,
          99_999_999_999_999_999_999_999_999_999_999_999_999,
         340_282_366_920_938_463_463_374_607_431_768_211_455
         ];
}