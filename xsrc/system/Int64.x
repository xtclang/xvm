/**
 * An Int64 is a 64-bit signed integer.
 */
const Int64
        implements IntNumber
        default(0)
    {
    construct(Bit[] bits)
        {
        assert bits.size == 64;
        this.bits = bits;
        }

    private Bit[] bits;

    @Override
    Int64 bitLength.get()
        {
        return 64;
        }

    @Override
    Int64 byteLength.get()
        {
        return 8;
        }

    @Override
    Signum sign.get()
        {
        if (bits[0x3F] == 1)
            {
            return Negative;
            }

        if (this == 0)
            {
            return Zero;
            }

        return Positive;
        }

    /**
     * The minimum value for an Int64.
     */
    static IntLiteral minvalue = 0; // TODO -0x8000000000000000;

    /**
     * The maximum value for an Int64.
     */
    static IntLiteral maxvalue =  0x7FFFFFFFFFFFFFFF;

    @RO UInt64 magnitude.get()
        {
        return to<Int128>().abs().to<UInt64>();
        }

// TODO
    @Op Int64 add(Int64 n);

// TODO / REVIEW
    /**
     * In addition to the implicit "add(Int64 n)" method, this method allows any
     * integer to be added to this value.
     */
    Int64 add(IntNumber n);

// TODO
    @Op Int64 sub(Int64 n);

// TODO
    @Op Int64 mul(Int64 n);

// TODO
    @Op Int64 div(Int64 n);

// TODO
    @Op Int64 mod(Int64 n);

// TODO
    Int64 abs();

// TODO
    @Op Int64 neg();

// TODO
    Int64 pow(Int64 n);

    @Op Int64 shl(Int64 count)
        {
        Bit[] bits = to<Bit[]>();
        for (Int64 i = 0x3F; i > 0; --i)
            {
            bits[i] = bits[i-1];
            }
        bits[0] = 0;
        return new Int(bits);
        }

    @Op Int64 shr(Int64 count)
        {
        Bit[] bits = to<Bit[]>();
        for (Int64 i = 0; i < 0x3F; ++i)
            {
            bits[i] = bits[i+1];
            }
        return new Int(bits);
        }

    @Op Int64 ushr(Int64 count)
        {
        Bit[] bits = to<Bit[]>();
        for (Int64 i = 0; i < 0x3F; ++i)
            {
            bits[i] = bits[i+1];
            }
        bits[0x3F] = 0;
        return new Int(bits);
        }

    Int64 rol(Int64 count)
        {
        Bit[] bits  = to<Bit[]>();
        Int64   rolls = count & 0x3F;
        while (rolls-- > 0)
            {
            Bit carry = bits[0x3F];
            for (Int64 i = 0x3F; i > 0; --i)
                {
                bits[i] = bits[i-1];
                }
            bits[0] = carry;
            }
        return new Int(bits);
        }

    Int64 ror(Int64 count)
        {
        Bit[] bits = to<Bit[]>();
        Int64   rolls = count & 0x3F;
        while (rolls-- > 0)
            {
            Bit carry = bits[0];
            for (Int64 i = 0; i < 0x3F; ++i)
                {
                bits[i] = bits[i+1];
                }
            bits[0x3F] = carry;
            }
        return new Int(bits);
        }

    @Override
    Boolean[] to<Boolean[]>()
        {
        Boolean[] bools = new Boolean[0x40];
        for (Int64 i = 0; i < 0x40; ++i)
            {
            bools[i] = bits[i].to<Boolean>();
            }
        return bools;
        }

    @Override
    Bit[] to<Bit[]>()
        {
        Bit[] copy = new Bit[0x40];
        for (Int64 i = 0; i < 0x40; ++i)
            {
            copy[i] = bits[i];
            }
        return copy;
        }

    // TODO Nibble[] to<Nibble[]>()

    // TODO Byte[] to<Byte[]>()

    // TODO to...
    }
