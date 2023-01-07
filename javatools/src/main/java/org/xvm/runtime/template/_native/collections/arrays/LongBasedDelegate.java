package org.xvm.runtime.template._native.collections.arrays;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt;

import org.xvm.util.Handy;


/**
 * A base class for native ArrayDelegate implementations based on long arrays.
 */
public abstract class LongBasedDelegate
        extends xRTDelegate
        implements BitView
    {
    protected LongBasedDelegate(Container container, ClassStructure structure,
                                int nBitsPerValue, boolean fSigned)
        {
        super(container, structure, false);

        f_nBitsPerValue  = nBitsPerValue;
        f_nValuesPerLong = 64 / nBitsPerValue;
        f_nIndexShift    = f_nValuesPerLong >> 1;
        f_nIndexMask     = (1 << f_nIndexShift) - 1;
        f_lValueMask     = -1L >>> (64 - f_nBitsPerValue);
        f_lSignBit       = 1L << (f_nBitsPerValue - 1);
        f_fSigned        = fSigned;
        }

    @Override
    public DelegateHandle createDelegate(Container container, TypeConstant typeElement, int cSize,
                                         ObjectHandle[] ahContent, Mutability mutability)
        {
        long[] alValue = new long[storage(cSize)];

        for (int i = 0, c = ahContent.length; i < c; i++)
            {
            setValue(alValue, i, ((JavaLong) ahContent[i]).getValue());
            }
        return makeHandle(alValue, cSize, mutability);
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    public DelegateHandle fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue)
        {
        assert cSize > 0;

        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;
        long[]          al        = hDelegate.m_alValue;
        long            lValue    = ((JavaLong) hValue).getValue();

        for (int i = 0; i < cSize; i++)
            {
            setValue(al, i, lValue);
            }

        hDelegate.m_cSize = cSize;
        return hDelegate;
        }

    @Override
    public int getPropertyCapacity(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        return frame.assignValue(iReturn,
                xInt.makeHandle((long) hDelegate.m_alValue.length * f_nValuesPerLong));
        }

    @Override
    public int setPropertyCapacity(Frame frame, ObjectHandle hTarget, long nCapacity)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        long[] abOld = hDelegate.m_alValue;
        long   cSize = hDelegate.m_cSize;

        if (nCapacity < cSize)
            {
            return frame.raiseException(
                xException.illegalArgument(frame, "Capacity cannot be less then size"));
            }

        // for now, no trimming
        int nNew = storage((int) nCapacity);
        int nOld = storage(abOld.length);
        if (nNew > nOld)
            {
            long[] abNew = new long[nNew];
            System.arraycopy(abOld, 0, abNew, 0, abOld.length);
            hDelegate.m_alValue = abNew;
            }
        return Op.R_NEXT;
        }

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        int    nStart = valueIndex(ofStart);
        long[] alValue;

        if (shiftCount(ofStart) == 0)
            {
            alValue = Arrays.copyOfRange(hDelegate.m_alValue, nStart, nStart + storage(cSize));
            }
        else
            {
            throw new UnsupportedOperationException("TODO"); // copy one by one
            }

        if (fReverse)
            {
            alValue = reverse(alValue, (int) cSize);
            }
        return new LongArrayHandle(hDelegate.getComposition(), alValue, cSize, mutability);
        }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        return frame.assignValue(iReturn, makeElementHandle(getValue(hDelegate.m_alValue, lIndex)));
        }

    @Override
    protected int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                       ObjectHandle hValue)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;
        long            cSize     = hDelegate.m_cSize;
        long[]          alValue   = hDelegate.m_alValue;

        if (lIndex >= cSize)
            {
            if (valueIndex(lIndex) >= alValue.length)
                {
                alValue = hDelegate.m_alValue = grow(alValue, storage(lIndex + 1));
                }

            hDelegate.m_cSize = lIndex + 1;
            }

        setValue(alValue, lIndex, ((JavaLong) hValue).getValue());
        return Op.R_NEXT;
        }

    @Override
    protected void insertElementImpl(DelegateHandle hTarget, ObjectHandle hElement, long lIndex)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;
        long            cSize     = hDelegate.m_cSize;
        long[]          alValue   = hDelegate.m_alValue;

        if (cSize == alValue.length)
            {
            alValue = hDelegate.m_alValue = grow(hDelegate.m_alValue, storage(cSize) + 1);
            }
        hDelegate.m_cSize++;

        if (lIndex == cSize)
            {
            setValue(alValue, cSize, ((JavaLong) hElement).getValue());
            }
        else
            {
            throw new UnsupportedOperationException("TODO GG"); // move the longs
            }
        }

    @Override
    protected void deleteElementImpl(DelegateHandle hTarget, long lIndex)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;
        long            cSize     = hDelegate.m_cSize;
        long[]          alValue   = hDelegate.m_alValue;

        if (lIndex < cSize - 1)
            {
            // TODO: improve naive implementation below by changing a long at the time
            for (long i = lIndex + 1; i < cSize; i++)
                {
                setValue(alValue, i - 1, getValue(alValue, i));
                }
            }

        alValue[(int) --hDelegate.m_cSize] = 0;
        }

    @Override
    protected void deleteRangeImpl(DelegateHandle hTarget, long lIndex, long cDelete)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;
        long            cSize     = hDelegate.m_cSize;
        long[]          alValue   = hDelegate.m_alValue;

        if (lIndex < cSize - cDelete)
            {
            // TODO: improve naive implementation below by changing a long at the time
            for (long i = lIndex + cDelete; i < cSize; i++)
                {
                setValue(alValue, i - cDelete, getValue(alValue, i));
                }
            }

        for (long i = cSize - cDelete; i < cSize; i++)
            {
            setValue(alValue, i, 0);
            }
        hDelegate.m_cSize -= cDelete;
        }

    /**
     * @return an ObjectHandle representing specified value
     */
    protected abstract ObjectHandle makeElementHandle(long lValue);


    // ----- BitView implementation ----------------------------------------------------------------

    @Override
    public byte[] getBits(DelegateHandle hDelegate, long ofStart, long cBits, boolean fReverse)
        {
        LongArrayHandle hLongs = (LongArrayHandle) hDelegate;

        byte[] abBits = extractBits(hLongs.m_alValue, ofStart, cBits);
        if (fReverse)
            {
            abBits = BitBasedDelegate.reverseBits(abBits, cBits);
            }
        return abBits;
        }

    @Override
    public boolean extractBit(DelegateHandle hDelegate, long of)
        {
        LongArrayHandle hLongs = (LongArrayHandle) hDelegate;

        return getBit(hLongs.m_alValue, of);
        }

    @Override
    public void assignBit(DelegateHandle hDelegate, long of, boolean fBit)
        {
        LongArrayHandle hLongs = (LongArrayHandle) hDelegate;

        setBit(hLongs.m_alValue, of, fBit);
        }


    // ----- ByteView implementation ---------------------------------------------------------------

    @Override
    public byte[] getBytes(DelegateHandle hDelegate, long ofStart, long cBytes, boolean fReverse)
        {
        return getBits(hDelegate, ofStart*8, cBytes*8, fReverse);
        }

    @Override
    public byte extractByte(DelegateHandle hDelegate, long of)
        {
        LongArrayHandle hLongs = (LongArrayHandle) hDelegate;

        return getByte(hLongs.m_alValue, of);
        }

    @Override
    public void assignByte(DelegateHandle hDelegate, long of, byte bValue)
        {
        LongArrayHandle hLongs = (LongArrayHandle) hDelegate;

        setByte(hLongs.m_alValue, of, bValue);
        }


    // ----- ClassTemplate API ---------------------------------------------------------------------

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        LongArrayHandle h1 = (LongArrayHandle) hValue1;
        LongArrayHandle h2 = (LongArrayHandle) hValue2;

        if (h1 == h2)
            {
            return frame.assignValue(iReturn, xBoolean.TRUE);
            }
        if (h1.m_cSize != h2.m_cSize)
            {
            return frame.assignValue(iReturn, xBoolean.FALSE);
            }

        // this is slightly incorrect; it assumes that we always trim the tail
        int cStore = storage(h1.m_cSize);
        return frame.assignValue(iReturn, xBoolean.makeHandle(
                Arrays.equals(h1.m_alValue, 0, cStore, h2.m_alValue, 0, cStore)));
        }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        LongArrayHandle h1 = (LongArrayHandle) hValue1;
        LongArrayHandle h2 = (LongArrayHandle) hValue2;

        if (h1 == h2)
            {
            return true;
            }

        return h1.getMutability() == h2.getMutability()
            && h1.m_cSize == h2.m_cSize
            && Arrays.equals(h1.m_alValue, h2.m_alValue);
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Reverse the array of longs represented by the specified array.
     *
     * @param alValue  the bit array
     * @param cSize    the actual number of values held by the array
     */
    public long[] reverse(long[] alValue, int cSize)
        {
        long[] alValueR = new long[cSize];
        for (int i = 0; i < cSize; i++)
            {
            setValue(alValueR, cSize - 1 - i, getValue(alValue, i));
            }
        return alValueR;
        }

    public static long[] grow(long[] alValue, int cNew)
        {
        int cCapacity = calculateCapacity(alValue.length, cNew);

        long[] alNew = new long[cCapacity];
        System.arraycopy(alValue, 0, alNew, 0, alValue.length);
        return alNew;
        }

    /**
     * Calculate the size of a long array to represent an array of values.
     *
     * @param cValues  the number of values to store
     *
     * @return the long array size
     */
    protected int storage(long cValues)
        {
        return (int) ((cValues - 1) / f_nValuesPerLong + 1);
        }

    /**
     * Get a value in the specified array of longs.
     *
     * @param alValue  the long array
     * @param lIndex   the value index
     *
     * @return the value as a long
     */
    protected long getValue(long[] alValue, long lIndex)
        {
        long l = (alValue[valueIndex(lIndex)] >>> shiftCount(lIndex)) & f_lValueMask;
        if (f_fSigned && (l & f_lSignBit) != 0)
            {
            // extend the sign
            l |= ~f_lValueMask;
            }
        return l;
        }

    /**
     * Set or clear a bit in the specified array of longs.
     *
     * @param alValue  the long array
     * @param lIndex   the value index
     * @param lValue   the value
     */
    protected void setValue(long[] alValue, long lIndex, long lValue)
        {
        int  nIndex = valueIndex(lIndex);
        int  cShift = shiftCount(lIndex);
        long lMask  = f_lValueMask << cShift;

        alValue[nIndex] = alValue[nIndex] & ~lMask | ((lValue & f_lValueMask) << cShift);
        }

    /**
     * Calculate an index of the specified value index in the long array.
     *
     * @param lIndex  the value index
     *
     * @return the index into the long array
     */
    private int valueIndex(long lIndex)
        {
        return (int) (lIndex >>> f_nIndexShift);
        }

    /**
     * Calculate a shift count for the specified index.
     *
     * @param lIndex   the value index
     *
     * @return the mask
     */
    private int shiftCount(long lIndex)
        {
        return 64 - f_nBitsPerValue * ((((int) lIndex) & f_nIndexMask) + 1);
        }

    /**
     * Get a byte in the specified array of longs.
     *
     * @param alValue  the long array
     * @param of       the byte index
     *
     * @return the byte value
     */
    protected static byte getByte(long[] alValue, long of)
        {
        int  ixVal  = (int) (of / 8);
        int  ofByte = 7 - (int) (of & 7);

        return (byte) (alValue[ixVal] >>> (ofByte * 8) & 0xFF);
        }

    /**
     * Set a byte in the specified array of longs.
     *
     * @param alValue  the long array
     * @param of       the byte index
     * @param bValue   the byte value
     */
    protected static void setByte(long[] alValue, long of, byte bValue)
        {
        int  ixVal  = (int) (of / 8);
        int  ofByte = 7 - (int) (of & 7);
        long lMask  = 0xFFL << (ofByte * 8);

        alValue[ixVal] = alValue[ixVal] & ~lMask | (bValue & 0xFFL) << (ofByte * 8);
        }

    /**
     * Extract an array of bits from the specified array of longs.
     *
     * @param alValue  the long array
     * @param ofStart  the starting bit index
     * @param cBits    the number of bits to extract
     *
     * @return the byte array for the bits
     */
    protected static byte[] extractBits(long[] alValue, long ofStart, long cBits)
        {
        int    cBytes = BitBasedDelegate.storage(cBits);
        byte[] abBits = new byte[cBytes];
        int    ofDest = 0;

        if (ofStart % 64 == 0)
            {
            int ixSource = (int) (ofStart / 64);
            int cVals    = (int) (cBits / 64);

            for (int i = 0; i < cVals; i++, ofDest += 8)
                {
                Handy.toByteArray(alValue[ixSource + i], abBits, ofDest);
                }
            if (cBits % 64 == 0)
                {
                return abBits;
                }

            // fill the tail
            ofStart = ofStart + cVals*64L;
            cBits   = cBits % 64;
            }

        for (long i = 0; i < cBits; i++)
            {
            BitBasedDelegate.setBit(abBits, ofDest + i, getBit(alValue, ofStart + i));
            }
        return abBits;
        }

    /**
     * Get a bit in the specified array of longs.
     *
     * @param alValue  the long array
     * @param lIndex   the bit index
     *
     * @return true iff the bit is set
     */
    protected static boolean getBit(long[] alValue, long lIndex)
        {
        return (alValue[bitIndex(lIndex)] & bitMask(lIndex)) != 0;
        }

    /**
     * Set or clear a bit in the specified array of longs.
     *
     * @param alValue  the long array
     * @param lIndex   the bit index
     * @param fSet     true if the bit is to be set; false for clear
     */
    protected static void setBit(long[] alValue, long lIndex, boolean fSet)
        {
        if (fSet)
            {
            alValue[bitIndex(lIndex)] |= bitMask(lIndex);
            }
        else
            {
            alValue[bitIndex(lIndex)] &= ~bitMask(lIndex);
            }
        }

    /**
     * Calculate an index of the specified bit in the byte array.
     *
     * @param lBit  the bit index
     *
     * @return the byte index
     */
    protected static int bitIndex(long lBit)
        {
        return (int) (lBit / 64);
        }

    /**
     * Calculate a mask of the specified bit in the byte array at {@ling #index}.
     *
     * @param lBit  the bit index
     *
     * @return the mask
     */
    protected static long bitMask(long lBit)
        {
        return 0x8000_0000_0000_0000L >>> (lBit & 0x3F);
        }


    // ----- handle --------------------------------------------------------------------------------

    public LongArrayHandle makeHandle(long[] alValue, long cSize, Mutability mutability)
        {
        return new LongArrayHandle(getCanonicalClass(), alValue, cSize, mutability);
        }

    /**
     * Array delegate handle based on a java long array.
     */
    public static class LongArrayHandle
            extends DelegateHandle
        {
        protected long[] m_alValue;

        protected LongArrayHandle(TypeComposition clazz, long[] alValue, long cValues,
                                  Mutability mutability)
            {
            super(clazz, mutability);

            m_alValue = alValue;
            m_cSize   = cValues;
            }

        @Override
        public boolean makeImmutable()
            {
            if (isMutable())
                {
                purgeUnusedSpace();
                }
            return super.makeImmutable();
            }

        protected void purgeUnusedSpace()
            {
            long[] ab = m_alValue;
            int    c  = ((LongBasedDelegate) getTemplate()).storage(m_cSize);
            if (ab.length != c)
                {
                long[] abNew = new long[c];
                System.arraycopy(ab, 0, abNew, 0, c);
                m_alValue = abNew;
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
            long[] abThis = m_alValue;
            long   cThis  = m_cSize;
            long[] abThat = ((LongArrayHandle) that).m_alValue;
            long   cThat  = ((LongArrayHandle) that).m_cSize;

            if (cThis != cThat)
                {
                return (int) (cThis - cThat);
                }

            int c = ((LongBasedDelegate) getTemplate()).storage(cThis);
            for (int i = 0; i < c; i++)
                {
                long iDiff = abThis[i] - abThat[i];
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
            return Arrays.hashCode(m_alValue);
            }

        @Override
        public boolean equals(Object obj)
            {
            return obj instanceof LongArrayHandle that
                && Arrays.equals(this.m_alValue, that.m_alValue);
            }
        }

    // ----- constants -----------------------------------------------------------------------------

    protected final int     f_nBitsPerValue;  // for Int16: 16
    protected final int     f_nValuesPerLong; // for Int16: 4
    protected final int     f_nIndexShift;    // for Int16: 2
    protected final int     f_nIndexMask;     // for Int16: 3
    protected final long    f_lValueMask;     // for Int16: 0x0000_0000_0000_FFFF
    protected final long    f_lSignBit;       // for Int16: 0x0000_0000_0000_8000
    protected final boolean f_fSigned;        // for Int16: true
    }