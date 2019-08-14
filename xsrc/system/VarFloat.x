const VarFloat
        extends BinaryFPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a variable-length binary floating point number from its bitwise machine
     * representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    construct(Bit[] bits)
        {
        assert bits.size >= 16 && bits.size.bitCount == 1;
        construct BinaryFPNumber(bits);
        }

    /**
     * Construct a variable-length binary floating point number from its network-portable
     * representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size >= 2 && bytes.size.bitCount == 1;
        construct BinaryFPNumber(bytes);
        }


    // ----- Number properties ---------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        Boolean negative = false;
        eachBit: for (Bit bit : toBitArray())
            {
            if (bit == 1)
                {
                if (eachBit.first)
                    {
                    negative = true;
                    }
                else
                    {
                    return negative ? Negative : Positive;
                    }
                }
            }
        return Zero;
        }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op VarFloat add(VarFloat n)
        {
        TODO
        }

    @Override
    @Op VarFloat sub(VarFloat n)
        {
        TODO
        }

    @Override
    @Op VarFloat mul(VarFloat n)
        {
        TODO
        }

    @Override
    @Op VarFloat div(VarFloat n)
        {
        TODO
        }

    @Override
    @Op VarFloat mod(VarFloat n)
        {
        TODO
        }

    @Override
    VarFloat abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    @Op VarFloat neg()
        {
        TODO
        }

    @Override
    VarFloat pow(VarFloat n)
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
    VarFloat round()
        {
        TODO
        }

    @Override
    VarFloat floor()
        {
        TODO
        }

    @Override
    VarFloat ceil()
        {
        TODO
        }

    @Override
    VarFloat exp()
        {
        TODO
        }

    @Override
    VarFloat log()
        {
        TODO
        }

    @Override
    VarFloat log10()
        {
        TODO
        }

    @Override
    VarFloat sqrt()
        {
        TODO
        }

    @Override
    VarFloat cbrt()
        {
        TODO
        }

    @Override
    VarFloat sin()
        {
        TODO
        }

    @Override
    VarFloat cos()
        {
        TODO
        }

    @Override
    VarFloat tan()
        {
        TODO
        }

    @Override
    VarFloat asin()
        {
        TODO
        }

    @Override
    VarFloat acos()
        {
        TODO
        }

    @Override
    VarFloat atan()
        {
        TODO
        }

    @Override
    VarFloat deg2rad()
        {
        TODO
        }

    @Override
    VarFloat rad2deg()
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
    VarFloat! toVarFloat()
        {
        return this;
        }

    @Override
    VarDec toVarDec()
        {
        TODO
        }
    }
