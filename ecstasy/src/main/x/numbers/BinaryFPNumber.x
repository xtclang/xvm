/**
 * An BinaryFPNumber is a Number that represents a binary floating point value, often referred to as
 * a "float" or "double" in common usage, because of the prevalence of those specific types in C and
 * subsequent languages, and because decimal floating point types were not standardized until 2008.
 */
@Abstract const BinaryFPNumber
        extends FPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a binary floating point number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    construct(Bit[] bits)
        {
        construct FPNumber(bits);
        }

    /**
     * Construct a binary floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    construct(Byte[] bytes)
        {
        construct FPNumber(bytes);
        }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    Boolean infinity.get()
        {
        return exponentOnly1s && significandOnly0s;
        }

    @Override
    Boolean NaN.get()
        {
        return exponentOnly1s && !significandOnly0s;
        }

    @Override
    @RO Int radix.get()
        {
        return 2;
        }

    @Override
    @RO Int precision.get()
        {
        return significandBitLength + 1;
        }

    @Override
    Int significandBitLength.get()
        {
        // k – round(4 × log2 (k)) + 12
        Int k = bitLength;
        return k - 4 * k.trailingZeroCount + 12;
        }

    @Override
    Int exponentBitLength.get()
        {
        // round(4 × log2 (k)) – 13
        Int k = bitLength;
        return 4 * k.trailingZeroCount - 13;
        }


    // ----- FPNumber properties -------------------------------------------------------------------

    private Boolean exponentOnly1s.get()
        {
        Bit[] bitsL2R = toBitArray();
        for (Bit bit : bitsL2R[1..exponentBitLength])
            {
            if (bit == 0)
                {
                return False;
                }
            }
        return True;
        }

    private Boolean significandOnly0s.get()
        {
        Bit[] bitsL2R = toBitArray();
        for (Bit bit : bitsL2R[bitsL2R.size-significandBitLength..bitsL2R.size))
            {
            if (bit == 1)
                {
                return False;
                }
            }
        return True;
        }
    }
