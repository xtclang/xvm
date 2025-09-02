package org.xvm.runtime.template._native.io;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xService;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;


/**
 * Base class for RawChannel implementations.
 */
public abstract class xRawChannel
        extends xService {
    public xRawChannel(Container container, ClassStructure structure) {
        super(container, structure, false);
    }

    @Override
    public void initNative() {
        markNativeMethod("allocate", BOOLEAN, null);
        markNativeMethod("incRefCount", BYTES, VOID);
        markNativeMethod("decRefCount", BYTES, VOID);

        invalidateTypeInfo();
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        ChannelHandle hChannel = (ChannelHandle) hTarget;
        switch (method.getName()) {
        case "allocate":
            return invokeAllocate(frame, hChannel, ((BooleanHandle) hArg).get(), iReturn);

        case "incRefCount":
            return invokeUpdateRefCount(frame, hChannel, (ArrayHandle) hArg, true);

        case "decRefCount":
            return invokeUpdateRefCount(frame, hChannel, (ArrayHandle) hArg, false);
        }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code Byte[]|Int allocate(Boolean internal)}.
     */
    protected int invokeAllocate(Frame frame, ChannelHandle hChannel, boolean fInternal, int iReturn) {
        // at the moment, there is nothing special about the byte array returned here;
        // it's *always* going to be wrapped by the native RTBuffer
        return frame.assignValue(iReturn, xArray.makeByteArrayHandle(
                new byte[hChannel.m_cPreferredBufferSize], Mutability.Mutable));
    }

    /**
     * Implementation for: {@code incRefCount(Byte[] buffer) and decRefCount(Byte[] buffer)}.
     */
    protected int invokeUpdateRefCount(Frame frame, ChannelHandle hChannel, ArrayHandle hBuffer,
                                       boolean fInc) {
        // TODO
        return Op.R_NEXT;
    }


    // ----- ObjectHandle --------------------------------------------------------------------------

    /**
     * The base handle class.
     */
    public static abstract class ChannelHandle
            extends ServiceHandle {
        public ChannelHandle(TypeComposition clazz, ServiceContext context) {
            super(clazz, context);
        }

        public void setPreferredBufferSize(int cb) {
            m_cPreferredBufferSize = cb;
        }
        protected int m_cPreferredBufferSize;
    }
}