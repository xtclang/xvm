const DecN
        extends DecimalFPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a variable-length decimal floating point number from its bitwise machine
     * representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size >= 32 && bits.size.bitCount == 1;
        construct DecimalFPNumber(bits);
        }

    /**
     * Construct a variable-length decimal floating point number from its network-portable
     * representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size >= 4 && bytes.size.bitCount == 1;
        construct DecimalFPNumber(bytes);
        }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op DecN add(DecN n)
        {
        TODO
        }

    @Override
    @Op DecN sub(DecN n)
        {
        TODO
        }

    @Override
    @Op DecN mul(DecN n)
        {
        TODO
        }

    @Override
    @Op DecN div(DecN n)
        {
        TODO
        }

    @Override
    @Op DecN mod(DecN n)
        {
        TODO
        }

    @Override
    DecN abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    @Op DecN neg()
        {
        TODO
        }

    @Override
    DecN pow(DecN n)
        {
        TODO
        }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    @RO IntN emax.get()
        {
        // from IEEE 754-2008:
        //   w    = k/16+4
        //   emax = 3×2^(w−1)
        return 3 * (1 << byteLength / 16 + 3);
        }

    @Override
    IntN emin.get()
        {
        return 1 - emax;
        }

    @Override
    IntN bias.get()
        {
        // from IEEE 754-2008:
        //   emax+p−2
        return emax + precision - 2;
        }


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    (Boolean signBit, IntN significand, IntN exponent) split()
        {
        TODO
        }

    @Override
    DecN round(Rounding direction = TiesToAway)
        {
        TODO
        }

    @Override
    DecN floor()
        {
        TODO
        }

    @Override
    DecN ceil()
        {
        TODO
        }

    @Override
    DecN exp()
        {
        TODO
        }

    @Override
    DecN scaleByPow(Int n)
        {
        TODO
        }

    @Override
    DecN log()
        {
        TODO
        }

    @Override
    DecN log2()
        {
        TODO
        }

    @Override
    DecN log10()
        {
        TODO
        }

    @Override
    DecN sqrt()
        {
        TODO
        }

    @Override
    DecN cbrt()
        {
        TODO
        }

    @Override
    DecN sin()
        {
        TODO
        }

    @Override
    DecN cos()
        {
        TODO
        }

    @Override
    DecN tan()
        {
        TODO
        }

    @Override
    DecN asin()
        {
        TODO
        }

    @Override
    DecN acos()
        {
        TODO
        }

    @Override
    DecN atan()
        {
        TODO
        }

    @Override
    DecN atan2(DecN y)
        {
        TODO
        }

    @Override
    DecN sinh()
        {
        TODO
        }

    @Override
    DecN cosh()
        {
        TODO
        }

    @Override
    DecN tanh()
        {
        TODO
        }

    @Override
    DecN asinh()
        {
        TODO
        }

    @Override
    DecN acosh()
        {
        TODO
        }

    @Override
    DecN atanh()
        {
        TODO
        }

    @Override
    DecN deg2rad()
        {
        TODO
        }

    @Override
    DecN rad2deg()
        {
        TODO
        }

    @Override
    DecN nextUp()
        {
        TODO
        }

    @Override
    DecN nextDown()
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
    FloatN toFloatN()
        {
        TODO
        }

    @Override
    DecN! toDecN()
        {
        return this;
        }
    }
