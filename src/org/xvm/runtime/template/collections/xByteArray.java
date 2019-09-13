package org.xvm.runtime.template.collections;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UInt8ArrayConstant;

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
import org.xvm.runtime.template.xString;
import org.xvm.runtime.template.xUInt8;


/**
 * Native Array<Byte> implementation.
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
    public void initDeclared()
        {
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

            frame.pushStack(makeHandle(constBytes.getValue(), Mutability.Constant));

            return Op.R_NEXT;
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public ArrayHandle createArrayHandle(ClassComposition clzArray, ObjectHandle[] ahArg)
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
    public ArrayHandle createArrayHandle(ClassComposition clzArray, int cCapacity, Mutability mutability)
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
            if (hArray.m_mutability == Mutability.FixedSize)
                {
                return frame.raiseException(xException.readOnly(frame));
                }

            // an array can only grow without any "holes"
            if (cSize == abValue.length)
                {
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
    public int callEquals(Frame frame, ClassComposition clazz,
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
    protected int addElement(Frame frame, ObjectHandle hTarget, ObjectHandle hValue, int iReturn)
        {
        ByteArrayHandle hArray = (ByteArrayHandle) hTarget;
        int            ixNext = hArray.m_cSize;

        switch (hArray.m_mutability)
            {
            case Constant:
                return frame.raiseException(xException.immutableObject(frame));

            case FixedSize:
                return frame.raiseException(xException.readOnly(frame));

            case Persistent:
                // TODO: implement
                return frame.raiseException(xException.unsupportedOperation(frame));
            }

        byte[] abValue = hArray.m_abValue;
        if (ixNext == abValue.length)
            {
            abValue = hArray.m_abValue = grow(hArray.m_abValue, ixNext + 1);
            }
        hArray.m_cSize++;

        abValue[ixNext] = (byte) ((JavaLong) hValue).getValue();
        return frame.assignValue(iReturn, hArray); // return this
        }

    @Override
    protected int addElements(Frame frame, ObjectHandle hTarget, ObjectHandle hValue, int iReturn)
        {
        ByteArrayHandle hArray = (ByteArrayHandle) hTarget;

        switch (hArray.m_mutability)
            {
            case Constant:
                return frame.raiseException(xException.immutableObject(frame));

            case FixedSize:
                return frame.raiseException(xException.readOnly(frame));

            case Persistent:
                // TODO: implement
                return frame.raiseException(xException.unsupportedOperation(frame));
            }

        ByteArrayHandle hArrayAdd = (ByteArrayHandle) hValue;
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

        return frame.assignValue(iReturn, hArray);
        }

    @Override
    protected int slice(Frame frame, ObjectHandle hTarget, long ixFrom, long ixTo, int iReturn)
        {
        ByteArrayHandle hArray = (ByteArrayHandle) hTarget;

        byte[] abValue = hArray.m_abValue;
        try
            {
            byte[]           abNew    = Arrays.copyOfRange(abValue, (int) ixFrom, (int) ixTo + 1);
            ByteArrayHandle hArrayNew = new ByteArrayHandle(hTarget.getComposition(), abNew, Mutability.Mutable);

            return frame.assignValue(iReturn, hArrayNew);
            }
        catch (ArrayIndexOutOfBoundsException e)
            {
            long c = abValue.length;
            return frame.raiseException(
                xException.outOfBounds(frame, ixFrom < 0 || ixFrom >= c ? ixFrom : ixTo, c));
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
        ConstantPool     pool = INSTANCE.pool();
        ClassComposition clz  = mutability == Mutability.Constant
            ? INSTANCE.ensureClass(pool.typeBinary())
            : INSTANCE.ensureClass(pool.typeByteArray());

        return new ByteArrayHandle(clz, ab, mutability);
        }
    }
