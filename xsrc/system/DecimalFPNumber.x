const DecimalFPNumber
        extends FPNumber
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a decimal floating point number from its bitwise machine representation.
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
     * Construct a decimal floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    protected construct(Byte[] bytes)
        {
        construct FPNumber(bytes);
        }


    // ----- Number properties ---------------------------------------------------------------------

    @Override
    Signum sign.get()
        {
        // TODO this is correct for the default 0 encoding, but not for all members of the 0 cohort
        Boolean negative = false;
        eachBit: for (Bit bit : toBitArray())
            {
            if (bit == 1)
                {
                if (eachBit.first)
                    {
                    negative = true;
                    }
                else
                    {
                    return negative ? Negative : Positive;
                    }
                }
            }
        return Zero;
        }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    Boolean finite.get()
        {
        Bit[] bitsL2R = toBitArray();
        return bitsL2R[1] != 1          // G0
            || bitsL2R[2] != 1          // G1
            || bitsL2R[3] != 1          // G2
            || bitsL2R[4] != 1;         // G3
        }

    @Override
    Boolean infinite.get()
        {
        // from IEEE 754-2008:
        //   If G0 through G4 are 11110 then r and v = (−1) S × (+∞).
        Bit[] bitsL2R = toBitArray();
        return bitsL2R[1] == 1          // G0
            && bitsL2R[2] == 1          // G1
            && bitsL2R[3] == 1          // G2
            && bitsL2R[4] == 1          // G3
            && bitsL2R[5] == 0;         // G4
        }

    @Override
    Boolean NaN.get()
        {
        // from IEEE 754-2008:
        //   If G0 through G4 are 11111, then v is NaN regardless of S.
        Bit[] bitsL2R = toBitArray();
        return bitsL2R[1] == 1          // G0
            && bitsL2R[2] == 1          // G1
            && bitsL2R[3] == 1          // G2
            && bitsL2R[4] == 1          // G3
            && bitsL2R[5] == 1;         // G4
        }

    @Override
    @RO Int radix.get()
        {
        return 10;
        }

    @Override
    @RO Int precision.get()
        {
        // from IEEE 754-2008:
        //   p = 9×k/32−2
        return 9 * byteLength / 32 - 2;
        }

    @Override
    @RO Int emax.get()
        {
        // from IEEE 754-2008:
        //   w    = k/16+4
        //   emax = 3×2^(w−1)
        return 3 * (1 << byteLength / 16 + 3);
        }
    }
