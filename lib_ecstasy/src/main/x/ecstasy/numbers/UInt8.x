/**
 * UInt8, also known as "Byte", is an 8-bit unsigned integer value.
 */
const UInt8
        extends UIntNumber
        default(0)
    {
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
    static conditional Int fixedBitLength()
        {
        return True, 8;
        }

    @Override
    static UInt8 zero()
        {
        return 0;
        }

    @Override
    static UInt8 one()
        {
        return 1;
        }

    @Override
    static conditional Range<UInt8> range()
        {
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
    construct(Bit[] bits)
        {
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
    construct(Byte[] bytes)
        {
        assert bytes.size == 1;
        super(bytes);
        }

    /**
     * Construct an 8-bit unsigned integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text)
        {
        construct UInt8(new IntLiteral(text).toUInt8().bits);
        }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        return this == 0 ? Zero : Positive;
        }

    /**
     * For a byte that represents the most significant byte of a 2's complement value, provide the
     * byte that would be used to sign-extend the value when adding more significant bytes.
     */
    Byte signExtend.get()
        {
        return this & 0x80 == 0 ? 0x00 : 0xFF;
        }

    @Override
    UInt8 leftmostBit.get()
        {
        TODO
        }

    @Override
    UInt8 rightmostBit.get()
        {
        TODO
        }

    /**
     * The high nibble of the byte.
     */
    Nibble highNibble.get()
        {
        return (this >>> 4).toNibble();
        }

    /**
     * The low nibble of the byte.
     */
    Nibble lowNibble.get()
        {
        return toNibble(True);
        }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("+") UInt8 add(UInt8! n)
        {
        return this + n;
        }

    @Override
    @Op("-") UInt8 sub(UInt8! n)
        {
        return this - n;
        }

    @Override
    @Op("*") UInt8 mul(UInt8! n)
        {
        return this * n;
        }

    @Override
    @Op("/") UInt8 div(UInt8! n)
        {
        return this / n;
        }

    @Override
    @Op("%") UInt8 mod(UInt8! n)
        {
        return this % n;
        }

    @Override
    @Op("&") UInt8 and(UInt8! n)
        {
        return new UInt8(this.bits & n.bits);
        }

    @Override
    @Op("|") UInt8 or(UInt8! n)
        {
        return new UInt8(this.bits | n.bits);
        }

    @Override
    @Op("^") UInt8 xor(UInt8! n)
        {
        return new UInt8(this.bits ^ n.bits);
        }

    @Override
    @Op("~") UInt8 not()
        {
        return new UInt8(~bits);
        }

    @Override
    @Op("<<") UInt8 shiftLeft(Int count)
        {
        return new UInt8(bits << count);
        }

    @Override
    @Op(">>") UInt8 shiftRight(Int count)
        {
        return new UInt8(bits >> count);
        }

    @Override
    @Op(">>>") UInt8 shiftAllRight(Int count)
        {
        return new UInt8(bits >>> count);
        }

    @Override
    UInt8 rotateLeft(Int count)
        {
        return new UInt8(bits.rotateLeft(count));
        }

    @Override
    UInt8 rotateRight(Int count)
        {
        return new UInt8(bits.rotateRight(count));
        }

    @Override
    UInt8 retainLSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new UInt8(bits.fill(0, 0 ..< bitLength-count));
        }

    @Override
    UInt8 retainMSBits(Int count)
        {
        if (count <= 0)
            {
            return 0;
            }

        if (count >= bitLength)
            {
            return this;
            }

        return new UInt8(bits.fill(0, count ..< bitLength));
        }

    @Override
    UInt8 reverseBits()
        {
        return new UInt8(bits.reversed());
        }

    @Override
    UInt8 reverseBytes()
        {
        return this;
        }

    @Override
    UInt8 pow(UInt8! n)
        {
        UInt8 result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional UInt8 next()
        {
        if (this < MaxValue)
            {
            return True, this + 1;
            }

        return False;
        }

    @Override
    conditional UInt8 prev()
        {
        if (this > MinValue)
            {
            return True, this - 1;
            }

        return False;
        }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    (UInt8 - Unchecked) toChecked()
        {
        return this.is(Unchecked) ? new UInt8(bits) : this;
        }

    @Override
    @Unchecked UInt8 toUnchecked()
        {
        return this.is(Unchecked) ? this : new @Unchecked UInt8(bits);
        }

    @Override
    Int8 toInt8(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= Int8.MaxValue;
        return new Int8(bits);
        }

    @Auto
    @Override
    Int16 toInt16(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return new Int16(new Bit[16](i -> i < 16-bitLength ? 0 : bits[i]));
        }

    @Auto
    @Override
    Int32 toInt32(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return new Int32(new Bit[32](i -> i < 32-bitLength ? 0 : bits[i]));
        }

    @Auto
    @Override
    Int64 toInt64(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return new Int64(new Bit[64](i -> i < 64-bitLength ? 0 : bits[i]));
        }

    @Auto
    @Override
    Int128 toInt128(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return new Int128(new Bit[128](i -> i < 128-bitLength ? 0 : bits[i]));
        }

    @Auto
    @Override
    IntN toIntN(Rounding direction = TowardZero)
        {
        return bits[0] == 0 ? new IntN(bits) : toUInt16().toIntN();
        }

    @Override
    UInt8 toUInt8(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return this;
        }

    @Auto
    @Override
    UInt16 toUInt16(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return new UInt16(new Bit[16](i -> i < 16-bitLength ? 0 : bits[i]));
        }

    @Auto
    @Override
    UInt32 toUInt32(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return new UInt32(new Bit[32](i -> i < 32-bitLength ? 0 : bits[i]));
        }

    @Auto
    @Override
    UInt64 toUInt64(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return new UInt64(new Bit[64](i -> i < 64-bitLength ? 0 : bits[i]));
        }

    @Auto
    @Override
    UInt128 toUInt128(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return new UInt128(new Bit[128](i -> i < 128-bitLength ? 0 : bits[i]));
        }

    @Auto
    @Override
    UIntN toUIntN(Rounding direction = TowardZero)
        {
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
    FloatN toFloatN()
        {
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
    DecN toDecN()
        {
        return toIntLiteral().toDecN();
        }


    // ----- Stringable implementation -------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        return calculateStringSize(this, sizeArray);
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        if (sign == Zero)
            {
            buf.add('0');
            }
        else
            {
            (UInt8 left, UInt8 digit) = this /% 10;
            if (left.sign != Zero)
                {
                left.appendTo(buf);
                }
            buf.add(DIGITS[digit]);
            }
        return buf;
        }

    private static UInt8[] sizeArray =
         [
         9, 99, 255
         ];
    }