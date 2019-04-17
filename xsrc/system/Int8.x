const Int8
        extends IntNumber
        default(0)
    {
    /**
     * The minimum value for an Int8.
     */
    static IntLiteral minvalue = -128;

    /**
     * The maximum value for an Int8.
     */
    static IntLiteral maxvalue = 127;

    construct(Bit[] bits)
        {
        assert bits.size == 8;
        construct IntNumber(bits);
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
    @RO UInt8 magnitude.get()
        {
        return to<Int16>().abs().to<UInt8>();
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
        return bits;
        }

    @Override
    conditional Int8 next()
        {
        if (this < maxvalue)
            {
            return true, this + 1;
            }

        return false;
        }

    @Override
    conditional Int8 prev()
        {
        if (this > minvalue)
            {
            return true, this - 1;
            }

        return false;
        }

    @Override
    @Op Int8 shiftLeft(Int count)
        {
        return new Int8(bitShiftLeft(bits, count));
        }

    @Override
    Int8 rotateLeft(Int count)
        {
        return new Int8(bitRotateLeft(bits, count));
        }

    @Override
    @Op Int8 shiftRight(Int count)
        {
        return new Int8(bitShiftRight(bits, count));
        }

    @Override
    @Op Int8 rotateRight(Int count)
        {
        return new Int8(bitRotateRight(bits, count));
        }

    @Override
    @Op Int8 shiftAllRight(Int count)
        {
        return new Int8(bitShiftAllRight(bits, count));
        }

    @Override
    Int8 truncate(Int count)
        {
        return new Int8(bitTruncate(bits, count));
        }

    @Override
    Int8 leftmostBit.get()
        {
        TODO
        }

    @Override
    Int8 rightmostBit.get()
        {
        TODO
        }

    @Override
    Int8 reverseBits()
        {
        return new Int8(bitReverse(bits));
        }

    @Override
    Int8 reverseBytes()
        {
        return this;
        }

    @Override
    @Op Int8 neg()
        {
        return ~this + 1;
        }

    @Override
    @Op Int8 or(Int8 n)
        {
        return new Int8(bitOr(bits, n.bits));
        }

    @Override
    @Op Int8 and(Int8 n)
        {
        return new Int8(bitAnd(bits, n.bits));
        }

    @Override
    @Op Int8 xor(Int8 n)
        {
        return new Int8(bitXor(bits, n.bits));
        }

    @Override
    @Op Int8 not()
        {
        return new Int8(bitNot(bits));
        }

    @Override
    @Op Int8 add(Int8 n)
        {
        return new Int8(bitAdd(bits, n.bits));
        }

    @Override
    @Op Int8 sub(Int8 n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op Int8 mul(Int8 n)
        {
        return this * n;
        }

    @Override
    @Op Int8 div(Int8 n)
        {
        return this / n;
        }

    @Override
    @Op Int8 mod(Int8 n)
        {
        return this % n;
        }

    @Override
    Int8 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    Int8 pow(Int8 n)
        {
        Int8 result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }
    }