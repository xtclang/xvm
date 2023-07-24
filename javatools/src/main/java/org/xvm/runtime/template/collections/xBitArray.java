package org.xvm.runtime.template.collections;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xEnum;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.numbers.xBit;

import org.xvm.runtime.template._native.collections.arrays.BitView;
import org.xvm.runtime.template._native.collections.arrays.xRTBitDelegate;
import org.xvm.runtime.template._native.collections.arrays.xRTDelegate.DelegateHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTSlicingDelegate.SliceHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTViewFromBitToBoolean;
import org.xvm.runtime.template._native.collections.arrays.xRTViewFromBitToByte;
import org.xvm.runtime.template._native.collections.arrays.xRTViewFromBitToNibble;


/**
 * Native BitArray<Bit> implementation.
 */
public class xBitArray
        extends BitBasedArray
    {
    public static xBitArray INSTANCE;

    public xBitArray(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        ClassTemplate mixin = f_container.getTemplate("collections.arrays.BitArray");

        mixin.markNativeProperty("Zero");
        mixin.markNativeProperty("One");

        mixin.markNativeMethod("asBooleanArray", VOID, null);
        mixin.markNativeMethod("asByteArray"   , VOID, null);
        mixin.markNativeMethod("asNibbleArray" , VOID, null);
        mixin.markNativeMethod("toByteArray"   , null, null);

        invalidateTypeInfo();
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        return pool().typeBitArray();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "Zero":
                return frame.assignValue(iReturn, xBit.ZERO);

            case "One":
                return frame.assignValue(iReturn, xBit.ONE);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "toByteArray":
                {
                ArrayHandle hArray = (ArrayHandle) hTarget;
                long        cBits  = hArray.m_hDelegate.m_cSize;

                if (cBits % 8 != 0)
                    {
                    return frame.raiseException(
                        xException.illegalArgument(frame, "Invalid array size: " + cBits));
                    }

                Mutability mutability = hArg == ObjectHandle.DEFAULT
                        ? Mutability.Constant
                        : Mutability.values()[((xEnum.EnumHandle) hArg).getOrdinal()];

                byte[] aBits = getBits(hArray);

                return frame.assignValue(iReturn, xArray.makeByteArrayHandle(aBits, mutability));
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
            case "asBooleanArray":
                {
                ArrayHandle hArray = (ArrayHandle) hTarget;
                long        cBits  = hArray.m_hDelegate.m_cSize;
                if (cBits % 8 != 0)
                    {
                    return frame.raiseException(xException.outOfBounds(
                            frame, "Invalid array size: " + cBits));
                    }

                Mutability     mutability = hArray.m_mutability;
                DelegateHandle hDelegate  = xRTViewFromBitToBoolean.INSTANCE.createBitViewDelegate(
                        hArray.m_hDelegate, mutability);

                return frame.assignValue(iReturn, new ArrayHandle(
                        xArray.getBooleanArrayComposition(), hDelegate, mutability));
                }

            case "asByteArray":
                {
                ArrayHandle hArray = (ArrayHandle) hTarget;
                long        cBits  = hArray.m_hDelegate.m_cSize;
                if (cBits % 8 != 0)
                    {
                    return frame.raiseException(xException.outOfBounds(
                            frame, "Invalid array size: " + cBits));
                    }

                Mutability     mutability = hArray.m_mutability;
                DelegateHandle hDelegate  = xRTViewFromBitToByte.INSTANCE.createBitViewDelegate(
                        hArray.m_hDelegate, mutability);

                return frame.assignValue(iReturn, new ArrayHandle(
                        xByteArray.INSTANCE.getCanonicalClass(), hDelegate, mutability));
                }

            case "asNibbleArray":
                {
                ArrayHandle hArray = (ArrayHandle) hTarget;
                long        cBits  = hArray.m_hDelegate.m_cSize;
                if (cBits % 4 != 0)
                    {
                    return frame.raiseException(xException.outOfBounds(
                            frame, "Invalid array size: " + cBits));
                    }

                Mutability     mutability = hArray.m_mutability;
                DelegateHandle hDelegate  = xRTViewFromBitToNibble.INSTANCE.createBitViewDelegate(
                        hArray.m_hDelegate, mutability);

                return frame.assignValue(iReturn, new ArrayHandle(
                        xNibbleArray.INSTANCE.getCanonicalClass(), hDelegate, mutability));
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    /**
     * Extract an array of bits from the ArrayDelegate<Bit> handle.
     */
    public static byte[] getBits(ArrayHandle hArray)
        {
        DelegateHandle hDelegate = hArray.m_hDelegate;

        long    cSize    = hDelegate.m_cSize;
        long    ofStart  = 0;
        boolean fReverse = false;

        if (hDelegate instanceof SliceHandle hSlice)
            {
            hDelegate = hSlice.f_hSource;
            ofStart   = hSlice.f_ofStart;
            fReverse  = hSlice.f_fReverse;
            }

        ClassTemplate tDelegate = hDelegate.getTemplate();
        if (tDelegate instanceof BitView tView)
            {
            return tView.getBits(hDelegate, ofStart, cSize, fReverse);
            }
        throw new UnsupportedOperationException();
        }

    /**
     * Copy bits from the specified array.
     */
    public static void setBits(ArrayHandle hArray, byte[] abVal, long cSize)
        {
        DelegateHandle hDelegate = hArray.m_hDelegate;

        long ofStart = 0;

        if (hDelegate instanceof SliceHandle hSlice)
            {
            hDelegate = hSlice.f_hSource;
            ofStart   = hSlice.f_ofStart;
            }

        ClassTemplate tDelegate = hDelegate.getTemplate();
        if (tDelegate instanceof BitView tView)
            {
            for (long i = 0; i < cSize; i++)
                {
                tView.assignBit(hDelegate, ofStart + i, xRTBitDelegate.getBit(abVal, i));
                }
            return;
            }
        throw new UnsupportedOperationException();
        }
    }