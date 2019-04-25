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
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xChar;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xInt64;
import org.xvm.runtime.template.xString;
import org.xvm.runtime.template.xString.StringHandle;


/**
 * Native Array implementation for chars.
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
        ConstantPool pool = pool();
        return pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeChar());
        }

    @Override
    public ArrayHandle createArrayHandle(ClassComposition clzArray, ObjectHandle[] ahArg)
        {
        int    c  = ahArg.length;
        char[] al = new char[c];
        for (int i = 0; i < c; i++)
            {
            al[i] = (char) ((JavaLong) ahArg[i]).getValue();
            }
        return new CharArrayHandle(clzArray, al, Mutability.Constant);
        }

    @Override
    protected void fill(ArrayHandle hArray, int cSize, ObjectHandle hValue)
        {
        CharArrayHandle ha = (CharArrayHandle) hArray;

        Arrays.fill(ha.m_achValue, 0, cSize, (char) ((JavaLong) hValue).getValue());
        ha.m_cSize = cSize;
        }

    @Override
    public ArrayHandle createArrayHandle(ClassComposition clzArray, long cCapacity, Mutability mutability)
        {
        return new CharArrayHandle(clzArray, cCapacity, mutability);
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
            if (hArray.m_mutability == Mutability.FixedSize)
                {
                return frame.raiseException(xException.illegalOperation());
                }

            // an array can only grow without any "holes"
            if (cSize == achValue.length)
                {
                achValue = hArray.m_achValue = grow(achValue, cSize + 1);
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
    public int callEquals(Frame frame, ClassComposition clazz,
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
            achValue = hArray.m_achValue = grow(hArray.m_achValue, ixNext + 1);
            }
        hArray.m_cSize++;

        achValue[ixNext] = (char) ((JavaLong) hValue).getValue();
        return frame.assignValue(iReturn, hArray); // return this
        }

    @Override
    protected int addElements(Frame frame, ObjectHandle hTarget, ObjectHandle hValue, int iReturn)
        {
        CharArrayHandle hArray = (CharArrayHandle) hTarget;

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

        int    cNew;
        char[] achNew;
        if (hValue instanceof StringHandle)
            {
            achNew = ((StringHandle) hValue).getValue();
            cNew   = achNew.length;
            }
        else if (hValue instanceof CharArrayHandle)
            {
            CharArrayHandle hArrayAdd = (CharArrayHandle) hValue;
            cNew   = hArrayAdd.m_cSize;
            achNew = hArrayAdd.m_achValue;
            }
        else
            {
            // TODO GG
            throw new UnsupportedOperationException("need to implement add(Iterable<Char>) support for type: " + hValue.getType());
            }

        if (cNew > 0)
            {
            char[] achArray = hArray.m_achValue;
            int    cArray   = hArray.m_cSize;

            if (cArray + cNew > achArray.length)
                {
                achArray = hArray.m_achValue = grow(achArray, cArray + cNew);
                }
            hArray.m_cSize += cNew;
            System.arraycopy(achNew, 0, achArray, cArray, cNew);
            }

        return frame.assignValue(iReturn, hArray);
        }

    @Override
    protected int slice(Frame frame, ObjectHandle hTarget, long ixFrom, long ixTo, int iReturn)
        {
        CharArrayHandle hArray = (CharArrayHandle) hTarget;

        char[] achValue = hArray.m_achValue;
        try
            {
            char[]           achNew    = Arrays.copyOfRange(achValue, (int) ixFrom, (int) ixTo + 1);
            CharArrayHandle  hArrayNew = new CharArrayHandle(hTarget.getComposition(), achNew, Mutability.Mutable);

            return frame.assignValue(iReturn, hArrayNew);
            }
        catch (ArrayIndexOutOfBoundsException e)
            {
            long c = achValue.length;
            return frame.raiseException(
                xException.outOfRange(ixFrom < 0 || ixFrom >= c ? ixFrom : ixTo, c));
            }
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        CharArrayHandle hArray = (CharArrayHandle) hTarget;
        int             c      = hArray.m_cSize;

        if (c == 0)
            {
            return frame.assignValue(iReturn, xString.EMPTY_ARRAY);
            }

        char[]        ach = hArray.m_achValue;
        StringBuilder sb  = new StringBuilder(c*3);
        sb.append('[');
        for (int i = 0; i < c; i++)
            {
            if (i > 0)
                {
                sb.append(", ");
                }
            sb.append(ach[i]);
            }
        sb.append(']');

        return frame.assignValue(iReturn, xString.makeHandle(sb.toString()));
        }


    // ----- helper methods -----

    private char[] grow(char[] achValue, int cSize)
        {
        int cCapacity = calculateCapacity(achValue.length, cSize);

        char[] achNew = new char[cCapacity];
        System.arraycopy(achValue, 0, achNew, 0, achValue.length);
        return achNew;
        }

    public static class CharArrayHandle
            extends ArrayHandle
        {
        public char[] m_achValue;

        protected CharArrayHandle(TypeComposition clzArray, char[] achValue, Mutability mutability)
            {
            super(clzArray, mutability);

            m_achValue = achValue;
            m_cSize = achValue.length;
            }

        protected CharArrayHandle(TypeComposition clzArray, long cCapacity, Mutability mutability)
            {
            super(clzArray, mutability);

            m_achValue = new char[(int) cCapacity];
            }

        @Override
        public void makeImmutable()
            {
            if (isMutable())
                {
                // purge the unused space
                char[] ach = m_achValue;
                int    c   = m_cSize;
                if (ach.length != c)
                    {
                    char[] achNew = new char[c];
                    System.arraycopy(ach, 0, achNew, 0, c);
                    m_achValue = achNew;
                    }
                super.makeImmutable();
                }
            }

        @Override
        public boolean isNativeEqual()
            {
            return true;
            }

        @Override
        public int compareTo(ObjectHandle that)
            {
            char[] achThis = m_achValue;
            int    cThis   = m_cSize;
            char[] achThat = ((CharArrayHandle) that).m_achValue;
            int    cThat   = ((CharArrayHandle) that).m_cSize;

            if (cThis != cThat)
                {
                return cThis - cThat;
                }

            for (int i = 0; i < cThis; i++)
                {
                int iDiff = achThis[i] - achThat[i];
                if (iDiff != 0)
                    {
                    return iDiff;
                    }
                }
            return 0;
            }

        @Override
        public int hashCode()
            {
            return Arrays.hashCode(m_achValue);
            }

        @Override
        public boolean equals(Object obj)
            {
            return obj instanceof CharArrayHandle
                && Arrays.equals(m_achValue, ((CharArrayHandle) obj).m_achValue);
            }
        }

    public static CharArrayHandle makeHandle(char[] ach, Mutability mutability)
        {
        return new CharArrayHandle(INSTANCE.getCanonicalClass(), ach, mutability);
        }
    }
