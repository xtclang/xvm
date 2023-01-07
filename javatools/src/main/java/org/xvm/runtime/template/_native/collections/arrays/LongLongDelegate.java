package org.xvm.runtime.template._native.collections.arrays;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.BaseInt128.LongLongHandle;
import org.xvm.runtime.template.numbers.LongLong;
import org.xvm.runtime.template.numbers.xInt;

import org.xvm.runtime.template._native.collections.arrays.LongBasedDelegate.LongArrayHandle;


/**
 * The abstract base for RTDelegate<Int128> and RTDelegate<UInt128> implementations.
 */
public abstract class LongLongDelegate
        extends xRTDelegate
    {
    public static LongLongDelegate INSTANCE;

    public LongLongDelegate(Container container, ClassStructure structure, boolean fSigned)
        {
        super(container, structure, false);

        f_fSigned = fSigned;
        }

    @Override
    public DelegateHandle createDelegate(Container container, TypeConstant typeElement, int cSize,
                                         ObjectHandle[] ahContent, Mutability mutability)
        {
        long[] alValue = new long[2*cSize];

        for (int iSrc = 0, iDst = 0, c = ahContent.length; iSrc < c; iSrc++)
            {
            LongLong ll = ((LongLongHandle) ahContent[iSrc]).getValue();

            alValue[iDst++] = ll.getHighValue();
            alValue[iDst++] = ll.getLowValue();
            }
        return new LongArrayHandle(getCanonicalClass(), alValue, cSize, mutability);
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    public DelegateHandle fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        long[]   alValue = hDelegate.m_alValue;
        LongLong ll      = ((LongLongHandle) hValue).getValue();
        long     lH      = ll.getHighValue();
        long     lL      = ll.getLowValue();

        for (int iSrc = 0, iDst = 0; iSrc < cSize; iSrc++)
            {
            alValue[iDst++] = lH;
            alValue[iDst++] = lL;
            }
        hDelegate.m_cSize = cSize;
        return hDelegate;
        }

    @Override
    public int getPropertyCapacity(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        return frame.assignValue(iReturn,
                xInt.makeHandle((long) hDelegate.m_alValue.length / 2));
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
        int nNew = (int) nCapacity * 2;
        int nOld = abOld.length;
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

        long[] alValue = Arrays.copyOfRange(hDelegate.m_alValue,
                            (int) ofStart*2, (int) (ofStart*2 + cSize*2));
        if (fReverse)
            {
            alValue = reverseLong2(alValue, (int) cSize);
            }
        return new LongArrayHandle(hDelegate.getComposition(), alValue, cSize, mutability);
        }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;
        long[]          alValue   = hDelegate.m_alValue;
        int             nIndex    = (int) lIndex*2;

        return frame.assignValue(iReturn,
                makeElementHandle(new LongLong(alValue[nIndex+1], alValue[nIndex])));
        }

    @Override
    protected int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                       ObjectHandle hValue)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        int    cSize   = (int) hDelegate.m_cSize;
        long[] alValue = hDelegate.m_alValue;
        int    nIndex  = (int) lIndex*2;

        if (lIndex >= cSize)
            {
            if (nIndex >= alValue.length)
                {
                alValue = hDelegate.m_alValue = LongBasedDelegate.grow(alValue, nIndex + 2);
                }

            hDelegate.m_cSize = lIndex + 1;
            }

        LongLong ll = ((LongLongHandle) hValue).getValue();
        alValue[nIndex]   = ll.getHighValue();
        alValue[nIndex+1] = ll.getLowValue();
        return Op.R_NEXT;
        }

    @Override
    protected void insertElementImpl(DelegateHandle hTarget, ObjectHandle hElement, long lIndex)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        int    cSize   = (int) hDelegate.m_cSize;
        long[] alValue = hDelegate.m_alValue;
        int    nIndex  = (int) lIndex*2;

        if (2*cSize == alValue.length)
            {
            alValue = hDelegate.m_alValue =
                    LongBasedDelegate.grow(hDelegate.m_alValue, 2*cSize + 2);
            }
        hDelegate.m_cSize++;

        LongLong ll = ((LongLongHandle) hElement).getValue();
        if (lIndex < cSize)
            {
            // insert
            System.arraycopy(alValue, nIndex, alValue, nIndex + 2, 2*cSize - nIndex);
            }
        alValue[nIndex]   = ll.getHighValue();
        alValue[nIndex+1] = ll.getLowValue();
        }

    @Override
    protected void deleteElementImpl(DelegateHandle hTarget, long lIndex)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        int    cSize   = (int) hDelegate.m_cSize;
        long[] alValue = hDelegate.m_alValue;
        int    nIndex  = (int) lIndex*2;

        if (lIndex < cSize - 1)
            {
            System.arraycopy(alValue, nIndex + 2, alValue, nIndex, 2*cSize - nIndex - 2);
            }

        alValue[nIndex]   = 0;
        alValue[nIndex+1] = 0;
        hDelegate.m_cSize--;
        }

    @Override
    protected void deleteRangeImpl(DelegateHandle hTarget, long lIndex, long cDelete)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        int    cSize   = (int) hDelegate.m_cSize;
        long[] alValue = hDelegate.m_alValue;

        if (lIndex < cSize - cDelete)
            {
            int nIndex  = (int) lIndex*2;
            int nDelete = (int) cDelete*2;
            System.arraycopy(alValue, nIndex + nDelete, alValue, nIndex, 2*cSize - nIndex - nDelete);
            }
        hDelegate.m_cSize -= cDelete;
        }


    protected abstract ObjectHandle makeElementHandle(LongLong ll);


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

        int cStore = (int) h1.m_cSize*2;
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

    public static long[] reverseLong2(long[] alValue, int cSize)
        {
        long[] alValueR = new long[2*cSize];
        for (int i = 0; i < cSize; i++)
            {
            alValueR[i]   = alValue[2*cSize - 2 - i];
            alValueR[i+1] = alValue[2*cSize - 1 - i];
            }
        return alValueR;
        }



    // ----- constants -----------------------------------------------------------------------------

    protected final boolean f_fSigned;
    }