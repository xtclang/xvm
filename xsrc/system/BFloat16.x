const BFloat16
        extends BinaryFPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 16-bit binary floating point number (a "brain float 16") from its bitwise machine
     * representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
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
    Boolean finite.get()
        {
        TODO
        }

    @Override
    Boolean infinite.get()
        {
        TODO
        }

    @Override
    Boolean NaN.get()
        {
        TODO
        }


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    BFloat16 round()
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
    BFloat16 log()
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
    BFloat16 deg2rad()
        {
        TODO
        }

    @Override
    BFloat16 rad2deg()
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
        TODO
        }
    }
