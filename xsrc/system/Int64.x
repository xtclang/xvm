
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

    Int bitLength.get()
        {
        return 64;
        }

    Int byteLength.get()
        {
        return 8;
        }

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
    static IntLiteral minvalue = -0x8000000000000000;
    /**
     * The maximum value for an Int64.
     */
    static IntLiteral maxvalue =  0x7FFFFFFFFFFFFFFF;

    @ro UInt64 magnitude
        {
        return to<Int128>().abs().to<UInt64>();
        }

    static Boolean equals(Int64 value1, Int64 value2)
        {
        return value1.to<Byte[]> == value2.to<Byte[]>;
        }

// TODO
    @op Int add(Int n);

// TODO / REVIEW
    /**
     * In addition to the implicit "add(Int64 n)" method, this method allows any
     * integer to be added to this value.
     */
    Int64 add(IntNumber n);

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
