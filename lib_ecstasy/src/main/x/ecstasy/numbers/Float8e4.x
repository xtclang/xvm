/**
 * Float8e4 is an 8-bit floating point number, known as "E4M3" in the proposed FP8 standard, and is
 * intended primarily for machine learning use cases. Like [BFloat16], the motivating factor is the
 * doubling of the number of data points in a machine learning model by halving the size of each
 * data point, trading off some amount of precision on each data point. It is composed of a sign
 * bit, 4 exponent bits (as implied by the name), and 3 mantissa bits. With such a small mantissa
 * (and so few bits in total), these values are almost useless for most purposes, but can be quite
 * effective in large machine learning models. A similar type, [Float8e5], adds one bit of exponent
 * in exchange for losing one bit of mantissa.
 */
const Float8e4
        extends BinaryFPNumber
        default(0.0)
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an 8-bit E4M3 binary floating point number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits)
        {
        assert:bounds bits.size == 8;
        super(bits);
        }

    /**
     * Construct an 8-bit E4M3 binary floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes)
        {
        assert:bounds bytes.size == 1;
        super(bytes);
        }

    /**
     * Construct an 8-bit E4M3 binary floating point number from its `String` representation.
     *
     * @param text  a floating point number, in text format
     */
    @Override
    construct(String text)
        {
        construct Float8e4(new FPLiteral(text).toFloat8e4().bits);
        }


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength()
        {
        return True, 8;
        }

    @Override
    static Float8e4 zero()
        {
        return 0.0;
        }

    @Override
    static Float8e4 one()
        {
        return 1.0;
        }


    // ----- Number properties ---------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        if (bits.toByte() & 0b01111111 == 0)
            {
            return Zero;
            }

        return bits[0] == 1 ? Negative : Positive;
        }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op Float8e4 add(Float8e4 n)
        {
        TODO
        }

    @Override
    @Op Float8e4 sub(Float8e4 n)
        {
        TODO
        }

    @Override
    @Op Float8e4 mul(Float8e4 n)
        {
        TODO
        }

    @Override
    @Op Float8e4 div(Float8e4 n)
        {
        TODO
        }

    @Override
    @Op Float8e4 mod(Float8e4 n)
        {
        TODO
        }

    @Override
    Float8e4 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    @Op Float8e4 neg()
        {
        TODO
        }

    @Override
    Float8e4 pow(Float8e4 n)
        {
        TODO
        }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    Int emax.get()
        {
        return 8;
        }

    @Override
    Int emin.get()
        {
        return -6;
        }

    @Override
    Int bias.get()
        {
        return 7;
        }

    @Override
    Int significandBitLength.get()
        {
        return 3;
        }

    @Override
    Int exponentBitLength.get()
        {
        return 4;
        }


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    (Boolean negative, Int significand, Int exponent) split()
        {
        TODO
        }

    @Override
    Float8e4 round(Rounding direction = TiesToAway)
        {
        TODO
        }

    @Override
    Float8e4 floor()
        {
        TODO
        }

    @Override
    Float8e4 ceil()
        {
        TODO
        }

    @Override
    Float8e4 exp()
        {
        TODO
        }

    @Override
    Float8e4 scaleByPow(Int n)
        {
        TODO
        }

    @Override
    Float8e4 log()
        {
        TODO
        }

    @Override
    Float8e4 log2()
        {
        TODO
        }

    @Override
    Float8e4 log10()
        {
        TODO
        }

    @Override
    Float8e4 sqrt()
        {
        TODO
        }

    @Override
    Float8e4 cbrt()
        {
        TODO
        }

    @Override
    Float8e4 sin()
        {
        TODO
        }

    @Override
    Float8e4 cos()
        {
        TODO
        }

    @Override
    Float8e4 tan()
        {
        TODO
        }

    @Override
    Float8e4 asin()
        {
        TODO
        }

    @Override
    Float8e4 acos()
        {
        TODO
        }

    @Override
    Float8e4 atan()
        {
        TODO
        }

    @Override
    Float8e4 atan2(Float8e4 y)
        {
        TODO
        }

    @Override
    Float8e4 sinh()
        {
        TODO
        }

    @Override
    Float8e4 cosh()
        {
        TODO
        }

    @Override
    Float8e4 tanh()
        {
        TODO
        }

    @Override
    Float8e4 asinh()
        {
        TODO
        }

    @Override
    Float8e4 acosh()
        {
        TODO
        }

    @Override
    Float8e4 atanh()
        {
        TODO
        }

    @Override
    Float8e4 deg2rad()
        {
        TODO
        }

    @Override
    Float8e4 rad2deg()
        {
        TODO
        }

    @Override
    Float8e4 nextUp()
        {
        TODO
        }

    @Override
    Float8e4 nextDown()
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
    Float8e4 toFloat8e4()
        {
        return this;
        }

    @Override
    Float8e5 toFloat8e5();

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