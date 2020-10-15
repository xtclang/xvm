const BFloat16
        extends BinaryFPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 16-bit binary floating point number (a "brain float 16") from its bitwise machine
     * representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size == 16;
        construct BinaryFPNumber(bits);
        }

    /**
     * Construct a 16-bit "brain" floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 2;
        construct BinaryFPNumber(bytes);
        }


    // ----- Number properties ---------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        TODO need to think this through carefully because there is a sign bit and both +/-0
        }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op BFloat16 add(BFloat16 n)
        {
        TODO
        }

    @Override
    @Op BFloat16 sub(BFloat16 n)
        {
        TODO
        }

    @Override
    @Op BFloat16 mul(BFloat16 n)
        {
        TODO
        }

    @Override
    @Op BFloat16 div(BFloat16 n)
        {
        TODO
        }

    @Override
    @Op BFloat16 mod(BFloat16 n)
        {
        TODO
        }

    @Override
    BFloat16 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    @Op BFloat16 neg()
        {
        TODO
        }

    @Override
    BFloat16 pow(BFloat16 n)
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

    @Override
    Int significandBitLength.get()
        {
        return 7;
        }

    @Override
    Int exponentBitLength.get()
        {
        return 8;
        }


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    (Boolean signBit, Int significand, Int exponent) split()
        {
        TODO
        }

    @Override
    BFloat16 round(Rounding direction = TiesToAway)
        {
        TODO
        }

    @Override
    BFloat16 floor()
        {
        TODO
        }

    @Override
    BFloat16 ceil()
        {
        TODO
        }

    @Override
    BFloat16 exp()
        {
        TODO
        }

    @Override
    BFloat16 scaleByPow(Int n)
        {
        TODO
        }

    @Override
    BFloat16 log()
        {
        TODO
        }

    @Override
    BFloat16 log2()
        {
        TODO
        }

    @Override
    BFloat16 log10()
        {
        TODO
        }

    @Override
    BFloat16 sqrt()
        {
        TODO
        }

    @Override
    BFloat16 cbrt()
        {
        TODO
        }

    @Override
    BFloat16 sin()
        {
        TODO
        }

    @Override
    BFloat16 cos()
        {
        TODO
        }

    @Override
    BFloat16 tan()
        {
        TODO
        }

    @Override
    BFloat16 asin()
        {
        TODO
        }

    @Override
    BFloat16 acos()
        {
        TODO
        }

    @Override
    BFloat16 atan()
        {
        TODO
        }

    @Override
    BFloat16 atan2(BFloat16 y)
        {
        TODO
        }

    @Override
    BFloat16 sinh()
        {
        TODO
        }

    @Override
    BFloat16 cosh()
        {
        TODO
        }

    @Override
    BFloat16 tanh()
        {
        TODO
        }

    @Override
    BFloat16 asinh()
        {
        TODO
        }

    @Override
    BFloat16 acosh()
        {
        TODO
        }

    @Override
    BFloat16 atanh()
        {
        TODO
        }

    @Override
    BFloat16 deg2rad()
        {
        TODO
        }

    @Override
    BFloat16 rad2deg()
        {
        TODO
        }

    @Override
    BFloat16 nextUp()
        {
        TODO
        }

    @Override
    BFloat16 nextDown()
        {
        TODO
        }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    BFloat16! toBFloat16()
        {
        return this;
        }

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
        TODO
        }
    }
