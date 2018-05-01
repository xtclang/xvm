/**
 * A signed integer with a power-of-2 number of bits.
 */
const VarInt
    {
    construct(Bit[] bits)
        {
        assert:always bits.length >= 8 && bits.length.bitCount == 1;
        this.bits = bits;
        }

    private Bit[] bits;

    @Lazy Signum sign.get()
        {
        // twos-complement number will have the MSB set if negative
        if (bits[bits.length-1] == 1)
            {
            return Negative;
            }

        // any other bits set is positive
        if (Bit bit : iterator((bit) -> bit == 1))
            {
            return Positive;
            }

        // no bits set is zero
        return Zero;
        }

    Int bitLength.get()
        {
        return bits.length;
        }

    Int byteLength.get()
        {
        return bitLength / 8;
        }

    VarUInt magnitude.get()
        {
        // use the bits "as is" for zero, positive numbers, and
        Bit[] bits = this.bits;
        if (sign == Negative)
            {
            TODO not sure what the code was planning to do here
            }
        return new VarUInt(sign == Negative ? this.twosComplement : this.to<Bit[]>());
        }
    }
