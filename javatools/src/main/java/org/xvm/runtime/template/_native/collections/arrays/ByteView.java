package org.xvm.runtime.template._native.collections.arrays;

import org.xvm.runtime.template._native.collections.arrays.xRTDelegate.DelegateHandle;

/**
 * A ClassTemplate implementation that knows how to extract/replace bytes in the underlying storage.
 */
public interface ByteView
    {
    /**
     * Obtain an array of bytes.
     *
     * @param hDelegate  the delegate
     * @param ofStart    the offset of the first byte
     * @param cBytes     the number of bytes to retrieve
     * @param fReverse   if true, reverse the bytes
     *
     * @return an array of bytes (most likely a copy)
     */
    byte[] getBytes(DelegateHandle hDelegate, long ofStart, long cBytes, boolean fReverse);

    /**
     * Obtain a byte.
     *
     * @param hDelegate  the delegate
     * @param of         the byte offset
     *
     * @return the byte value
     */
    byte extractByte(DelegateHandle hDelegate, long of);

    /**
     * Set a byte value.
     *
     * @param hDelegate  the delegate
     * @param of         the byte offset
     * @param bValue     the byte value
     */
    void assignByte(DelegateHandle hDelegate, long of, byte bValue);
    }
