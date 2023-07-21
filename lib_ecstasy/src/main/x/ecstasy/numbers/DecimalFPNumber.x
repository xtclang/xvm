/**
 * A DecimalFPNumber is a Number that represents a decimal floating point value.
 */
@Abstract const DecimalFPNumber
        extends FPNumber {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a decimal floating point number from its bitwise machine representation.
     *
     * @param bits  an array of bit values that represent this number, ordered from left-to-right,
     *              Most Significant Bit (MSB) to Least Significant Bit (LSB)
     */
    @Override
    construct(Bit[] bits) {
        super(bits);
    }

    /**
     * Construct a decimal floating point number from its network-portable representation.
     *
     * @param bytes  an array of byte values that represent this number, ordered from left-to-right,
     *               as they would appear on the wire or in a file
     */
    @Override
    construct(Byte[] bytes) {
        super(bytes);
    }


    // ----- Number properties ---------------------------------------------------------------------

    @Override
    Signum sign.get() {
        // TODO this is correct for the default 0 encoding, but not for all members of the 0 cohort
        eachBit: for (Bit bit : bits) {
            if (bit == 1) {
                if (!eachBit.first) {
                    return negative ? Negative : Positive;
                }
            }
        }
        return Zero;
    }


    // ----- FPNumber properties -------------------------------------------------------------------

    @Override
    Boolean finite.get() {
        Bit[] bitsL2R = bits;
        return bitsL2R[1] != 1          // G0
            || bitsL2R[2] != 1          // G1
            || bitsL2R[3] != 1          // G2
            || bitsL2R[4] != 1;         // G3
    }

    @Override
    Boolean infinity.get() {
        // from IEEE 754-2008:
        //   If G0 through G4 are 11110 then r and v = (−1) S × (+∞).
        Bit[] bitsL2R = bits;
        return bitsL2R[1] == 1          // G0
            && bitsL2R[2] == 1          // G1
            && bitsL2R[3] == 1          // G2
            && bitsL2R[4] == 1          // G3
            && bitsL2R[5] == 0;         // G4
    }

    @Override
    Boolean NaN.get() {
        // from IEEE 754-2008:
        //   If G0 through G4 are 11111, then v is NaN regardless of S.
        Bit[] bitsL2R = bits;
        return bitsL2R[1] == 1          // G0
            && bitsL2R[2] == 1          // G1
            && bitsL2R[3] == 1          // G2
            && bitsL2R[4] == 1          // G3
            && bitsL2R[5] == 1;         // G4
    }

    @Override
    @RO Int radix.get() {
        return 10;
    }

    @Override
    @RO Int precision.get() {
        // from IEEE 754-2008:
        //   p = 9×k/32−2
        return 9 * byteLength / 32 - 2;
    }

    @Override
    @RO Int significandBitLength.get() {
        // from IEEE 754-2008:
        //   15×k/16 – 10
        return 15 * bitLength / 16 - 10;
    }

    @Override
    @RO Int exponentBitLength.get() {
        // from IEEE 754-2008:
        //   combination field width in bits
        //   w+5 = 15×k/16 – 10
        // subtract 5 bits for the raw exponent length
        return bitLength / 16 + 4;
    }


    // ----- conversions ---------------------------------------------------------------------------

    @Override
    IntN toIntN(Rounding direction = TowardZero) = round(direction).toIntN();

    @Override
    UIntN toUIntN(Rounding direction = TowardZero) = round(direction).toUIntN();

    @Auto
    @Override
    FloatN toFloatN() = toFPLiteral().toFloatN();

    @Auto
    @Override
    DecN toDecN() = new DecN(bits);
}