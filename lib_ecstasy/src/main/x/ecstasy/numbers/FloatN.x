const FloatN
        extends BinaryFPNumber
        // TODO default(0.0)
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a variable-length binary floating point number from its bitwise machine
     * representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits)
        {
        assert:bounds bits.size >= 16 && bits.bitCount == 1;
        super(bits);
        }

    /**
     * Construct a variable-length binary floating point number from its network-portable
     * representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes)
        {
        assert:bounds bytes.size >= 2 && bytes.size.toUIntN().bitCount == 1;
        super(bytes);
        }

    /**
     * Construct a variable-length binary floating point number from its `String` representation.
     *
     * @param text  a floating point number, in text format
     */
    @Override
    construct(String text)
        {
        construct FloatN(new FPLiteral(text).toFloatN().bits);
        }


    // ----- Numeric funky interface ---------------------------------------------------------------

    @Override
    static FloatN zero()
        {
        TODO return 0.0;
        }

    @Override
    static FloatN one()
        {
        TODO return 1.0;
        }


    // ----- Number properties ---------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        eachBit: for (Bit bit : bits)
            {
            if (bit == 1 && !eachBit.first)
                {
                return negative ? Negative : Positive;
                }
            }
        return Zero;
        }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op FloatN add(FloatN n)
        {
        TODO
        }

    @Override
    @Op FloatN sub(FloatN n)
        {
        TODO
        }

    @Override
    @Op FloatN mul(FloatN n)
        {
        TODO
        }

    @Override
    @Op FloatN div(FloatN n)
        {
        TODO
        }

    @Override
    @Op FloatN mod(FloatN n)
        {
        TODO
        }

    @Override
    FloatN abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    @Op FloatN neg()
        {
        TODO
        }

    @Override
    FloatN pow(FloatN n)
        {
        TODO
        }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    IntN emax.get()
        {
        // 2^(k–p–1) – 1
        Int k = bitLength;
        return (1 << (k - precision - 1)) - 1;
        }

    @Override
    IntN emin.get()
        {
        return 1 - emax;
        }

    @Override
    IntN bias.get()
        {
        return emax;
        }


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    (Boolean negative, IntN significand, IntN exponent) split()
        {
        TODO
        }

    @Override
    FloatN round(Rounding direction = TiesToAway)
        {
        TODO
        }

    @Override
    FloatN floor()
        {
        TODO
        }

    @Override
    FloatN ceil()
        {
        TODO
        }

    @Override
    FloatN exp()
        {
        TODO
        }

    @Override
    FloatN scaleByPow(Int n)
        {
        TODO
        }

    @Override
    FloatN log()
        {
        TODO
        }

    @Override
    FloatN log2()
        {
        TODO
        }

    @Override
    FloatN log10()
        {
        TODO
        }

    @Override
    FloatN sqrt()
        {
        TODO
        }

    @Override
    FloatN cbrt()
        {
        TODO
        }

    @Override
    FloatN sin()
        {
        TODO
        }

    @Override
    FloatN cos()
        {
        TODO
        }

    @Override
    FloatN tan()
        {
        TODO
        }

    @Override
    FloatN asin()
        {
        TODO
        }

    @Override
    FloatN acos()
        {
        TODO
        }

    @Override
    FloatN atan()
        {
        TODO
        }

    @Override
    FloatN atan2(FloatN y)
        {
        TODO
        }

    @Override
    FloatN sinh()
        {
        TODO
        }

    @Override
    FloatN cosh()
        {
        TODO
        }

    @Override
    FloatN tanh()
        {
        TODO
        }

    @Override
    FloatN asinh()
        {
        TODO
        }

    @Override
    FloatN acosh()
        {
        TODO
        }

    @Override
    FloatN atanh()
        {
        TODO
        }

    @Override
    FloatN deg2rad()
        {
        TODO
        }

    @Override
    FloatN rad2deg()
        {
        TODO
        }

    @Override
    FloatN nextUp()
        {
        TODO
        }

    @Override
    FloatN nextDown()
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
    Float32 toFloat32();

    @Override
    Float64 toFloat64();

    @Override
    Float128 toFloat128();

    @Override
    FloatN toFloatN()
        {
        return this;
        }

    @Override
    Dec32 toDec32();

    @Override
    Dec64 toDec64();

    @Override
    Dec128 toDec128();

    @Auto
    @Override
    DecN toDecN()
        {
        return toFPLiteral().toDecN();
        }
    }