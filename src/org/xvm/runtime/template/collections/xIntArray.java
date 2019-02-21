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
import org.xvm.runtime.ObjectHandle.MutabilityConstraint;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xInt64;
import org.xvm.runtime.template.xString;


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
        return new IntArrayHandle(clzArray, al);
        }

    @Override
    public ArrayHandle createArrayHandle(ClassComposition clzArray, long cCapacity)
        {
        return new IntArrayHandle(clzArray, cCapacity);
        }

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        IntArrayHandle hArray = (IntArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hArray.m_cSize)
            {
            return frame.raiseException(xException.outOfRange(lIndex, hArray.m_cSize));
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
            return frame.raiseException(xException.outOfRange(lIndex, cSize));
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
            return frame.raiseException(xException.outOfRange(lIndex, hArray.m_cSize));
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
            alValue = hArray.m_alValue = grow(hArray.m_alValue, ixNext + 1);
            }
        hArray.m_cSize++;

        alValue[ixNext] = ((JavaLong) hValue).getValue();
        return frame.assignValue(iReturn, hArray); // return this
        }

    @Override
    protected int addElements(Frame frame, ObjectHandle hTarget, ObjectHandle hValue, int iReturn)
        {
        IntArrayHandle hThis = (IntArrayHandle) hTarget;

        switch (hThis.m_mutability)
            {
            case Constant:
                return frame.raiseException(xException.immutableObject());

            case FixedSize:
                return frame.raiseException(xException.illegalOperation());

            case Persistent:
                // TODO: implement
                return frame.raiseException(xException.unsupportedOperation());
            }

        IntArrayHandle hThat = (IntArrayHandle) hValue;

        int cThat = hThat.m_cSize;
        if (cThat > 0)
            {
            long[] alThis = hThis.m_alValue;
            int    cThis  = hThis.m_cSize;

            if (cThis + cThat > alThis.length)
                {
                alThis = hThis.m_alValue = grow(alThis, cThis + cThat);
                }
            hThis.m_cSize += cThat;
            System.arraycopy(hThat.m_alValue, 0, alThis, cThis, cThat);
            }
        return frame.assignValue(iReturn, hThis);
        }

    @Override
    protected int slice(Frame frame, ObjectHandle hTarget, long ixFrom, long ixTo, int iReturn)
        {
        IntArrayHandle hArray = (IntArrayHandle) hTarget;

        long[] alValue = hArray.m_alValue;
        try
            {
            long[]          alNew     = Arrays.copyOfRange(alValue, (int) ixFrom, (int) ixTo);
            IntArrayHandle  hArrayNew = new IntArrayHandle(hTarget.getComposition(), alNew);
            hArrayNew.m_mutability = MutabilityConstraint.Mutable;

            return frame.assignValue(iReturn, hArrayNew);
            }
        catch (ArrayIndexOutOfBoundsException e)
            {
            long c = alValue.length;
            return frame.raiseException(
                xException.outOfRange(ixFrom < 0 || ixFrom >= c ? ixFrom : ixTo, c));
            }
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        IntArrayHandle hArray = (IntArrayHandle) hTarget;
        int            c      = hArray.m_cSize;

        if (c == 0)
            {
            return frame.assignValue(iReturn, xString.EMPTY_ARRAY);
            }

        long[]        al = hArray.m_alValue;
        StringBuilder sb = new StringBuilder(c*7); // on average 5-digit long values
        sb.append('[');
        for (int i = 0; i < c; i++)
            {
            if (i > 0)
                {
                sb.append(", ");
                }
            sb.append(al[i]);
            }
        sb.append(']');
        return frame.assignValue(iReturn, xString.makeHandle(sb.toString()));
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
        }
    }
