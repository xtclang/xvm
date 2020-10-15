const Dec128
        extends DecimalFPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 128-bit decimal floating point number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size == 128;
        construct DecimalFPNumber(bits);
        }

    /**
     * Construct a 128-bit decimal floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 16;
        construct DecimalFPNumber(bytes);
        }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op Dec128 add(Dec128 n)
        {
        TODO
        }

    @Override
    @Op Dec128 sub(Dec128 n)
        {
        TODO
        }

    @Override
    @Op Dec128 mul(Dec128 n)
        {
        TODO
        }

    @Override
    @Op Dec128 div(Dec128 n)
        {
        TODO
        }

    @Override
    @Op Dec128 mod(Dec128 n)
        {
        TODO
        }

    @Override
    Dec128 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    @Op Dec128 neg()
        {
        TODO
        }

    @Override
    Dec128 pow(Dec128 n)
        {
        TODO
        }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    @RO Int emax.get()
        {
        return 6144;
        }

    @Override
    Int emin.get()
        {
        return 1 - emax;
        }

    @Override
    Int bias.get()
        {
        return 6176;
        }


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    (Boolean signBit, Int128 significand, Int exponent) split()
        {
        TODO
        }

    @Override
    Dec128 round(Rounding direction = TiesToAway)
        {
        TODO
        }

    @Override
    Dec128 floor()
        {
        TODO
        }

    @Override
    Dec128 ceil()
        {
        TODO
        }

    @Override
    Dec128 exp()
        {
        TODO
        }

    @Override
    Dec128 scaleByPow(Int n)
        {
        TODO
        }

    @Override
    Dec128 log()
        {
        TODO
        }

    @Override
    Dec128 log2()
        {
        TODO
        }

    @Override
    Dec128 log10()
        {
        TODO
        }

    @Override
    Dec128 sqrt()
        {
        TODO
        }

    @Override
    Dec128 cbrt()
        {
        TODO
        }

    @Override
    Dec128 sin()
        {
        TODO
        }

    @Override
    Dec128 cos()
        {
        TODO
        }

    @Override
    Dec128 tan()
        {
        TODO
        }

    @Override
    Dec128 asin()
        {
        TODO
        }

    @Override
    Dec128 acos()
        {
        TODO
        }

    @Override
    Dec128 atan()
        {
        TODO
        }

    @Override
    Dec128 atan2(Dec128 y)
        {
        TODO
        }

    @Override
    Dec128 sinh()
        {
        TODO
        }

    @Override
    Dec128 cosh()
        {
        TODO
        }

    @Override
    Dec128 tanh()
        {
        TODO
        }

    @Override
    Dec128 asinh()
        {
        TODO
        }

    @Override
    Dec128 acosh()
        {
        TODO
        }

    @Override
    Dec128 atanh()
        {
        TODO
        }

    @Override
    Dec128 deg2rad()
        {
        TODO
        }

    @Override
    Dec128 rad2deg()
        {
        TODO
        }

    @Override
    Dec128 nextUp()
        {
        TODO
        }

    @Override
    Dec128 nextDown()
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
    DecN toDecN()
        {
        return new DecN(bits);
        }
    }
