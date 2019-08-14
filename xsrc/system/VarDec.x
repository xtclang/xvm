const VarDec
        extends DecimalFPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a variable-length decimal floating point number from its bitwise machine
     * representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    construct(Bit[] bits)
        {
        assert bits.size >= 32 && bits.size.bitCount == 1;
        construct DecimalFPNumber(bits);
        }

    /**
     * Construct a variable-length decimal floating point number from its network-portable
     * representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size >= 4 && bytes.size.bitCount == 1;
        construct DecimalFPNumber(bytes);
        }


    // ----- Number operations ---------------------------------------------------------------------

    @Override
    @Op VarDec add(VarDec n)
        {
        TODO
        }

    @Override
    @Op VarDec sub(VarDec n)
        {
        TODO
        }

    @Override
    @Op VarDec mul(VarDec n)
        {
        TODO
        }

    @Override
    @Op VarDec div(VarDec n)
        {
        TODO
        }

    @Override
    @Op VarDec mod(VarDec n)
        {
        TODO
        }

    @Override
    VarDec abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    @Op VarDec neg()
        {
        TODO
        }

    @Override
    VarDec pow(VarDec n)
        {
        TODO
        }


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    VarDec round()
        {
        TODO
        }

    @Override
    VarDec floor()
        {
        TODO
        }

    @Override
    VarDec ceil()
        {
        TODO
        }

    @Override
    VarDec exp()
        {
        TODO
        }

    @Override
    VarDec log()
        {
        TODO
        }

    @Override
    VarDec log10()
        {
        TODO
        }

    @Override
    VarDec sqrt()
        {
        TODO
        }

    @Override
    VarDec cbrt()
        {
        TODO
        }

    @Override
    VarDec sin()
        {
        TODO
        }

    @Override
    VarDec cos()
        {
        TODO
        }

    @Override
    VarDec tan()
        {
        TODO
        }

    @Override
    VarDec asin()
        {
        TODO
        }

    @Override
    VarDec acos()
        {
        TODO
        }

    @Override
    VarDec atan()
        {
        TODO
        }

    @Override
    VarDec deg2rad()
        {
        TODO
        }

    @Override
    VarDec rad2deg()
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
    VarDec! toVarDec()
        {
        return this;
        }
    }
