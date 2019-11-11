package org.xvm.runtime.template.collections;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xInt64;


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
    public void initDeclared()
        {
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeInt());
        }

    @Override
    public ArrayHandle createArrayHandle(ClassComposition clzArray, ObjectHandle[] ahArg)
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
    public ArrayHandle createArrayHandle(ClassComposition clzArray, int cCapacity, Mutability mutability)
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
                if (hArray.m_mutability == Mutability.FixedSize)
                    {
                    return frame.raiseException(xException.readOnly(frame));
                    }

                alValue = hArray.m_alValue = grow(alValue, cSize + 1);
                }

            hArray.m_cSize++;
            }

        alValue[(int) lIndex] = ((JavaLong) hValue).getValue();
        return Op.R_NEXT;
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
    public int callEquals(Frame frame, ClassComposition clazz,
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
    protected void addElement(ArrayHandle hTarget, ObjectHandle hElement)
        {
        IntArrayHandle hArray  = (IntArrayHandle) hTarget;
        int            ixNext  = hArray.m_cSize;
        long[]         alValue = hArray.m_alValue;

        if (ixNext == alValue.length)
            {
            alValue = hArray.m_alValue = grow(hArray.m_alValue, ixNext + 1);
            }
        hArray.m_cSize++;

        alValue[ixNext] = ((JavaLong) hElement).getValue();
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
    protected int slice(Frame frame, ObjectHandle hTarget, long ixFrom, long ixTo, boolean fReverse, int iReturn)
        {
        IntArrayHandle hArray = (IntArrayHandle) hTarget;

        long[] alValue = hArray.m_alValue;
        try
            {
            long[] alNew;

            if (fReverse)
                {
                int cNew = (int) (ixTo - ixFrom + 1);

                alNew = new long[cNew];
                for (int i = 0; i < cNew; i++)
                    {
                    alNew[i] = alNew[(int) ixTo - i];
                    }
                }
            else
                {
                alNew = Arrays.copyOfRange(alValue, (int) ixFrom, (int) ixTo + 1);
                }

            IntArrayHandle hArrayNew = new IntArrayHandle(
                hArray.getComposition(), alNew, hArray.m_mutability);

            return frame.assignValue(iReturn, hArrayNew);
            }
        catch (ArrayIndexOutOfBoundsException e)
            {
            long c = alValue.length;
            return frame.raiseException(
                xException.outOfBounds(frame, ixFrom < 0 || ixFrom >= c ? ixFrom : ixTo, c));
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
