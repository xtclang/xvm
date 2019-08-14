const Dec64
        extends DecimalFPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 64-bit decimal floating point number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    construct(Bit[] bits)
        {
        assert bits.size == 64;
        construct DecimalFPNumber(bits);
        }

    /**
     * Construct a 64-bit decimal floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 8;
        construct DecimalFPNumber(bytes);
        }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op Dec64 add(Dec64 n)
        {
        TODO
        }

    @Override
    @Op Dec64 sub(Dec64 n)
        {
        TODO
        }

    @Override
    @Op Dec64 mul(Dec64 n)
        {
        TODO
        }

    @Override
    @Op Dec64 div(Dec64 n)
        {
        TODO
        }

    @Override
    @Op Dec64 mod(Dec64 n)
        {
        TODO
        }

    @Override
    Dec64 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    @Op Dec64 neg()
        {
        TODO
        }

    @Override
    Dec64 pow(Dec64 n)
        {
        TODO
        }


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    Dec64 round()
        {
        TODO
        }

    @Override
    Dec64 floor()
        {
        TODO
        }

    @Override
    Dec64 ceil()
        {
        TODO
        }

    @Override
    Dec64 exp()
        {
        TODO
        }

    @Override
    Dec64 log()
        {
        TODO
        }

    @Override
    Dec64 log10()
        {
        TODO
        }

    @Override
    Dec64 sqrt()
        {
        TODO
        }

    @Override
    Dec64 cbrt()
        {
        TODO
        }

    @Override
    Dec64 sin()
        {
        TODO
        }

    @Override
    Dec64 cos()
        {
        TODO
        }

    @Override
    Dec64 tan()
        {
        TODO
        }

    @Override
    Dec64 asin()
        {
        TODO
        }

    @Override
    Dec64 acos()
        {
        TODO
        }

    @Override
    Dec64 atan()
        {
        TODO
        }

    @Override
    Dec64 deg2rad()
        {
        TODO
        }

    @Override
    Dec64 rad2deg()
        {
        TODO
        }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    VarInt toVarInt()
        {
        TODO
        }

    @Override
    VarUInt toVarUInt()
        {
        TODO
        }

    @Override
    VarFloat toVarFloat()
        {
        TODO
        }

    @Override
    VarDec toVarDec()
        {
        return new VarDec(bits);
        }
    }
