const Float16
        extends BinaryFPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 16-bit binary floating point number (a "half float") from its bitwise machine
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
     * Construct a 16-bit binary floating point number from its network-portable representation.
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
        UInt16 n = bits.toUInt16();
        if (n == 0x0000 || n == 0x8000)
            {
            return Zero;
            }

        return n & 0x8000 == 0 ? Positive : Negative;
        }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op Float16 add(Float16 n)
        {
        TODO
        }

    @Override
    @Op Float16 sub(Float16 n)
        {
        TODO
        }

    @Override
    @Op Float16 mul(Float16 n)
        {
        TODO
        }

    @Override
    @Op Float16 div(Float16 n)
        {
        TODO
        }

    @Override
    @Op Float16 mod(Float16 n)
        {
        TODO
        }

    @Override
    Float16 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    @Op Float16 neg()
        {
        TODO
        }

    @Override
    Float16 pow(Float16 n)
        {
        TODO
        }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    Int emax.get()
        {
        return 15;
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
    Float16 round(Rounding direction = TiesToAway)
        {
        TODO
        }

    @Override
    Float16 floor()
        {
        TODO
        }

    @Override
    Float16 ceil()
        {
        TODO
        }

    @Override
    Float16 exp()
        {
        TODO
        }

    @Override
    Float16 scaleByPow(Int n)
        {
        TODO
        }

    @Override
    Float16 log()
        {
        TODO
        }

    @Override
    Float16 log2()
        {
        TODO
        }

    @Override
    Float16 log10()
        {
        TODO
        }

    @Override
    Float16 sqrt()
        {
        TODO
        }

    @Override
    Float16 cbrt()
        {
        TODO
        }

    @Override
    Float16 sin()
        {
        TODO
        }

    @Override
    Float16 cos()
        {
        TODO
        }

    @Override
    Float16 tan()
        {
        TODO
        }

    @Override
    Float16 asin()
        {
        TODO
        }

    @Override
    Float16 acos()
        {
        TODO
        }

    @Override
    Float16 atan()
        {
        TODO
        }

    @Override
    Float16 atan2(Float16 y)
        {
        TODO
        }

    @Override
    Float16 sinh()
        {
        TODO
        }

    @Override
    Float16 cosh()
        {
        TODO
        }

    @Override
    Float16 tanh()
        {
        TODO
        }

    @Override
    Float16 asinh()
        {
        TODO
        }

    @Override
    Float16 acosh()
        {
        TODO
        }

    @Override
    Float16 atanh()
        {
        TODO
        }

    @Override
    Float16 deg2rad()
        {
        TODO
        }

    @Override
    Float16 rad2deg()
        {
        TODO
        }

    @Override
    Float16 nextUp()
        {
        TODO
        }

    @Override
    Float16 nextDown()
        {
        TODO
        }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    Float16! toFloat16()
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
