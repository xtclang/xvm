/**
 * An Int is a 64-bit signed integer.
 */
const Int(Bit[] bits)
        implements IntNumber
    {
    construct Int(Bit[] bits)
        {
        assert:always bits.
        this.bits = bits;
        }

    Int bitLength
        {
        Int get()
            {
            return 64;
            }
        }

    Int byteLength
        {
        Int get()
            {
            return 8;
            }
        }

    Signum sign
        {
        Int get()
            {
            if (bits[0x3F] == 1)
                {
                return Negative;
                }

            for (Bit bit : bits)
                {
                if (bit)
                    {
                    return Positive;
                    }
                }

            return Zero;
            }
        }

// TODO
    @op Int add(Int n);

// TODO
    @op Int sub(Int n);

// TODO
    @op Int mul(Int n);

// TODO
    @op Int div(Int n);

// TODO
    @op Int mod(Int n);

// TODO
    Int abs();

// TODO
    @op Int neg();

// TODO
    Int pow(Int n);

    @op Int shl(Int count)
        {
        Bit[] bits = to<Bit[]>();
        for (Int i = 0x3F; i > 0; --i)
            {
            bits[i] = bits[i-1];
            }
        bits[0] = 0;
        return new Int(bits);
        }

    @op Int shr(Int count)
        {
        Bit[] bits = to<Bit[]>();
        for (Int i = 0; i < 0x3F; ++i)
            {
            bits[i] = bits[i+1];
            }
        return new Int(bits);
        }

    @op IntInt ushr(Int count)
        {
        Bit[] bits = to<Bit[]>();
        for (Int i = 0; i < 0x3F; ++i)
            {
            bits[i] = bits[i+1];
            }
        bits[0x3F] = 0;
        return new Int(bits);
        }

    Int rol(Int count)
        {
        Bit[] bits  = to<Bit[]>();
        Int   rolls = count & 0x3F;
        while (rolls-- > 0)
            {
            Bit carry = bits[0x3F];
            for (Int i = 0x3F; i > 0; --i)
                {
                bits[i] = bits[i-1];
                }
            bits[0] = carry;
            }
        return new Int(bits);
        }

    Int ror(Int count)
        {
        Bit[] bits = to<Bit[]>();
        Int   rolls = count & 0x3F;
        while (rolls-- > 0)
            {
            Bit carry = bits[0];
            for (Int i = 0; i < 0x3F; ++i)
                {
                bits[i] = bits[i+1];
                }
            bits[0x3F] = carry;
            }
        return new Int(bits);
        }

    Boolean[] to<Boolean[]>()
        {
        Boolean[] bools = new Boolean[0x40];
        for (Int i = 0; i < 0x40; ++i)
            {
            bools[i] = bits[i].to<Boolean>();
            }
        return bools;
        }

    Bit[] to<Bit[]>()
        {
        Bit[] copy = new Bit[0x40];
        for (Int i = 0; i < 0x40; ++i)
            {
            copy[i] = bits[i];
            }
        return copy;
        }

    // TODO Nibble[] to<Nibble[]>()

    // TODO Byte[] to<Byte[]>()

    // TODO to...
    }
