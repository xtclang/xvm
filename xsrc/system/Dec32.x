const Dec32
        extends DecimalFPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 32-bit decimal floating point number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
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


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    Dec32 round()
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
    Dec32 log()
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
    Dec32 deg2rad()
        {
        TODO
        }

    @Override
    Dec32 rad2deg()
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
