package org.xvm.runtime.template.collections;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UInt8ArrayConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template._native.collections.arrays.ByteBasedDelegate.ByteArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.ByteView;
import org.xvm.runtime.template._native.collections.arrays.xRTDelegate.DelegateHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTSlicingDelegate.SliceHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTViewFromByteToInt8;
import org.xvm.runtime.template._native.collections.arrays.xRTViewFromByteToInt16;
import org.xvm.runtime.template._native.collections.arrays.xRTViewFromByteToInt64;


/**
 * Native ByteArray implementation.
 */
public class xByteArray
        extends xArray
    {
    public static xByteArray INSTANCE;

    public xByteArray(Container container, ClassStructure structure, boolean fInstance)
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
        ClassTemplate mixin = f_container.getTemplate("collections.arrays.ByteArray");

        mixin.markNativeMethod("asByteArray", VOID, null);
        mixin.markNativeMethod("asInt8Array", VOID, null);
        mixin.markNativeMethod("asInt16Array", VOID, null);
        mixin.markNativeMethod("asInt64Array", VOID, null);

        invalidateTypeInfo();
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        return pool().typeByteArray();
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof UInt8ArrayConstant constBytes)
            {
            return frame.pushStack(makeByteArrayHandle(constBytes.getValue(), Mutability.Constant));
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "asByteArray":
                {
                ArrayHandle hArray = (ArrayHandle) hTarget;
                if (hArray.m_hDelegate instanceof ByteArrayHandle)
                    {
                    return frame.assignValue(iReturn, hArray);
                    }

                // TODO GG: we need a reifiable view (see the comments in ByteArray.x)
                return frame.raiseException(
                        xException.notImplemented(frame, "Not implemented"));
                }
            case "asInt8Array":
                {
                ArrayHandle hArray = (ArrayHandle) hTarget;

                Mutability      mutability = hArray.m_mutability;
                DelegateHandle  hView      = xRTViewFromByteToInt8.INSTANCE.createByteView(
                                                    hArray.m_hDelegate, mutability, 1);
                return frame.assignValue(iReturn,
                        new ArrayHandle(getInt8ArrayComposition(), hView, mutability));
                }

            case "asInt16Array":
                {
                ArrayHandle hArray = (ArrayHandle) hTarget;

                if (hArray.m_hDelegate.m_cSize % 2 != 0)
                    {
                    return frame.raiseException(xException.illegalArgument(frame,
                                "Invalid array size: " + hArray.m_hDelegate.m_cSize));
                    }

                Mutability     mutability = hArray.m_mutability;
                DelegateHandle hView      = xRTViewFromByteToInt16.INSTANCE.createByteView(
                                                    hArray.m_hDelegate, mutability, 2);
                return frame.assignValue(iReturn,
                        new ArrayHandle(getInt16ArrayComposition(), hView, mutability));
                }

            case "asInt64Array":
                {
                ArrayHandle hArray = (ArrayHandle) hTarget;

                if (hArray.m_hDelegate.m_cSize % 8 != 0)
                    {
                    return frame.raiseException(xException.illegalArgument(frame,
                                "Invalid array size: " + hArray.m_hDelegate.m_cSize));
                    }

                Mutability     mutability = hArray.m_mutability;
                DelegateHandle hView      = xRTViewFromByteToInt64.INSTANCE.createByteView(
                                                    hArray.m_hDelegate, mutability, 8);
                return frame.assignValue(iReturn,
                        new ArrayHandle(getInt64ArrayComposition(), hView, mutability));
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    /**
     * Extract an array of bytes from the Array<Byte> handle.
     */
    public static byte[] getBytes(ArrayHandle hArray)
        {
        DelegateHandle hDelegate = hArray.m_hDelegate;
        long           cSize     = hDelegate.m_cSize;
        long           ofStart   = 0;
        boolean        fReverse  = false;

        if (hDelegate instanceof SliceHandle hSlice)
            {
            hDelegate = hSlice.f_hSource;
            ofStart   = hSlice.f_ofStart;
            fReverse  = hSlice.f_fReverse;
            }

        ClassTemplate tDelegate = hDelegate.getTemplate();
        if (tDelegate instanceof ByteView hView)
            {
            return hView.getBytes(hDelegate, ofStart, cSize, fReverse);
            }
        throw new UnsupportedOperationException();
        }

    /**
     * Copy bytes from the specified array.
     */
    public static void setBytes(ArrayHandle hArray, byte[] abVal)
        {
        DelegateHandle hDelegate = hArray.m_hDelegate;

        long ofStart = 0;

        if (hDelegate instanceof SliceHandle hSlice)
            {
            hDelegate = hSlice.f_hSource;
            ofStart   = hSlice.f_ofStart;
            }

        ClassTemplate tDelegate = hDelegate.getTemplate();
        if (tDelegate instanceof ByteView tView)
            {
            for (int i = 0, c = abVal.length; i < c; i++)
                {
                tView.assignByte(hDelegate, ofStart + i, abVal[i]);
                }
            return;
            }
        throw new UnsupportedOperationException();
        }

    private TypeComposition getInt8ArrayComposition()
        {
        TypeComposition clz = INT8_ARRAY_CLZ;
        if (clz == null)
            {
            TypeConstant typeInt8 = pool().typeInt8();

            INT8_ARRAY_CLZ = clz = f_container.resolveClass(pool().ensureArrayType(typeInt8));
            }
        return clz;
        }

    private TypeComposition getInt16ArrayComposition()
        {
        TypeComposition clz = INT16_ARRAY_CLZ;
        if (clz == null)
            {
            TypeConstant typeInt16 = pool().typeInt16();
            INT16_ARRAY_CLZ = clz = f_container.resolveClass(pool().ensureArrayType(typeInt16));
            }
        return clz;
        }

    private TypeComposition getInt64ArrayComposition()
        {
        TypeComposition clz = INT64_ARRAY_CLZ;
        if (clz == null)
            {
            INT64_ARRAY_CLZ = clz = f_container.resolveClass(pool().ensureArrayType(pool().typeInt64()));
            }
        return clz;
        }

    private static TypeComposition INT8_ARRAY_CLZ;
    private static TypeComposition INT16_ARRAY_CLZ;
    private static TypeComposition INT64_ARRAY_CLZ;
    }