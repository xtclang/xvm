/**
 * A signed integer with a power-of-2 number of bits.
 */
const VarInt
        extends IntNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a variable-length signed integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    construct(Bit[] bits)
        {
        assert bits.size >= 8 && bits.size.bitCount == 1;
        construct IntNumber(bits);
        }

    /**
     * Construct a variable-length signed integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size >= 1;
        construct IntNumber(bytes);
        }


    @Override
    @Lazy Signum sign.get()
        {
        // twos-complement number will have the MSB set if negative
        if (bits[bits.size-1] == 1)
            {
            return Negative;
            }

        // any other bits set is positive
        for (Bit bit : bits.iterator((bit) -> bit == 1))
            {
            return Positive;
            }

        // no bits set is zero
        return Zero;
        }

    @Override
    VarUInt magnitude.get()
        {
        // use the bits "as is" for zero, positive numbers, and
        Bit[] bits = this.bits;
        if (sign == Negative)
            {
            TODO not sure what the code was planning to do here
            }
        return new VarUInt(this.toBitArray());
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
    @Auto VarInt toVarInt()
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
    @Op VarInt neg()
        {
        return ~this + 1;
        }

    @Override
    @Op VarInt and(VarInt n)
        {
        TODO
        }

    @Override
    @Op VarInt or(VarInt n)
        {
        TODO
        }

    @Override
    @Op VarInt xor(VarInt n)
        {
        TODO
        }

    @Override
    @Op VarInt not()
        {
        return new VarInt(bitNot(bits));
        }

    @Override
    conditional VarInt next()
        {
        return true, this + 1;
        }

    @Override
    conditional VarInt prev()
        {
        return true, this - 1;
        }

    @Override
    @Op VarInt add(VarInt n)
        {
        return new VarInt(bitAdd(bits, n.bits));
        }

    @Override
    @Op VarInt sub(VarInt n)
        {
        return this + ~n + 1;
        }

    @Override
    @Op VarInt mul(VarInt n)
        {
        return this * n;
        }

    @Override
    @Op VarInt div(VarInt n)
        {
        return this / n;
        }

    @Override
    @Op VarInt mod(VarInt n)
        {
        return this % n;
        }

    @Override
    @Op VarInt shiftLeft(Int count)
        {
        return new VarInt(bitShiftLeft(bits, count));
        }

    @Override
    VarInt rotateLeft(Int count)
        {
        return new VarInt(bitRotateLeft(bits, count));
        }

    @Override
    @Op VarInt shiftRight(Int count)
        {
        return new VarInt(bitShiftRight(bits, count));
        }

    @Override
    @Op VarInt rotateRight(Int count)
        {
        return new VarInt(bitRotateRight(bits, count));
        }

    @Override
    @Op VarInt shiftAllRight(Int count)
        {
        return new VarInt(bitShiftAllRight(bits, count));
        }

    @Override
    VarInt truncate(Int count)
        {
        return new VarInt(bitTruncate(bits, count));
        }

    @Override
    VarInt leftmostBit.get()
        {
        TODO
        }

    @Override
    VarInt rightmostBit.get()
        {
        TODO
        }

    @Override
    VarInt reverseBits()
        {
        return new VarInt(bits.reverse());
        }

    @Override
    VarInt reverseBytes()
        {
        return new VarInt(toByteArray().reverse());
        }

    @Override
    VarInt abs()
        {
        return this < 0 ? -this : this;
        }

    @Override
    VarInt pow(VarInt n)
        {
        VarInt result = 1;

        while (n-- > 0)
            {
            result *= this;
            }

        return result;
        }
    }
