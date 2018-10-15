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
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xChar;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xInt64;


/**
 * TODO:
 */
public class xCharArray
        extends xArray
    {
    public static xCharArray INSTANCE;

    public xCharArray(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        return pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeChar());
        }

    @Override
    public ArrayHandle createArrayHandle(Frame frame, TypeComposition clzArray, ObjectHandle[] ahArg)
        {
        int    c  = ahArg.length;
        char[] al = new char[c];
        for (int i = 0; i < c; i++)
            {
            al[i] = (char) ((JavaLong) ahArg[i]).getValue();
            }
        return new CharArrayHandle(clzArray, al);
        }

    @Override
    public ArrayHandle createArrayHandle(Frame frame, TypeComposition clzArray, long cCapacity)
        {
        return new CharArrayHandle(clzArray, cCapacity);
        }

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        CharArrayHandle hArray = (CharArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hArray.m_cSize)
            {
            return frame.raiseException(xException.outOfRange(lIndex, hArray.m_cSize));
            }
        return frame.assignValue(iReturn, xInt64.makeHandle(hArray.m_achValue[(int) lIndex]));
        }

    @Override
    public int assignArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        CharArrayHandle hArray = (CharArrayHandle) hTarget;

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

        char[] achValue = hArray.m_achValue;
        if (lIndex == cSize)
            {
            if (hArray.m_mutability == MutabilityConstraint.FixedSize)
                {
                return frame.raiseException(xException.illegalOperation());
                }

            // an array can only grow without any "holes"
            if (cSize == achValue.length)
                {
                achValue = hArray.m_achValue = grow(achValue, cSize);
                }

            hArray.m_cSize++;
            }

        achValue[(int) lIndex] = (char) ((JavaLong) hValue).getValue();
        return Op.R_NEXT;
        }

    public int invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        CharArrayHandle hArray = (CharArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hArray.m_cSize)
            {
            return frame.raiseException(xException.outOfRange(lIndex, hArray.m_cSize));
            }

        return frame.assignValue(iReturn,
                xChar.makeHandle(++hArray.m_achValue[(int) lIndex]));
        }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        CharArrayHandle h1 = (CharArrayHandle) hValue1;
        CharArrayHandle h2 = (CharArrayHandle) hValue2;

        return frame.assignValue(iReturn,
                xBoolean.makeHandle(Arrays.equals(h1.m_achValue, h2.m_achValue)));
        }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        CharArrayHandle hArray1 = (CharArrayHandle) hValue1;
        CharArrayHandle hArray2 = (CharArrayHandle) hValue2;

        if (hArray1.isMutable() || hArray2.isMutable() || hArray1.m_cSize != hArray2.m_cSize)
            {
            return false;
            }

        return Arrays.equals(hArray1.m_achValue, hArray2.m_achValue);
        }

    @Override
    protected int addElement(Frame frame, ObjectHandle hTarget, ObjectHandle hValue, int iReturn)
        {
        CharArrayHandle hArray = (CharArrayHandle) hTarget;
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

        char[] achValue = hArray.m_achValue;
        if (ixNext == achValue.length)
            {
            achValue = hArray.m_achValue = grow(hArray.m_achValue, ixNext);
            }
        hArray.m_cSize++;

        achValue[ixNext] = (char) ((JavaLong) hValue).getValue();
        return frame.assignValue(iReturn, hArray); // return this
        }

    @Override
    protected int slice(Frame frame, ObjectHandle hTarget, long ixFrom, long ixTo, int iReturn)
        {
        CharArrayHandle hArray = (CharArrayHandle) hTarget;

        char[] achValue = hArray.m_achValue;
        try
            {
            char[]           achNew    = Arrays.copyOfRange(achValue, (int) ixFrom, (int) ixTo);
            CharArrayHandle  hArrayNew = new CharArrayHandle(hTarget.getComposition(), achNew);
            hArrayNew.m_mutability = MutabilityConstraint.Mutable;

            return frame.assignValue(iReturn, hArrayNew);
            }
        catch (ArrayIndexOutOfBoundsException e)
            {
            long c = achValue.length;
            return frame.raiseException(
                xException.outOfRange(ixFrom < 0 || ixFrom >= c ? ixFrom : ixTo, c));
            }
        }

    // ----- helper methods -----

    private char[] grow(char[] achValue, int cSize)
        {
        // an array can only grow without any "holes"
        int cCapacity = achValue.length;

        // resize (TODO: we should be much smarter here)
        cCapacity = cCapacity + Math.max(cCapacity >> 2, 16);

        char[] achNew = new char[cCapacity];
        System.arraycopy(achValue, 0, achNew, 0, cSize);
        return achNew;
        }

    public static class CharArrayHandle
            extends ArrayHandle
        {
        public char[] m_achValue;

        protected CharArrayHandle(TypeComposition clzArray, char[] achValue)
            {
            super(clzArray);

            m_achValue = achValue;
            m_cSize = achValue.length;
            }

        protected CharArrayHandle(TypeComposition clzArray, long cCapacity)
            {
            super(clzArray);

            m_achValue = new char[(int) cCapacity];
            }

        @Override
        public int hashCode()
            {
            return Arrays.hashCode(m_achValue);
            }

        @Override
        public boolean equals(Object obj)
            {
            return Arrays.equals(m_achValue, ((CharArrayHandle) obj).m_achValue);
            }
        }

    public static CharArrayHandle makeHandle(char[] ach)
        {
        return new CharArrayHandle(INSTANCE.getCanonicalClass(), ach);
        }
    }
