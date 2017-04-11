
/**
 * An Int64 is a 64-bit signed integer.
 */
const Int64
        implements IntNumber
    {
    construct Int64(Bit[] bits)
        {
        assert bits.size == 64;
        this.bits = bits;
        }

    private Bit[] bits;
    
    Int64 bitLength.get()
        {
        return 64;
        }

    Int64 byteLength.get()
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

    @ro UInt64 magnitude.get()
        {
        return to<Int128>().abs().to<UInt64>();
        }

    static Boolean equals(Int64 value1, Int64 value2)
        {
        return value1.to<Byte[]> == value2.to<Byte[]>;
        }

// TODO
    @op Int64 add(Int64 n);

// TODO / REVIEW
    /**
     * In addition to the implicit "add(Int64 n)" method, this method allows any
     * integer to be added to this value.
     */
    Int64 add(IntNumber n);

// TODO
    @op Int64 sub(Int64 n);

// TODO
    @op Int64 mul(Int64 n);

// TODO
    @op Int64 div(Int64 n);

// TODO
    @op Int64 mod(Int64 n);

// TODO
    Int64 abs();

// TODO
    @op Int64 neg();

// TODO
    Int64 pow(Int64 n);

    @op Int64 shl(Int64 count)
        {
        Bit[] bits = to<Bit[]>();
        for (Int64 i = 0x3F; i > 0; --i)
            {
            bits[i] = bits[i-1];
            }
        bits[0] = 0;
        return new Int(bits);
        }

    @op Int64 shr(Int64 count)
        {
        Bit[] bits = to<Bit[]>();
        for (Int64 i = 0; i < 0x3F; ++i)
            {
            bits[i] = bits[i+1];
            }
        return new Int(bits);
        }

    @op IntInt64 ushr(Int64 count)
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

    Boolean[] to<Boolean[]>()
        {
        Boolean[] bools = new Boolean[0x40];
        for (Int64 i = 0; i < 0x40; ++i)
            {
            bools[i] = bits[i].to<Boolean>();
            }
        return bools;
        }

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
