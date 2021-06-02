package org.xvm.runtime.template._native.collections.arrays;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;


/**
 * Native RTDelegate<Int> implementation.
 */
public class xRTInt64Delegate
        extends LongBasedDelegate
    {
    public static xRTInt64Delegate INSTANCE;

    public xRTInt64Delegate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, 64, true);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(
                getInceptionClassConstant().getType(),
                pool.typeInt());
        }

    @Override
    public int invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hDelegate.m_cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, hDelegate.m_cSize));
            }

        return frame.assignValue(iReturn,
                xInt64.makeHandle(++hDelegate.m_alValue[(int) lIndex]));
        }

    @Override
    public DelegateHandle createDelegate(TypeConstant typeElement, int cCapacity,
                                         ObjectHandle[] ahContent, Mutability mutability)
        {
        int    cSize = ahContent.length;
        long[] al    = new long[cCapacity];
        for (int i = 0; i < cSize; i++)
            {
            al[i] = ((JavaLong) ahContent[i]).getValue();
            }
        return new LongArrayHandle(getCanonicalClass(), al, cSize, mutability);
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    public void fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        Arrays.fill(hDelegate.m_alValue, 0, cSize, ((JavaLong) hValue).getValue());
        hDelegate.m_cSize = cSize;
        }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        return frame.assignValue(iReturn,
                xInt64.makeHandle(hDelegate.m_alValue[(int) lIndex]));
        }

    @Override
    protected int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                       ObjectHandle hValue)
        {
        LongArrayHandle hDelegate = (LongArrayHandle) hTarget;

        int    cSize   = (int) hDelegate.m_cSize;
        long[] alValue = hDelegate.m_alValue;

        if (lIndex == cSize)
            {
            if (cSize == alValue.length)
                {
                alValue = hDelegate.m_alValue = grow(alValue, cSize + 1);
                }

            hDelegate.m_cSize++;
            }

        try
            {
            alValue[(int) lIndex] = ((JavaLong) hValue).getValue();
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
        int            cSize     = (int) hDelegate.m_cSize;
        long[]         alValue   = hDelegate.m_alValue;

        if (lIndex < cSize - 1)
            {
            int nIndex = (int) lIndex;
            System.arraycopy(alValue, nIndex + 1, alValue, nIndex, cSize - nIndex -1);
            }

        --hDelegate.m_cSize;
        }

    @Override
    protected ObjectHandle makeElementHandle(long lValue)
        {
        return xInt64.makeHandle(lValue);
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
    }