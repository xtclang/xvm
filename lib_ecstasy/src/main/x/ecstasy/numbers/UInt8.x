/**
 * UInt8, also known as "Byte", is an 8-bit unsigned integer value.
 */
const UInt8
        extends UIntNumber
        default(0) {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an UInt8.
     */
    static IntLiteral MinValue = 0;

    /**
     * The maximum value for an UInt8.
     */
    static IntLiteral MaxValue = 0xFF;


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength() {
        return True, 8;
    }

    @Override
    static UInt8 zero() {
        return 0;
    }

    @Override
    static UInt8 one() {
        return 1;
    }

    @Override
    static conditional Range<UInt8> range() {
        return True, MinValue..MaxValue;
    }


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an 8-bit unsigned integer number from its bitwise machine representation.
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
     * Construct an 8-bit unsigned integer number from its network-portable representation.
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
     * Construct an 8-bit unsigned integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text) {
        construct UInt8(new IntLiteral(text).toUInt8().bits);
    }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get() {
        return this == 0 ? Zero : Positive;
    }

    /**
     * For a byte that represents the most significant byte of a 2's complement value, provide the
     * byte that would be used to sign-extend the value when adding more significant bytes.
     */
    Byte signExtend.get() {
        return this & 0x80 == 0 ? 0x00 : 0xFF;
    }

    /**
     * The high nibble of the byte.
     */
    Nibble highNibble.get() {
        return (this >>> 4).toNibble();
    }

    /**
     * The low nibble of the byte.
     */
    Nibble lowNibble.get() {
        return toNibble(True);
    }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("+")
    UInt8 add(UInt8! n) {
        return this + n;
    }

    @Override
    @Op("-")
    UInt8 sub(UInt8! n) {
        return this - n;
    }

    @Override
    @Op("*")
    UInt8 mul(UInt8! n) {
        return this * n;
    }

    @Override
    @Op("/")
    UInt8 div(UInt8! n) {
        return this / n;
    }

    @Override
    @Op("%")
    UInt8 mod(UInt8! n) {
        return this % n;
    }

    @Override
    UInt8 pow(UInt8! n) {
        UInt8 result = 1;

        while (n-- > 0) {
            result *= this;
        }

        return result;
    }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional UInt8 next() {
        if (this < MaxValue) {
            return True, this + 1;
        }

        return False;
    }

    @Override
    conditional UInt8 prev() {
        if (this > MinValue) {
            return True, this - 1;
        }

        return False;
    }


    // ----- conversions ---------------------------------------------------------------------------

    /**
     * Convert this UInt8 to an Int8.
     *
     * Conversion is performed after optionally checking the bounds, by preserving the bit pattern
     * of this Int8. The high-order bit becomes the sign bit.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int8
     *
     * @return  an Int8 that has the same bit pattern as this UInt8
     */
    @Override
    Int8 toInt8(Boolean checkBounds = False) {
        assert:bounds !checkBounds || this <= Int8.MaxValue;
        return new Int8(bits);
    }

    /**
     * Convert this UInt8 to an Int16.
     *
     * Conversion is performed by zero-extending this UInt8 up to an Int16.
     * A UInt8 will always fit within an Int16, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int16
     *
     * @return  an Int16 that is equivalent to this Int8
     */
    @Auto
    @Override
    Int16 toInt16(Boolean checkBounds = False) = new Int16(new Bit[16](i -> i < 16-bitLength ? 0 : bits[i]));

    /**
     * Convert this UInt8 to an Int32.
     *
     * Conversion is performed by zero-extending this UInt8 up to an Int32.
     * A UInt8 will always fit within an Int32, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int32
     *
     * @return  an Int32 that is equivalent to this Int8
     */
    @Auto
    @Override
    Int32 toInt32(Boolean checkBounds = False) = new Int32(new Bit[32](i -> i < 32-bitLength ? 0 : bits[i]));

    /**
     * Convert this UInt8 to an Int64.
     *
     * Conversion is performed by zero-extending this UInt8 up to an Int64.
     * A UInt8 will always fit within an Int64, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int64
     *
     * @return  an Int64 that is equivalent to this Int8
     */
    @Auto
    @Override
    Int64 toInt64(Boolean checkBounds = False) = new Int64(new Bit[64](i -> i < 64-bitLength ? 0 : bits[i]));

    /**
     * Convert this UInt8 to an Int128.
     *
     * Conversion is performed by zero-extending this UInt8 up to an Int128.
     * A UInt8 will always fit within an Int128, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of an Int128
     *
     * @return  an Int128 that is equivalent to this Int8
     */
    @Auto
    @Override
    Int128 toInt128(Boolean checkBounds = False) = new Int128(new Bit[128](i -> i < 128-bitLength ? 0 : bits[i]));

    /**
     * Convert this UInt8 to an UInt8, which is effectively a no-op and returns this UInt8.
     *
     * @param checkBounds  this parameter will be ignored
     *
     * @return  this UInt8
     */
    @Override
    UInt8 toUInt8(Boolean checkBounds = False) = this;

    /**
     * Convert this UInt8 to an UInt16.
     *
     * Conversion is performed by sign extending this UInt8 up to an UInt16.
     * A UInt8 will always fit within an Int16, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a Unt16
     *
     * @return  an UInt16 that is equivalent to this UInt8
     */
    @Auto
    @Override
    UInt16 toUInt16(Boolean checkBounds = False) = new UInt16(new Bit[16](i -> i < 16-bitLength ? 0 : bits[i]));

    /**
     * Convert this UInt8 to an UInt32.
     *
     * Conversion is performed by sign extending this UInt8 up to an UInt32.
     * A UInt8 will always fit within an Int32, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt32
     *
     * @return  an UInt32 that is equivalent to this UInt8
     */
    @Auto
    @Override
    UInt32 toUInt32(Boolean checkBounds = False) =  new UInt32(new Bit[32](i -> i < 32-bitLength ? 0 : bits[i]));

    /**
     * Convert this UInt8 to an UInt64.
     *
     * Conversion is performed by sign extending this UInt8 up to an UInt64.
     * A UInt8 will always fit within an Int64, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt64
     *
     * @return  an UInt64 that is equivalent to this UInt8
     */
    @Auto
    @Override
    UInt64 toUInt64(Boolean checkBounds = False) = new UInt64(new Bit[64](i -> i < 64-bitLength ? 0 : bits[i]));

    /**
     * Convert this UInt8 to an UInt128.
     *
     * Conversion is performed by sign extending this UInt8 up to an UInt128.
     * A UInt8 will always fit within an Int128, so the checkBounds parameter is effectively ignored.
     *
     * @param checkBounds  whether to check whether the result fits within the bounds of a UInt128
     *
     * @return  an UInt128 that is equivalent to this UInt8
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
            (UInt8 left, UInt8 digit) = this /% 10;
            if (left.sign != Zero) {
                left.appendTo(buf);
            }
            buf.add(Digits[digit]);
        }
        return buf;
    }

    private static UInt8[] sizeArray =
         [
         9, 99, 255
         ];
}