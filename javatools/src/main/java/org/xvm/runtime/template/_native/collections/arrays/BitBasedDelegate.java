package org.xvm.runtime.template._native.collections.arrays;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;


/**
 * A base class for native ArrayDelegate implementations based on bit arrays.
 */
public abstract class BitBasedDelegate
        extends xRTDelegate
    {
    public static BitBasedDelegate INSTANCE;

    protected BitBasedDelegate(TemplateRegistry templates, ClassStructure structure)
        {
        super(templates, structure, false);
        }

    @Override
    public void initNative()
        {
        }

    @Override
    public DelegateHandle createDelegate(TypeConstant typeElement, int cCapacity,
                                         ObjectHandle[] ahContent, Mutability mutability)
        {
        byte[] ab    = new byte[storage(cCapacity)];
        int    cSize = ahContent.length;
        for (int i = 0; i < cSize; i++)
            {
            if (isSet(ahContent[i]))
                {
                ab[index(i)] |= bitMask(i);
                }
            }

        return new BitArrayHandle(getCanonicalClass(), ab, cSize, mutability);
        }

    @Override
    public void fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue)
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
        }

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            int ofStart, int cSize, boolean fReverse)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;

        byte[] abBits = getBits(hDelegate, ofStart, cSize, fReverse);
        return new BitArrayHandle(hDelegate.getComposition(), abBits, cSize, mutability);
        }


    // ----- delegate API --------------------------------------------------------------------------

    @Override
    public int getPropertyCapacity(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;

        return frame.assignValue(iReturn, xInt64.makeHandle((long) hDelegate.m_abValue.length << 3));
        }

    @Override
    public int setPropertyCapacity(Frame frame, ObjectHandle hTarget, long nCapacity)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;

        byte[] abOld = hDelegate.m_abValue;
        int    nSize = hDelegate.m_cSize;

        if (nCapacity < nSize)
            {
            return frame.raiseException(
                xException.illegalArgument(frame, "Capacity cannot be less then size"));
            }

        // for now, no trimming
        int nNew = storage((int) nCapacity);
        int nOld = storage(abOld.length);
        if (nNew > nOld)
            {
            byte[] abNew = new byte[nNew];
            System.arraycopy(abOld, 0, abNew, 0, abOld.length);
            hDelegate.m_abValue = abNew;
            }
        return Op.R_NEXT;
        }

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hDelegate.m_cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, hDelegate.m_cSize));
            }
        return frame.assignValue(iReturn, makeBitHandle(getBit(hDelegate.m_abValue, (int) lIndex)));
        }

    @Override
    public int assignArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;

        int cSize = hDelegate.m_cSize;

        if (lIndex < 0 || lIndex > cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, cSize));
            }

        switch (hDelegate.getMutability())
            {
            case Constant:
                return frame.raiseException(xException.immutableObject(frame));

            case Persistent:
                return frame.raiseException(xException.unsupportedOperation(frame));
            }

        byte[] abValue = hDelegate.m_abValue;
        if (lIndex == cSize)
            {
            // an array can only grow without any "holes"
            if (index(cSize) > abValue.length)
                {
                if (hDelegate.getMutability() == Mutability.Fixed)
                    {
                    return frame.raiseException(xException.readOnly(frame));
                    }

                abValue = hDelegate.m_abValue = grow(abValue, storage(cSize) + 1);
                }

            hDelegate.m_cSize++;
            }

        setBit(abValue, (int) lIndex, isSet(hValue));
        return Op.R_NEXT;
        }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        BitArrayHandle h1 = (BitArrayHandle) hValue1;
        BitArrayHandle h2 = (BitArrayHandle) hValue2;

        return frame.assignValue(iReturn,
                xBoolean.makeHandle(Arrays.equals(h1.m_abValue, h2.m_abValue)));
        }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        BitArrayHandle h1 = (BitArrayHandle) hValue1;
        BitArrayHandle h2 = (BitArrayHandle) hValue2;

        if (h1 == h2)
            {
            return true;
            }

        return h1.getMutability() == h2.getMutability()
            && h1.m_cSize == h2.m_cSize
            && Arrays.equals(h1.m_abValue, h2.m_abValue);
        }

    @Override
    protected void insertElementImpl(DelegateHandle hTarget, ObjectHandle hElement, int nIndex)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;
        int            cSize   = hDelegate.m_cSize;
        byte[]         abValue = hDelegate.m_abValue;

        if (cSize == abValue.length)
            {
            abValue = hDelegate.m_abValue = grow(hDelegate.m_abValue, cSize + 1);
            }
        hDelegate.m_cSize++;

        if (nIndex == -1 || nIndex == cSize)
            {
            setBit(abValue, cSize, isSet(hElement));
            }
        else
            {
            throw new UnsupportedOperationException("TODO"); // move the bits
            }
        }

    @Override
    protected void deleteElementImpl(DelegateHandle hTarget, int nIndex)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;
        int            cSize     = hDelegate.m_cSize;
        byte[]         abValue   = hDelegate.m_abValue;

        if (nIndex < cSize - 1)
            {
            // TODO: improve naive implementation below by changing a byte at the time
            for (int i = nIndex + 1; i < cSize; i++)
                {
                setBit(abValue, i - 1, getBit(abValue, i));
                }
            }

        setBit(abValue, --hDelegate.m_cSize, false);
        }

    /**
     * @return true iff the specified value represents a "set" bit
     */
    protected abstract boolean isSet(ObjectHandle hValue);

    /**
     * @return an ObjectHandle representing the bit value
     */
    protected abstract ObjectHandle makeBitHandle(boolean f);


    // ----- helper methods ------------------------------------------------------------------------

    public static byte[] getBits(BitArrayHandle hBits, int ofStart, int cSize, boolean fReverse)
        {
        int    cBits = hBits.m_cSize;
        byte[] abSrc = hBits.m_abValue;

        if (hBits.getMutability() == Mutability.Constant &&
                ofStart == 0 && cBits == cSize && !fReverse)
            {
            return abSrc;
            }

        int    cBytes = storage(cSize);
        byte[] abDst;
        if (ofStart == 0)
            {
            abDst = Arrays.copyOfRange(abSrc, 0, cBytes);
            }
        else
            {
            abDst = new byte[cBytes];
            for (int i = 0; i < cSize; i++)
                {
                if (getBit(abSrc, i + ofStart))
                    {
                    setBit(abDst, i, true);
                    }
                }
            }

        return fReverse ? reverse(abDst, cSize) : abDst;
        }

    private static byte[] reverse(byte[] abValue, int cSize)
        {
        byte[] abValueR = new byte[abValue.length];
        for (int i = 0; i < cSize; i++)
            {
            if (getBit(abValue, i))
                {
                setBit(abValueR, cSize - 1 - i, true);
                }
            }
        return abValueR;
        }

    private static byte[] grow(byte[] abValue, int cSize)
        {
        int cCapacity = calculateCapacity(abValue.length, cSize);

        byte[] abNew = new byte[cCapacity];
        System.arraycopy(abValue, 0, abNew, 0, abValue.length);
        return abNew;
        }

    /**
     * Calculate the size of a byte array to represent a bit array.
     *
     * @param cBits  the bit array size
     *
     * @return the byte array size
     */
    public static int storage(int cBits)
        {
        return (cBits - 1) / 8 + 1;
        }

    /**
     * Get a bit in the specified array of bytes.
     *
     * @param abValue  the byte array
     * @param iIndex   the bit index
     *
     * @return true iff the bit is set
     */
    public static boolean getBit(byte[] abValue, int iIndex)
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
    public static void setBit(byte[] abValue, int iIndex, boolean fSet)
        {
        if (fSet)
            {
            abValue[index(iIndex)] |= bitMask(iIndex);
            }
        else
            {
            abValue[index(iIndex)] &= ~bitMask(iIndex);
            }
        }

    /**
     * Calculate an index of the specified bit in the byte array.
     *
     * @param iBit  the bit index
     *
     * @return the byte index
     */
    public static int index(int iBit)
        {
        return iBit / 8;
        }

    /**
     * Calculate a mask of the specified bit in the byte array at {@ling #index}.
     *
     * @param iBit  the bit index
     *
     * @return the mask
     */
    public static int bitMask(int iBit)
        {
        return 0x80 >>> (iBit & 0x7);
        }

    /**
     * Calculate a tail mask for the specified bit in the byte array at {@ling #index}.
     *
     * @param iBit  the bit index
     *
     * @return the tail mask (all zeros pass the bit's position)
     */
    public static byte tailMask(int iBit)
        {
        return (byte) (0x80 >> (iBit & 0x7));
        }


    // ----- handle --------------------------------------------------------------------------------

    /**
     * The handle for Bit/Boolean array delegate.
     */
    public static class BitArrayHandle
            extends DelegateHandle
        {
        public byte[] m_abValue;

        public BitArrayHandle(TypeComposition clazz, byte[] abValue, int cBits, Mutability mutability)
            {
            super(clazz, mutability);

            m_abValue = abValue;
            m_cSize   = cBits;
            }

        @Override
        public void makeImmutable()
            {
            if (isMutable())
                {
                // purge the unused space
                byte[] ab = m_abValue;
                int    c  = storage(m_cSize);
                if (ab.length != c)
                    {
                    byte[] abNew = new byte[c];
                    System.arraycopy(ab, 0, abNew, 0, c);
                    m_abValue = abNew;
                    }
                super.makeImmutable();
                }
            }

        @Override
        public boolean isNativeEqual()
            {
            return true;
            }

        @Override
        public int compareTo(ObjectHandle that)
            {
            byte[] abThis = m_abValue;
            int    cThis  = m_cSize;
            byte[] abThat = ((BitArrayHandle) that).m_abValue;
            int    cThat  = ((BitArrayHandle) that).m_cSize;

            if (cThis != cThat)
                {
                return cThis - cThat;
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

        @Override
        public int hashCode()
            {
            return Arrays.hashCode(m_abValue);
            }

        @Override
        public boolean equals(Object obj)
            {
            return obj instanceof BitArrayHandle
                && Arrays.equals(m_abValue, ((BitArrayHandle) obj).m_abValue);
            }
        }
    }