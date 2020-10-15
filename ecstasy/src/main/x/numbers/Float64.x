const Float64
        extends BinaryFPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 64-bit binary floating point number (a "double float") from its bitwise machine
     * representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size == 64;
        construct BinaryFPNumber(bits);
        }

    /**
     * Construct a 64-bit binary floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 8;
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
    @Op Float64 add(Float64 n)
        {
        TODO
        }

    @Override
    @Op Float64 sub(Float64 n)
        {
        TODO
        }

    @Override
    @Op Float64 mul(Float64 n)
        {
        TODO
        }

    @Override
    @Op Float64 div(Float64 n)
        {
        TODO
        }

    @Override
    @Op Float64 mod(Float64 n)
        {
        TODO
        }

    @Override
    Float64 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    @Op Float64 neg()
        {
        TODO
        }

    @Override
    Float64 pow(Float64 n)
        {
        TODO
        }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    Int emax.get()
        {
        return 1023;
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
    (Boolean signBit, Int significand, Int exponent) split()
        {
        TODO
        }

    @Override
    Float64 round(Rounding direction = TiesToAway)
        {
        TODO
        }

    @Override
    Float64 floor()
        {
        TODO
        }

    @Override
    Float64 ceil()
        {
        TODO
        }

    @Override
    Float64 exp()
        {
        TODO
        }

    @Override
    Float64 scaleByPow(Int n)
        {
        TODO
        }

    @Override
    Float64 log()
        {
        TODO
        }

    @Override
    Float64 log2()
        {
        TODO
        }

    @Override
    Float64 log10()
        {
        TODO
        }

    @Override
    Float64 sqrt()
        {
        TODO
        }

    @Override
    Float64 cbrt()
        {
        TODO
        }

    @Override
    Float64 sin()
        {
        TODO
        }

    @Override
    Float64 cos()
        {
        TODO
        }

    @Override
    Float64 tan()
        {
        TODO
        }

    @Override
    Float64 asin()
        {
        TODO
        }

    @Override
    Float64 acos()
        {
        TODO
        }

    @Override
    Float64 atan()
        {
        TODO
        }

    @Override
    Float64 atan2(Float64 y)
        {
        TODO
        }

    @Override
    Float64 sinh()
        {
        TODO
        }

    @Override
    Float64 cosh()
        {
        TODO
        }

    @Override
    Float64 tanh()
        {
        TODO
        }

    @Override
    Float64 asinh()
        {
        TODO
        }

    @Override
    Float64 acosh()
        {
        TODO
        }

    @Override
    Float64 atanh()
        {
        TODO
        }

    @Override
    Float64 deg2rad()
        {
        TODO
        }

    @Override
    Float64 rad2deg()
        {
        TODO
        }

    @Override
    Float64 nextUp()
        {
        TODO
        }

    @Override
    Float64 nextDown()
        {
        TODO
        }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    Float64! toFloat64()
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
