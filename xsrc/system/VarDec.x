const VarDec
        extends DecimalFPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a variable-length decimal floating point number from its bitwise machine
     * representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
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


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    @RO VarInt emax.get()
        {
        // from IEEE 754-2008:
        //   w    = k/16+4
        //   emax = 3×2^(w−1)
        return 3 * (1 << byteLength / 16 + 3);
        }

    @Override
    VarInt emin.get()
        {
        return 1 - emax;
        }

    @Override
    VarInt bias.get()
        {
        // from IEEE 754-2008:
        //   emax+p−2
        return emax + precision - 2;
        }


    // ----- FPNumber operations -------------------------------------------------------------------

    @Override
    (Boolean signBit, VarInt significand, VarInt exponent) split()
        {
        TODO
        }

    @Override
    VarDec round(Rounding direction = TiesToAway)
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
    VarDec scaleByPow(Int n)
        {
        TODO
        }

    @Override
    VarDec log()
        {
        TODO
        }

    @Override
    VarDec log2()
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
    VarDec atan2(VarDec y)
        {
        TODO
        }

    @Override
    VarDec sinh()
        {
        TODO
        }

    @Override
    VarDec cosh()
        {
        TODO
        }

    @Override
    VarDec tanh()
        {
        TODO
        }

    @Override
    VarDec asinh()
        {
        TODO
        }

    @Override
    VarDec acosh()
        {
        TODO
        }

    @Override
    VarDec atanh()
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

    @Override
    VarDec nextUp()
        {
        TODO
        }

    @Override
    VarDec nextDown()
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
