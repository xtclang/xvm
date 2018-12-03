const Int16
        implements IntNumber
        default(0)
    {
    /**
     * The minimum value for an Int16.
     */
    static IntLiteral minvalue = -0x8000;

    /**
     * The maximum value for an Int16.
     */
    static IntLiteral maxvalue = 0x7FFF;

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
    @RO UInt16 magnitude.get()
        {
        return to<Int32>().abs().to<UInt16>();
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
    @Op Int16 shiftLeft(Int count)
        {
        return new Int16(bitShiftLeft(bits, count));
        }

    @Override
    Int16 rotateLeft(Int count)
        {
        return new Int16(bitRotateLeft(bits, count));
        }

    @Override
    @Op Int16 shiftRight(Int count)
        {
        return new Int16(bitShiftRight(bits, count));
        }

    @Override
    @Op Int16 rotateRight(Int count)
        {
        return new Int16(bitRotateRight(bits, count));
        }

    @Override
    @Op Int16 shiftAllRight(Int count)
        {
        return new Int16(bitShiftAllRight(bits, count));
        }

    @Override
    Int16 truncate(Int count)
        {
        return new Int16(bitTruncate(bits, count));
        }

    @Override
    Int16 reverseBits()
        {
        return new Int16(bitReverse(bits));
        }

    @Override
    Int16 reverseBytes()
        {
        Int16 result = 0;

        for (Int i = 0; i < bitLength; i += 8)
            {
            result |= ((this >>> i) & 0xFF) << (bitLength - i - 8);
            }

        return result;
        }

    @Override
    @Op Int16 neg()
        {
        return ~this + 1;
        }

    @Override
    @Op Int16 or(Int16 n)
        {
        return new Int16(bitOr(bits, n.bits));
        }

    @Override
    @Op Int16 and(Int16 n)
        {
        return new Int16(bitAnd(bits, n.bits));
        }

    @Override
    @Op Int16 xor(Int16 n)
        {
        return new Int16(bitXor(bits, n.bits));
        }

    @Override
    @Op Int16 not()
        {
        return new Int16(bitNot(bits));
        }

    @Override
    conditional Int16 next()
        {
        if (this < maxvalue)
            {
            return true, this + 1;
            }

        return false;
        }

    @Override
    conditional Int16 prev()
        {
        if (this > minvalue)
            {
            return true, this - 1;
            }

        return false;
        }

    @Override
    @Op Int16 add(Int16 n)
        {
        return new Int16(bitAdd(bits, n.bits));
        }

    @Override
    @Op Int16 sub(Int16 n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op Int16 mul(Int16 n)
        {
        return this * n;
        }

    @Override
    @Op Int16 div(Int16 n)
        {
        return this / n;
        }

    @Override
    @Op Int16 mod(Int16 n)
        {
        return this % n;
        }

    @Override
    Int16 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    Int16 pow(Int16 n)
        {
        Int16 result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }
    }