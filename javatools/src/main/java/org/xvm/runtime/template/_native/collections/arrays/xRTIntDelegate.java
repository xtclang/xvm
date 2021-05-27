package org.xvm.runtime.template._native.collections.arrays;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;


/**
 * Native RTDelegate<Int> implementation.
 */
public class xRTIntDelegate
        extends xRTDelegate
    {
    public static xRTIntDelegate INSTANCE;

    public xRTIntDelegate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

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
    public DelegateHandle createDelegate(TypeConstant typeElement, int cCapacity,
                                         ObjectHandle[] ahContent, Mutability mutability)
        {
        int    cSize = ahContent.length;
        long[] al    = new long[cCapacity];
        for (int i = 0; i < cSize; i++)
            {
            al[i] = ((JavaLong) ahContent[i]).getValue();
            }
        return new IntArrayHandle(getCanonicalClass(), al, cSize, mutability);
        }

    @Override
    public void fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue)
        {
        IntArrayHandle hDelegate = (IntArrayHandle) hTarget;

        Arrays.fill(hDelegate.m_alValue, 0, cSize, ((JavaLong) hValue).getValue());
        hDelegate.m_cSize = cSize;
        }

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            int ofStart, int cSize, boolean fReverse)
        {
        IntArrayHandle hDelegate = (IntArrayHandle) hTarget;

        long[] alValue = Arrays.copyOfRange(hDelegate.m_alValue, ofStart, ofStart + cSize);
        if (fReverse)
            {
            alValue = reverse(alValue, cSize);
            }

        return new IntArrayHandle(hDelegate.getComposition(), alValue, cSize, mutability);
        }


    // ----- delegate API --------------------------------------------------------------------------

    @Override
    public int getPropertyCapacity(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        IntArrayHandle hDelegate = (IntArrayHandle) hTarget;

        return frame.assignValue(iReturn, xInt64.makeHandle(hDelegate.m_alValue.length));
        }

    @Override
    public int setPropertyCapacity(Frame frame, ObjectHandle hTarget, long nCapacity)
        {
        IntArrayHandle hDelegate = (IntArrayHandle) hTarget;

        long[] alOld = hDelegate.m_alValue;
        int    nSize = hDelegate.m_cSize;

        if (nCapacity < nSize)
            {
            return frame.raiseException(
                xException.illegalArgument(frame, "Capacity cannot be less then size"));
            }

        // for now, no trimming
        int nCapacityOld = alOld.length;
        if (nCapacity > nCapacityOld)
            {
            long[] alNew = new long[(int) nCapacity];
            System.arraycopy(alOld, 0, alNew, 0, alOld.length);
            hDelegate.m_alValue = alNew;
            }
        return Op.R_NEXT;
        }

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        IntArrayHandle hDelegate = (IntArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hDelegate.m_cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, hDelegate.m_cSize));
            }
        return frame.assignValue(iReturn, xInt64.makeHandle(hDelegate.m_alValue[(int) lIndex]));
        }

    @Override
    public int assignArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        IntArrayHandle hDelegate = (IntArrayHandle) hTarget;

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

        long[] alValue = hDelegate.m_alValue;
        if (lIndex == cSize)
            {
            // an array can only grow without any "holes"
            if (cSize == alValue.length)
                {
                if (hDelegate.getMutability() == Mutability.Fixed)
                    {
                    return frame.raiseException(xException.readOnly(frame));
                    }

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
    public int invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        IntArrayHandle hDelegate = (IntArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hDelegate.m_cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, hDelegate.m_cSize));
            }

        return frame.assignValue(iReturn,
                xInt64.makeHandle(++hDelegate.m_alValue[(int) lIndex]));
        }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        IntArrayHandle h1 = (IntArrayHandle) hValue1;
        IntArrayHandle h2 = (IntArrayHandle) hValue2;

        return frame.assignValue(iReturn,
                xBoolean.makeHandle(Arrays.equals(h1.m_alValue, h2.m_alValue)));
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

    @Override
    protected void insertElementImpl(DelegateHandle hTarget, ObjectHandle hElement, int nIndex)
        {
        IntArrayHandle hDelegate = (IntArrayHandle) hTarget;
        int            cSize     = hDelegate.m_cSize;
        long[]         alValue   = hDelegate.m_alValue;

        if (cSize == alValue.length)
            {
            alValue = hDelegate.m_alValue = grow(hDelegate.m_alValue, cSize + 1);
            }
        hDelegate.m_cSize++;

        if (nIndex == -1 || nIndex == cSize)
            {
            alValue[cSize] = ((JavaLong) hElement).getValue();
            }
        else
            {
            // insert
            System.arraycopy(alValue, nIndex, alValue, nIndex+1, cSize-nIndex);
            alValue[nIndex] = ((JavaLong) hElement).getValue();
            }
        }

    @Override
    protected void deleteElementImpl(DelegateHandle hTarget, int nIndex)
        {
        IntArrayHandle hDelegate = (IntArrayHandle) hTarget;
        int            cSize     = hDelegate.m_cSize;
        long[]         alValue   = hDelegate.m_alValue;

        if (nIndex < cSize - 1)
            {
            System.arraycopy(alValue, nIndex +1, alValue, nIndex, cSize- nIndex -1);
            }
        alValue[--hDelegate.m_cSize] = 0;
        }


    // ----- helper methods ------------------------------------------------------------------------

    private static long[] reverse(long[] alValue, int cSize)
        {
        long[] alValueR = new long[cSize];
        for (int i = 0; i < cSize; i++)
            {
            alValueR[i] = alValue[cSize - 1 - i];
            }
        return alValueR;
        }

    private static long[] grow(long[] alValue, int cSize)
        {
        int cCapacity = calculateCapacity(alValue.length, cSize);

        long[] alNew = new long[cCapacity];
        System.arraycopy(alValue, 0, alNew, 0, alValue.length);
        return alNew;
        }

    public static class IntArrayHandle
            extends DelegateHandle
        {
        public long[] m_alValue;

        protected IntArrayHandle(TypeComposition clazz, long[] alValue,
                                 int cSize, Mutability mutability)
            {
            super(clazz, mutability);

            m_alValue = alValue;
            m_cSize   = cSize;
            }

        @Override
        public boolean isNativeEqual()
            {
            return true;
            }

        @Override
        public int compareTo(ObjectHandle that)
            {
            long[] alThis = m_alValue;
            int    cThis  = m_cSize;
            long[] alThat = ((IntArrayHandle) that).m_alValue;
            int    cThat  = ((IntArrayHandle) that).m_cSize;

            if (cThis != cThat)
                {
                return cThis - cThat;
                }

            for (int i = 0; i < cThis; i++)
                {
                long lDiff = alThis[i] - alThat[i];
                if (lDiff != 0)
                    {
                    return lDiff < 0 ? -1 : 1;
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
            return obj instanceof IntArrayHandle
                && Arrays.equals(m_alValue, ((IntArrayHandle) obj).m_alValue);
            }
        }
    }