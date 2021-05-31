package org.xvm.runtime.template._native.collections.arrays;

import org.xvm.runtime.template._native.collections.arrays.xRTDelegate.DelegateHandle;

/**
 * A ClassTemplate implementation that knows how to extract/replace bits in the underlying storage.
 */
public interface BitView
        extends ByteView
    {
    /**
     * Obtain an array of bytes representing bits.
     *
     * @param hDelegate  the delegate
     * @param ofStart    the offset of the first bit
     * @param cBits      the number of bits to retrieve
     * @param fReverse   if true, reverse the bits
     *
     * @return an array of bytes with holding the bits (most likely a copy)
     */
    byte[] getBits(DelegateHandle hDelegate, long ofStart, long cBits, boolean fReverse);

    /**
     * Obtain a bit value.
     *
     * @param hDelegate  the delegate
     * @param of         the bit offset
     *
     * @return the bit value
     */
    boolean extractBit(DelegateHandle hDelegate, long of);

    /**
     * Set the bit value
     *
     * @param hDelegate  the delegate
     * @param of         the bit offset
     * @param fBit       the bit value
     */
    void assignBit(DelegateHandle hDelegate, long of, boolean fBit);
    }
