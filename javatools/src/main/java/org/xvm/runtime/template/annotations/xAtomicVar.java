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

import org.xvm.runtime.template.numbers.xUncheckedInt16;
import org.xvm.runtime.template.numbers.xUncheckedInt32;
import org.xvm.runtime.template.numbers.xUncheckedInt64;
import org.xvm.runtime.template.numbers.xUncheckedInt8;
import org.xvm.runtime.template.numbers.xUncheckedUInt16;
import org.xvm.runtime.template.numbers.xUncheckedUInt32;
import org.xvm.runtime.template.numbers.xUncheckedUInt64;
import org.xvm.runtime.template.numbers.xUncheckedUInt8;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.numbers.xInt16;
import org.xvm.runtime.template.numbers.xInt32;
import org.xvm.runtime.template.numbers.xInt64;
import org.xvm.runtime.template.numbers.xInt8;
import org.xvm.runtime.template.numbers.xUInt16;
import org.xvm.runtime.template.numbers.xUInt32;
import org.xvm.runtime.template.numbers.xUInt64;
import org.xvm.runtime.template.numbers.xUInt8;

import org.xvm.runtime.template.reflect.xVar;


/**
 * Native implementation of AtomicVar.
 */
public class xAtomicVar
        extends xVar
    {
    public static xAtomicVar INSTANCE;

    public xAtomicVar(Container container, ClassStructure structure, boolean fInstance)
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
        markNativeMethod("exchange", null, null);
        markNativeMethod("replace", null, BOOLEAN);
        markNativeMethod("replaceFailed", null, null);

        ConstantPool                        pool         = f_container.getConstantPool();
        Map<TypeConstant, xAtomicIntNumber> mapTemplates = new HashMap<>();

        // checked
        mapTemplates.put(pool.typeCInt8(),   new xAtomicIntNumber(xInt8 .INSTANCE));
        mapTemplates.put(pool.typeCInt16(),  new xAtomicIntNumber(xInt16.INSTANCE));
        mapTemplates.put(pool.typeCInt32(),  new xAtomicIntNumber(xInt32.INSTANCE));
        mapTemplates.put(pool.typeCInt64(),  new xAtomicIntNumber(xInt64.INSTANCE));

        mapTemplates.put(pool.typeCUInt8(),  new xAtomicIntNumber(xUInt8.INSTANCE));
        mapTemplates.put(pool.typeCUInt16(), new xAtomicIntNumber(xUInt16.INSTANCE));
        mapTemplates.put(pool.typeCUInt32(), new xAtomicIntNumber(xUInt32.INSTANCE));
        mapTemplates.put(pool.typeCUInt64(), new xAtomicIntNumber(xUInt64.INSTANCE));

        // unchecked
        mapTemplates.put(pool.typeInt8(),   new xAtomicIntNumber(xUncheckedInt8.INSTANCE));
        mapTemplates.put(pool.typeInt16(),  new xAtomicIntNumber(xUncheckedInt16.INSTANCE));
        mapTemplates.put(pool.typeInt32(),  new xAtomicIntNumber(xUncheckedInt32.INSTANCE));
        mapTemplates.put(pool.typeInt64(),  new xAtomicIntNumber(xUncheckedInt64.INSTANCE));

        mapTemplates.put(pool.typeUInt8(),  new xAtomicIntNumber(xUncheckedUInt8.INSTANCE));
        mapTemplates.put(pool.typeUInt16(), new xAtomicIntNumber(xUncheckedUInt16.INSTANCE));
        mapTemplates.put(pool.typeUInt32(), new xAtomicIntNumber(xUncheckedUInt32.INSTANCE));
        mapTemplates.put(pool.typeUInt64(), new xAtomicIntNumber(xUncheckedUInt64.INSTANCE));

        NUMBER_TEMPLATES = mapTemplates;

        invalidateTypeInfo();
        }

    @Override
    public ClassTemplate getTemplate(TypeConstant type)
        {
        xAtomicIntNumber templateAtomicInt = NUMBER_TEMPLATES.get(type.getParamType(0));
        return templateAtomicInt == null ? this : templateAtomicInt;
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "exchange":
                {
                AtomicHandle hThis = (AtomicHandle) hTarget;

                return frame.assignValue(iReturn, hThis.f_atomic.getAndSet(hArg));
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
            case "replace":
                {
                AtomicHandle hThis = (AtomicHandle) hTarget;

                ObjectHandle hExpect = ahArg[0];
                ObjectHandle hNew = ahArg[1];
                AtomicReference<ObjectHandle> atomic = hThis.f_atomic;

                // conceptually, the logic looks like:
                //
                //    if (atomic.compareAndSet(hExpect, hNew))
                //       {
                //       return true;
                //       }
                //    TypeConstant type = hThis.f_clazz.getActualType("Referent");
                //
                //    ObjectHandle hCurrent;
                //    while (type.callEquals(hCurrent = atomic.get(), hExpect))
                //       {
                //       if (atomic.compareAndSet(hCurrent, hNew))
                //           {
                //           return true;
                //           }
                //       nExpect = hCurrent;
                //       }
                //    return false;

                if (atomic.compareAndSet(hExpect, hNew))
                    {
                    return frame.assignValue(iReturn, xBoolean.TRUE);
                    }

                TypeConstant type = hThis.getType().resolveGenericType("Referent");

                return new Replace(type, atomic, hExpect, hNew, iReturn).doNext(frame);
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "replaceFailed":
                {
                AtomicHandle hThis   = (AtomicHandle) hTarget;
                ObjectHandle hExpect = ahArg[0];
                ObjectHandle hNew    = ahArg[1];
                AtomicReference<ObjectHandle> atomic = hThis.f_atomic;

                // conceptually, the logic looks like:
                //
                //    if (atomic.compareAndSet(hExpect, hNew))
                //       {
                //       return false;
                //       }
                //    TypeConstant type = hThis.f_clazz.getActualType("Referent");
                //
                //    ObjectHandle hCurrent;
                //    while (type.callEquals(hCurrent = atomic.get(), hExpect))
                //       {
                //       if (atomic.compareAndSet(hCurrent, hNew))
                //           {
                //           return false;
                //           }
                //       nExpect = hCurrent;
                //       }
                //    return true, hExpect;

                if (atomic.compareAndSet(hExpect, hNew))
                    {
                    return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                    }

                TypeConstant type = hThis.getType().resolveGenericType("Referent");

                return new ReplaceFailed(type, atomic, hExpect, hNew, aiReturn).doNext(frame);
                }
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    @Override
    public RefHandle createRefHandle(Frame frame, TypeComposition clazz, String sName)
        {
        // native handle - no further initialization is required
        return new AtomicHandle(clazz.ensureAccess(Access.PUBLIC), sName, null);
        }

    @Override
    protected int getReferentImpl(Frame frame, RefHandle hTarget, boolean fNative, int iReturn)
        {
        AtomicHandle hAtomic = (AtomicHandle) hTarget;
        ObjectHandle hValue = hAtomic.f_atomic.get();
        return hValue == null
            ? frame.raiseException(xException.unassignedReference(frame))
            : frame.assignValue(iReturn, hValue);
        }

    @Override
    protected int setReferentImpl(Frame frame, RefHandle hTarget, boolean fNative, ObjectHandle hValue)
        {
        AtomicHandle hAtomic = (AtomicHandle) hTarget;
        hAtomic.f_atomic.set(hValue);
        return Op.R_NEXT;
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    public static class AtomicHandle
            extends RefHandle
        {
        protected final AtomicReference<ObjectHandle> f_atomic;

        protected AtomicHandle(TypeComposition clazz, String sName, ObjectHandle hValue)
            {
            super(clazz, sName);

            f_atomic = new AtomicReference<>();
            if (hValue != null)
                {
                f_atomic.set(hValue);
                }
            }

        @Override
        public boolean isAssigned(Frame frame)
            {
            return f_atomic.get() != null;
            }

        @Override
        public String toString()
            {
            return m_clazz + " -> " + f_atomic.get();
            }
        }

    /**
     * Helper class for replace() implementation.
     */
    protected static class Replace
            implements Frame.Continuation
        {
        private final TypeConstant type;
        private final        AtomicReference<ObjectHandle> atomic;
        private ObjectHandle hExpect;
        private final ObjectHandle hNew;
        private final int iReturn;

        public Replace(TypeConstant type, AtomicReference<ObjectHandle> atomic,
                       ObjectHandle hExpect, ObjectHandle hNew, int iReturn)
            {
            this.type    = type;
            this.atomic  = atomic;
            this.hExpect = hExpect;
            this.hNew    = hNew;
            this.iReturn = iReturn;
            }

        protected int doNext(Frame frameCaller)
            {
            while (true)
                {
                ObjectHandle hCurrent = atomic.get();

                switch (type.callEquals(frameCaller, hCurrent, hExpect, Op.A_STACK))
                    {
                    case Op.R_NEXT:
                        if (frameCaller.popStack() == xBoolean.FALSE)
                            {
                            return frameCaller.assignValue(iReturn, xBoolean.FALSE);
                            }

                        if (atomic.compareAndSet(hCurrent, hNew))
                            {
                            return frameCaller.assignValue(iReturn, xBoolean.TRUE);
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

        @Override
        public int proceed(Frame frameCaller)
            {
            if (frameCaller.popStack() == xBoolean.FALSE)
                {
                return frameCaller.assignValue(iReturn, xBoolean.FALSE);
                }

            if (atomic.compareAndSet(hExpect, hNew))
                {
                return frameCaller.assignValue(iReturn, xBoolean.TRUE);
                }

            return doNext(frameCaller);
            }
        }

    /**
     * Helper class for replaceFailed() implementation.
     */
    protected static class ReplaceFailed
            implements Frame.Continuation
        {
        private final TypeConstant                  type;
        private final AtomicReference<ObjectHandle> atomic;
        private       ObjectHandle                  hExpect;
        private final ObjectHandle                  hNew;
        private final int[]                         aiReturn;

        public ReplaceFailed(TypeConstant type, AtomicReference<ObjectHandle> atomic,
                             ObjectHandle hExpect, ObjectHandle hNew, int[] aiReturn)
            {
            this.type     = type;
            this.atomic   = atomic;
            this.hExpect  = hExpect;
            this.hNew     = hNew;
            this.aiReturn = aiReturn;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            if (frameCaller.popStack() == xBoolean.FALSE)
                {
                return frameCaller.assignValues(aiReturn, xBoolean.TRUE, hExpect);
                }

            if (atomic.compareAndSet(hExpect, hNew))
                {
                return frameCaller.assignValue(aiReturn[0], xBoolean.FALSE);
                }

            return doNext(frameCaller);
            }

        public int doNext(Frame frameCaller)
            {
            while (true)
                {
                ObjectHandle hCurrent = atomic.get();

                switch (type.callEquals(frameCaller, hCurrent, hExpect, Op.A_STACK))
                    {
                    case Op.R_NEXT:
                        if (frameCaller.popStack() == xBoolean.FALSE)
                            {
                            return frameCaller.assignValues(aiReturn, xBoolean.TRUE, hCurrent);
                            }

                        if (atomic.compareAndSet(hCurrent, hNew))
                            {
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


    // ----- data fields ---------------------------------------------------------------------------

    protected static Map<TypeConstant, xAtomicIntNumber> NUMBER_TEMPLATES;
    }