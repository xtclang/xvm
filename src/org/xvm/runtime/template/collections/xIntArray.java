package org.xvm.runtime.template.collections;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.IndexSupport;
import org.xvm.runtime.template.xBoolean;
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
    public ObjectHandle createConstHandle(Frame frame, Constant constant)
        {
        ArrayConstant constArray = (ArrayConstant) constant;

        assert constArray.getFormat() == Constant.Format.Array;

        Constant[] aconst = constArray.getValue();
        int c = aconst.length;

        long[] alValue = new long[c];
        for (int i = 0; i < c; i++)
            {
            alValue[i] = ((IntConstant) aconst[i]).getValue().getLong();
            }

        ArrayHandle hArray = makeIntArrayInstance(alValue);
        hArray.makeImmutable();
        return hArray;
        }

    @Override
    public ObjectHandle extractArrayValue(ObjectHandle hTarget, long lIndex)
            throws ExceptionHandle.WrapperException
        {
        IntArrayHandle hArray = (IntArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hArray.m_cSize)
            {
            throw IndexSupport.outOfRange(lIndex, hArray.m_cSize).getException();
            }
        return xInt64.makeHandle(hArray.m_alValue[(int) lIndex]);
        }

    @Override
    public ExceptionHandle assignArrayValue(ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        IntArrayHandle hArray = (IntArrayHandle) hTarget;

        int cSize = hArray.m_cSize;

        if (lIndex < 0 || lIndex > cSize)
            {
            return IndexSupport.outOfRange(lIndex, cSize);
            }

        if (lIndex == cSize)
            {
            // an array can only grow without any "holes"
            int cCapacity = hArray.m_alValue.length;
            if (cSize == cCapacity)
                {
                if (hArray.m_fFixed)
                    {
                    return IndexSupport.outOfRange(lIndex, cSize);
                    }

                // resize (TODO: we should be much smarter here)
                cCapacity = cCapacity + Math.max(cCapacity >> 2, 16);

                long[] alNew = new long[cCapacity];
                System.arraycopy(hArray.m_alValue, 0, alNew, 0, cSize);
                hArray.m_alValue = alNew;
                }

            hArray.m_cSize++;
            }

        ((IntArrayHandle) hTarget).m_alValue[(int) lIndex] = ((JavaLong) hValue).getValue();
        return null;
        }

    public int invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        IntArrayHandle hArray = (IntArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hArray.m_cSize)
            {
            return frame.raiseException(IndexSupport.outOfRange(lIndex, hArray.m_cSize));
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

    public static IntArrayHandle makeIntArrayInstance(long[] alValue)
        {
        return new IntArrayHandle(INSTANCE.ensureCanonicalClass(), alValue);
        }

    public static IntArrayHandle makeIntArrayInstance(long cCapacity)
        {
        return new IntArrayHandle(INSTANCE.ensureCanonicalClass(), cCapacity);
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
            return super.toString() + (m_fFixed ? "fixed" : "capacity=" + m_alValue.length)
                    + ", size=" + m_cSize;
            }
        }
    }
