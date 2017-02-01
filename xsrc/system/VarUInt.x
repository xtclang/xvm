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
    construct VarUInt(Bit[] bits)
        {
        // ignore leading zeros to find the actual number of bits necessary for the value
        Int    bitsUsed = bits.length;
        while (bitsUsed > 0 && bits[bitsUsed-1] == 0)
            {
            --bitsUsed;
            }

        // determine the sign of the integer
        sign = bitsUsed == 0 ? Zero : Positive;

        // round up the number of bits to the next largest power of two
        bitsUsed = bitsUsed < 8 ? 8 : (bitsUsed * 2 - 1).leftmostBit;

        // if the passed-in bits array isn't the right length, then replace it with a correct one
        if (bitsUsed == bits.length)
            {
            this.bits = bits;
            }
        else
            {
            // need to allocate a new array of bits of the calculated size, and copy over
            // the least significant bitsUsed number of bits that were passed in
            Bit[] newBits = new Bit[bitsUsed];
            for (Int i = 0, c = bitsUsed.min(bits.length); i < c; ++i)
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
    Signum sign;

    Int bitLength.get()
        {
        return bits.length;
        }

    Bit[] to<Bit[]>()
        {
        return bits;
        }
    }
