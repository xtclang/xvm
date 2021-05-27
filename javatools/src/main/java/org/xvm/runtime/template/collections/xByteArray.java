package org.xvm.runtime.template.collections;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UInt8ArrayConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template._native.collections.arrays.xRTByteDelegate.ByteArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTByteDelegate;
import org.xvm.runtime.template._native.collections.arrays.xRTDelegate.DelegateHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTSlicingDelegate.SliceHandle;


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

            return frame.pushStack(makeByteArrayHandle(constBytes.getValue(), Mutability.Constant));
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
                ArrayHandle hArray = (ArrayHandle) hTarget;
                byte[]      aBytes = xByteArray.getBytes(hArray);

                Mutability mutability = hArg == ObjectHandle.DEFAULT
                        ? Mutability.Constant
                        : Mutability.values()[((xEnum.EnumHandle) hArg).getOrdinal()];

                if (hArray.m_mutability == Mutability.Constant && mutability != Mutability.Constant)
                    {
                    // if the array is not constant, getBytes() returns a copy
                    aBytes = aBytes.clone();
                    }

                return frame.assignValue(iReturn,
                        xArray.makeBitArrayHandle(aBytes, aBytes.length >>> 3, mutability));
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
                byte[] ab = getBytes((ArrayHandle) hTarget);

                if (ab.length != 8)
                    {
                    return frame.raiseException(
                        xException.illegalArgument(frame, "Invalid array size: " + ab.length));
                    }

                long l =   ((long) (ab[0])        << 56)
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


    /**
     * Extract a array of bytes from the Array<Byte> handle.
     */
    public static byte[] getBytes(ArrayHandle hArray)
        {
        DelegateHandle hDelegate = hArray.m_hDelegate;
        if (hDelegate instanceof SliceHandle)
            {
            SliceHandle     hSlice = (SliceHandle) hDelegate;
            ByteArrayHandle hBytes = (ByteArrayHandle) hSlice.f_hSource;
            return xRTByteDelegate.getBytes(hBytes,
                    hSlice.f_ofStart, hSlice.m_cSize, hSlice.f_fReverse);
            }

        if (hDelegate instanceof ByteArrayHandle)
            {
            ByteArrayHandle hBytes = (ByteArrayHandle) hDelegate;
            return xRTByteDelegate.getBytes(hBytes, 0, hBytes.m_cSize, false);
            }
        throw new UnsupportedOperationException();
        }

    public static void setBytes(ArrayHandle hArray, byte[] abVal)
        {
        DelegateHandle hDelegate = hArray.m_hDelegate;
        if (hDelegate instanceof SliceHandle)
            {
            SliceHandle     hSlice = (SliceHandle) hDelegate;
            ByteArrayHandle hBytes = (ByteArrayHandle) hSlice.f_hSource;

            System.arraycopy(abVal, 0, hBytes.m_abValue, hSlice.f_ofStart, abVal.length);
            return;
            }

        if (hDelegate instanceof ByteArrayHandle)
            {
            ByteArrayHandle hBytes = (ByteArrayHandle) hDelegate;
            System.arraycopy(abVal, 0, hBytes.m_abValue, 0, abVal.length);
            return;
            }
        throw new UnsupportedOperationException();
        }
    }
