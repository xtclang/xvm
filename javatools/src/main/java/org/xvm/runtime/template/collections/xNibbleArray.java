package org.xvm.runtime.template.collections;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template._native.collections.arrays.xRTDelegate.DelegateHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTViewFromBitToByte;
import org.xvm.runtime.template._native.collections.arrays.xRTViewToBitFromNibble;


/**
 * Native NibbleArray<Bit> implementation.
 */
public class xNibbleArray
        extends BitBasedArray
    {
    public static xNibbleArray INSTANCE;

    public xNibbleArray(Container container, ClassStructure structure, boolean fInstance)
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
        ClassTemplate mixin = f_container.getTemplate("collections.arrays.NibbleArray");

        mixin.markNativeMethod("asBitArray", VOID, null);
        mixin.markNativeMethod("asByteArray", VOID, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public TypeConstant getCanonicalType()
        {
        return pool().ensureArrayType(pool().typeNibble());
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "asBitArray":
                {
                ArrayHandle hArray = (ArrayHandle) hTarget;

                Mutability     mutability = hArray.m_mutability;
                DelegateHandle hDelegate  = xRTViewToBitFromNibble.INSTANCE.createBitViewDelegate(
                        hArray.m_hDelegate, mutability);

                return frame.assignValue(iReturn, new ArrayHandle(
                        xBitArray.INSTANCE.getCanonicalClass(), hDelegate, mutability));
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
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }
    }