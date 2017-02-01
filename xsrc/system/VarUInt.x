/**
 * An unsigned integer with a power-of-2 number of bits.
 */
const VarUInt
    {
    construct VarUInt(Bit[] bits)
        {
        // ignore leading zeros to find the actual number of bits necessary for the value
        Int bitsUsed = bits.length;
        while (bitsUsed > 0 && bits[bitsUsed-1] == 0)
            {
            --bitsUsed;
            }
            
        // round up the number of bits to the next largest power of two
        bitsUsed = bitsUsed < 8 ? 8 : (bitsUsed * 2 - 1).leftmostBit;

        Bit[] newBits = bits;
        if (bitsUsed != bits.length)
            {
            // need to allocate a new array of bits of the calculated size, and copy over
            // the least significant bitsUsed number of bits that were passed in
            newBits = new Bit[bitsUsed];
            for (Int i = 0, c = bitsUsed.min(bits.length); i < c; ++i)
                {
                newBits[i] = bits[i];
                }
            bits = newBits;
            }
            
        // determine how many bits are necessary to store the value
        for (Int i = bits.length - 1; i >= 0; --i)
            {
            
            bits[i]
            }
            
        // TODO
        }

    // TODO
    
    VarInt
    }