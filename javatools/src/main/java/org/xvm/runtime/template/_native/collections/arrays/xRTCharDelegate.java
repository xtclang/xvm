package org.xvm.runtime.template._native.collections.arrays;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
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

    public xRTCharDelegate(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

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
    public DelegateHandle createDelegate(Container container, TypeConstant typeElement, int cSize,
                                         ObjectHandle[] ahContent, Mutability mutability)
        {
        char[] ach = new char[cSize];

        for (int i = 0, c = ahContent.length; i < c; i++)
            {
            ach[i] = (char) ((JavaLong) ahContent[i]).getValue();
            }
        return new CharArrayHandle(getCanonicalClass(), ach, cSize, mutability);
        }


    // ----- RTDelegate API ------------------------------------------------------------------------

    @Override
    public int invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;

        if (checkWriteInPlace(frame, hDelegate, lIndex, hDelegate.m_cSize) == Op.R_EXCEPTION)
            {
            return Op.R_EXCEPTION;
            }

        // we post increment to get the old value and check the range
        char chValue = hDelegate.m_achValue[(int) lIndex]++;
        if (chValue == Character.MAX_VALUE)
            {
            --hDelegate.m_achValue[(int) lIndex];
            return overflow(frame);
            }
        return frame.assignValue(iReturn, xChar.makeHandle(chValue+1));
        }

    @Override
    public int invokePreDec(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;

        if (checkWriteInPlace(frame, hDelegate, lIndex, hDelegate.m_cSize) == Op.R_EXCEPTION)
            {
            return Op.R_EXCEPTION;
            }

        // we post decrement to get the old value and check the range
        char chValue = hDelegate.m_achValue[(int) lIndex]--;
        if (chValue == Character.MIN_VALUE)
            {
            ++hDelegate.m_achValue[(int) lIndex];
            return overflow(frame);
            }
        return frame.assignValue(iReturn, xChar.makeHandle(chValue-1));
        }

    @Override
    public int invokePostInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;

        if (checkWriteInPlace(frame, hDelegate, lIndex, hDelegate.m_cSize) == Op.R_EXCEPTION)
            {
            return Op.R_EXCEPTION;
            }

        char chValue = hDelegate.m_achValue[(int) lIndex]++;
        if (chValue == Character.MAX_VALUE)
            {
            --hDelegate.m_achValue[(int) lIndex];
            return overflow(frame);
            }
        return frame.assignValue(iReturn, xChar.makeHandle(chValue));
        }

    @Override
    public int invokePostDec(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;

        if (checkWriteInPlace(frame, hDelegate, lIndex, hDelegate.m_cSize) == Op.R_EXCEPTION)
            {
            return Op.R_EXCEPTION;
            }

        char chValue = hDelegate.m_achValue[(int) lIndex]--;
        if (chValue == Character.MIN_VALUE)
            {
            ++hDelegate.m_achValue[(int) lIndex];
            return overflow(frame);
            }
        return frame.assignValue(iReturn, xChar.makeHandle(chValue));
        }

    @Override
    public DelegateHandle fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;

        Arrays.fill(hDelegate.m_achValue, 0, cSize, (char) ((JavaLong) hValue).getValue());
        hDelegate.m_cSize = cSize;
        return hDelegate;
        }

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
        int    nSize  = (int) hDelegate.m_cSize;

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
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;

        char[] achValue = Arrays.copyOfRange(
                hDelegate.m_achValue, (int) ofStart, (int) (ofStart + cSize));
        if (fReverse)
            {
            achValue = reverse(achValue, (int) cSize);
            }

        return new CharArrayHandle(hDelegate.getComposition(), achValue, (int) cSize, mutability);
        }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;

        return frame.assignValue(iReturn, xChar.makeHandle(hDelegate.m_achValue[(int) lIndex]));
        }

    @Override
    protected int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                       ObjectHandle hValue)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;

        int    cSize    = (int) hDelegate.m_cSize;
        char[] achValue = hDelegate.m_achValue;
        int    nIndex   = (int) lIndex;

        if (nIndex >= cSize)
            {
            if (nIndex >= achValue.length)
                {
                achValue = hDelegate.m_achValue = grow(achValue, nIndex + 1);
                }

            hDelegate.m_cSize = nIndex + 1;
            }

        achValue[nIndex] = (char) ((JavaLong) hValue).getValue();
        return Op.R_NEXT;
        }

    @Override
    protected void insertElementImpl(DelegateHandle hTarget, ObjectHandle hElement, long lIndex)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;
        int             cSize     = (int) hDelegate.m_cSize;
        char[]          achValue  = hDelegate.m_achValue;

        if (cSize == achValue.length)
            {
            achValue = hDelegate.m_achValue = grow(hDelegate.m_achValue, cSize + 1);
            }
        hDelegate.m_cSize++;

        if (lIndex == cSize)
            {
            achValue[cSize] = (char) ((JavaLong) hElement).getValue();
            }
        else
            {
            // insert
            int nIndex = (int) lIndex;
            System.arraycopy(achValue, nIndex, achValue, nIndex + 1, cSize - nIndex);
            achValue[nIndex] = (char) ((JavaLong) hElement).getValue();
            }
        }

    @Override
    protected void deleteElementImpl(DelegateHandle hTarget, long lIndex)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;
        int             cSize     = (int) hDelegate.m_cSize;
        char[]          achValue  = hDelegate.m_achValue;
        int             nIndex    = (int) lIndex;

        if (nIndex < cSize - 1)
            {
            System.arraycopy(achValue, nIndex + 1, achValue, nIndex, cSize - nIndex - 1);
            }
        achValue[(int) --hDelegate.m_cSize] = 0;
        }

    @Override
    protected void deleteRangeImpl(DelegateHandle hTarget, long lIndex, long cDelete)
        {
        CharArrayHandle hDelegate = (CharArrayHandle) hTarget;
        int             cSize     = (int) hDelegate.m_cSize;
        char[]          achValue  = hDelegate.m_achValue;
        int             nIndex    = (int) lIndex;
        int             nDelete   = (int) cDelete;

        if (nIndex < cSize - nDelete)
            {
            System.arraycopy(achValue, nIndex + nDelete, achValue, nIndex, cSize - nIndex - nDelete);
            }
        hDelegate.m_cSize -= cDelete;
        }


    // ----- helper methods ------------------------------------------------------------------------

    public static char[] getChars(CharArrayHandle hChars, int ofStart, int cChars, boolean fReverse)
        {
        char[] ach = hChars.m_achValue;

        if (hChars.getMutability() == Mutability.Constant &&
                ofStart == 0 && cChars == ach.length && !fReverse)
            {
            return ach;
            }

        ach = Arrays.copyOfRange(ach, ofStart, ofStart + cChars);
        return fReverse ? reverse(ach, cChars) : ach;
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
        public boolean makeImmutable()
            {
            if (isMutable())
                {
                purgeUnusedSpace();
                }
            return super.makeImmutable();
            }

        protected void purgeUnusedSpace()
            {
            char[] ach = m_achValue;
            int    c   = (int) m_cSize;
            if (ach.length != c)
                {
                char[] achNew = new char[c];
                System.arraycopy(ach, 0, achNew, 0, c);
                m_achValue = achNew;
                }
            }

        @Override
        public int compareTo(ObjectHandle that)
            {
            char[] achThis = m_achValue;
            int    cThis   = (int) m_cSize;
            char[] achThat = ((CharArrayHandle) that).m_achValue;
            int    cThat   = (int) ((CharArrayHandle) that).m_cSize;

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
            return String.copyValueOf(m_achValue, 0, (int) m_cSize);
            }
        }

    public CharArrayHandle makeHandle(char[] ach, Mutability mutability)
        {
        return new CharArrayHandle(getCanonicalClass(), ach, ach.length, mutability);
        }
    }