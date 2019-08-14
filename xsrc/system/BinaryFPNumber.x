const BinaryFPNumber
        extends FPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a binary floating point number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from Least
     *              Significant Bit (LSB) in the `0` element, to Most Significant Bit (MSB) in the
     *              `size-1` element
     */
    protected construct(Bit[] bits)
        {
        construct FPNumber(bits);
        }

    /**
     * Construct a binary floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    protected construct(Byte[] bytes)
        {
        construct FPNumber(bytes);
        }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    @RO Int radix.get()
        {
        return 2;
        }

    @Override
    @RO Int precision.get()
        {
        // TODO k – round(4×log2(k)) + 13
        return 0;
        }

    @Override
    @RO Int emax.get()
        {
        // TODO 2^(k–p–1) –1
        return 0;
        }
    }
