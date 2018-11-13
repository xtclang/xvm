const UInt16
        implements IntNumber
        default(0)
    {
    /**
     * The minimum value for an UInt16.
     */
    static IntLiteral minvalue = 0;

    /**
     * The maximum value for an UInt16.
     */
    static IntLiteral maxvalue = 0x10000;

    private Bit[] bits;

    construct(Bit[] bits)
        {
        assert bits.size == 16;
        this.bits = bits;
        }

    @Override
    Int bitLength.get()
        {
        return 16;
        }

    @Override
    Int byteLength.get()
        {
        return 2;
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
    conditional UInt16 next()
        {
        if (this < maxvalue)
            {
            return true, this + 1;
            }

        return false;
        }

    @Override
    conditional UInt16 prev()
        {
        if (this > minvalue)
            {
            return true, this - 1;
            }

        return false;
        }

    @Override
    @Op UInt16 shiftLeft(Int count)
        {
        return new UInt16(bitShiftLeft(bits, count));
        }

    @Override
    UInt16 rotateLeft(Int count)
        {
        return new UInt16(bitRotateLeft(bits, count));
        }

    @Override
    @Op UInt16 shiftRight(Int count)
        {
        return new UInt16(bitShiftAllRight(bits, count));
        }

    @Override
    @Op UInt16 rotateRight(Int count)
        {
        return new UInt16(bitRotateRight(bits, count));
        }

    @Override
    @Op UInt16 shiftAllRight(Int count)
        {
        return new UInt16(bitShiftAllRight(bits, count));
        }

    @Override
    UInt16 truncate(Int count)
        {
        return new UInt16(bitTruncate(bits, count));
        }

    @Override
    UInt16 reverseBits()
        {
        return new UInt16(bitReverse(bits));
        }

    @Override
    UInt16 reverseBytes()
        {
        UInt16 result = 0;

        for (Int i = 0; i < bitLength; i += 8)
            {
            result |= ((this >>> i) & 0xFF) << (bitLength - i - 8);
            }

        return result;
        }

    @Override
    @Op UInt16 neg()
        {
        return this;
        }

    @Override
    @Op UInt16 or(UInt16 n)
        {
        return new UInt16(bitOr(bits, n.bits));
        }

    @Override
    @Op UInt16 and(UInt16 n)
        {
        return new UInt16(bitAnd(bits, n.bits));
        }

    @Override
    @Op UInt16 xor(UInt16 n)
        {
        return new UInt16(bitXor(bits, n.bits));
        }

    @Override
    @Op UInt16 not()
        {
        return new UInt16(bitNot(bits));
        }

    @Override
    @Op UInt16 add(UInt16 n)
        {
        return new UInt16(bitAdd(bits, n.bits));
        }

    @Override
    @Op UInt16 sub(UInt16 n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op UInt16 mul(UInt16 n)
        {
        return this * n;
        }

    @Override
    @Op UInt16 div(UInt16 n)
        {
        return this / n;
        }

    @Override
    @Op UInt16 mod(UInt16 n)
        {
        return this % n;
        }

    @Override
    UInt16 pow(UInt16 n)
        {
        UInt16 result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }

    }