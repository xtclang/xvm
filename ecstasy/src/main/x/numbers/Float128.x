const Float128
        extends BinaryFPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 128-bit binary floating point number (a "quad float") from its bitwise machine
     * representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size == 128;
        construct BinaryFPNumber(bits);
        }

    /**
     * Construct a 128-bit binary floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 16;
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
    @Op Float128 add(Float128 n)
        {
        TODO
        }

    @Override
    @Op Float128 sub(Float128 n)
        {
        TODO
        }

    @Override
    @Op Float128 mul(Float128 n)
        {
        TODO
        }

    @Override
    @Op Float128 div(Float128 n)
        {
        TODO
        }

    @Override
    @Op Float128 mod(Float128 n)
        {
        TODO
        }

    @Override
    Float128 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    @Op Float128 neg()
        {
        TODO
        }

    @Override
    Float128 pow(Float128 n)
        {
        TODO
        }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    Int emax.get()
        {
        return 16383;
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
    (Boolean signBit, Int128 significand, Int exponent) split()
        {
        TODO
        }

    @Override
    Float128 round(Rounding direction = TiesToAway)
        {
        TODO
        }

    @Override
    Float128 floor()
        {
        TODO
        }

    @Override
    Float128 ceil()
        {
        TODO
        }

    @Override
    Float128 exp()
        {
        TODO
        }

    @Override
    Float128 scaleByPow(Int n)
        {
        TODO
        }

    @Override
    Float128 log()
        {
        TODO
        }

    @Override
    Float128 log2()
        {
        TODO
        }

    @Override
    Float128 log10()
        {
        TODO
        }

    @Override
    Float128 sqrt()
        {
        TODO
        }

    @Override
    Float128 cbrt()
        {
        TODO
        }

    @Override
    Float128 sin()
        {
        TODO
        }

    @Override
    Float128 cos()
        {
        TODO
        }

    @Override
    Float128 tan()
        {
        TODO
        }

    @Override
    Float128 asin()
        {
        TODO
        }

    @Override
    Float128 acos()
        {
        TODO
        }

    @Override
    Float128 atan()
        {
        TODO
        }

    @Override
    Float128 atan2(Float128 y)
        {
        TODO
        }

    @Override
    Float128 sinh()
        {
        TODO
        }

    @Override
    Float128 cosh()
        {
        TODO
        }

    @Override
    Float128 tanh()
        {
        TODO
        }

    @Override
    Float128 asinh()
        {
        TODO
        }

    @Override
    Float128 acosh()
        {
        TODO
        }

    @Override
    Float128 atanh()
        {
        TODO
        }

    @Override
    Float128 deg2rad()
        {
        TODO
        }

    @Override
    Float128 rad2deg()
        {
        TODO
        }

    @Override
    Float128 nextUp()
        {
        TODO
        }

    @Override
    Float128 nextDown()
        {
        TODO
        }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    Float128! toFloat128()
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
