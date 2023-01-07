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

import org.xvm.runtime.template.numbers.BaseInt128.LongLongHandle;
import org.xvm.runtime.template.numbers.LongLong;
import org.xvm.runtime.template.numbers.xInt;

import org.xvm.runtime.template._native.collections.arrays.LongBasedDelegate.LongArrayHandle;


/**
 * The abstract base for RTDelegate<Int> and RTDelegate<UInt> implementations.
 */
public abstract class IntBasedDelegate
        extends xRTDelegate
    {
    public static IntBasedDelegate INSTANCE;

    public IntBasedDelegate(Container container, ClassStructure structure, boolean fSigned)
        {
        super(container, structure, false);

        f_fSigned = fSigned;
        }

    @Override
    public DelegateHandle createDelegate(Container container, TypeConstant typeElement, int cSize,
                                         ObjectHandle[] ahContent, Mutability mutability)
        {
        // assume all small values
        int    cV      = 1;
        long[] alValue = new long[cSize];

        for (int i = 0, c = ahContent.length; i < c; i++)
            {
            if (ahContent[i] instanceof JavaLong hV)
                {
                alValue[i] = hV.getValue();
                }
            else
                {
                cV = 2;
                break;
                }
            }

        if (cV == 2)
            {
            alValue = new long[cSize*2];
            for (int iSrc = 0, iDst = 0, c = ahContent.length; iSrc < c; iSrc++)
                {
                LongLong ll;

                if (ahContent[iSrc] instanceof JavaLong hV)
                    {
                    ll = new LongLong(hV.getValue(), f_fSigned);
                    }
                else
                    {
                    ll = ((LongLongHandle) ahContent[iSrc]).getValue();
                    }

                alValue[iDst++] = ll.getHighValue();
                alValue[iDst++] = ll.getLowValue();
                }
            }

        return new IntArrayHandle(getCanonicalClass(), alValue, cSize, cV, mutability);
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    public DelegateHandle fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue)
        {
        IntArrayHandle hDelegate = (IntArrayHandle) hTarget;

        long[] alValue = hDelegate.m_alValue;
        if (hValue instanceof JavaLong hL)
            {
            long lL = hL.getValue();
            if (hDelegate.isSmall())
                {
                for (int i = 0; i < cSize; i++)
                    {
                    alValue[i] = lL;
                    }
                }
            else
                {
                long lH = f_fSigned & lL < 0 ? -1 : 0;
                for (int iSrc = 0, iDst = 0; iSrc < cSize; iSrc++)
                    {
                    alValue[iDst++] = lH;
                    alValue[iDst++] = lL;
                    }
                }
            }
        else
            {
            LongLong ll = ((LongLongHandle) hValue).getValue();
            long     lH = ll.getHighValue();
            long     lL = ll.getLowValue();

            if (hDelegate.isSmall())
                {
                alValue = hDelegate.scaleUp(f_fSigned);
                }
            for (int iSrc = 0, iDst = 0; iSrc < cSize; iSrc++)
                {
                alValue[iDst++] = lH;
                alValue[iDst++] = lL;
                }
            }

        hDelegate.m_cSize = cSize;
        return hDelegate;
        }

    @Override
    public int getPropertyCapacity(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        IntArrayHandle hDelegate = (IntArrayHandle) hTarget;

        return frame.assignValue(iReturn,
                xInt.makeHandle((long) hDelegate.m_alValue.length / hDelegate.getValueSize()));
        }

    @Override
    public int setPropertyCapacity(Frame frame, ObjectHandle hTarget, long nCapacity)
        {
        IntArrayHandle hDelegate = (IntArrayHandle) hTarget;

        long[] abOld = hDelegate.m_alValue;
        long   cSize = hDelegate.m_cSize;

        if (nCapacity < cSize)
            {
            return frame.raiseException(
                xException.illegalArgument(frame, "Capacity cannot be less then size"));
            }

        // for now, no trimming
        int nNew = (int) nCapacity * hDelegate.getValueSize();
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
        IntArrayHandle hDelegate = (IntArrayHandle) hTarget;

        int    cV      = hDelegate.getValueSize();
        long[] alValue = Arrays.copyOfRange(hDelegate.m_alValue,
                            (int) ofStart*cV, (int) (ofStart*cV + cSize*cV));
        if (fReverse)
            {
            alValue = cV == 1
                ? LongDelegate.reverseLongs(alValue, (int) cSize)
                : LongLongDelegate.reverseLong2(alValue, (int) cSize);
            }
        return new IntArrayHandle(hDelegate.getComposition(), alValue, cSize, cV, mutability);
        }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        IntArrayHandle hDelegate = (IntArrayHandle) hTarget;
        long[]          alValue   = hDelegate.m_alValue;

        if (hDelegate.isSmall())
            {
            int nIndex = (int) lIndex;
            return frame.assignValue(iReturn, makeElementHandle(alValue[nIndex]));
            }
        else
            {
            int nIndex = (int) lIndex*2;
            return frame.assignValue(iReturn,
                makeElementHandle(new LongLong(alValue[nIndex+1], alValue[nIndex])));
            }
        }

    @Override
    protected int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                       ObjectHandle hValue)
        {
        IntArrayHandle hDelegate = (IntArrayHandle) hTarget;

        int    cSize   = (int) hDelegate.m_cSize;
        long[] alValue = hDelegate.m_alValue;

        if (hDelegate.isSmall())
            {
            if (hValue instanceof JavaLong hL)
                {
                int nIndex = (int) lIndex;
                if (nIndex >= cSize)
                    {
                    if (nIndex >= alValue.length)
                        {
                        alValue = hDelegate.m_alValue =
                                LongBasedDelegate.grow(alValue, nIndex + 1);
                        }
                    hDelegate.m_cSize = lIndex + 1;
                    }

                alValue[nIndex] = hL.getValue();
                return Op.R_NEXT;
                }

            alValue = hDelegate.scaleUp(f_fSigned);
            }

        int nIndex = (int) lIndex*2;

        if (lIndex >= cSize)
            {
            if (nIndex >= alValue.length)
                {
                alValue = hDelegate.m_alValue =
                        LongBasedDelegate.grow(alValue, nIndex + 2);
                }

            hDelegate.m_cSize = lIndex + 1;
            }

        long lL, lH;
        if (hValue instanceof JavaLong hL)
            {
            lL = hL.getValue();
            lH = f_fSigned & lL < 0 ? -1 : 0;
            }
        else
            {
            LongLong ll = ((LongLongHandle) hValue).getValue();
            lL = ll.getLowValue();
            lH = ll.getHighValue();
            }

        alValue[nIndex]   = lH;
        alValue[nIndex+1] = lL;
        return Op.R_NEXT;
        }

    @Override
    protected void insertElementImpl(DelegateHandle hTarget, ObjectHandle hElement, long lIndex)
        {
        IntArrayHandle hDelegate = (IntArrayHandle) hTarget;

        int    cSize   = (int) hDelegate.m_cSize;
        long[] alValue = hDelegate.m_alValue;

        if (hDelegate.isSmall())
            {
            if (hElement instanceof JavaLong hL)
                {
                int nIndex = (int) lIndex;

                if (cSize == alValue.length)
                    {
                    alValue = hDelegate.m_alValue =
                        LongBasedDelegate.grow(hDelegate.m_alValue, cSize + 1);
                    }
                hDelegate.m_cSize++;

                if (nIndex < cSize)
                    {
                    // insert
                    System.arraycopy(alValue, nIndex, alValue, nIndex + 1, cSize - nIndex);
                    }
                alValue[nIndex] = hL.getValue();
                return;
                }
            alValue = hDelegate.scaleUp(f_fSigned);
            }

        int nIndex = (int) lIndex*2;

        if (2*cSize == alValue.length)
            {
            alValue = hDelegate.m_alValue =
                LongBasedDelegate.grow(hDelegate.m_alValue, 2*cSize + 2);
            }
        hDelegate.m_cSize++;

        if (lIndex < cSize)
            {
            // insert
            System.arraycopy(alValue, nIndex, alValue, nIndex + 2, 2*cSize - nIndex);
            }

        long lL, lH;
        if (hElement instanceof JavaLong hL)
            {
            lL = hL.getValue();
            lH = f_fSigned & lL < 0 ? -1 : 0;
            }
        else
            {
            LongLong ll = ((LongLongHandle) hElement).getValue();
            lL = ll.getLowValue();
            lH = ll.getHighValue();
            }
        alValue[nIndex]   = lH;
        alValue[nIndex+1] = lL;
        }

    @Override
    protected void deleteElementImpl(DelegateHandle hTarget, long lIndex)
        {
        IntArrayHandle hDelegate = (IntArrayHandle) hTarget;

        int    cSize   = (int) hDelegate.m_cSize;
        long[] alValue = hDelegate.m_alValue;

        if (hDelegate.isSmall())
            {
            if (lIndex < cSize - 1)
                {
                int nIndex = (int) lIndex;
                System.arraycopy(alValue, nIndex + 1, alValue, nIndex, cSize - nIndex - 1);
                }
            alValue[(int) --hDelegate.m_cSize] = 0;
            return;
            }

        int nIndex = (int) lIndex*2;
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
        IntArrayHandle hDelegate = (IntArrayHandle) hTarget;

        int    cSize   = (int) hDelegate.m_cSize;
        long[] alValue = hDelegate.m_alValue;

        if (hDelegate.isSmall())
            {
            if (lIndex < cSize - cDelete)
                {
                int nIndex  = (int) lIndex;
                int nDelete = (int) cDelete;
                System.arraycopy(alValue, nIndex + nDelete, alValue, nIndex, cSize - nIndex - nDelete);
                }
            hDelegate.m_cSize -= cDelete;
            return;
            }

        if (lIndex < cSize - cDelete)
            {
            int nIndex  = (int) lIndex*2;
            int nDelete = (int) cDelete*2;
            System.arraycopy(alValue, nIndex + nDelete, alValue, nIndex, 2*cSize - nIndex - nDelete);
            }
        hDelegate.m_cSize -= cDelete;
        }

    protected abstract ObjectHandle makeElementHandle(long l);
    protected abstract ObjectHandle makeElementHandle(LongLong ll);


    // ----- ClassTemplate API ---------------------------------------------------------------------

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        IntArrayHandle h1 = (IntArrayHandle) hValue1;
        IntArrayHandle h2 = (IntArrayHandle) hValue2;

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
        IntArrayHandle h1 = (IntArrayHandle) hValue1;
        IntArrayHandle h2 = (IntArrayHandle) hValue2;

        if (h1 == h2)
            {
            return true;
            }

        return h1.getMutability() == h2.getMutability()
            && h1.m_cSize == h2.m_cSize
            && Arrays.equals(h1.m_alValue, h2.m_alValue);
        }


    // ----- handle --------------------------------------------------------------------------------

    /**
     * Array delegate handle based on a java long array.
     */
    public static class IntArrayHandle
            extends LongArrayHandle
        {
        protected IntArrayHandle(TypeComposition clazz, long[] alValue, long cValues,
                                 int cValSize, Mutability mutability)
            {
            super(clazz, alValue, cValues, mutability);

            m_cValueSize = cValSize;
            }

        /**
         * @return true iff the handle holds one long per value
         */
        public boolean isSmall()
            {
            return m_cValueSize == 1;
            }

        /**
         * @return true iff the handle holds one long per value
         */
        public int getValueSize()
            {
            return m_cValueSize;
            }

        /**
         * Scale up from a "small" model to the "large" one.
         */
        protected long[] scaleUp(boolean fSigned)
            {
            assert m_cValueSize == 1;

            long[] alOld = m_alValue;
            int    cOld  = alOld.length;
            long[] alNew = new long[cOld * 2];
            for (int iSrc = 0, iDst = 0; iSrc < cOld; iSrc++)
                {
                long l = alOld[iSrc];

                alNew[iDst++] = fSigned & l < 0 ? -1 : 0;
                alNew[iDst++] = l;
                }
            m_alValue       = alNew;
            m_cValueSize = 2;
            return alNew;
            }

        @Override
        protected void purgeUnusedSpace()
            {
            long[] ab = m_alValue;
            int    c  = (int) m_cSize*m_cValueSize;
            if (ab.length != c)
                {
                long[] abNew = new long[c];
                System.arraycopy(ab, 0, abNew, 0, c);
                m_alValue = abNew;
                }
            }

        private int m_cValueSize;
        }


    // ----- constants -----------------------------------------------------------------------------

    protected final boolean f_fSigned;
    }