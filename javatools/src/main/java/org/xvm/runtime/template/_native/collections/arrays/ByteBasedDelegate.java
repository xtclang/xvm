package org.xvm.runtime.template._native.collections.arrays;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;


/**
 * A base class for native ArrayDelegate implementations based on byte arrays.
 */
public abstract class ByteBasedDelegate
        extends xRTDelegate
        implements ByteView
    {
    public static ByteBasedDelegate INSTANCE;

    public ByteBasedDelegate(TemplateRegistry templates, ClassStructure structure)
        {
        super(templates, structure, false);
        }

    @Override
    public DelegateHandle createDelegate(TypeConstant typeElement, int cSize,
                                         ObjectHandle[] ahContent, Mutability mutability)
        {
        byte[] ab = new byte[cSize];

        for (int i = 0, c = ahContent.length; i < c; i++)
            {
            ab[i] = (byte) ((JavaLong) ahContent[i]).getValue();
            }
        return makeHandle(ab, cSize, mutability);
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    public DelegateHandle fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue)
        {
        ByteArrayHandle hDelegate = (ByteArrayHandle) hTarget;

        Arrays.fill(hDelegate.m_abValue, 0, cSize, (byte) ((JavaLong) hValue).getValue());
        hDelegate.m_cSize = cSize;
        return hDelegate;
        }

    @Override
    public int getPropertyCapacity(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        ByteArrayHandle hDelegate = (ByteArrayHandle) hTarget;

        return frame.assignValue(iReturn, xInt64.makeHandle(hDelegate.m_abValue.length));
        }

    @Override
    public int setPropertyCapacity(Frame frame, ObjectHandle hTarget, long nCapacity)
        {
        ByteArrayHandle hDelegate = (ByteArrayHandle) hTarget;

        byte[] abOld = hDelegate.m_abValue;
        int    nSize = (int) hDelegate.m_cSize;

        if (nCapacity < nSize)
            {
            return frame.raiseException(
                xException.illegalArgument(frame, "Capacity cannot be less then size"));
            }

        // for now, no trimming
        int nCapacityOld = abOld.length;
        if (nCapacity > nCapacityOld)
            {
            byte[] abNew = new byte[(int) nCapacity];
            System.arraycopy(abOld, 0, abNew, 0, abOld.length);
            hDelegate.m_abValue = abNew;
            }
        return Op.R_NEXT;
        }

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse)
        {
        ByteArrayHandle hDelegate = (ByteArrayHandle) hTarget;

        byte[] abValue = Arrays.copyOfRange(hDelegate.m_abValue, (int) ofStart, (int) (ofStart + cSize));
        if (fReverse)
            {
            abValue = reverseBytes(abValue, (int) cSize);
            }

        return new ByteArrayHandle(hDelegate.getComposition(), abValue, cSize, mutability);
        }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        ByteArrayHandle hDelegate = (ByteArrayHandle) hTarget;

        byte b = hDelegate.m_abValue[(int) lIndex];
        return frame.assignValue(iReturn, makeElementHandle(b));
        }

    @Override
    protected int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                       ObjectHandle hValue)
        {
        ByteArrayHandle hDelegate = (ByteArrayHandle) hTarget;

        int    cSize   = (int) hDelegate.m_cSize;
        byte[] abValue = hDelegate.m_abValue;
        int    nIndex  = (int) lIndex;

        if (nIndex >= cSize)
            {
            if (nIndex >= abValue.length)
                {
                abValue = hDelegate.m_abValue = grow(abValue, nIndex + 1);
                }

            hDelegate.m_cSize = nIndex + 1;
            }

        abValue[nIndex] = (byte) ((JavaLong) hValue).getValue();
        return Op.R_NEXT;
        }

    @Override
    protected void insertElementImpl(DelegateHandle hTarget, ObjectHandle hElement, long lIndex)
        {
        ByteArrayHandle hDelegate = (ByteArrayHandle) hTarget;
        int             cSize     = (int) hDelegate.m_cSize;
        byte[]          abValue   = hDelegate.m_abValue;
        byte            bValue    = (byte) ((JavaLong) hElement).getValue();

        if (cSize == abValue.length)
            {
            abValue = hDelegate.m_abValue = grow(hDelegate.m_abValue, cSize + 1);
            }
        hDelegate.m_cSize++;

        int nIndex = (int) lIndex;
        if (nIndex < cSize)
            {
            System.arraycopy(abValue, nIndex, abValue, nIndex + 1, cSize - nIndex);
            }
        abValue[nIndex] = bValue;
        }

    @Override
    protected void deleteElementImpl(DelegateHandle hTarget, long lIndex)
        {
        ByteArrayHandle hDelegate = (ByteArrayHandle) hTarget;
        int             cSize     = (int) hDelegate.m_cSize;
        byte[]          abValue   = hDelegate.m_abValue;

        if (lIndex < cSize - 1)
            {
            int nIndex = (int) lIndex;
            System.arraycopy(abValue, nIndex + 1, abValue, nIndex, cSize - nIndex - 1);
            }
        abValue[(int) --hDelegate.m_cSize] = 0;
        }

    @Override
    protected void deleteRangeImpl(DelegateHandle hTarget, long lIndex, long cDelete)
        {
        ByteArrayHandle hDelegate = (ByteArrayHandle) hTarget;
        int             cSize     = (int) hDelegate.m_cSize;
        byte[]          abValue   = hDelegate.m_abValue;
        int             nIndex    = (int) lIndex;
        int             nDelete   = (int) cDelete;

        if (nIndex < cSize - nDelete)
            {
            System.arraycopy(abValue, nIndex + 1, abValue, nIndex, cSize - nIndex - nDelete);
            }
        Arrays.fill(abValue, cSize - nDelete, nDelete, (byte) 0);
        hDelegate.m_cSize -= cDelete;
        }


    // ----- ByteView implementation ---------------------------------------------------------------

    @Override
    public byte[] getBytes(DelegateHandle hDelegate, long ofStart, long cBytes, boolean fReverse)
        {
        ByteArrayHandle hBytes = (ByteArrayHandle) hDelegate;

        byte[] ab = hBytes.m_abValue;

        if (hBytes.getMutability() == Mutability.Constant &&
                ofStart == 0 && cBytes == ab.length && !fReverse)
            {
            return ab;
            }

        ab = Arrays.copyOfRange(ab, (int) ofStart, (int) (ofStart + cBytes));
        return fReverse ? reverseBytes(ab, (int) cBytes) : ab;
        }

    @Override
    public byte extractByte(DelegateHandle hDelegate, long of)
        {
        ByteArrayHandle hBytes = (ByteArrayHandle) hDelegate;

        return hBytes.m_abValue[(int) of];
        }

    @Override
    public void assignByte(DelegateHandle hDelegate, long of, byte bValue)
        {
        ByteArrayHandle hBytes = (ByteArrayHandle) hDelegate;

        hBytes.m_abValue[(int) of] = bValue;
        }


    // ----- ClassTemplate API ---------------------------------------------------------------------

    public int invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        ByteArrayHandle hDelegate = (ByteArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hDelegate.m_cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, hDelegate.m_cSize));
            }

        // TODO GG: range check is missing!
        return frame.assignValue(iReturn,
                makeElementHandle(++hDelegate.m_abValue[(int) lIndex]));
        }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        ByteArrayHandle h1 = (ByteArrayHandle) hValue1;
        ByteArrayHandle h2 = (ByteArrayHandle) hValue2;

        return frame.assignValue(iReturn,
                xBoolean.makeHandle(Arrays.equals(h1.m_abValue, h2.m_abValue)));
        }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        ByteArrayHandle h1 = (ByteArrayHandle) hValue1;
        ByteArrayHandle h2 = (ByteArrayHandle) hValue2;

        if (h1 == h2)
            {
            return true;
            }

        return h1.getMutability() == h2.getMutability()
            && h1.m_cSize == h2.m_cSize
            && Arrays.equals(h1.m_abValue, h2.m_abValue);
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Reverse the array of bites represented by the specified array.
     *
     * @param abValue the byte array
     * @param cSize   the actual number of bytes held by the array
     */
    public static byte[] reverseBytes(byte[] abValue, int cSize)
        {
        byte[] abValueR = new byte[cSize];
        for (int i = 0; i < cSize; i++)
            {
            abValueR[i] = abValue[cSize - 1 - i];
            }
        return abValueR;
        }

    protected static byte[] grow(byte[] abValue, int cSize)
        {
        int cCapacity = calculateCapacity(abValue.length, cSize);

        byte[] abNew = new byte[cCapacity];
        System.arraycopy(abValue, 0, abNew, 0, abValue.length);
        return abNew;
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    /**
     * Make an element handle for the specified value.
     */
    abstract protected ObjectHandle makeElementHandle(long lValue);

    /**
     * Make a canonical array handle.
     */
    public ByteArrayHandle makeHandle(byte[] ab, long cSize, Mutability mutability)
        {
        return new ByteArrayHandle(getCanonicalClass(), ab, cSize, mutability);
        }

    public static class ByteArrayHandle
            extends DelegateHandle
        {
        protected byte[] m_abValue;

        protected ByteArrayHandle(TypeComposition clazz, byte[] abValue,
                                  long cSize, Mutability mutability)
            {
            super(clazz, mutability);

            m_abValue = abValue;
            m_cSize   = cSize;
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
            byte[] ab = m_abValue;
            int    c  = (int) m_cSize;
            if (ab.length != c)
                {
                byte[] abNew = new byte[c];
                System.arraycopy(ab, 0, abNew, 0, c);
                m_abValue = abNew;
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
            int    cThis  = (int) m_cSize;
            byte[] abThat = ((ByteArrayHandle) that).m_abValue;
            int    cThat  = (int) ((ByteArrayHandle) that).m_cSize;

            if (cThis != cThat)
                {
                return cThis - cThat;
                }

            for (int i = 0; i < cThis; i++)
                {
                int iDiff = abThis[i] - abThat[i];
                if (iDiff != 0)
                    {
                    return iDiff;
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
            return obj instanceof ByteArrayHandle
                && Arrays.equals(m_abValue, ((ByteArrayHandle) obj).m_abValue);
            }
        }
    }
