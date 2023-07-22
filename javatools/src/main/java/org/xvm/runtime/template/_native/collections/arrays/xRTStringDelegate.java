package org.xvm.runtime.template._native.collections.arrays;


import java.util.Arrays;

import java.util.stream.Stream;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;


/**
 * A native ArrayDelegate implementations based on String arrays.
 */
public class xRTStringDelegate
        extends xRTDelegate
    {
    public static xRTStringDelegate INSTANCE;

    public xRTStringDelegate(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    // ----- RTDelegate API ------------------------------------------------------------------------

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
                pool.typeString());
        }

    @Override
    public DelegateHandle createDelegate(Container container, TypeConstant typeElement, int cSize,
                                         ObjectHandle[] ahContent, Mutability mutability)
        {
        String[] as = new String[cSize];

        for (int i = 0, c = ahContent.length; i < c; i++)
            {
            as[i] = ((StringHandle) ahContent[i]).getStringValue();
            }
        return makeHandle(as, cSize, mutability);
        }

    @Override
    public DelegateHandle fill(DelegateHandle hTarget, int cSize, ObjectHandle hValue)
        {
        StringArrayHandle hDelegate = (StringArrayHandle) hTarget;

        Arrays.fill(hDelegate.m_asValue, 0, cSize, ((StringHandle) hValue).getStringValue());
        hDelegate.m_cSize = cSize;
        return hDelegate;
        }

    @Override
    public int getPropertyCapacity(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        StringArrayHandle hDelegate = (StringArrayHandle) hTarget;

        return frame.assignValue(iReturn, xInt64.makeHandle(hDelegate.m_asValue.length));
        }

    @Override
    public int setPropertyCapacity(Frame frame, ObjectHandle hTarget, long nCapacity)
        {
        StringArrayHandle hDelegate = (StringArrayHandle) hTarget;

        String[] asOld = hDelegate.m_asValue;
        int      nSize = (int) hDelegate.m_cSize;

        if (nCapacity < nSize)
            {
            return frame.raiseException(
                xException.illegalArgument(frame, "Capacity cannot be less then size"));
            }

        // for now, no trimming
        int nCapacityOld = asOld.length;
        if (nCapacity > nCapacityOld)
            {
                String[] abNew = new String[(int) nCapacity];
            System.arraycopy(asOld, 0, abNew, 0, asOld.length);
            hDelegate.m_asValue = abNew;
            }
        return Op.R_NEXT;
        }

    @Override
    protected DelegateHandle createCopyImpl(DelegateHandle hTarget, Mutability mutability,
                                            long ofStart, long cSize, boolean fReverse)
        {
        StringArrayHandle hDelegate = (StringArrayHandle) hTarget;

        String[] abValue = Arrays.copyOfRange(hDelegate.m_asValue, (int) ofStart, (int) (ofStart + cSize));
        if (fReverse)
            {
            abValue = reverseBytes(abValue, (int) cSize);
            }

        return new StringArrayHandle(hDelegate.getComposition(), abValue, cSize, mutability);
        }

    @Override
    protected int extractArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex, int iReturn)
        {
        StringArrayHandle hDelegate = (StringArrayHandle) hTarget;

        String s = hDelegate.m_asValue[(int) lIndex];
        return frame.assignValue(iReturn, xString.makeHandle(s));
        }

    @Override
    protected int assignArrayValueImpl(Frame frame, DelegateHandle hTarget, long lIndex,
                                       ObjectHandle hValue)
        {
        StringArrayHandle hDelegate = (StringArrayHandle) hTarget;

        int    cSize   = (int) hDelegate.m_cSize;
        String[] abValue = hDelegate.m_asValue;

        if (lIndex == cSize)
            {
            if (cSize == abValue.length)
                {
                abValue = hDelegate.m_asValue = grow(abValue, cSize + 1);
                }

            hDelegate.m_cSize++;
            }

        abValue[(int) lIndex] = ((StringHandle) hValue).getStringValue();
        return Op.R_NEXT;
        }

    @Override
    protected void insertElementImpl(DelegateHandle hTarget, ObjectHandle hElement, long lIndex)
        {
        StringArrayHandle hDelegate = (StringArrayHandle) hTarget;
        int               cSize     = (int) hDelegate.m_cSize;
        String[]          asValue   = hDelegate.m_asValue;
        String            bValue    = ((StringHandle) hElement).getStringValue();

        if (cSize == asValue.length)
            {
            asValue = hDelegate.m_asValue = grow(hDelegate.m_asValue, cSize + 1);
            }
        hDelegate.m_cSize++;

        int nIndex = (int) lIndex;
        if (nIndex < cSize)
            {
            System.arraycopy(asValue, nIndex, asValue, nIndex + 1, cSize - nIndex);
            }
        asValue[nIndex] = bValue;
        }

    @Override
    protected void deleteElementImpl(DelegateHandle hTarget, long lIndex)
        {
        StringArrayHandle hDelegate = (StringArrayHandle) hTarget;
        int               cSize     = (int) hDelegate.m_cSize;
        String[]          asValue   = hDelegate.m_asValue;

        if (lIndex < cSize - 1)
            {
            int nIndex = (int) lIndex;
            System.arraycopy(asValue, nIndex + 1, asValue, nIndex, cSize - nIndex -1);
            }
        asValue[(int) --hDelegate.m_cSize] = null;
        }


    // ----- ClassTemplate API ---------------------------------------------------------------------

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        StringArrayHandle h1 = (StringArrayHandle) hValue1;
        StringArrayHandle h2 = (StringArrayHandle) hValue2;

        return frame.assignValue(iReturn,
                xBoolean.makeHandle(Arrays.equals(h1.m_asValue, h2.m_asValue)));
        }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        StringArrayHandle h1 = (StringArrayHandle) hValue1;
        StringArrayHandle h2 = (StringArrayHandle) hValue2;

        if (h1 == h2)
            {
            return true;
            }

        return h1.getMutability() == h2.getMutability()
            && h1.m_cSize == h2.m_cSize
            && Arrays.equals(h1.m_asValue, h2.m_asValue);
        }


    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Reverse the array of bites represented by the specified array.
     *
     * @param abValue the byte array
     * @param cSize   the actual number of bytes held by the array
     */
    public static String[] reverseBytes(String[] abValue, int cSize)
        {
        String[] abValueR = new String[cSize];
        for (int i = 0; i < cSize; i++)
            {
            abValueR[i] = abValue[cSize - 1 - i];
            }
        return abValueR;
        }

    protected static String[] grow(String[] abValue, int cSize)
        {
        int cCapacity = calculateCapacity(abValue.length, cSize);

        String[] abNew = new String[cCapacity];
        System.arraycopy(abValue, 0, abNew, 0, abValue.length);
        return abNew;
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    /**
     * Make a canonical array handle.
     */
    public StringArrayHandle makeHandle(String[] as, long cSize, Mutability mutability)
        {
        return new StringArrayHandle(getCanonicalClass(), as, cSize, mutability);
        }

    public static class StringArrayHandle
            extends DelegateHandle
        {
        protected String[] m_asValue;

        protected StringArrayHandle(TypeComposition clazz, String[] asValue,
                                    long cSize, Mutability mutability)
            {
            super(clazz, mutability);

            m_asValue = asValue;
            m_cSize   = cSize;
            }

        /**
         * Get the String value at the specified index in the array.
         *
         * @param nIndex  the index of the String value to return
         *
         * @return  the String value at the specified index in the array
         *
         * @throws IndexOutOfBoundsException if the index is out of
         *         range (nIndex < 0 || nIndex >= m_cSize)
         */
        public String get(long nIndex)
            {
            if (nIndex < 0 || nIndex >= m_cSize)
                {
                throw new ArrayIndexOutOfBoundsException((int) nIndex);
                }
            return m_asValue[(int) nIndex];
            }

        /**
         * Obtain the contents of the String array as a {@link Stream}.
         *
         * @return the contents of the String array as a {@link Stream}
         */
        public Stream<String> stream()
            {
            return Arrays.stream(m_asValue, 0, (int) m_cSize);
            }

        @Override
        public boolean makeImmutable()
            {
            if (isMutable())
                {
                // purge the unused space
                String[] as = m_asValue;
                int    c  = (int) m_cSize;
                if (as.length != c)
                    {
                    String[] asNew = new String[c];
                    System.arraycopy(as, 0, asNew, 0, c);
                    m_asValue = asNew;
                    }
                return super.makeImmutable();
                }
            return true;
            }

        @Override
        public boolean isNativeEqual()
            {
            return true;
            }

        @Override
        public int compareTo(ObjectHandle that)
            {
            String[] asThis = m_asValue;
            int      cThis  = (int) m_cSize;
            String[] asThat = ((StringArrayHandle) that).m_asValue;
            int      cThat  = (int) ((StringArrayHandle) that).m_cSize;

            if (cThis != cThat)
                {
                return cThis - cThat;
                }

            for (int i = 0; i < cThis; i++)
                {
                int iDiff = asThis[i].compareTo(asThat[i]);
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
            return Arrays.hashCode(m_asValue);
            }

        @Override
        public boolean equals(Object obj)
            {
            return obj instanceof StringArrayHandle that
                && Arrays.equals(this.m_asValue, that.m_asValue);
            }
        }
    }