const Int64
        extends IntNumber
        default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an Int64.
     */
    static IntLiteral minvalue = -0x8000_0000_0000_0000;

    /**
     * The maximum value for an Int64.
     */
    static IntLiteral maxvalue =  0x7FFF_FFFF_FFFF_FFFF;


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 64-bit signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    construct(Bit[] bits)
        {
        assert bits.size == 64;
        construct IntNumber(bits);
        }

    /**
     * Construct a 64-bit signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 8;
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
    @RO UInt64 magnitude.get()
        {
        return toInt128().abs().toUInt();
        }

    @Override
    @Unchecked Int64 toUnchecked()
        {
        return new @Unchecked Int64(bits);
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
    Int64 leftmostBit.get()
        {
        return new Int64(bitLeftmost(bits));
        }

    @Override
    Int64 rightmostBit.get()
        {
        return new Int64(bitRightmost(bits));
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