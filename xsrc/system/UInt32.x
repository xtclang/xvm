const UInt32
        implements IntNumber
        default(0)
    {
    /**
     * The minimum value for an UInt32.
     */
    static IntLiteral minvalue = 0;

    /**
     * The maximum value for an UInt32.
     */
    static IntLiteral maxvalue =  0x100000000;

    private Bit[] bits;

    construct(Bit[] bits)
        {
        assert bits.size == 32;
        this.bits = bits;
        }

    @Override
    Int bitLength.get()
        {
        return 32;
        }

    @Override
    Int byteLength.get()
        {
        return 4;
        }

    @Override
    Signum sign.get()
        {
        if (this == 0)
            {
            return Zero;
            }

        return Positive;
        }

    @Override
    @Auto Int8 to<Int8>()
        {
        return this;
        }

    @Override
    @Auto Int16 to<Int16>()
        {
        return this;
        }

    @Override
    @Auto Int32 to<Int32>()
        {
        return this;
        }

    @Override
    @Auto Int64 to<Int64>()
        {
        return this;
        }

    @Override
    @Auto UInt8 to<UInt8>()
        {
        return this;
        }

    @Override
    @Auto UInt16 to<UInt16>()
        {
        return this;
        }

    @Override
    @Auto UInt32 to<UInt32>()
        {
        return this;
        }

    @Override
    @Auto UInt64 to<UInt64>()
        {
        return this;
        }

    @Override
    @Auto VarUInt to<VarUInt>()
        {
        return this;
        }

    @Override
    @Auto VarFloat to<VarFloat>()
        {
        return this;
        }

    @Override
    @Auto VarDec to<VarDec>()
        {
        return this;
        }

    @Override
    @Auto VarInt to<VarInt>()
        {
        return this;
        }

    @Override
    Boolean[] to<Boolean[]>()
        {
        return bitBooleans(bits);
        }

    @Override
    Bit[] to<Bit[]>()
        {
        return bits.clone();
        }

    @Override
    conditional UInt32 next()
        {
        if (this < maxvalue)
            {
            return true, this + 1;
            }

        return false;
        }

    @Override
    conditional UInt32 prev()
        {
        if (this > minvalue)
            {
            return true, this - 1;
            }

        return false;
        }

    @Override
    @Op UInt32 shiftLeft(Int count)
        {
        return new UInt32(bitShiftLeft(bits, count));
        }

    @Override
    UInt32 rotateLeft(Int count)
        {
        return new UInt32(bitRotateLeft(bits, count));
        }

    @Override
    @Op UInt32 shiftRight(Int count)
        {
        return new UInt32(bitShiftAllRight(bits, count));
        }

    @Override
    @Op UInt32 rotateRight(Int count)
        {
        return new UInt32(bitRotateRight(bits, count));
        }

    @Override
    @Op UInt32 shiftAllRight(Int count)
        {
        return new UInt32(bitShiftAllRight(bits, count));
        }

    @Override
    UInt32 truncate(Int count)
        {
        return new UInt32(bitTruncate(bits, count));
        }

    @Override
    UInt32 reverseBits()
        {
        return new UInt32(bitReverse(bits));
        }

    @Override
    UInt32 reverseBytes()
        {
        UInt32 result = 0;

        for (Int i = 0; i < bitLength; i += 8)
            {
            result |= ((this >>> i) & 0xFF) << (bitLength - i - 8);
            }

        return result;
        }

    @Override
    @Op UInt32 neg()
        {
        return this;
        }

    @Override
    @Op UInt32 or(UInt32 n)
        {
        return new UInt32(bitOr(bits, n.bits));
        }

    @Override
    @Op UInt32 and(UInt32 n)
        {
        return new UInt32(bitAnd(bits, n.bits));
        }

    @Override
    @Op UInt32 xor(UInt32 n)
        {
        return new UInt32(bitXor(bits, n.bits));
        }

    @Override
    @Op UInt32 not()
        {
        return new UInt32(bitNot(bits));
        }

    @Override
    @Op UInt32 add(UInt32 n)
        {
        return new UInt32(bitAdd(bits, n.bits));
        }

    @Override
    @Op UInt32 sub(UInt32 n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op UInt32 mul(UInt32 n)
        {
        return this * n;
        }

    @Override
    @Op UInt32 div(UInt32 n)
        {
        return this / n;
        }

    @Override
    @Op UInt32 mod(UInt32 n)
        {
        return this % n;
        }

    @Override
    UInt32 pow(UInt32 n)
        {
        UInt32 result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }
    }