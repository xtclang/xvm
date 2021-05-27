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
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xChar;


/**
 * Native RTDelegate<Char> implementation.
 */
public class xRTCharDelegate
        extends xRTDelegate
    {
    public static xRTCharDelegate INSTANCE;

    public xRTCharDelegate(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
                pool.typeChar());
        }

    @Override
    public DelegateHandle createDelegate(TypeConstant typeElement, int cCapacity,
                                         ObjectHandle[] ahContent, Mutability mutability)
        {
        int    cSize = ahContent.length;
        char[] ach   = new char[cCapacity];
        for (int i = 0; i < cSize; i++)
            {
            ach[i] = (char) ((JavaLong) ahContent[i]).getValue();
            }
        return new CharArrayHandle(getCanonicalClass(), ach, cSize, mutability);
        }

    @Override
    public void fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;

        Arrays.fill(hDelegate.m_achValue, 0, cSize, (char) ((JavaLong) hValue).getValue());
        hDelegate.m_cSize = cSize;
        }

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            int ofStart, int cSize, boolean fReverse)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;

        char[] achValue = Arrays.copyOfRange(hDelegate.m_achValue, ofStart, ofStart + cSize);
        if (fReverse)
            {
            achValue = reverse(achValue, cSize);
            }

        return new CharArrayHandle(hDelegate.getComposition(), achValue, cSize, mutability);
        }


    // ----- delegate API --------------------------------------------------------------------------

    @Override
    public int getPropertyCapacity(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;

        return frame.assignValue(iReturn, xInt64.makeHandle(hDelegate.m_achValue.length));
        }

    @Override
    public int setPropertyCapacity(Frame frame, ObjectHandle hTarget, long nCapacity)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;

        char[] achOld = hDelegate.m_achValue;
        int    nSize  = hDelegate.m_cSize;

        if (nCapacity < nSize)
            {
            return frame.raiseException(
                xException.illegalArgument(frame, "Capacity cannot be less then size"));
            }

        // for now, no trimming
        int nCapacityOld = achOld.length;
        if (nCapacity > nCapacityOld)
            {
            char[] achNew = new char[(int) nCapacity];
            System.arraycopy(achOld, 0, achNew, 0, achOld.length);
            hDelegate.m_achValue = achNew;
            }
        return Op.R_NEXT;
        }

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hDelegate.m_cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, hDelegate.m_cSize));
            }
        return frame.assignValue(iReturn, xChar.makeHandle(hDelegate.m_achValue[(int) lIndex]));
        }

    @Override
    public int assignArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;

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

        char[] achValue = hDelegate.m_achValue;
        if (lIndex == cSize)
            {
            // an array can only grow without any "holes"
            if (cSize == achValue.length)
                {
                if (hDelegate.getMutability() == Mutability.Fixed)
                    {
                    return frame.raiseException(xException.readOnly(frame));
                    }

                achValue = hDelegate.m_achValue = grow(achValue, cSize + 1);
                }

            hDelegate.m_cSize++;
            }

        achValue[(int) lIndex] = (char) ((JavaLong) hValue).getValue();
        return Op.R_NEXT;
        }

    public int invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hDelegate.m_cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, hDelegate.m_cSize));
            }

        return frame.assignValue(iReturn,
                xChar.makeHandle(++hDelegate.m_achValue[(int) lIndex]));
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
        CharArrayHandle h1 = (CharArrayHandle) hValue1;
        CharArrayHandle h2 = (CharArrayHandle) hValue2;

        if (h1 == h2)
            {
            return true;
            }

        return h1.getMutability() == h2.getMutability()
            && h1.m_cSize == h2.m_cSize
            && Arrays.equals(h1.m_achValue, h2.m_achValue);
        }

    @Override
    protected void insertElementImpl(DelegateHandle hTarget, ObjectHandle hElement, int nIndex)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;
        int             cSize     = hDelegate.m_cSize;
        char[]          achValue  = hDelegate.m_achValue;

        if (cSize == achValue.length)
            {
            achValue = hDelegate.m_achValue = grow(hDelegate.m_achValue, cSize + 1);
            }
        hDelegate.m_cSize++;

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
    protected void deleteElementImpl(DelegateHandle hTarget, int nIndex)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;
        int             cSize     = hDelegate.m_cSize;
        char[]          achValue  = hDelegate.m_achValue;

        if (nIndex < cSize - 1)
            {
            System.arraycopy(achValue, nIndex +1, achValue, nIndex, cSize- nIndex -1);
            }
        achValue[--hDelegate.m_cSize] = 0;
        }


    // ----- helper methods ------------------------------------------------------------------------

    public static char[] getChars(CharArrayHandle hChars, int ofStart, int cSize, boolean fReverse)
        {
        int    cChars = hChars.m_cSize;
        char[] ach    = hChars.m_achValue;

        if (hChars.getMutability() == Mutability.Constant &&
                ofStart == 0 && cChars == cSize && !fReverse)
            {
            return ach;
            }

        ach = Arrays.copyOfRange(ach, ofStart, ofStart + cSize);
        return fReverse ? reverse(ach, cSize) : ach;
        }

    private static char[] reverse(char[] achValue, int cSize)
        {
        char[] achValueR = new char[cSize];
        for (int i = 0; i < cSize; i++)
            {
            achValueR[i] = achValue[cSize - 1 - i];
            }
        return achValueR;
        }

    private char[] grow(char[] achValue, int cSize)
        {
        int cCapacity = calculateCapacity(achValue.length, cSize);

        char[] achNew = new char[cCapacity];
        System.arraycopy(achValue, 0, achNew, 0, achValue.length);
        return achNew;
        }

    public static class CharArrayHandle
            extends DelegateHandle
        {
        protected char[] m_achValue;

        protected CharArrayHandle(TypeComposition clazz, char[] achValue,
                                  int cSize, Mutability mutability)
            {
            super(clazz, mutability);

            m_achValue = achValue;
            m_cSize    = cSize;
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

    public CharArrayHandle makeHandle(char[] ach, Mutability mutability)
        {
        return new CharArrayHandle(getCanonicalClass(), ach, ach.length, mutability);
        }
    }
