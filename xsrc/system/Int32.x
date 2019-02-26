const Int32
        extends IntNumber
        default(0)
    {
    /**
     * The minimum value for an Int32.
     */
    static IntLiteral minvalue = -0x80000000;

    /**
     * The maximum value for an Int32.
     */
    static IntLiteral maxvalue =  0x7FFFFFFF;

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

    @Override
    @RO UInt32 magnitude.get()
        {
        return to<Int64>().abs().to<UInt32>();
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
    @Op Int32 shiftLeft(Int count)
        {
        return new Int32(bitShiftLeft(bits, count));
        }

    @Override
    Int32 rotateLeft(Int count)
        {
        return new Int32(bitRotateLeft(bits, count));
        }

    @Override
    @Op Int32 shiftRight(Int count)
        {
        return new Int32(bitShiftRight(bits, count));
        }

    @Override
    @Op Int32 rotateRight(Int count)
        {
        return new Int32(bitRotateRight(bits, count));
        }

    @Override
    @Op Int32 shiftAllRight(Int count)
        {
        return new Int32(bitShiftAllRight(bits, count));
        }

    @Override
    Int32 truncate(Int count)
        {
        return new Int32(bitTruncate(bits, count));
        }

    @Override
    Int32 reverseBits()
        {
        return new Int32(bitReverse(bits));
        }

    @Override
    Int32 reverseBytes()
        {
        Int32 result = 0;

        for (Int i = 0; i < bitLength; i += 8)
            {
            result |= ((this >>> i) & 0xFF) << (bitLength - i - 8);
            }

        return result;
        }

    @Override
    @Op Int32 neg()
        {
        return ~this + 1;
        }

    @Override
    @Op Int32 or(Int32 n)
        {
        return new Int32(bitOr(bits, n.bits));
        }

    @Override
    @Op Int32 and(Int32 n)
        {
        return new Int32(bitAnd(bits, n.bits));
        }

    @Override
    @Op Int32 xor(Int32 n)
        {
        return new Int32(bitXor(bits, n.bits));
        }

    @Override
    @Op Int32 not()
        {
        return new Int32(bitNot(bits));
        }

    @Override
    conditional Int32 next()
        {
        if (this < maxvalue)
            {
            return true, this + 1;
            }

        return false;
        }

    @Override
    conditional Int32 prev()
        {
        if (this > minvalue)
            {
            return true, this - 1;
            }

        return false;
        }

    @Override
    @Op Int32 add(Int32 n)
        {
        return new Int32(bitAdd(bits, n.bits));
        }

    @Override
    @Op Int32 sub(Int32 n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op Int32 mul(Int32 n)
        {
        return this * n;
        }

    @Override
    @Op Int32 div(Int32 n)
        {
        return this / n;
        }

    @Override
    @Op Int32 mod(Int32 n)
        {
        return this % n;
        }

    @Override
    Int32 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    Int32 pow(Int32 n)
        {
        Int32 result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }
    }