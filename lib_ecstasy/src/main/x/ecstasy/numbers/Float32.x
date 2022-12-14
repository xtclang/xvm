const Float32
        extends BinaryFPNumber
        default(0.0)
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 32-bit binary floating point number (a "single float") from its bitwise machine
     * representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits)
        {
        assert:bounds bits.size == 32;
        super(bits);
        }

    /**
     * Construct a 32-bit binary floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes)
        {
        assert:bounds bytes.size == 4;
        super(bytes);
        }

    /**
     * Construct a 32-bit binary floating point number from its `String` representation.
     *
     * @param text  a floating point number, in text format
     */
    @Override
    construct(String text)
        {
        construct Float32(new FPLiteral(text).toFloat32().bits);
        }


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static conditional Int fixedBitLength()
        {
        return True, 32;
        }

    @Override
    static Float32 zero()
        {
        return 0.0;
        }

    @Override
    static Float32 one()
        {
        return 1.0;
        }


    // ----- Number properties ---------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        TODO need to think this through carefully because there is a sign bit and both +/-0
        }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op Float32 add(Float32 n)
        {
        TODO
        }

    @Override
    @Op Float32 sub(Float32 n)
        {
        TODO
        }

    @Override
    @Op Float32 mul(Float32 n)
        {
        TODO
        }

    @Override
    @Op Float32 div(Float32 n)
        {
        TODO
        }

    @Override
    @Op Float32 mod(Float32 n)
        {
        TODO
        }

    @Override
    Float32 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    @Op Float32 neg()
        {
        TODO
        }

    @Override
    Float32 pow(Float32 n)
        {
        TODO
        }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    Int emax.get()
        {
        return 127;
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
    Float32 round(Rounding direction = TiesToAway)
        {
        TODO
        }

    @Override
    Float32 floor()
        {
        TODO
        }

    @Override
    Float32 ceil()
        {
        TODO
        }

    @Override
    Float32 exp()
        {
        TODO
        }

    @Override
    Float32 scaleByPow(Int n)
        {
        TODO
        }

    @Override
    Float32 log()
        {
        TODO
        }

    @Override
    Float32 log2()
        {
        TODO
        }

    @Override
    Float32 log10()
        {
        TODO
        }

    @Override
    Float32 sqrt()
        {
        TODO
        }

    @Override
    Float32 cbrt()
        {
        TODO
        }

    @Override
    Float32 sin()
        {
        TODO
        }

    @Override
    Float32 cos()
        {
        TODO
        }

    @Override
    Float32 tan()
        {
        TODO
        }

    @Override
    Float32 asin()
        {
        TODO
        }

    @Override
    Float32 acos()
        {
        TODO
        }

    @Override
    Float32 atan()
        {
        TODO
        }

    @Override
    Float32 atan2(Float32 y)
        {
        TODO
        }

    @Override
    Float32 sinh()
        {
        TODO
        }

    @Override
    Float32 cosh()
        {
        TODO
        }

    @Override
    Float32 tanh()
        {
        TODO
        }

    @Override
    Float32 asinh()
        {
        TODO
        }

    @Override
    Float32 acosh()
        {
        TODO
        }

    @Override
    Float32 atanh()
        {
        TODO
        }

    @Override
    Float32 deg2rad()
        {
        TODO
        }

    @Override
    Float32 rad2deg()
        {
        TODO
        }

    @Override
    Float32 nextUp()
        {
        TODO
        }

    @Override
    Float32 nextDown()
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
    Float8e4 toFloat8e4();

    @Override
    Float8e5 toFloat8e5();

    @Override
    BFloat16 toBFloat16();

    @Override
    Float16 toFloat16();

    @Override
    Float32 toFloat32()
        {
        return this;
        }

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