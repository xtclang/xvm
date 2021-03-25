package org.xvm.runtime.template.collections;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.util.Handy;


/**
 * Native Array<Int> implementation.
 */
public class xIntArray
        extends xArray
    {
    public static xIntArray INSTANCE;

    public xIntArray(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        return pool.ensureArrayType(pool.typeInt());
        }

    @Override
    public ArrayHandle createArrayHandle(TypeComposition clzArray, ObjectHandle[] ahArg)
        {
        int    c  = ahArg.length;
        long[] al = new long[c];
        for (int i = 0; i < c; i++)
            {
            al[i] = ((JavaLong) ahArg[i]).getValue();
            }
        return new IntArrayHandle(clzArray, al, Mutability.Constant);
        }

    @Override
    protected ArrayHandle createCopy(ArrayHandle hArray, Mutability mutability)
        {
        IntArrayHandle hSrc = (IntArrayHandle) hArray;

        return new IntArrayHandle(hSrc.getComposition(),
            Arrays.copyOfRange(hSrc.m_alValue, 0, hSrc.m_cSize), mutability);
        }

    @Override
    protected void fill(ArrayHandle hArray, int cSize, ObjectHandle hValue)
        {
        IntArrayHandle ha = (IntArrayHandle) hArray;

        Arrays.fill(ha.m_alValue, 0, cSize, ((JavaLong) hValue).getValue());
        ha.m_cSize = cSize;
        }

    @Override
    public ArrayHandle createEmptyArrayHandle(TypeComposition clzArray, int cCapacity, Mutability mutability)
        {
        return new IntArrayHandle(clzArray, cCapacity, mutability);
        }

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        IntArrayHandle hArray = (IntArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hArray.m_cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, hArray.m_cSize));
            }
        return frame.assignValue(iReturn, xInt64.makeHandle(hArray.m_alValue[(int) lIndex]));
        }

    @Override
    public int assignArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        IntArrayHandle hArray = (IntArrayHandle) hTarget;

        int cSize = hArray.m_cSize;

        if (lIndex < 0 || lIndex > cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, cSize));
            }

        switch (hArray.m_mutability)
            {
            case Constant:
                return frame.raiseException(xException.immutableObject(frame));

            case Persistent:
                return frame.raiseException(xException.unsupportedOperation(frame));
            }

        long[] alValue = hArray.m_alValue;
        if (lIndex == cSize)
            {
            // an array can only grow without any "holes"
            if (cSize == alValue.length)
                {
                if (hArray.m_mutability == Mutability.Fixed)
                    {
                    return frame.raiseException(xException.readOnly(frame));
                    }

                alValue = hArray.m_alValue = grow(alValue, cSize + 1);
                }

            hArray.m_cSize++;
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

    public int invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        IntArrayHandle hArray = (IntArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hArray.m_cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, hArray.m_cSize));
            }

        return frame.assignValue(iReturn,
                xInt64.makeHandle(++hArray.m_alValue[(int) lIndex]));
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
        IntArrayHandle hArray1 = (IntArrayHandle) hValue1;
        IntArrayHandle hArray2 = (IntArrayHandle) hValue2;

        if (hArray1.isMutable() || hArray2.isMutable() || hArray1.m_cSize != hArray2.m_cSize)
            {
            return false;
            }

        return Arrays.equals(hArray1.m_alValue, hArray2.m_alValue);
        }

    @Override
    protected void insertElement(ArrayHandle hTarget, ObjectHandle hElement, int nIndex)
        {
        IntArrayHandle hArray  = (IntArrayHandle) hTarget;
        int cSize = hArray.m_cSize;
        long[]         alValue = hArray.m_alValue;

        if (cSize == alValue.length)
            {
            alValue = hArray.m_alValue = grow(hArray.m_alValue, cSize + 1);
            }
        hArray.m_cSize++;

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
    protected void addElements(ArrayHandle hTarget, ObjectHandle hElements)
        {
        IntArrayHandle hArray    = (IntArrayHandle) hTarget;
        IntArrayHandle hArrayAdd = (IntArrayHandle) hElements;

        int cAdd = hArrayAdd.m_cSize;
        if (cAdd > 0)
            {
            long[] alThis = hArray.m_alValue;
            int    cThis  = hArray.m_cSize;
            int    cNew   = cThis + cAdd;
            if (cNew > alThis.length)
                {
                alThis = hArray.m_alValue = grow(alThis, cNew);
                }
            hArray.m_cSize = cNew;
            System.arraycopy(hArrayAdd.m_alValue, 0, alThis, cThis, cAdd);
            }
        }

    @Override
    protected int slice(Frame        frame,
                        ObjectHandle hTarget,
                        long         ixLower,
                        boolean      fExLower,
                        long         ixUpper,
                        boolean      fExUpper,
                        boolean      fReverse,
                        int          iReturn)
        {
        IntArrayHandle hArray = (IntArrayHandle) hTarget;

        // calculate inclusive lower
        if (fExLower)
            {
            ++ixLower;
            }

        // calculate exclusive upper
        if (!fExUpper)
            {
            ++ixUpper;
            }

        long[] alValue = hArray.m_alValue;
        try
            {
            long[] alNew;

            if (ixLower >= ixUpper)
                {
                alNew = new long[0];
                }
            else if (fReverse)
                {
                int cNew = (int) (ixUpper - ixLower);
                alNew = new long[cNew];
                for (int i = 0; i < cNew; i++)
                    {
                    alNew[i] = alValue[(int) ixUpper - i - 1];
                    }
                }
            else
                {
                alNew = Arrays.copyOfRange(alValue, (int) ixLower, (int) ixUpper);
                }

            IntArrayHandle hArrayNew = new IntArrayHandle(
                hArray.getComposition(), alNew, hArray.m_mutability);

            return frame.assignValue(iReturn, hArrayNew);
            }
        catch (ArrayIndexOutOfBoundsException e)
            {
            long c = alValue.length;
            return frame.raiseException(
                xException.outOfBounds(frame, ixLower < 0 || ixLower >= c ? ixLower : ixUpper, c));
            }
        }


    // ----- helper methods -----

    private long[] grow(long[] alValue, int cSize)
        {
        int cCapacity = calculateCapacity(alValue.length, cSize);

        long[] alNew = new long[cCapacity];
        System.arraycopy(alValue, 0, alNew, 0, alValue.length);
        return alNew;
        }

    public static class IntArrayHandle
            extends ArrayHandle
        {
        public long[] m_alValue;

        protected IntArrayHandle(TypeComposition clzArray, long[] alValue, Mutability mutability)
            {
            super(clzArray, mutability);

            m_alValue = alValue;
            m_cSize   = alValue.length;
            }

        protected IntArrayHandle(TypeComposition clzArray, int cCapacity, Mutability mutability)
            {
            super(clzArray, mutability);

            m_alValue = new long[cCapacity];
            }

        @Override
        public int getCapacity()
            {
            return m_alValue.length;
            }

        @Override
        public void setCapacity(int nCapacity)
            {
            long[] alOld = m_alValue;
            long[] alNew = new long[nCapacity];
            System.arraycopy(alOld, 0, alNew, 0, alOld.length);
            m_alValue = alNew;
            }

        @Override
        public ObjectHandle getElement(int ix)
            {
            return xInt64.makeHandle(m_alValue[ix]);
            }

        @Override
        public void deleteElement(int ix)
            {
            if (ix < m_cSize - 1)
                {
                System.arraycopy(m_alValue, ix+1, m_alValue, ix, m_cSize-ix-1);
                }
            m_alValue[--m_cSize] = 0;
            }

        @Override
        public void clear()
            {
            m_alValue = Handy.EMPTY_LONG_ARRAY;
            m_cSize   = 0;
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
