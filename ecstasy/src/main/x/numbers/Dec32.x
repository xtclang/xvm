const Dec32
        extends DecimalFPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 32-bit decimal floating point number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size == 32;
        construct DecimalFPNumber(bits);
        }

    /**
     * Construct a 32-bit decimal floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 4;
        construct DecimalFPNumber(bytes);
        }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op Dec32 add(Dec32 n)
        {
        TODO
        }

    @Override
    @Op Dec32 sub(Dec32 n)
        {
        TODO
        }

    @Override
    @Op Dec32 mul(Dec32 n)
        {
        TODO
        }

    @Override
    @Op Dec32 div(Dec32 n)
        {
        TODO
        }

    @Override
    @Op Dec32 mod(Dec32 n)
        {
        TODO
        }

    @Override
    Dec32 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    @Op Dec32 neg()
        {
        TODO
        }

    @Override
    Dec32 pow(Dec32 n)
        {
        TODO
        }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    @RO Int emax.get()
        {
        return 96;
        }

    @Override
    Int emin.get()
        {
        return 1 - emax;
        }

    @Override
    Int bias.get()
        {
        return 101;
        }


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    (Boolean signBit, Int significand, Int exponent) split()
        {
        TODO
        }

    @Override
    Dec32 round(Rounding direction = TiesToAway)
        {
        TODO
        }

    @Override
    Dec32 floor()
        {
        TODO
        }

    @Override
    Dec32 ceil()
        {
        TODO
        }

    @Override
    Dec32 exp()
        {
        TODO
        }

    @Override
    Dec32 scaleByPow(Int n)
        {
        TODO
        }

    @Override
    Dec32 log()
        {
        TODO
        }

    @Override
    Dec32 log2()
        {
        TODO
        }

    @Override
    Dec32 log10()
        {
        TODO
        }

    @Override
    Dec32 sqrt()
        {
        TODO
        }

    @Override
    Dec32 cbrt()
        {
        TODO
        }

    @Override
    Dec32 sin()
        {
        TODO
        }

    @Override
    Dec32 cos()
        {
        TODO
        }

    @Override
    Dec32 tan()
        {
        TODO
        }

    @Override
    Dec32 asin()
        {
        TODO
        }

    @Override
    Dec32 acos()
        {
        TODO
        }

    @Override
    Dec32 atan()
        {
        TODO
        }

    @Override
    Dec32 atan2(Dec32 y)
        {
        TODO
        }

    @Override
    Dec32 sinh()
        {
        TODO
        }

    @Override
    Dec32 cosh()
        {
        TODO
        }

    @Override
    Dec32 tanh()
        {
        TODO
        }

    @Override
    Dec32 asinh()
        {
        TODO
        }

    @Override
    Dec32 acosh()
        {
        TODO
        }

    @Override
    Dec32 atanh()
        {
        TODO
        }

    @Override
    Dec32 deg2rad()
        {
        TODO
        }

    @Override
    Dec32 rad2deg()
        {
        TODO
        }

    @Override
    Dec32 nextUp()
        {
        TODO
        }

    @Override
    Dec32 nextDown()
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
