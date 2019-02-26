/**
 * A signed integer with a power-of-2 number of bits.
 */
const VarInt
        extends IntNumber
    {
    construct(Bit[] bits)
        {
        assert bits.size >= 8 && bits.size.bitCount == 1;
        construct IntNumber(bits);
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
        return new VarUInt(this.to<Bit[]>());
        }

    @Override
    VarInt abs()
        {
        return this < 0 ? -this : this;
        }
    }
