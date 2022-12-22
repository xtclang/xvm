const UInt32
        extends UIntNumber
        incorporates Bitwise
        default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an UInt32.
     */
    static IntLiteral MinValue = 0;

    /**
     * The maximum value for an UInt32.
     */
    static IntLiteral MaxValue =  0xFFFFFFFF;


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength()
        {
        return True, 32;
        }

    @Override
    static UInt32 zero()
        {
        return 0;
        }

    @Override
    static UInt32 one()
        {
        return 1;
        }

    @Override
    static conditional Range<UInt32> range()
        {
        return True, MinValue..MaxValue;
        }


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 32-bit unsigned integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits)
        {
        assert bits.size == 32;
        super(bits);
        }

    /**
     * Construct a 32-bit unsigned integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes)
        {
        assert bytes.size == 4;
        super(bytes);
        }

    /**
     * Construct a 32-bit unsigned integer number from its `String` representation.
     *
     * @param text  an integer number, in text format
     */
    @Override
    construct(String text)
        {
        construct UInt32(new IntLiteral(text).toUInt32().bits);
        }


    // ----- properties ----------------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        return this == 0 ? Zero : Positive;
        }


    // ----- operations ----------------------------------------------------------------------------

    @Override
    @Op("+")
    UInt32 add(UInt32! n)
        {
        return this + n;
        }

    @Override
    @Op("-")
    UInt32 sub(UInt32! n)
        {
        return this - n;
        }

    @Override
    @Op("*")
    UInt32 mul(UInt32! n)
        {
        return this * n;
        }

    @Override
    @Op("/")
    UInt32 div(UInt32! n)
        {
        return this / n;
        }

    @Override
    @Op("%")
    UInt32 mod(UInt32! n)
        {
        return this % n;
        }

    @Override
    UInt32 pow(UInt32! n)
        {
        UInt32 result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }


    // ----- Sequential interface ------------------------------------------------------------------

    @Override
    conditional UInt32 next()
        {
        if (this < MaxValue)
            {
            return True, this + 1;
            }

        return False;
        }

    @Override
    conditional UInt32 prev()
        {
        if (this > MinValue)
            {
            return True, this - 1;
            }

        return False;
        }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    (UInt32 - Unchecked) toChecked()
        {
        return this.is(Unchecked) ? new UInt32(bits) : this;
        }

    @Override
    @Unchecked UInt32 toUnchecked()
        {
        return this.is(Unchecked) ? this : new @Unchecked UInt32(bits);
        }

    @Auto
    @Override
    Xnt toInt(Boolean truncate = False, Rounding direction = TowardZero);

    @Auto
    @Override
    UInt toUInt(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    Int8 toInt8(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= Int8.MaxValue;
        return new Int8(bits[bitLength-8 ..< bitLength]);
        }

    @Override
    Int16 toInt16(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= Int16.MaxValue;
        return new Int16(bits[bitLength-16 ..< bitLength]);
        }

    @Override
    Int32 toInt32(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= Int32.MaxValue;
        return new Int32(bits);
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
        return bits[0] == 0 ? new IntN(bits) : toUIntN().toIntN();
        }

    @Override
    UInt8 toUInt8(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= UInt8.MaxValue;
        return new UInt8(bits[bitLength-8 ..< bitLength]);
        }

    @Override
    UInt16 toUInt16(Boolean truncate = False, Rounding direction = TowardZero)
        {
        assert:bounds this <= UInt16.MaxValue;
        return new UInt16(bits[bitLength-16 ..< bitLength]);
        }

    @Override
    UInt32 toUInt32(Boolean truncate = False, Rounding direction = TowardZero)
        {
        return this;
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
            (UInt32 left, UInt32 digit) = this /% 10;
            if (left.sign != Zero)
                {
                left.appendTo(buf);
                }
            buf.add(DIGITS[digit]);
            }
        return buf;
        }

    // MaxValue = 4_294_967_295 (10 digits)
    private static UInt32[] sizeArray =
         [
         9, 99, 999, 9_999, 99_999, 999_999,
         9_999_999, 99_999_999, 999_999_999,
         4_294_967_295
         ];
     }