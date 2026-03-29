package org.xvm.runtime.template._native.io;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xByteArray;

/**
 * Native RTBuffer implementation.
 */
public class xRTBuffer
        extends ClassTemplate {
    public static xRTBuffer INSTANCE;

    public xRTBuffer(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public void initNative() {
        markNativeMethod("copyRawBytes", null, VOID);

        PROP_RAW_BYTES = getStructure().findPropertyDeep("rawBytes").getIdentityConstant();
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn) {
        RTBufferHandle hBuf = (RTBufferHandle) hTarget;

        switch (method.getName()) {
        case "copyRawBytes":
            return invokeCopyRawBytes(frame, hBuf, ahArg);
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    @Override
    public ObjectHandle createStruct(Frame frame, TypeComposition clazz) {
        assert clazz.getTemplate() == this;

        return new RTBufferHandle(clazz.ensureAccess(Constants.Access.STRUCT));
    }

    /**
     * Implementation for:
     *  {@code void copyRawBytes(Int srcOffset, Byte[] bytes, Int dstOffset, Int count)}.
     */
    protected int invokeCopyRawBytes(Frame frame, RTBufferHandle hBuf, ObjectHandle[] ahArg) {
        long        ofSrc  = ((JavaLong) ahArg[0]).getValue();
        ArrayHandle hBytes = (ArrayHandle) ahArg[1];
        long        ofDst  = ((JavaLong) ahArg[2]).getValue();
        long        count  = ((JavaLong) ahArg[3]).getValue();

        ArrayHandle hRawBytes = (ArrayHandle) hBuf.getField(frame, PROP_RAW_BYTES);
        byte[]      abRaw     = xByteArray.getBytes(hRawBytes);

        xByteArray.setBytes(abRaw, (int) ofSrc, hBytes, (int) ofDst, (int) count);
        return Op.R_NEXT;
    }


    // ----- ObjectHandle --------------------------------------------------------------------------

    /**
     * Native RTBuffer handle. It's allowed to cross the service boundaries.
     */
    protected static class RTBufferHandle
            extends GenericHandle {
        public RTBufferHandle(TypeComposition clazz) {
            super(clazz);
        }

        @Override
        public boolean isPassThrough(Container container) {
            return true;
        }
    }

    private static PropertyConstant PROP_RAW_BYTES;
}