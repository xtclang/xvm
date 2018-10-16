const UInt8
        implements IntNumber
        default(0)
    {
    /**
     * The minimum value for an UInt8.
     */
    static IntLiteral minvalue = 0;

    /**
     * The maximum value for an UInt8.
     */
    static IntLiteral maxvalue = 0xFF;

    private Bit[] bits;

    construct(Bit[] bits)
        {
        assert bits.size == 8;
        this.bits = bits;
        }

    @Override
    Int bitLength.get()
        {
        return 8;
        }

    @Override
    Int byteLength.get()
        {
        return 1;
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
    conditional IntNumber next()
        {
        if (this < maxvalue)
            {
            return true, this + 1;
            }

        return false;
        }

    @Override
    conditional IntNumber prev()
        {
        if (this > minvalue)
            {
            return true, this - 1;
            }

        return false;
        }

    @Override
    @Op UInt8 shiftLeft(Int count)
        {
        return new UInt8(bitShiftLeft(bits, count));
        }

    @Override
    UInt8 rotateLeft(Int count)
        {
        return new UInt8(bitRotateLeft(bits, count));
        }

    @Override
    @Op UInt8 shiftRight(Int count)
        {
        return new UInt8(bitShiftAllRight(bits, count));
        }

    @Override
    @Op UInt8 rotateRight(Int count)
        {
        return new UInt8(bitRotateRight(bits, count));
        }

    @Override
    @Op UInt8 shiftAllRight(Int count)
        {
        return new UInt8(bitShiftAllRight(bits, count));
        }

    @Override
    UInt8 truncate(Int count)
        {
        return new UInt8(bitTruncate(bits, count));
        }

    @Override
    UInt8 reverseBits()
        {
        return new UInt8(bitReverse(bits));
        }

    @Override
    UInt8 reverseBytes()
        {
        UInt8 result = 0;

        for (Int i = 0; i < bitLength; i += 8)
            {
            result |= ((this >>> i) & 0xFF) << (bitLength - i - 8);
            }

        return result;
        }

    @Override
    @Op UInt8 neg()
        {
        return this;
        }

    @Override
    @Op UInt8 or(UInt8 n)
        {
        return new UInt8(bitOr(bits, n.bits));
        }

    @Override
    @Op UInt8 and(UInt8 n)
        {
        return new UInt8(bitAnd(bits, n.bits));
        }

    @Override
    @Op UInt8 xor(UInt8 n)
        {
        return new UInt8(bitXor(bits, n.bits));
        }

    @Override
    @Op UInt8 not()
        {
        return new UInt8(bitNot(bits));
        }

    @Override
    @Op UInt8 add(UInt8 n)
        {
        return new UInt8(bitAdd(bits, n.bits));
        }

    @Override
    @Op UInt8 sub(UInt8 n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op UInt8 mul(UInt8 n)
        {
        return this * n;
        }

    @Override
    @Op UInt8 div(UInt8 n)
        {
        return this / n;
        }

    @Override
    @Op UInt8 mod(UInt8 n)
        {
        return this % n;
        }

    @Override
    UInt8 pow(UInt8 n)
        {
        UInt8 result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }
    }