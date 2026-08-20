package org.xvm.runtime.template.annotations;


import java.util.HashMap;
import java.util.Map;

import java.util.concurrent.atomic.AtomicReference;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.numbers.*;

import org.xvm.runtime.template.reflect.xVar;

import org.xvm.util.Lazy;


/**
 * Native implementation of Atomic.
 */
public class xAtomic
        extends xVar {
    public xAtomic(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);
    }

    @Override
    public void initNative() {
        markNativeMethod("exchange", null, null);
        markNativeMethod("replaceFailed", null, null);

        invalidateTypeInfo();
    }

    @Override
    public ClassTemplate getTemplate(TypeConstant type) {
        ClassTemplate templateAtomicInt = f_numberTemplates.get().get(type.getParamType(0));
        return templateAtomicInt == null ? this : templateAtomicInt;
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        switch (method.getName()) {
        case "exchange": {
            AtomicHandle hThis = (AtomicHandle) hTarget;

            return frame.assignValue(iReturn, hThis.f_atomic.getAndSet(hArg));
        }
        }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn) {
        switch (method.getName()) {
        case "replaceFailed": {
            AtomicHandle hThis   = (AtomicHandle) hTarget;
            ObjectHandle hExpect = ahArg[0];
            ObjectHandle hNew    = ahArg[1];

            AtomicReference<ObjectHandle> atomic = hThis.f_atomic;

            // conceptually, the logic looks like:
            //
            //    if (atomic.compareAndSet(hExpect, hNew))
            //       {
            //       return false;
            //   }
            //    TypeConstant type = hThis.f_clazz.getActualType("Referent");
            //
            //    ObjectHandle hCurrent;
            //    while (type.callEquals(hCurrent = atomic.get(), hExpect))
            //       {
            //       if (atomic.compareAndSet(hCurrent, hNew))
            //           {
            //           return false;
            //       }
            //       nExpect = hCurrent;
            //   }
            //    return true, hExpect;

            if (atomic.compareAndSet(hExpect, hNew)) {
                return frame.assignValue(aiReturn[0], xBoolean.FALSE);
            }

            TypeConstant type = hThis.getType().resolveGenericType("Referent");

            return new ReplaceFailed(type, atomic, hExpect, hNew, aiReturn).doNext(frame);
        }
        }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
    }

    @Override
    public RefHandle createRefHandle(Frame frame, TypeComposition clazz, String sName) {
        // native handle - no further initialization is required
        return new AtomicHandle(clazz.ensureAccess(Access.PUBLIC), sName, null);
    }

    @Override
    protected int getReferentImpl(Frame frame, RefHandle hTarget, boolean fNative, int iReturn) {
        AtomicHandle hAtomic = (AtomicHandle) hTarget;
        ObjectHandle hValue  = hAtomic.f_atomic.get();
        return hValue == null
                ? frame.raiseException(xException.unassignedReference(frame))
                : frame.assignValue(iReturn, hValue);
    }

    @Override
    protected int setReferentImpl(Frame frame, RefHandle hTarget, boolean fNative, ObjectHandle hValue) {
        AtomicHandle hAtomic = (AtomicHandle) hTarget;
        hAtomic.f_atomic.set(hValue);
        return Op.R_NEXT;
    }


    // ----- ObjectHandle --------------------------------------------------------------------------

    public static class AtomicHandle
            extends RefHandle {
        protected final AtomicReference<ObjectHandle> f_atomic;

        protected AtomicHandle(TypeComposition clazz, String sName, ObjectHandle hValue) {
            super(clazz, sName);

            f_atomic = new AtomicReference<>();
            if (hValue != null) {
                f_atomic.set(hValue);
            }
        }

        @Override
        public boolean isAssigned() {
            return f_atomic.get() != null;
        }

        @Override
        public String toString() {
            return m_clazz + " -> " + f_atomic.get();
        }
    }

    /**
     * Helper class for replaceFailed() implementation.
     */
    protected static class ReplaceFailed
            implements Frame.Continuation {
        private final TypeConstant                  type;
        private final AtomicReference<ObjectHandle> atomic;
        private       ObjectHandle                  hExpect;
        private final ObjectHandle                  hNew;
        private final int[]                         aiReturn;

        public ReplaceFailed(TypeConstant type, AtomicReference<ObjectHandle> atomic,
                             ObjectHandle hExpect, ObjectHandle hNew, int[] aiReturn) {
            this.type     = type;
            this.atomic   = atomic;
            this.hExpect  = hExpect;
            this.hNew     = hNew;
            this.aiReturn = aiReturn;
        }

        @Override
        public int proceed(Frame frameCaller) {
            if (frameCaller.popStack() == xBoolean.FALSE) {
                return frameCaller.assignValues(aiReturn, xBoolean.TRUE, hExpect);
            }

            if (atomic.compareAndSet(hExpect, hNew)) {
                return frameCaller.assignValue(aiReturn[0], xBoolean.FALSE);
            }

            return doNext(frameCaller);
        }

        public int doNext(Frame frameCaller) {
            while (true) {
                ObjectHandle hCurrent = atomic.get();

                switch (type.callEquals(frameCaller, hCurrent, hExpect, Op.A_STACK)) {
                case Op.R_NEXT:
                    if (frameCaller.popStack() == xBoolean.FALSE) {
                        return frameCaller.assignValues(aiReturn, xBoolean.TRUE, hCurrent);
                    }

                    if (atomic.compareAndSet(hCurrent, hNew)) {
                        return frameCaller.assignValue(aiReturn[0], xBoolean.FALSE);
                    }
                    hExpect = hCurrent;
                    break;

                case Op.R_CALL:
                    frameCaller.m_frameNext.addContinuation(this);
                    hExpect = hCurrent;
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }
        }
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Owner-scoped replacement for the old NUMBER_TEMPLATES static map. The
     * keys and referent templates are derived from this container's pool and
     * template registry, so a JVM-global map would cross container ownership.
     */
    private final Lazy<Map<TypeConstant, xAtomic>> f_numberTemplates =
            Lazy.of(this::createNumberTemplates);

    private Map<TypeConstant, xAtomic> createNumberTemplates() {
        ConstantPool       pool              = f_container.getConstantPool();
        xAtomicIntNumber   templateAtomicInt = f_container.getTemplate(
                "annotations.AtomicIntNumber", xAtomicIntNumber.class);
        Map<TypeConstant, xAtomic> mapTemplates = new HashMap<>();

        mapTemplates.put(pool.typeInt128(),  new xAtomicInt128(
                numberTemplate(pool.typeInt128(), xInt128.class), templateAtomicInt));
        mapTemplates.put(pool.typeUInt128(), new xAtomicInt128(
                numberTemplate(pool.typeUInt128(), xUInt128.class), templateAtomicInt));

        mapTemplates.put(pool.typeInt8(),    new xAtomicIntNumber(
                numberTemplate(pool.typeInt8(), xInt8.class), templateAtomicInt));
        mapTemplates.put(pool.typeInt16(),   new xAtomicIntNumber(
                numberTemplate(pool.typeInt16(), xInt16.class), templateAtomicInt));
        mapTemplates.put(pool.typeInt32(),   new xAtomicIntNumber(
                numberTemplate(pool.typeInt32(), xInt32.class), templateAtomicInt));
        mapTemplates.put(pool.typeInt64(),   new xAtomicIntNumber(
                numberTemplate(pool.typeInt64(), xInt64.class), templateAtomicInt));

        mapTemplates.put(pool.typeNibble(),  new xAtomicIntNumber(
                numberTemplate(pool.typeNibble(), xNibble.class), templateAtomicInt));
        mapTemplates.put(pool.typeUInt8(),   new xAtomicIntNumber(
                numberTemplate(pool.typeUInt8(), xUInt8.class), templateAtomicInt));
        mapTemplates.put(pool.typeUInt16(),  new xAtomicIntNumber(
                numberTemplate(pool.typeUInt16(), xUInt16.class), templateAtomicInt));
        mapTemplates.put(pool.typeUInt32(),  new xAtomicIntNumber(
                numberTemplate(pool.typeUInt32(), xUInt32.class), templateAtomicInt));
        mapTemplates.put(pool.typeUInt64(),  new xAtomicIntNumber(
                numberTemplate(pool.typeUInt64(), xUInt64.class), templateAtomicInt));

        return Map.copyOf(mapTemplates);
    }

    private <T extends ClassTemplate> T numberTemplate(TypeConstant type, Class<T> clz) {
        return f_container.getTemplate(type, clz);
    }
}
