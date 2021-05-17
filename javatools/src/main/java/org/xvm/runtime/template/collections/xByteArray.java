package org.xvm.runtime.template.collections;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UInt8ArrayConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.numbers.xInt64;
import org.xvm.runtime.template.numbers.xUInt8;

import org.xvm.runtime.template.text.xChar;

import org.xvm.util.Handy;


/**
 * Native ByteArray<Byte> implementation.
 */
public class xByteArray
        extends xArray
    {
    public static xByteArray INSTANCE;

    public xByteArray(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        ClassTemplate mixin = f_templates.getTemplate("collections.arrays.ByteArray");

        mixin.markNativeMethod("toBitArray", null, null);
        mixin.markNativeMethod("toInt64", VOID, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        return pool().typeByteArray();
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof UInt8ArrayConstant)
            {
            UInt8ArrayConstant constBytes = (UInt8ArrayConstant) constant;

            return frame.pushStack(makeHandle(constBytes.getValue(), Mutability.Constant));
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "toBitArray":
                {
                ByteArrayHandle hArray = (ByteArrayHandle) hTarget;

                int    cBytes = hArray.m_cSize;
                byte[] aBytes = hArray.m_abValue;

                Mutability mutability = hArg == ObjectHandle.DEFAULT
                    ? Mutability.Constant
                    : xArray.Mutability.values()[((xEnum.EnumHandle) hArg).getOrdinal()];

                // TODO: do we always need a copy?
                byte[] aBits = Arrays.copyOfRange(aBytes, 0, cBytes);

                return frame.assignValue(iReturn,
                        xBitArray.makeHandle(aBits, cBytes >>> 3, mutability));
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "toInt64":
                {
                ByteArrayHandle hArray = (ByteArrayHandle) hTarget;

                int cBytes = hArray.m_cSize;
                if (cBytes != 8)
                    {
                    return frame.raiseException(
                        xException.illegalArgument(frame, "Invalid array size: " + cBytes));
                    }

                byte[] ab = hArray.m_abValue;
                long   l  =   ((long) (ab[0])        << 56)
                            + ((long) (ab[1] & 0xFF) << 48)
                            + ((long) (ab[2] & 0xFF) << 40)
                            + ((long) (ab[3] & 0xFF) << 32)
                            + ((long) (ab[4] & 0xFF) << 24)
                            + (       (ab[5] & 0xFF) << 16)
                            + (       (ab[6] & 0xFF) << 8 )
                            + (        ab[7] & 0xFF       );
                return frame.assignValue(iReturn, xInt64.makeHandle(l));
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public ArrayHandle createArrayHandle(TypeComposition clzArray, ObjectHandle[] ahArg)
        {
        int    c  = ahArg.length;
        byte[] al = new byte[c];
        for (int i = 0; i < c; i++)
            {
            al[i] = (byte) ((JavaLong) ahArg[i]).getValue();
            }
        return new ByteArrayHandle(clzArray, al, Mutability.Constant);
        }

    @Override
    protected ArrayHandle createCopy(ArrayHandle hArray, Mutability mutability)
        {
        ByteArrayHandle hSrc = (ByteArrayHandle) hArray;

        return new ByteArrayHandle(hSrc.getComposition(),
            Arrays.copyOfRange(hSrc.m_abValue, 0, hSrc.m_cSize), mutability);
        }

    @Override
    protected void fill(ArrayHandle hArray, int cSize, ObjectHandle hValue)
        {
        ByteArrayHandle ha = (ByteArrayHandle) hArray;

        Arrays.fill(ha.m_abValue, 0, cSize, (byte) ((JavaLong) hValue).getValue());
        ha.m_cSize = cSize;
        }

    @Override
    public ArrayHandle createEmptyArrayHandle(TypeComposition clzArray, int cCapacity, Mutability mutability)
        {
        return new ByteArrayHandle(clzArray, cCapacity, mutability);
        }

    @Override
    public int extractArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        ByteArrayHandle hArray = (ByteArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hArray.m_cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, hArray.m_cSize));
            }

        byte b = hArray.m_abValue[(int) lIndex];
        return frame.assignValue(iReturn, xUInt8.makeHandle(((long) b) & 0xFF));
        }

    @Override
    public int assignArrayValue(Frame frame, ObjectHandle hTarget, long lIndex, ObjectHandle hValue)
        {
        ByteArrayHandle hArray = (ByteArrayHandle) hTarget;

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

        byte[] abValue = hArray.m_abValue;
        if (lIndex == cSize)
            {
            // an array can only grow without any "holes"
            if (cSize == abValue.length)
                {
                if (hArray.m_mutability == Mutability.Fixed)
                    {
                    return frame.raiseException(xException.readOnly(frame));
                    }

                abValue = hArray.m_abValue = grow(abValue, cSize + 1);
                }

            hArray.m_cSize++;
            }

        abValue[(int) lIndex] = (byte) ((JavaLong) hValue).getValue();
        return Op.R_NEXT;
        }

    public int invokePreInc(Frame frame, ObjectHandle hTarget, long lIndex, int iReturn)
        {
        ByteArrayHandle hArray = (ByteArrayHandle) hTarget;

        if (lIndex < 0 || lIndex >= hArray.m_cSize)
            {
            return frame.raiseException(xException.outOfBounds(frame, lIndex, hArray.m_cSize));
            }

        return frame.assignValue(iReturn,
                xChar.makeHandle(++hArray.m_abValue[(int) lIndex]));
        }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        ByteArrayHandle h1 = (ByteArrayHandle) hValue1;
        ByteArrayHandle h2 = (ByteArrayHandle) hValue2;

        return frame.assignValue(iReturn,
                xBoolean.makeHandle(Arrays.equals(h1.m_abValue, h2.m_abValue)));
        }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        ByteArrayHandle hArray1 = (ByteArrayHandle) hValue1;
        ByteArrayHandle hArray2 = (ByteArrayHandle) hValue2;

        if (hArray1.isMutable() || hArray2.isMutable() || hArray1.m_cSize != hArray2.m_cSize)
            {
            return false;
            }

        return Arrays.equals(hArray1.m_abValue, hArray2.m_abValue);
        }

    @Override
    protected void insertElement(ArrayHandle hTarget, ObjectHandle hElement, int nIndex)
        {
        ByteArrayHandle hArray  = (ByteArrayHandle) hTarget;
        int             cSize   = hArray.m_cSize;
        byte[]          abValue = hArray.m_abValue;

        if (cSize == abValue.length)
            {
            abValue = hArray.m_abValue = grow(hArray.m_abValue, cSize + 1);
            }
        hArray.m_cSize++;

        if (nIndex == -1 || nIndex == cSize)
            {
            abValue[cSize] = (byte) ((JavaLong) hElement).getValue();
            }
        else
            {
            // insert
            System.arraycopy(abValue, nIndex, abValue, nIndex+1, cSize-nIndex);
            abValue[nIndex] = (byte) ((JavaLong) hElement).getValue();
            }
        }

    @Override
    protected void addElements(ArrayHandle hTarget, ObjectHandle hElements)
        {
        ByteArrayHandle hArray    = (ByteArrayHandle) hTarget;
        ByteArrayHandle hArrayAdd = (ByteArrayHandle) hElements;

        int    cNew  = hArrayAdd.m_cSize;
        byte[] abNew = hArrayAdd.m_abValue;

        if (cNew > 0)
            {
            byte[] abArray = hArray.m_abValue;
            int    cArray   = hArray.m_cSize;

            if (cArray + cNew > abArray.length)
                {
                abArray = hArray.m_abValue = grow(abArray, cArray + cNew);
                }
            hArray.m_cSize += cNew;
            System.arraycopy(abNew, 0, abArray, cArray, cNew);
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
        ByteArrayHandle hArray = (ByteArrayHandle) hTarget;

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

        byte[] abValue = hArray.m_abValue;
        try
            {
            byte[] abNew;

            if (ixLower >= ixUpper)
                {
                abNew = new byte[0];
                }
            else if (fReverse)
                {
                int cNew = (int) (ixUpper - ixLower);
                abNew = new byte[cNew];
                for (int i = 0; i < cNew; i++)
                    {
                    abNew[i] = abValue[(int) ixUpper - i - 1];
                    }
                }
            else
                {
                abNew = Arrays.copyOfRange(abValue, (int) ixLower, (int) ixUpper);
                }

            ByteArrayHandle hArrayNew = new ByteArrayHandle(
                hArray.getComposition(), abNew, hArray.m_mutability);

            return frame.assignValue(iReturn, hArrayNew);
            }
        catch (ArrayIndexOutOfBoundsException e)
            {
            long c = abValue.length;
            return frame.raiseException(
                xException.outOfBounds(frame, ixLower < 0 || ixLower >= c ? ixLower : ixUpper, c));
            }
        }


    // ----- helper methods -----

    private byte[] grow(byte[] abValue, int cSize)
        {
        int cCapacity = calculateCapacity(abValue.length, cSize);

        byte[] abNew = new byte[cCapacity];
        System.arraycopy(abValue, 0, abNew, 0, abValue.length);
        return abNew;
        }

    public static class ByteArrayHandle
            extends ArrayHandle
        {
        public byte[] m_abValue;

        protected ByteArrayHandle(TypeComposition clzArray, byte[] abValue, Mutability mutability)
            {
            super(clzArray, mutability);

            m_abValue = abValue;
            m_cSize   = abValue.length;
            }

        protected ByteArrayHandle(TypeComposition clzArray, int cCapacity, Mutability mutability)
            {
            super(clzArray, mutability);

            m_abValue = new byte[cCapacity];
            }

        @Override
        public int getCapacity()
            {
            return m_abValue.length;
            }

        @Override
        public void setCapacity(int nCapacity)
            {
            byte[] abOld = m_abValue;
            byte[] abNew = new byte[nCapacity];
            System.arraycopy(abOld, 0, abNew, 0, abOld.length);
            m_abValue = abNew;
            }

        @Override
        public ObjectHandle getElement(int ix)
            {
            return xUInt8.makeHandle(m_abValue[ix]);
            }

        @Override
        public void deleteElement(int ix)
            {
            if (ix < m_cSize - 1)
                {
                System.arraycopy(m_abValue, ix+1, m_abValue, ix, m_cSize-ix-1);
                }
            m_abValue[--m_cSize] = 0;
            }

        @Override
        public void clear()
            {
            m_abValue = Handy.EMPTY_BYTE_ARRAY;
            m_cSize   = 0;
            }

        @Override
        public void makeImmutable()
            {
            if (isMutable())
                {
                // purge the unused space
                byte[] ab = m_abValue;
                int    c  = m_cSize;
                if (ab.length != c)
                    {
                    byte[] abNew = new byte[c];
                    System.arraycopy(ab, 0, abNew, 0, c);
                    m_abValue = abNew;
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
            byte[] abThis = m_abValue;
            int    cThis  = m_cSize;
            byte[] abThat = ((ByteArrayHandle) that).m_abValue;
            int    cThat  = ((ByteArrayHandle) that).m_cSize;

            if (cThis != cThat)
                {
                return cThis - cThat;
                }

            for (int i = 0; i < cThis; i++)
                {
                int iDiff = abThis[i] - abThat[i];
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
            return Arrays.hashCode(m_abValue);
            }

        @Override
        public boolean equals(Object obj)
            {
            return obj instanceof ByteArrayHandle
                && Arrays.equals(m_abValue, ((ByteArrayHandle) obj).m_abValue);
            }
        }

    public static ByteArrayHandle makeHandle(byte[] ab, Mutability mutability)
        {
        ConstantPool    pool = INSTANCE.pool();
        TypeComposition clz  = mutability == Mutability.Constant
            ? INSTANCE.ensureClass(pool.typeBinary())
            : INSTANCE.ensureClass(pool.typeByteArray());

        return new ByteArrayHandle(clz, ab, mutability);
        }
    }
