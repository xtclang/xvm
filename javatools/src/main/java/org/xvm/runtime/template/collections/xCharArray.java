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
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.text.xChar;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.util.Handy;


/**
 * Native Array<Char> implementation.
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
    public void initNative()
        {
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        ConstantPool pool = pool();
        return pool.ensureArrayType(pool.typeChar());
        }

    @Override
    public ArrayHandle createArrayHandle(TypeComposition clzArray, ObjectHandle[] ahArg)
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
    protected ArrayHandle createCopy(ArrayHandle hArray, Mutability mutability)
        {
        CharArrayHandle hSrc = (CharArrayHandle) hArray;

        return new CharArrayHandle(hSrc.getComposition(),
            Arrays.copyOfRange(hSrc.m_achValue, 0, hSrc.m_cSize), mutability);
        }

    @Override
    protected void fill(ArrayHandle hArray, int cSize, ObjectHandle hValue)
        {
        CharArrayHandle ha = (CharArrayHandle) hArray;

        Arrays.fill(ha.m_achValue, 0, cSize, (char) ((JavaLong) hValue).getValue());
        ha.m_cSize = cSize;
        }

    @Override
    public ArrayHandle createEmptyArrayHandle(TypeComposition clzArray, int cCapacity, Mutability mutability)
        {
        return new CharArrayHandle(clzArray, cCapacity, mutability);
        }

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        CharArrayHandle hArray = (CharArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hArray.m_cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, hArray.m_cSize));
            }
        return frame.assignValue(iReturn, xChar.makeHandle(hArray.m_achValue[(int) lIndex]));
        }

    @Override
    public int assignArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        CharArrayHandle hArray = (CharArrayHandle) hTarget;

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

        char[] achValue = hArray.m_achValue;
        if (lIndex == cSize)
            {
            // an array can only grow without any "holes"
            if (cSize == achValue.length)
                {
                if (hArray.m_mutability == Mutability.Fixed)
                    {
                    return frame.raiseException(xException.readOnly(frame));
                    }

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
            return frame.raiseException(xException.outOfBounds(frame, lIndex, hArray.m_cSize));
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
    protected void insertElement(ArrayHandle hTarget, ObjectHandle hElement, int nIndex)
        {
        CharArrayHandle hArray   = (CharArrayHandle) hTarget;
        int cSize = hArray.m_cSize;
        char[]          achValue = hArray.m_achValue;

        if (cSize == achValue.length)
            {
            achValue = hArray.m_achValue = grow(hArray.m_achValue, cSize + 1);
            }
        hArray.m_cSize++;

        if (nIndex == -1 || nIndex == cSize)
            {
            achValue[cSize] = (char) ((JavaLong) hElement).getValue();
            }
        else
            {
            // insert
            System.arraycopy(achValue, nIndex, achValue, nIndex+1, cSize-nIndex);
            achValue[nIndex] = (char) ((JavaLong) hElement).getValue();
            }
        }

    @Override
    protected void addElements(ArrayHandle hTarget, ObjectHandle hElements)
        {
        CharArrayHandle hArray = (CharArrayHandle) hTarget;

        int    cNew;
        char[] achNew;
        if (hElements instanceof StringHandle)
            {
            achNew = ((StringHandle) hElements).getValue();
            cNew   = achNew.length;
            }
        else if (hElements instanceof CharArrayHandle)
            {
            CharArrayHandle hArrayAdd = (CharArrayHandle) hElements;
            cNew   = hArrayAdd.m_cSize;
            achNew = hArrayAdd.m_achValue;
            }
        else
            {
            // TODO GG
            throw new UnsupportedOperationException("need to implement add(Iterable<Char>) support for type: " + hElements.getType());
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
        CharArrayHandle hArray = (CharArrayHandle) hTarget;

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

        char[] achValue = hArray.m_achValue;
        try
            {
            char[] achNew;
            if (ixLower >= ixUpper)
                {
                achNew = new char[0];
                }
            else if (fReverse)
                {
                int cNew = (int) (ixUpper - ixLower);
                achNew = new char[cNew];
                for (int i = 0; i < cNew; i++)
                    {
                    achNew[i] = achValue[(int) ixUpper - i - 1];
                    }
                }
            else
                {
                achNew = Arrays.copyOfRange(achValue, (int) ixLower, (int) ixUpper);
                }

            CharArrayHandle  hArrayNew = new CharArrayHandle(
                hArray.getComposition(), achNew, hArray.m_mutability);

            return frame.assignValue(iReturn, hArrayNew);
            }
        catch (ArrayIndexOutOfBoundsException e)
            {
            long c = achValue.length;
            return frame.raiseException(
                xException.outOfBounds(frame, ixLower < 0 || ixLower >= c ? ixLower : ixUpper, c));
            }
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
            m_cSize    = achValue.length;
            }

        protected CharArrayHandle(TypeComposition clzArray, int cCapacity, Mutability mutability)
            {
            super(clzArray, mutability);

            m_achValue = new char[cCapacity];
            }

        @Override
        public int getCapacity()
            {
            return m_achValue.length;
            }

        @Override
        public void setCapacity(int nCapacity)
            {
            char[] achOld = m_achValue;
            char[] achNew = new char[nCapacity];
            System.arraycopy(achOld, 0, achNew, 0, achOld.length);
            m_achValue = achNew;
            }

        @Override
        public ObjectHandle getElement(int ix)
            {
            return xChar.makeHandle(m_achValue[ix]);
            }

        @Override
        public void deleteElement(int ix)
            {
            if (ix < m_cSize - 1)
                {
                System.arraycopy(m_achValue, ix+1, m_achValue, ix, m_cSize-ix-1);
                }
            m_achValue[--m_cSize] = 0;
            }

        @Override
        public void clear()
            {
            m_achValue = Handy.EMPTY_CHAR_ARRAY;
            m_cSize    = 0;
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

        @Override
        public String toString()
            {
            // for debugging only
            return String.copyValueOf(m_achValue, 0, m_cSize);
            }
        }

    public static CharArrayHandle makeHandle(char[] ach, Mutability mutability)
        {
        return new CharArrayHandle(INSTANCE.getCanonicalClass(), ach, mutability);
        }
    }
