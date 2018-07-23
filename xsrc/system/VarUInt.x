/**
 * An unsigned integer with a power-of-2 number of bits (at least 8).
 */
const VarUInt
        implements UIntNumber
    {
    /**
     * TODO
     *
     * Note that the bits are arranged such that bits[0] is the least significant bit and
     * bits[bits.length-1] is the most significant bit.
     */
    construct(Bit[] bits)
        {
        // ignore leading zeros to find the actual number of bits necessary for the value
        Int    bitsUsed = bits.size;
        while (bitsUsed > 0 && bits[bitsUsed-1] == 0)
            {
            --bitsUsed;
            }

        // determine the sign of the integer
        sign = bitsUsed == 0 ? Zero : Positive;

        // round up the number of bits to the next largest power of two
        bitsUsed = bitsUsed < 8 ? 8 : (bitsUsed * 2 - 1).leftmostBit;

        // if the passed-in bits array isn't the right length, then replace it with a correct one
        if (bitsUsed == bits.size)
            {
            this.bits = bits;
            }
        else
            {
            // need to allocate a new array of bits of the calculated size, and copy over
            // the least significant bitsUsed number of bits that were passed in
            Bit[] newBits = new Bit[bitsUsed];
            for (Int i = 0, c = bitsUsed.min(bits.size); i < c; ++i)
                {
                newBits[i] = bits[i];
                }
            this.bits = newBits;
            }
        }

    /**
     * The actual bits of the unsigned integer.
     */
    private Bit[] bits;

    /**
     * The sign property is declared by Number as read-only, but the VarUInt actually needs storage
     * for the sign property, so it is declaring it at this level as a read/write property (which,
     * due to the constness of this class, is read-only in reality.)
     */

    @Override
    Signum sign;

    @Override
    Int bitLength.get()
        {
        return bits.size;
        }

    @Override
    Bit[] to<Bit[]>()
        {
        return bits;
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
    @RO Int leftmostBit.get()
        {
        TODO
        }

    @Override
    @RO Int rightmostBit.get()
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
    VarInt to<VarInt>()
        {
        TODO
        }

    @Override
    VarUInt to<VarUInt>()
        {
        TODO
        }

    @Override
    VarFloat to<VarFloat>()
        {
        TODO
        }

    @Override
    VarDec to<VarDec>()
        {
        TODO
        }

    // ----- Sequential interface ------------------------------------------------------------------

    /**
     * Value increment. Never throws.
     */
    @Override
    IntNumber nextValue()
        {
        return this + 1;
        }

    /**
     * Value decrement. Never throws.
     */
    @Override
    IntNumber prevValue()
        {
        return this - 1;
        }

    /**
     * Checked value increment.
     */
    @Override
    conditional IntNumber next()
        {
        return true, this + 1;
        }

    /**
     * Checked value decrement.
     */
    @Override
    conditional IntNumber prev()
        {
        return true, this - 1;
        }
    }
