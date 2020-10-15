const Float32
        extends BinaryFPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 32-bit binary floating point number (a "single float") from its bitwise machine
     * representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        assert bits.size == 32;
        construct BinaryFPNumber(bits);
        }

    /**
     * Construct a 32-bit binary floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 4;
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
    (Boolean signBit, Int significand, Int exponent) split()
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
    Float32! toFloat32()
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
