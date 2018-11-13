const Int64
        implements IntNumber
        default(0)
    {
    /**
     * The minimum value for an Int64.
     */
    static IntLiteral minvalue = -0x8000000000000000;

    /**
     * The maximum value for an Int64.
     */
    static IntLiteral maxvalue =  0x7FFFFFFFFFFFFFFF;

    private Bit[] bits;

    construct(Bit[] bits)
        {
        assert bits.size == 64;
        this.bits = bits;
        }

    @Override
    Int bitLength.get()
        {
        return 64;
        }

    @Override
    Int byteLength.get()
        {
        return 8;
        }

    @Override
    Signum sign.get()
        {
        if (this < 0)
            {
            return Negative;
            }

        if (this == 0)
            {
            return Zero;
            }

        return Positive;
        }

    @RO UInt64 magnitude.get()
        {
        return to<Int128>().abs().to<UInt64>();
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
    @Op Int64 shiftLeft(Int count)
        {
        return new Int64(bitShiftLeft(bits, count));
        }

    @Override
    Int64 rotateLeft(Int count)
        {
        return new Int64(bitRotateLeft(bits, count));
        }

    @Override
    @Op Int64 shiftRight(Int count)
        {
        return new Int64(bitShiftRight(bits, count));
        }

    @Override
    @Op Int64 rotateRight(Int count)
        {
        return new Int64(bitRotateRight(bits, count));
        }

    @Override
    @Op Int64 shiftAllRight(Int count)
        {
        return new Int64(bitShiftAllRight(bits, count));
        }

    @Override
    Int64 truncate(Int count)
        {
        return new Int64(bitTruncate(bits, count));
        }

    @Override
    Int64 reverseBits()
        {
        return new Int64(bitReverse(bits));
        }

    @Override
    Int64 reverseBytes()
        {
        Int64 result = 0;

        for (Int i = 0; i < bitLength; i += 8)
            {
            result |= ((this >>> i) & 0xFF) << (bitLength - i - 8);
            }

        return result;
        }

    @Override
    @Op Int64 neg()
        {
        if (this == minvalue)
            {
            // TODO: where should the OverflowException be placed?
            // throw new OverflowException();
            }
        return ~this + 1;
        }

    @Override
    @Op Int64 or(Int64 n)
        {
        return new Int64(bitOr(bits, n.bits));
        }

    @Override
    @Op Int64 and(Int64 n)
        {
        return new Int64(bitAnd(bits, n.bits));
        }

    @Override
    @Op Int64 xor(Int64 n)
        {
        return new Int64(bitXor(bits, n.bits));
        }

    @Override
    @Op Int64 not()
        {
        return new Int64(bitNot(bits));
        }

    @Override
    conditional Int64 next()
        {
        if (this < maxvalue)
            {
            return true, this + 1;
            }

        return false;
        }

    @Override
    conditional Int64 prev()
        {
        if (this > minvalue)
            {
            return true, this - 1;
            }

        return false;
        }

    @Override
    @Op Int64 add(Int64 n)
        {
        return new Int64(bitAdd(bits, n.bits));
        }

    @Override
    @Op Int64 sub(Int64 n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op Int64 mul(Int64 n)
        {
        return this * n;
        }

    @Override
    @Op Int64 div(Int64 n)
        {
        return this / n;
        }

    @Override
    @Op Int64 mod(Int64 n)
        {
        return this % n;
        }

    @Override
    Int64 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    Int64 pow(Int64 n)
        {
        Int64 result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }
    }