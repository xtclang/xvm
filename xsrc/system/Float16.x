const Float16
        extends BinaryFPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 16-bit binary floating point number (a "half float") from its bitwise machine
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
    Float16 round()
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
    Float16 log()
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
    Float16 deg2rad()
        {
        TODO
        }

    @Override
    Float16 rad2deg()
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
