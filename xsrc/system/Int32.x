const Int32
        extends IntNumber
        default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an Int32.
     */
    static IntLiteral minvalue = -0x80000000;

    /**
     * The maximum value for an Int32.
     */
    static IntLiteral maxvalue =  0x7FFFFFFF;


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 32-bit signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    construct(Bit[] bits)
        {
        assert bits.size == 32;
        construct IntNumber(bits);
        }

    /**
     * Construct a 32-bit signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 4;
        construct IntNumber(bytes);
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
        return toInt().abs().toUInt32();
        }

    @Override
    @Auto Int8 toInt8()
        {
        return this;
        }

    @Override
    @Auto Int16 toInt16()
        {
        return this;
        }

    @Override
    @Auto Int32 toInt32()
        {
        return this;
        }

    @Override
    @Auto Int64 toInt()
        {
        return this;
        }

    @Override
    @Auto Int128 toInt128()
        {
        return this;
        }

    @Override
    @Auto UInt8 toByte()
        {
        return this;
        }

    @Override
    @Auto UInt16 toUInt16()
        {
        return this;
        }

    @Override
    @Auto UInt32 toUInt32()
        {
        return this;
        }

    @Override
    @Auto UInt64 toUInt()
        {
        return this;
        }

    @Override
    @Auto UInt128 toUInt128()
        {
        return this;
        }

    @Override
    @Auto VarUInt toVarUInt()
        {
        return this;
        }

    @Override
    @Auto VarFloat toVarFloat()
        {
        return this;
        }

    @Override
    @Auto VarDec toVarDec()
        {
        return this;
        }

    @Override
    @Auto VarInt toVarInt()
        {
        return this;
        }

    @Override
    immutable Boolean[] toBooleanArray()
        {
        return bitBooleans(bits);
        }

    @Override
    immutable Bit[] toBitArray()
        {
        return bits.reverse.as(immutable Bit[]);
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
    Int32 leftmostBit.get()
        {
        TODO
        }

    @Override
    Int32 rightmostBit.get()
        {
        TODO
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