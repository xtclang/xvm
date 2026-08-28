package org.xvm.runtime.template.collections;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.UInt8ArrayConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template._native.collections.arrays.ByteBasedDelegate.ByteArrayHandle;
import org.xvm.runtime.template._native.collections.arrays.ByteView;
import org.xvm.runtime.template._native.collections.arrays.xRTDelegate.DelegateHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTSlicingDelegate.SliceHandle;
import org.xvm.runtime.template._native.collections.arrays.xRTViewFromByte;

import org.xvm.util.Lazy;


/**
 * Native ByteArray implementation.
 */
public class xByteArray
        extends xArray {
    public xByteArray(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void initNative() {
        ClassTemplate mixin = f_container.getTemplate("collections.arrays.ByteArray");

        mixin.markNativeMethod("asByteArray", VOID, null);
        mixin.markNativeMethod("asInt8Array", VOID, null);
        mixin.markNativeMethod("asInt16Array", VOID, null);
        mixin.markNativeMethod("asInt64Array", VOID, null);
        mixin.markNativeMethod("asFloat64Array", VOID, null);

        invalidateTypeInfo();
    }

    @Override
    public TypeConstant getCanonicalType() {
        return pool().typeByteArray();
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        if (constant instanceof UInt8ArrayConstant constBytes) {
            // copy(): the handle owns its storage, and this constant is pool-interned - handing
            // the handle the constant's own array aliased shared metadata into the runtime
            return frame.pushStack(makeByteArrayHandle(
                    frame.container(), constBytes.getValue().copy(), Mutability.Constant));
        }

        return super.createConstHandle(frame, constant);
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn) {
        switch (method.getName()) {
        case "asByteArray": {
            ArrayHandle hArray = (ArrayHandle) hTarget;
            if (hArray.getDelegate() instanceof ByteArrayHandle) {
                return frame.assignValue(iReturn, hArray);
            }

            // TODO GG: we need a reifiable view (see the comments in ByteArray.x)
            return frame.raiseException(
                    xException.notImplemented(frame, "Not implemented"));
        }
        case "asInt8Array": {
            ArrayHandle    hArray     = (ArrayHandle) hTarget;
            Mutability     mutability = hArray.getMutability();
            DelegateHandle hView      = xRTViewFromByte.getInstance(frame.container())
                    .createByteView(frame.poolContext().typeInt8(),
                            hArray.getDelegate(), mutability, 1);
            return frame.assignValue(iReturn,
                    new ArrayHandle(getInt8ArrayComposition(), hView, mutability));
        }

        case "asInt16Array": {
            ArrayHandle hArray = (ArrayHandle) hTarget;
            if (hArray.getDelegate().m_cSize % 2 != 0) {
                return frame.raiseException(xException.illegalArgument(frame,
                            "Invalid array size: " + hArray.getDelegate().m_cSize));
            }

            Mutability     mutability = hArray.getMutability();
            DelegateHandle hView      = xRTViewFromByte.getInstance(frame.container())
                    .createByteView(frame.poolContext().typeInt16(),
                            hArray.getDelegate(), mutability, 2);
            return frame.assignValue(iReturn,
                    new ArrayHandle(getInt16ArrayComposition(), hView, mutability));
        }

        case "asInt64Array": {
            ArrayHandle hArray = (ArrayHandle) hTarget;
            if (hArray.getDelegate().m_cSize % 8 != 0) {
                return frame.raiseException(xException.illegalArgument(frame,
                            "Invalid array size: " + hArray.getDelegate().m_cSize));
            }

            Mutability     mutability = hArray.getMutability();
            DelegateHandle hView      = xRTViewFromByte.getInstance(frame.container())
                    .createByteView(frame.poolContext().typeInt64(),
                            hArray.getDelegate(), mutability, 8);
            return frame.assignValue(iReturn,
                    new ArrayHandle(getInt64ArrayComposition(), hView, mutability));
        }

        case "asFloat64Array": {
            ArrayHandle hArray = (ArrayHandle) hTarget;
            if (hArray.getDelegate().m_cSize % 8 != 0) {
                return frame.raiseException(xException.illegalArgument(frame,
                            "Invalid array size: " + hArray.getDelegate().m_cSize));
            }

            Mutability     mutability = hArray.getMutability();
            DelegateHandle hView      = xRTViewFromByte.getInstance(frame.container())
                    .createByteView(frame.poolContext().typeFloat64(),
                            hArray.getDelegate(), mutability, 8);
            return frame.assignValue(iReturn,
                    new ArrayHandle(getFloat64ArrayComposition(), hView, mutability));
        }
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    /**
     * Extract an array of bytes from the Array<Byte> handle.
     */
    public static byte[] getBytes(ArrayHandle hArray) {
        DelegateHandle hDelegate = hArray.getDelegate();
        long           cSize     = hDelegate.m_cSize;
        long           ofStart   = 0;
        boolean        fReverse  = false;

        if (hDelegate instanceof SliceHandle hSlice) {
            hDelegate = hSlice.f_hSource;
            ofStart   = hSlice.f_ofStart;
            fReverse  = hSlice.f_fReverse;
        }

        ClassTemplate tDelegate = hDelegate.getTemplate();
        if (tDelegate instanceof ByteView hView) {
            return hView.getBytes(hDelegate, ofStart, cSize, fReverse);
        }
        throw new UnsupportedOperationException("unsupported delegate: " + hDelegate);
    }

    /**
     * Copy bytes from the Array<Byte> handle.
     *
     * @param abVal   the byte array to copy bytes into
     * @param ofSrc   the offset in the byte array to copy from
     * @param hArray  the byte array to copy into
     * @param ofDst   the offset in the byte array to copy into
     * @param cSize   the number of bytes to copy
     */
    public static void setBytes(byte[] abVal, int ofSrc,
                                ArrayHandle hArray, int ofDst, int cSize) {
        DelegateHandle hDelegate = hArray.getDelegate();

        if (hDelegate instanceof SliceHandle hSlice) {
            hDelegate =  hSlice.f_hSource;
            ofDst     += (int) hSlice.f_ofStart;
        }

        ClassTemplate tDelegate = hDelegate.getTemplate();
        if (tDelegate instanceof ByteView tView) {
            // TODO: add an "assignBytes" method to the ByteView interface
            for (int i = 0; i < cSize; i++) {
                tView.assignByte(hDelegate, ofDst + i, abVal[ofSrc + i]);
            }
            hDelegate.m_cSize = ofDst + cSize;
            return;
        }
        throw new UnsupportedOperationException("unsupported delegate: " + hDelegate);
    }

    private TypeComposition getInt8ArrayComposition() {
        return f_clzInt8Array.get(this);
    }

    private TypeComposition getInt16ArrayComposition() {
        return f_clzInt16Array.get(this);
    }

    private TypeComposition getInt64ArrayComposition() {
        return f_clzInt64Array.get(this);
    }

    private TypeComposition getFloat64ArrayComposition() {
        return f_clzFloat64Array.get(this);
    }

    public static xByteArray getInstance(Container container) {
        return NativeTemplates.get(container).byteArray();
    }

    private final Lazy.Bound<xByteArray, TypeComposition> f_clzInt8Array = Lazy.ofBound(owner ->
            owner.container().resolveClass(owner.pool().ensureArrayType(owner.pool().typeInt8())));

    private final Lazy.Bound<xByteArray, TypeComposition> f_clzInt16Array = Lazy.ofBound(owner ->
            owner.container().resolveClass(owner.pool().ensureArrayType(owner.pool().typeInt16())));

    private final Lazy.Bound<xByteArray, TypeComposition> f_clzInt64Array = Lazy.ofBound(owner ->
            owner.container().resolveClass(owner.pool().ensureArrayType(owner.pool().typeInt64())));

    private final Lazy.Bound<xByteArray, TypeComposition> f_clzFloat64Array = Lazy.ofBound(owner ->
            owner.container().resolveClass(owner.pool().ensureArrayType(owner.pool().typeFloat64())));
}
