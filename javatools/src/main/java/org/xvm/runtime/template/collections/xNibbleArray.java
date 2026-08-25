package org.xvm.runtime.template.collections;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template._native.collections.arrays.xRTDelegate.DelegateHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTViewFromBit;
import org.xvm.runtime.template._native.collections.arrays.xRTViewToBit;


/**
 * Native NibbleArray<Bit> implementation.
 */
public class xNibbleArray
        extends BitBasedArray {
    public xNibbleArray(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void initNative() {
        ClassTemplate mixin = f_container.getTemplate("collections.arrays.NibbleArray");

        mixin.markNativeMethod("asBitArray", VOID, null);
        mixin.markNativeMethod("asByteArray", VOID, null);

        invalidateTypeInfo();
    }

    @Override
    public TypeConstant getCanonicalType() {
        return pool().ensureArrayType(pool().typeNibble());
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn) {
        switch (method.getName()) {
        case "asBitArray": {
            ArrayHandle hArray = (ArrayHandle) hTarget;

            Mutability     mutability = hArray.getMutability();
            DelegateHandle hDelegate  = xRTViewToBit.getInstance(frame.container())
                    .createBitViewDelegate(hArray.getDelegate(), mutability);

            return frame.assignValue(iReturn, new ArrayHandle(
                    xBitArray.getInstance(frame.container()).getCanonicalClass(),
                    hDelegate, mutability));
        }

        case "asByteArray": {
            ArrayHandle hArray   = (ArrayHandle) hTarget;
            long        cNibbles = hArray.getDelegate().m_cSize;
            if (cNibbles % 2 != 0) {
                return frame.raiseException(xException.outOfBounds(
                        frame, "Invalid array size: " + cNibbles));
            }

            Mutability     mutability = hArray.getMutability();
            DelegateHandle hDelegate  = xRTViewFromBit.getInstance(frame.container())
                    .createBitViewDelegate(frame.poolContext().typeByte(),
                            hArray.getDelegate(), mutability);

            return frame.assignValue(iReturn, new ArrayHandle(
                    xByteArray.getInstance(frame.container()).getCanonicalClass(),
                    hDelegate, mutability));
        }
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    public static xNibbleArray getInstance(Container container) {
        return NativeTemplates.get(container).nibbleArray();
    }
}
