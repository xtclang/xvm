package org.xvm.runtime.template._native.collections.arrays;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;


/**
 * The abstract base for RTDelegate<Int64> and RTDelegate<UInt64> implementations.
 */
public abstract class LongDelegate
        extends LongBasedDelegate<LongBasedDelegate.LongArrayHandle> {
    public LongDelegate(Container container, ClassStructure structure, boolean fSigned) {
        super(container, structure, 64, fSigned);

        f_fSigned = fSigned;
    }

    @Override
    public int invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn) {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        if (checkWriteInPlace(frame, hDelegate, lIndex, hDelegate.m_cSize) == Op.R_EXCEPTION) {
            return Op.R_EXCEPTION;
        }

        return frame.assignValue(iReturn,
                makeElementHandle(++hDelegate.m_alValue[(int) lIndex]));
    }

    @Override
    public int invokePostInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn) {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        if (checkWriteInPlace(frame, hDelegate, lIndex, hDelegate.m_cSize) == Op.R_EXCEPTION) {
            return Op.R_EXCEPTION;
        }

        return frame.assignValue(iReturn,
                makeElementHandle(hDelegate.m_alValue[(int) lIndex]++));
    }

    @Override
    public int invokePreDec(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn) {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        if (checkWriteInPlace(frame, hDelegate, lIndex, hDelegate.m_cSize) == Op.R_EXCEPTION) {
            return Op.R_EXCEPTION;
        }

        return frame.assignValue(iReturn,
                makeElementHandle(--hDelegate.m_alValue[(int) lIndex]));
    }

    @Override
    public int invokePostDec(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn) {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        if (checkWriteInPlace(frame, hDelegate, lIndex, hDelegate.m_cSize) == Op.R_EXCEPTION) {
            return Op.R_EXCEPTION;
        }

        return frame.assignValue(iReturn,
                makeElementHandle(hDelegate.m_alValue[(int) lIndex]--));
    }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    public DelegateHandle fill(LongArrayHandle hDelegate, int cSize, ObjectHandle hValue) {

        Arrays.fill(hDelegate.m_alValue, 0, cSize, ((JavaLong) hValue).getValue());
        hDelegate.m_cSize = cSize;
        return hDelegate;
    }

    @Override
    protected int extractArrayValueImpl(Frame frame, LongArrayHandle hDelegate, long lIndex, int iReturn) {

        return frame.assignValue(iReturn,
                makeElementHandle(hDelegate.m_alValue[(int) lIndex]));
    }

    @Override
    protected int assignArrayValueImpl(Frame frame, LongArrayHandle hDelegate, long lIndex,
                                       ObjectHandle hValue) {

        int    cSize   = (int) hDelegate.m_cSize;
        long[] alValue = hDelegate.m_alValue;
        int    nIndex  = (int) lIndex;

        if (nIndex >= cSize) {
            if (nIndex >= alValue.length) {
                alValue = hDelegate.m_alValue = grow(alValue, nIndex + 1);
            }

            hDelegate.m_cSize = nIndex + 1;
        }

        alValue[nIndex] = ((JavaLong) hValue).getValue();
        return Op.R_NEXT;
    }

    @Override
    protected void insertElementImpl(LongArrayHandle hDelegate, ObjectHandle hElement, long lIndex) {
        int            cSize     = (int) hDelegate.m_cSize;
        long[]         alValue   = hDelegate.m_alValue;

        if (cSize == alValue.length) {
            alValue = hDelegate.m_alValue = grow(hDelegate.m_alValue, cSize + 1);
        }
        hDelegate.m_cSize++;

        if (lIndex == cSize) {
            alValue[cSize] = ((JavaLong) hElement).getValue();
        } else {
            // insert
            int nIndex = (int) lIndex;
            System.arraycopy(alValue, nIndex, alValue, nIndex + 1, cSize - nIndex);
            alValue[nIndex] = ((JavaLong) hElement).getValue();
        }
    }

    @Override
    protected void deleteElementImpl(LongArrayHandle hDelegate, long lIndex) {
        int             cSize     = (int) hDelegate.m_cSize;
        long[]          alValue   = hDelegate.m_alValue;

        if (lIndex < cSize - 1) {
            int nIndex = (int) lIndex;
            System.arraycopy(alValue, nIndex + 1, alValue, nIndex, cSize - nIndex - 1);
        }

        alValue[(int) --hDelegate.m_cSize] = 0;
    }

    @Override
    protected void deleteRangeImpl(LongArrayHandle hDelegate, long lIndex, long cDelete) {
        int             cSize     = (int) hDelegate.m_cSize;
        long[]          alValue   = hDelegate.m_alValue;
        int             nIndex    = (int) lIndex;
        int             nDelete   = (int) cDelete;

        if (nIndex < cSize - nDelete) {
            System.arraycopy(alValue, nIndex + nDelete, alValue, nIndex, cSize - nIndex - nDelete);
        }
        hDelegate.m_cSize -= cDelete;
    }


    // ----- helper methods ------------------------------------------------------------------------

    @Override
    public long[] reverse(long[] alValue, int cSize) {
        return reverseLongs(alValue, cSize);
    }

    public static long[] reverseLongs(long[] alValue, int cSize) {
        long[] alValueR = new long[cSize];
        for (int i = 0; i < cSize; i++) {
            alValueR[i] = alValue[cSize - 1 - i];
        }
        return alValueR;
    }


    // ----- constants -----------------------------------------------------------------------------

    protected final boolean f_fSigned;
}
