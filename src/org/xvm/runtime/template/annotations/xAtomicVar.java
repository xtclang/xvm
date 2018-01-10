package org.xvm.runtime.template.annotations;


import java.util.concurrent.atomic.AtomicReference;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xVar;


/**
 * TODO:
 */
public class xAtomicVar
        extends xVar
    {
    public xAtomicVar(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initDeclared()
        {
        markNativeMethod("replace", new String[]{"RefType", "RefType"});
        markNativeMethod("replaceFailed", new String[]{"RefType", "RefType"}, new String[] {"RefType"});
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        AtomicHandle hThis = (AtomicHandle) hTarget;

        switch (ahArg.length)
            {
            case 2:
                switch (method.getName())
                    {
                    case "replace":
                        {
                        ObjectHandle hExpect = ahArg[0];
                        ObjectHandle hNew = ahArg[1];
                        AtomicReference<ObjectHandle> atomic = hThis.m_atomic;

                        // conceptually, the logic looks like:
                        //
                        //    if (atomic.compareAndSet(hExpect, hNew))
                        //       {
                        //       return true;
                        //       }
                        //    TypeConstant type = hThis.f_clazz.getActualType("RefType");
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

                        TypeConstant type = hThis.getType().getActualParamType("RefType");

                        return new Replace(type, atomic, hExpect, hNew, iReturn).doNext(frame);
                        }
                    }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        AtomicHandle hThis = (AtomicHandle) hTarget;

        switch (ahArg.length)
            {
            case 2:
                switch (method.getName())
                    {
                    case "replaceFailed":
                        {
                        ObjectHandle hExpect = ahArg[0];
                        ObjectHandle hNew = ahArg[1];
                        AtomicReference<ObjectHandle> atomic = hThis.m_atomic;

                        // conceptually, the logic looks like:
                        //
                        //    if (atomic.compareAndSet(hExpect, hNew))
                        //       {
                        //       return false;
                        //       }
                        //    TypeConstant type = hThis.f_clazz.getActualType("RefType");
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

                        TypeConstant type = hThis.getType().getActualParamType("RefType");

                        return new ReplaceFailed(type, atomic, hExpect, hNew, aiReturn).doNext(frame);
                        }
                    }
            }
        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    @Override
    public RefHandle createRefHandle(TypeComposition clazz, String sName)
        {
        return new AtomicHandle(clazz, sName, null);
        }

    public static class AtomicHandle
            extends RefHandle
        {
        protected AtomicReference<ObjectHandle> m_atomic = new AtomicReference<>();

        protected AtomicHandle(TypeComposition clazz, String sName, ObjectHandle hValue)
            {
            super(clazz, sName);

            if (hValue != null)
                {
                m_atomic.set(hValue);
                }
            }

        @Override
        public boolean isAssigned()
            {
            return m_atomic != null;
            }

        @Override
        protected ObjectHandle getInternal()
                throws ExceptionHandle.WrapperException
            {
            ObjectHandle hValue = m_atomic.get();
            if (hValue == null)
                {
                throw xException.makeHandle("Unassigned reference").getException();
                }
            return hValue;
            }

        @Override
        protected ExceptionHandle setInternal(ObjectHandle handle)
            {
            m_atomic.set(handle);
            return null;
            }

        @Override
        public String toString()
            {
            return f_clazz + " -> " + m_atomic.get();
            }
        }

    /**
     * Helper class for replace() implementation.
     */
    protected static class Replace
            implements Frame.Continuation
        {
        private final TypeConstant type;
        private final AtomicReference<ObjectHandle> atomic;
        private ObjectHandle hExpect;
        private final ObjectHandle hNew;
        private final int iReturn;

        public Replace(TypeConstant type, AtomicReference<ObjectHandle> atomic,
                       ObjectHandle hExpect, ObjectHandle hNew, int iReturn)
            {
            this.type = type;
            this.atomic = atomic;
            this.hExpect = hExpect;
            this.hNew = hNew;
            this.iReturn = iReturn;
            }

        protected int doNext(Frame frameCaller)
            {
            while (true)
                {
                ObjectHandle hCurrent = atomic.get();

                switch (type.callEquals(frameCaller, hCurrent, hExpect, Frame.RET_LOCAL))
                    {
                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    case Op.R_NEXT:
                        if (frameCaller.getFrameLocal() == xBoolean.FALSE)
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
                        frameCaller.m_frameNext.setContinuation(this);
                        hExpect = hCurrent;
                        return Op.R_CALL;

                    default:
                        throw new IllegalStateException();
                    }
                }
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            if (frameCaller.getFrameLocal() == xBoolean.FALSE)
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
        private final TypeConstant type;
        private final AtomicReference<ObjectHandle> atomic;
        private ObjectHandle hExpect;
        private final ObjectHandle hNew;
        private final int[] aiReturn;

        public ReplaceFailed(TypeConstant type, AtomicReference<ObjectHandle> atomic,
                             ObjectHandle hExpect, ObjectHandle hNew, int[] aiReturn)
            {
            this.type = type;
            this.atomic = atomic;
            this.hExpect = hExpect;
            this.hNew = hNew;
            this.aiReturn = aiReturn;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            if (frameCaller.getFrameLocal() == xBoolean.FALSE)
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

                switch (type.callEquals(frameCaller, hCurrent, hExpect, Frame.RET_LOCAL))
                    {
                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    case Op.R_NEXT:
                        if (frameCaller.getFrameLocal() == xBoolean.FALSE)
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
                        frameCaller.m_frameNext.setContinuation(this);
                        hExpect = hCurrent;
                        return Op.R_CALL;

                    default:
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }
