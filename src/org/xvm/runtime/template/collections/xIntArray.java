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
import org.xvm.runtime.ObjectHandle.MutabilityConstraint;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.IndexSupport;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xInt64;


/**
 * TODO:
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
        ConstantPool pool = f_struct.getConstantPool();
        return pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeInt());
        }

    @Override
    public ArrayHandle createArrayHandle(Frame frame, TypeComposition clzArray, ObjectHandle[] ahArg)
        {
        int    c  = ahArg.length;
        long[] al = new long[c];
        for (int i = 0; i < c; i++)
            {
            al[i] = ((JavaLong) ahArg[i]).getValue();
            }
        return new IntArrayHandle(clzArray, al);
        }

    @Override
    public ArrayHandle createArrayHandle(Frame frame, TypeComposition clzArray, long cCapacity)
        {
        return new IntArrayHandle(clzArray, cCapacity);
        }

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        IntArrayHandle hArray = (IntArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hArray.m_cSize)
            {
            return frame.raiseException(IndexSupport.outOfRange(lIndex, hArray.m_cSize));
            }
        // TODO: should be a handle of the element's class
        return frame.assignValue(iReturn, xInt64.makeHandle(hArray.m_alValue[(int) lIndex]));
        }

    @Override
    public int assignArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        IntArrayHandle hArray = (IntArrayHandle) hTarget;

        int cSize = hArray.m_cSize;

        if (lIndex < 0 || lIndex > cSize)
            {
            return frame.raiseException(IndexSupport.outOfRange(lIndex, cSize));
            }

        switch (hArray.m_mutability)
            {
            case Constant:
                return frame.raiseException(xException.immutableObject());

            case Persistent:
                return frame.raiseException(xException.unsupportedOperation());
            }

        long[] alValue = hArray.m_alValue;
        if (lIndex == cSize)
            {
            if (hArray.m_mutability == MutabilityConstraint.FixedSize)
                {
                return frame.raiseException(xException.illegalOperation());
                }

            // an array can only grow without any "holes"
            if (cSize == alValue.length)
                {
                alValue = hArray.m_alValue = grow(alValue, cSize);
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
            return frame.raiseException(IndexSupport.outOfRange(lIndex, hArray.m_cSize));
            }

        // TODO: should be a handle of the element's class
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
    protected int addElement(Frame frame, ObjectHandle hTarget, ObjectHandle hValue, int iReturn)
        {
        IntArrayHandle hArray = (IntArrayHandle) hTarget;
        int            ixNext = hArray.m_cSize;

        switch (hArray.m_mutability)
            {
            case Constant:
                return frame.raiseException(xException.immutableObject());

            case FixedSize:
                return frame.raiseException(xException.illegalOperation());

            case Persistent:
                // TODO: implement
                return frame.raiseException(xException.unsupportedOperation());
            }

        long[] alValue = hArray.m_alValue;
        if (ixNext == alValue.length)
            {
            alValue = hArray.m_alValue = grow(hArray.m_alValue, ixNext);
            }
        hArray.m_cSize++;

        alValue[ixNext] = ((JavaLong) hValue).getValue();
        return frame.assignValue(iReturn, hArray); // return this
        }

    // ----- helper methods -----

    private long[] grow(long[] alValue, int cSize)
        {
        // an array can only grow without any "holes"
        int cCapacity = alValue.length;

        // resize (TODO: we should be much smarter here)
        cCapacity = cCapacity + Math.max(cCapacity >> 2, 16);

        long[] alNew = new long[cCapacity];
        System.arraycopy(alValue, 0, alNew, 0, cSize);
        return alNew;
        }

    public static class IntArrayHandle
            extends ArrayHandle
        {
        public long[] m_alValue;

        protected IntArrayHandle(TypeComposition clzArray, long[] alValue)
            {
            super(clzArray);

            m_alValue = alValue;
            m_cSize = alValue.length;
            }

        protected IntArrayHandle(TypeComposition clzArray, long cCapacity)
            {
            super(clzArray);

            m_alValue = new long[(int) cCapacity];
            }

        @Override
        public int hashCode()
            {
            return Arrays.hashCode(m_alValue);
            }

        @Override
        public boolean equals(Object obj)
            {
            return Arrays.equals(m_alValue, ((IntArrayHandle) obj).m_alValue);
            }

        @Override
        public String toString()
            {
            return super.toString() + m_mutability + ", size=" + m_cSize;
            }
        }
    }
