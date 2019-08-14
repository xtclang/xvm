const Int16
        extends IntNumber
        default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an Int16.
     */
    static IntLiteral minvalue = -0x8000;

    /**
     * The maximum value for an Int16.
     */
    static IntLiteral maxvalue = 0x7FFF;


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 16-bit signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    construct(Bit[] bits)
        {
        assert bits.size == 16;
        construct IntNumber(bits);
        }

    /**
     * Construct a 16-bit signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 2;
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
    @RO UInt16 magnitude.get()
        {
        return toInt32().abs().toUInt16();
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
    @Auto Int128 toInt128()
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
    Int16 leftmostBit.get()
        {
        TODO
        }

    @Override
    Int16 rightmostBit.get()
        {
        TODO
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