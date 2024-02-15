package org.xvm.runtime.template._native.collections.arrays;


import java.util.Arrays;

import org.xvm.asm.Op;
import org.xvm.asm.ClassStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;


/**
 * A base class for native ArrayDelegate implementations based on bit arrays.
 */
public abstract class BitBasedDelegate
        extends ByteBasedDelegate
        implements BitView
    {
    protected BitBasedDelegate(Container container, ClassStructure structure)
        {
        super(container, structure, (byte) 0, (byte) 1);
        }

    @Override
    public DelegateHandle createDelegate(Container container, TypeConstant typeElement, int cSize,
                                         ObjectHandle[] ahContent, Mutability mutability)
        {
        byte[] ab = new byte[storage(cSize)];

        for (int i = 0, c = ahContent.length; i < c; i++)
            {
            if (isSet(ahContent[i]))
                {
                ab[index(i)] |= bitMask(i);
                }
            }
        return new BitArrayHandle(getCanonicalClass(), ab, cSize, mutability);
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    public int invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;

        if (checkWriteInPlace(frame, hDelegate, lIndex, hDelegate.m_cSize) == Op.R_EXCEPTION)
            {
            return Op.R_EXCEPTION;
            }

        if (getBit(hDelegate.m_abValue, lIndex))
            {
            return overflow(frame);
            }
        setBit(hDelegate.m_abValue, lIndex, true);
        return frame.assignValue(iReturn, makeBitHandle(true));
        }

    @Override
    public int invokePreDec(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;

        if (checkWriteInPlace(frame, hDelegate, lIndex, hDelegate.m_cSize) == Op.R_EXCEPTION)
            {
            return Op.R_EXCEPTION;
            }

        if (!getBit(hDelegate.m_abValue, lIndex))
            {
            return overflow(frame);
            }
        setBit(hDelegate.m_abValue, lIndex, false);
        return frame.assignValue(iReturn, makeBitHandle(false));
        }

    @Override
    public int invokePostInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;

        if (checkWriteInPlace(frame, hDelegate, lIndex, hDelegate.m_cSize) == Op.R_EXCEPTION)
            {
            return Op.R_EXCEPTION;
            }

        if (getBit(hDelegate.m_abValue, lIndex))
            {
            return overflow(frame);
            }
        setBit(hDelegate.m_abValue, lIndex, true);
        return frame.assignValue(iReturn, makeBitHandle(false));
        }

    @Override
    public int invokePostDec(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;

        if (checkWriteInPlace(frame, hDelegate, lIndex, hDelegate.m_cSize) == Op.R_EXCEPTION)
            {
            return Op.R_EXCEPTION;
            }

        if (!getBit(hDelegate.m_abValue, lIndex))
            {
            return overflow(frame);
            }
        setBit(hDelegate.m_abValue, lIndex, false);
        return frame.assignValue(iReturn, makeBitHandle(true));
        }

    @Override
    public DelegateHandle fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue)
        {
        assert cSize > 0;

        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;
        byte[]         ab        = hDelegate.m_abValue;

        if (isSet(hValue))
            {
            int ix = index(cSize - 1);
            if (ix > 0)
                {
                Arrays.fill(ab, 0, ix - 1, (byte) 1);
                }

            ab[ix] = tailMask(cSize - 1);

            if (ix + 1 < ab.length)
                {
                Arrays.fill(ab, ix + 1, ab.length - 1, (byte) 0);
                }
            }
        else
            {
            Arrays.fill(ab, 0, ab.length - 1, (byte) 0);
            }

        hDelegate.m_cSize = cSize;
        return hDelegate;
        }

    @Override
    public int getPropertyCapacity(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;

        return frame.assignValue(iReturn, xInt64.makeHandle((long) hDelegate.m_abValue.length << 3));
        }

    @Override
    public int setPropertyCapacity(Frame frame, ObjectHandle hTarget, long nCapacity)
        {
        return super.setPropertyCapacity(frame, hTarget, storage(nCapacity));
        }

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;

        byte[] abBits = getBits(hDelegate, ofStart, cSize, fReverse);

        return new BitArrayHandle(hDelegate.getComposition(), abBits, cSize, mutability);
        }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;

        return frame.assignValue(iReturn, makeBitHandle(getBit(hDelegate.m_abValue, lIndex)));
        }

    @Override
    protected int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                       ObjectHandle hValue)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;

        long   cSize   = hDelegate.m_cSize;
        byte[] abValue = hDelegate.m_abValue;

        if (lIndex >= cSize)
            {
            if (index(lIndex) >= abValue.length)
                {
                abValue = hDelegate.m_abValue = grow(abValue, storage(lIndex + 1));
                }

            hDelegate.m_cSize = lIndex + 1;
            }

        setBit(abValue, (int) lIndex, isSet(hValue));
        return Op.R_NEXT;
        }

    @Override
    protected void insertElementImpl(DelegateHandle hTarget, ObjectHandle hElement, long lIndex)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;
        long           cSize     = hDelegate.m_cSize;
        byte[]         abValue   = hDelegate.m_abValue;

        if (storage(cSize + 1) > abValue.length)
            {
            abValue = hDelegate.m_abValue = grow(hDelegate.m_abValue, storage(cSize) + 1);
            }
        hDelegate.m_cSize++;

        if (lIndex < cSize)
            {
            for (long i = lIndex + 1; i < cSize; i++)
                {
                setBit(abValue, i, getBit(abValue, i-1));
                }
            }
        setBit(abValue, lIndex, isSet(hElement));
        }

    @Override
    protected void deleteElementImpl(DelegateHandle hTarget, long lIndex)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;
        long           cSize     = hDelegate.m_cSize;
        byte[]         abValue   = hDelegate.m_abValue;

        if (lIndex < cSize - 1)
            {
            // TODO: improve naive implementation below by changing a byte at the time
            for (long i = lIndex + 1; i < cSize; i++)
                {
                setBit(abValue, i - 1, getBit(abValue, i));
                }
            }

        setBit(abValue, --hDelegate.m_cSize, false);
        }

    @Override
    protected void deleteRangeImpl(DelegateHandle hTarget, long lIndex, long cDelete)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;
        long           cSize     = hDelegate.m_cSize;
        byte[]         abValue   = hDelegate.m_abValue;

        if (lIndex < cSize - cDelete)
            {
            // TODO: improve naive implementation below by changing a byte at the time
            for (long i = lIndex + cDelete; i < cSize; i++)
                {
                setBit(abValue, i - cDelete, getBit(abValue, i));
                }
            }

        for (long i = cSize - cDelete; i < cSize; i++)
            {
            setBit(abValue, i, false);
            }
        hDelegate.m_cSize -= cDelete;
        }


    // ----- BitView implementation ----------------------------------------------------------------

    @Override
    public byte[] getBits(DelegateHandle hDelegate, long ofStart, long cBits, boolean fReverse)
        {
        BitArrayHandle hBits = (BitArrayHandle) hDelegate;

        byte[] ab = hBits.m_abValue;

        if (hBits.getMutability() == Mutability.Constant &&
                ofStart == 0 && cBits == ((long) ab.length << 3) && !fReverse)
            {
            return ab;
            }

        byte[] abBits = extractBits(ab, ofStart, cBits);

        return fReverse ? reverseBits(abBits, cBits) : abBits;
        }

    @Override
    public boolean extractBit(DelegateHandle hDelegate, long of)
        {
        BitArrayHandle hBits = (BitArrayHandle) hDelegate;

        return getBit(hBits.m_abValue, (int) of);
        }

    @Override
    public void assignBit(DelegateHandle hDelegate, long of, boolean fBit)
        {
        BitArrayHandle hBits = (BitArrayHandle) hDelegate;

        setBit(hBits.m_abValue, (int) of, fBit);
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Reverse the array of bits represented by the specified array.
     *
     * @param abBits the bit array
     * @param cBits  the actual number of bits held by the array
     */
    public static byte[] reverseBits(byte[] abBits, long cBits)
        {
        byte[] abValueR = new byte[abBits.length];
        for (int i = 0; i < cBits; i++)
            {
            if (getBit(abBits, i))
                {
                setBit(abValueR, cBits - 1 - i, true);
                }
            }
        return abValueR;
        }

    /**
     * Calculate the size of a byte array to represent a bit array.
     *
     * @param cBits  the bit array size
     *
     * @return the byte array size
     */
    public static int storage(long cBits)
        {
        return (int) ((cBits - 1) / 8 + 1);
        }

    /**
     * Get a bit in the specified array of bytes.
     *
     * @param abValue  the byte array
     * @param iIndex   the bit index
     *
     * @return true iff the bit is set
     */
    public static boolean getBit(byte[] abValue, long iIndex)
        {
        return (abValue[index(iIndex)] & bitMask(iIndex)) != 0;
        }

    /**
     * Set or clear a bit in the specified array of bytes.
     *
     * @param abValue  the byte array
     * @param iIndex   the bit index
     * @param fSet     true if the bit is to be set; false for clear
     */
    public static void setBit(byte[] abValue, long iIndex, boolean fSet)
        {
        if (fSet)
            {
            abValue[index(iIndex)] |= bitMask(iIndex);
            }
        else
            {
            abValue[index(iIndex)] &= (byte) ~bitMask(iIndex);
            }
        }

    /**
     * Extract an array of bits from the specified array of bits.
     *
     * @param abSrc    the byte array of bits to extract from
     * @param ofStart  the starting bit index
     * @param cBits    the number of bits to extract
     *
     * @return the byte array for the bits
     */
    protected static byte[] extractBits(byte[] abSrc, long ofStart, long cBits)
        {
        int    cBytes = storage(cBits);
        byte[] abDst;
        if (ofStart == 0)
            {
            abDst = Arrays.copyOfRange(abSrc, 0, cBytes);
            }
        else
            {
            abDst = new byte[cBytes];
            for (int i = 0; i < cBits; i++)
                {
                if (getBit(abSrc, i + (int) ofStart))
                    {
                    setBit(abDst, i, true);
                    }
                }
            }
        return abDst;
        }

    /**
     * Calculate an index of the specified bit in the byte array.
     *
     * @param iBit  the bit index
     *
     * @return the byte index
     */
    private static int index(long iBit)
        {
        return (int) (iBit / 8);
        }

    /**
     * Calculate a mask of the specified bit in the byte array at {@ling #index}.
     *
     * @param iBit  the bit index
     *
     * @return the mask
     */
    private static byte bitMask(long iBit)
        {
        return (byte) (0x80 >>> (iBit & 0x7));
        }

    /**
     * Calculate a tail mask for the specified bit in the byte array at {@ling #index}.
     *
     * @param iBit  the bit index
     *
     * @return the tail mask (all zeros pass the bit's position)
     */
    public static byte tailMask(long iBit)
        {
        return (byte) (0x80 >> (iBit & 0x7));
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    @Override
    protected ObjectHandle makeElementHandle(long lValue)
        {
        throw new IllegalStateException();
        }

    /**
     * @return true iff the specified value represents a "set" bit
     */
    protected abstract boolean isSet(ObjectHandle hValue);

    /**
     * @return an ObjectHandle representing the bit value
     */
    protected abstract ObjectHandle makeBitHandle(boolean f);

    /**
     * The handle for Bit/Boolean array delegate.
     */
    public static class BitArrayHandle
            extends ByteArrayHandle
        {
        public BitArrayHandle(TypeComposition clazz, byte[] abValue,
                              long cSize, Mutability mutability)
            {
            super(clazz, abValue, cSize, mutability);
            }

        @Override
        public long getBitCount()
            {
            return m_cSize;
            }

        @Override
        protected void purgeUnusedSpace()
            {
            byte[] ab = m_abValue;
            int    c  = storage(m_cSize);
            if (ab.length != c)
                {
                byte[] abNew = new byte[c];
                System.arraycopy(ab, 0, abNew, 0, c);
                m_abValue = abNew;
                }
            }

        @Override
        public int compareTo(ObjectHandle that)
            {
            byte[] abThis = m_abValue;
            long   cThis  = m_cSize;
            byte[] abThat = ((BitArrayHandle) that).m_abValue;
            long   cThat  = ((BitArrayHandle) that).m_cSize;

            if (cThis != cThat)
                {
                return (int) (cThis - cThat);
                }

            for (int i = 0, c = storage(cThis); i < c; i++)
                {
                int iDiff = abThis[i] - abThat[i];
                if (iDiff != 0)
                    {
                    return iDiff < 0 ? -1 : 1;
                    }
                }
            return 0;
            }
        }
    }