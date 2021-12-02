package org.xvm.runtime.template._native.collections.arrays;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.xException;


/**
 * The abstract base for RTDelegate<Int64> and RTDelegate<UInt64> implementations.
 */
public abstract class LongDelegate
        extends LongBasedDelegate
    {
    public static LongDelegate INSTANCE;

    public LongDelegate(TemplateRegistry templates, ClassStructure structure, boolean fSigned)
        {
        super(templates, structure, 64, true);

        f_fSigned = fSigned;
        }

    @Override
    public int invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hDelegate.m_cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, hDelegate.m_cSize));
            }

        // TODO GG: range check is missing
        return frame.assignValue(iReturn,
                makeElementHandle(++hDelegate.m_alValue[(int) lIndex]));
        }

    @Override
    public DelegateHandle createDelegate(TypeConstant typeElement, int cSize,
                                         ObjectHandle[] ahContent, Mutability mutability)
        {
        long[] al = new long[cSize];

        for (int i = 0, c = ahContent.length; i < c; i++)
            {
            al[i] = ((JavaLong) ahContent[i]).getValue();
            }
        return new LongArrayHandle(getCanonicalClass(), al, cSize, mutability);
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    public DelegateHandle fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        Arrays.fill(hDelegate.m_alValue, 0, cSize, ((JavaLong) hValue).getValue());
        hDelegate.m_cSize = cSize;
        return hDelegate;
        }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        return frame.assignValue(iReturn,
                makeElementHandle(hDelegate.m_alValue[(int) lIndex]));
        }

    @Override
    protected int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                       ObjectHandle hValue)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        int    cSize   = (int) hDelegate.m_cSize;
        long[] alValue = hDelegate.m_alValue;
        int    nIndex  = (int) lIndex;

        if (nIndex >= cSize)
            {
            if (nIndex >= alValue.length)
                {
                alValue = hDelegate.m_alValue = grow(alValue, nIndex + 1);
                }

            hDelegate.m_cSize = nIndex + 1;
            }

        try
            {
            alValue[nIndex] = ((JavaLong) hValue).getValue();
            return Op.R_NEXT;
            }
        catch (ClassCastException e)
            {
            return frame.raiseException(
                xException.illegalCast(frame, hValue.getType().getValueString()));
            }
        }

    @Override
    protected void insertElementImpl(DelegateHandle hTarget, ObjectHandle hElement, long lIndex)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;
        int            cSize     = (int) hDelegate.m_cSize;
        long[]         alValue   = hDelegate.m_alValue;

        if (cSize == alValue.length)
            {
            alValue = hDelegate.m_alValue = grow(hDelegate.m_alValue, cSize + 1);
            }
        hDelegate.m_cSize++;

        if (lIndex == cSize)
            {
            alValue[cSize] = ((JavaLong) hElement).getValue();
            }
        else
            {
            // insert
            int nIndex = (int) lIndex;
            System.arraycopy(alValue, nIndex, alValue, nIndex + 1, cSize - nIndex);
            alValue[nIndex] = ((JavaLong) hElement).getValue();
            }
        }

    @Override
    protected void deleteElementImpl(DelegateHandle hTarget, long lIndex)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;
        int             cSize     = (int) hDelegate.m_cSize;
        long[]          alValue   = hDelegate.m_alValue;

        if (lIndex < cSize - 1)
            {
            int nIndex = (int) lIndex;
            System.arraycopy(alValue, nIndex + 1, alValue, nIndex, cSize - nIndex - 1);
            }

        alValue[(int) --hDelegate.m_cSize] = 0;
        }

    @Override
    protected void deleteRangeImpl(DelegateHandle hTarget, long lIndex, long cDelete)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;
        int             cSize     = (int) hDelegate.m_cSize;
        long[]          alValue   = hDelegate.m_alValue;
        int             nIndex    = (int) lIndex;
        int             nDelete   = (int) cDelete;

        if (nIndex < cSize - nDelete)
            {
            System.arraycopy(alValue, nIndex + nDelete, alValue, nIndex, cSize - nIndex - nDelete);
            }
        hDelegate.m_cSize -= cDelete;
        }


    // ----- helper methods ------------------------------------------------------------------------

    @Override
    public long[] reverse(long[] alValue, int cSize)
        {
        long[] alValueR = new long[cSize];
        for (int i = 0; i < cSize; i++)
            {
            alValueR[i] = alValue[cSize - 1 - i];
            }
        return alValueR;
        }


    // ----- constants -----------------------------------------------------------------------------

    protected final boolean f_fSigned;
    }