package org.xvm.runtime.template._native.collections.arrays;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
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
    public static BitBasedDelegate INSTANCE;

    protected BitBasedDelegate(TemplateRegistry templates, ClassStructure structure)
        {
        super(templates, structure);
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


    // ----- RTDelegate API ------------------------------------------------------------------------

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

        return frame.assignValue(iReturn, makeBitHandle(getBit(hDelegate.m_abValue, (int) lIndex)));
        }

    @Override
    protected int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                       ObjectHandle hValue)
        {
        BitArrayHandle hDelegate = (BitArrayHandle) hTarget;

        long   cSize   = hDelegate.m_cSize;
        byte[] abValue = hDelegate.m_abValue;

        if (lIndex == cSize)
            {
            if (index(cSize) == abValue.length)
                {
                abValue = hDelegate.m_abValue = grow(abValue, storage(cSize) + 1);
                }

            hDelegate.m_cSize++;
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

        if (cSize == abValue.length)
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


    // ----- BitView implementation ----------------------------------------------------------------

    @Override
    public byte[] getBits(DelegateHandle hDelegate, long ofStart, long cBits, boolean fReverse)
        {
        BitArrayHandle hBits = (BitArrayHandle) hDelegate;

        byte[] abSrc = hBits.m_abValue;

        if (hBits.getMutability() == Mutability.Constant &&
                ofStart == 0 && cBits == hBits.m_cSize && !fReverse)
            {
            return abSrc;
            }

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

        return fReverse ? reverseBits(abDst, cBits) : abDst;
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
    private static int bitMask(long iBit)
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