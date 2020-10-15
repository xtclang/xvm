const FloatN
        extends BinaryFPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a variable-length binary floating point number from its bitwise machine
     * representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size >= 16 && bits.size.bitCount == 1;
        construct BinaryFPNumber(bits);
        }

    /**
     * Construct a variable-length binary floating point number from its network-portable
     * representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size >= 2 && bytes.size.bitCount == 1;
        construct BinaryFPNumber(bytes);
        }


    // ----- Number properties ---------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        Boolean negative = False;
        eachBit: for (Bit bit : toBitArray())
            {
            if (bit == 1)
                {
                if (eachBit.first)
                    {
                    negative = True;
                    }
                else
                    {
                    return negative ? Negative : Positive;
                    }
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
    (Boolean signBit, IntN significand, IntN exponent) split()
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
    IntN toIntN()
        {
        TODO
        }

    @Override
    UIntN toUIntN()
        {
        TODO
        }

    @Override
    FloatN! toFloatN()
        {
        return this;
        }

    @Override
    DecN toDecN()
        {
        TODO
        }
    }
