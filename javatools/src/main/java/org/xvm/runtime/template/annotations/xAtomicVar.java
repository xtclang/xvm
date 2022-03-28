package org.xvm.runtime.template.annotations;


import java.util.concurrent.atomic.AtomicReference;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.reflect.xVar;


/**
 * Native implementation of AtomicVar.
 */
public class xAtomicVar
        extends xVar
    {
    public static xAtomicVar INSTANCE;

    public xAtomicVar(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        markNativeMethod("exchange", null, null);
        markNativeMethod("replace", null, BOOLEAN);
        markNativeMethod("replaceFailed", null, null);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public ClassTemplate getTemplate(TypeConstant type)
        {
        // if Referent is Int64, then the template should be AtomicIntNumber
        return type.getParamType(0) == pool().typeCInt64() // REVIEW GG checked vs. unchecked
            ? xAtomicIntNumber.INSTANCE
            : this;
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
    }