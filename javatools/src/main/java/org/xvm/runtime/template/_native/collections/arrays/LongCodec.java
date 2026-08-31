package org.xvm.runtime.template._native.collections.arrays;

/**
 * How fixed-width integer values are packed into a {@code long[]}.
 *
 * <p>This is the whole of what a long-backed delegate needs in order to read and write elements,
 * and it is derived from two facts about the element type: its width in bits, and whether it is
 * signed. It is therefore data, not behaviour - no delegate overrides the packing, they only pass
 * different widths to the same constructor - which is why it can be held by whichever object owns
 * the array rather than by the template that happens to know the element type.
 *
 * <p>Deliberately not a record: the masks, shifts and sign bit are how the packing is implemented,
 * not part of what a caller needs, and a record would publish all seven as accessors.
 */
public final class LongCodec {
    /**
     * @param cBitsPerValue  the element width in bits; must be a power of two
     * @param fSigned        whether the element type is signed
     *
     * @return the codec for that element type
     */
    public static LongCodec of(int cBitsPerValue, boolean fSigned) {
        assert Integer.bitCount(cBitsPerValue) == 1;

        return new LongCodec(cBitsPerValue, fSigned);
    }

    private LongCodec(int cBitsPerValue, boolean fSigned) {
        f_cBitsPerValue  = cBitsPerValue;
        f_cValuesPerLong = 64 / cBitsPerValue;
        f_nIndexShift    = f_cValuesPerLong >> 1;
        f_nIndexMask     = (1 << f_nIndexShift) - 1;
        f_lValueMask     = -1L >>> (64 - cBitsPerValue);
        f_lSignBit       = 1L << (cBitsPerValue - 1);
        f_fSigned        = fSigned;
    }

    /**
     * @return how many elements fit in one long
     */
    public int cValuesPerLong() {
        return f_cValuesPerLong;
    }

    /**
     * @return the number of longs needed to hold the specified number of elements
     */
    public int storage(long cValues) {
        return (int) ((cValues - 1) / f_cValuesPerLong + 1);
    }

    /**
     * @return the index into the long array holding the specified element
     */
    public int valueIndex(long lIndex) {
        return (int) (lIndex >>> f_nIndexShift);
    }

    /**
     * @return the element at the specified index
     */
    public long getValue(long[] alValue, long lIndex) {
        long l = (alValue[valueIndex(lIndex)] >>> shiftCount(lIndex)) & f_lValueMask;
        if (f_fSigned && (l & f_lSignBit) != 0) {
            // extend the sign
            l |= ~f_lValueMask;
        }
        return l;
    }

    /**
     * Store an element at the specified index.
     */
    public void setValue(long[] alValue, long lIndex, long lValue) {
        int  nIndex = valueIndex(lIndex);
        int  cShift = shiftCount(lIndex);
        long lMask  = f_lValueMask << cShift;

        alValue[nIndex] = alValue[nIndex] & ~lMask | ((lValue & f_lValueMask) << cShift);
    }

    private int shiftCount(long lIndex) {
        return 64 - f_cBitsPerValue * ((((int) lIndex) & f_nIndexMask) + 1);
    }

    private final int     f_cBitsPerValue;
    private final int     f_cValuesPerLong;
    private final int     f_nIndexShift;
    private final int     f_nIndexMask;
    private final long    f_lValueMask;
    private final long    f_lSignBit;
    private final boolean f_fSigned;
}
