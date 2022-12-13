/**
 * Float16 is a 16-bit floating point number, commonly known as a "half", and technically named
 * "binary16" in the IEEE 754 floating point standard. It is composed of a sign bit, 5 exponent
 * bits, and 10 mantissa bits.
 */
const Float16
        extends BinaryFPNumber
        default(0.0)
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 16-bit binary floating point number (a "half float") from its bitwise machine
     * representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits)
        {
        assert:bounds bits.size == 16;
        super(bits);
        }

    /**
     * Construct a 16-bit binary floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes)
        {
        assert:bounds bytes.size == 2;
        super(bytes);
        }

    /**
     * Construct a 16-bit binary floating point number from its `String` representation.
     *
     * @param text  a floating point number, in text format
     */
    @Override
    construct(String text)
        {
        construct Float16(new FPLiteral(text).toFloat16().bits);
        }


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength()
        {
        return True, 16;
        }

    @Override
    static Float16 zero()
        {
        return 0.0;
        }

    @Override
    static Float16 one()
        {
        return 1.0;
        }


    // ----- Number properties ---------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        UInt16 n = bits.toUInt16();
        if (n == 0x0000 || n == 0x8000)
            {
            return Zero;
            }

        return n & 0x8000 == 0 ? Positive : Negative;
        }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op Float16 add(Float16 n)
        {
        TODO
        }

    @Override
    @Op Float16 sub(Float16 n)
        {
        TODO
        }

    @Override
    @Op Float16 mul(Float16 n)
        {
        TODO
        }

    @Override
    @Op Float16 div(Float16 n)
        {
        TODO
        }

    @Override
    @Op Float16 mod(Float16 n)
        {
        TODO
        }

    @Override
    Float16 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    @Op Float16 neg()
        {
        TODO
        }

    @Override
    Float16 pow(Float16 n)
        {
        TODO
        }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    Int emax.get()
        {
        return 15;
        }

    @Override
    Int emin.get()
        {
        return 1 - emax;
        }

    @Override
    Int bias.get()
        {
        return emax;
        }


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    (Boolean negative, Int significand, Int exponent) split()
        {
        TODO
        }

    @Override
    Float16 round(Rounding direction = TiesToAway)
        {
        TODO
        }

    @Override
    Float16 floor()
        {
        TODO
        }

    @Override
    Float16 ceil()
        {
        TODO
        }

    @Override
    Float16 exp()
        {
        TODO
        }

    @Override
    Float16 scaleByPow(Int n)
        {
        TODO
        }

    @Override
    Float16 log()
        {
        TODO
        }

    @Override
    Float16 log2()
        {
        TODO
        }

    @Override
    Float16 log10()
        {
        TODO
        }

    @Override
    Float16 sqrt()
        {
        TODO
        }

    @Override
    Float16 cbrt()
        {
        TODO
        }

    @Override
    Float16 sin()
        {
        TODO
        }

    @Override
    Float16 cos()
        {
        TODO
        }

    @Override
    Float16 tan()
        {
        TODO
        }

    @Override
    Float16 asin()
        {
        TODO
        }

    @Override
    Float16 acos()
        {
        TODO
        }

    @Override
    Float16 atan()
        {
        TODO
        }

    @Override
    Float16 atan2(Float16 y)
        {
        TODO
        }

    @Override
    Float16 sinh()
        {
        TODO
        }

    @Override
    Float16 cosh()
        {
        TODO
        }

    @Override
    Float16 tanh()
        {
        TODO
        }

    @Override
    Float16 asinh()
        {
        TODO
        }

    @Override
    Float16 acosh()
        {
        TODO
        }

    @Override
    Float16 atanh()
        {
        TODO
        }

    @Override
    Float16 deg2rad()
        {
        TODO
        }

    @Override
    Float16 rad2deg()
        {
        TODO
        }

    @Override
    Float16 nextUp()
        {
        TODO
        }

    @Override
    Float16 nextDown()
        {
        TODO
        }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    Int8 toInt8(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    Int16 toInt16(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    Int32 toInt32(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    Int64 toInt64(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    Int128 toInt128(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    IntN toIntN(Rounding direction = TowardZero)
        {
        return round(direction).toIntN();
        }

    @Override
    UInt8 toUInt8(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    UInt16 toUInt16(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    UInt32 toUInt32(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    UInt64 toUInt64(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    UInt128 toUInt128(Boolean truncate = False, Rounding direction = TowardZero);

    @Override
    UIntN toUIntN(Rounding direction = TowardZero)
        {
        return round(direction).toUIntN();
        }

    @Override
    BFloat16 toBFloat16();

    @Override
    Float16 toFloat16()
        {
        return this;
        }

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
        return new FloatN(bits);
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
        return toFPLiteral().toDecN();
        }
    }