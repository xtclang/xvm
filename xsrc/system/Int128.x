const Int128
        extends IntNumber
        default(0)
    {
    // ----- constants -----------------------------------------------------------------------------

    /**
     * The minimum value for an Int128.
     */
    static IntLiteral minvalue = -0x8000_0000_0000_0000_0000_0000_0000_0000;

    /**
     * The maximum value for an Int128.
     */
    static IntLiteral maxvalue =  0x7FFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF_FFFF;


    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a 128-bit signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    construct(Bit[] bits)
        {
        assert bits.size == 128;
        construct IntNumber(bits);
        }

    /**
     * Construct a 128-bit signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size == 16;
        construct IntNumber(bytes);
        }


    @Override
    Int bitLength.get()
        {
        return 128;
        }

    @Override
    Int byteLength.get()
        {
        return 16;
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
    @RO UInt128 magnitude.get()
        {
        return toVarInt().abs().toUInt128();
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
    @Op Int128 shiftLeft(Int count)
        {
        return new Int128(bitShiftLeft(bits, count));
        }

    @Override
    Int128 rotateLeft(Int count)
        {
        return new Int128(bitRotateLeft(bits, count));
        }

    @Override
    @Op Int128 shiftRight(Int count)
        {
        return new Int128(bitShiftRight(bits, count));
        }

    @Override
    @Op Int128 rotateRight(Int count)
        {
        return new Int128(bitRotateRight(bits, count));
        }

    @Override
    @Op Int128 shiftAllRight(Int count)
        {
        return new Int128(bitShiftAllRight(bits, count));
        }

    @Override
    Int128 truncate(Int count)
        {
        return new Int128(bitTruncate(bits, count));
        }

    @Override
    Int128 leftmostBit.get()
        {
        TODO
        }

    @Override
    Int128 rightmostBit.get()
        {
        TODO
        }

    @Override
    Int128 reverseBits()
        {
        return new Int128(bitReverse(bits));
        }

    @Override
    Int128 reverseBytes()
        {
        Int128 result = 0;

        for (Int i = 0; i < bitLength; i += 8)
            {
            result |= ((this >>> i) & 0xFF) << (bitLength - i - 8);
            }

        return result;
        }

    @Override
    @Op Int128 neg()
        {
        return ~this + 1;
        }

    @Override
    @Op Int128 or(Int128 n)
        {
        return new Int128(bitOr(bits, n.bits));
        }

    @Override
    @Op Int128 and(Int128 n)
        {
        return new Int128(bitAnd(bits, n.bits));
        }

    @Override
    @Op Int128 xor(Int128 n)
        {
        return new Int128(bitXor(bits, n.bits));
        }

    @Override
    @Op Int128 not()
        {
        return new Int128(bitNot(bits));
        }

    @Override
    conditional Int128 next()
        {
        if (this < maxvalue)
            {
            return true, this + 1;
            }

        return false;
        }

    @Override
    conditional Int128 prev()
        {
        if (this > minvalue)
            {
            return true, this - 1;
            }

        return false;
        }

    @Override
    @Op Int128 add(Int128 n)
        {
        return new Int128(bitAdd(bits, n.bits));
        }

    @Override
    @Op Int128 sub(Int128 n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op Int128 mul(Int128 n)
        {
        return this * n;
        }

    @Override
    @Op Int128 div(Int128 n)
        {
        return this / n;
        }

    @Override
    @Op Int128 mod(Int128 n)
        {
        return this % n;
        }

    @Override
    Int128 abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    Int128 pow(Int128 n)
        {
        Int128 result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }
    }