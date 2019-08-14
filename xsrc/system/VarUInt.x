/**
 * An unsigned integer with a power-of-2 number of bits (at least 8).
 */
const VarUInt
        extends UIntNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a variable-length unsigned integer number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    construct(Bit[] bits)
        {
        assert bits.size >= 8 && bits.size.bitCount == 1;
        construct UIntNumber(bits);
        }

    /**
     * Construct a variable-length unsigned integer number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        assert bytes.size >= 1;
        construct UIntNumber(bytes);
        }


    /**
     * The sign property is declared by Number as read-only, but the VarUInt actually needs storage
     * for the sign property, so it is declaring it at this level as a read/write property (which,
     * due to the constness of this class, is read-only in reality.)
     */
    @Override
    public/private Signum sign; // TODO when this was Boolean, it didn't complain (should have been compiler error)

    @Override
    immutable Bit[] toBitArray()
        {
        return bits.reverse.as(immutable Bit[]);
        }


    // ----- IntNumber support ---------------------------------------------------------------------

    @Override
    @Op VarUInt and(VarUInt that)
        {
        return this & that;
        }

    @Override
    @Op VarUInt or(VarUInt that)
        {
        return this | that;
        }

    @Override
    @Op VarUInt xor(VarUInt that)
        {
        return this ^ that;
        }

    @Override
    @Op VarUInt not()
        {
        return ~this;
        }

    @Override
    @Op VarUInt shiftLeft(Int count)
        {
        return this << count;
        }

    @Override
    @Op VarUInt shiftRight(Int count)
        {
        return this >> count;
        }

    @Override
    @Op VarUInt shiftAllRight(Int count)
        {
        return this >>> count;
        }

    @Override
    VarUInt rotateLeft(Int count)
        {
        TODO
        }

    @Override
    VarUInt rotateRight(Int count)
        {
        TODO
        }

    @Override
    VarUInt truncate(Int count)
        {
        TODO
        }

    @Override
    @RO VarUInt leftmostBit.get()
        {
        TODO
        }

    @Override
    @RO VarUInt rightmostBit.get()
        {
        TODO
        }

    @Override
    @RO Int leadingZeroCount.get()
        {
        TODO
        }

    @Override
    @RO Int trailingZeroCount.get()
        {
        TODO
        }

    @Override
    @RO Int bitCount.get()
        {
        TODO
        }

    @Override
    VarUInt reverseBits()
        {
        TODO
        }

    @Override
    VarUInt reverseBytes()
        {
        TODO
        }

    // ----- Number support ------------------------------------------------------------------------

    @Override
    @Op VarUInt neg()
        {
        return -this;
        }

    @Override
    @Op VarUInt pow(VarUInt n)
        {
        VarUInt result = 1;
        for (VarUInt p = 0; p < n; p++)
            {
            result *= this;
            }
        return result;
        }

    @Override
    @Op VarUInt add(VarUInt n)
        {
        return this + n;
        }

    @Override
    @Op VarUInt sub(VarUInt n)
        {
        return this - n;
        }

    @Override
    @Op VarUInt mul(VarUInt n)
        {
        return this * n;
        }

    @Override
    @Op VarUInt div(VarUInt n)
        {
        return this / n;
        }

    @Override
    @Op VarUInt mod(VarUInt n)
        {
        return this % n;
        }

    @Override
    VarInt toVarInt()
        {
        TODO
        }

    @Override
    VarUInt toVarUInt()
        {
        TODO
        }

    @Override
    VarFloat toVarFloat()
        {
        TODO
        }

    @Override
    VarDec toVarDec()
        {
        TODO
        }

    @Override
    immutable Boolean[] toBooleanArray()
        {
        return bitBooleans(bits);
        }

    // ----- Sequential interface ------------------------------------------------------------------

    /**
     * Value increment. Never throws.
     */
    @Override
    VarUInt nextValue()
        {
        return this + 1;
        }

    /**
     * Value decrement. Never throws.
     */
    @Override
    VarUInt prevValue()
        {
        return this - 1;
        }

    /**
     * Checked value increment.
     */
    @Override
    conditional VarUInt next()
        {
        return true, this + 1;
        }

    /**
     * Checked value decrement.
     */
    @Override
    conditional VarUInt prev()
        {
        return true, this - 1;
        }
    }
