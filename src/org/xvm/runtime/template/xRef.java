package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.VarSupport;


/**
 * TODO:
 */
public class xRef
        extends ClassTemplate
    {
    public static xRef INSTANCE;

    public xRef(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        markNativeGetter("assigned");
        markNativeMethod("get", VOID, new String[]{"RefType"});
        markNativeGetter("name");
        markNativeGetter("selfContained");

        // extends Referent
        markNativeGetter("ActualType");
        markNativeGetter("service_");
        markNativeGetter("const_");
        markNativeGetter("immutable_");
        }

    @Override
    public int invokeNativeGet(Frame frame, PropertyStructure property, ObjectHandle hTarget, int iReturn)
        {
        RefHandle hThis = (RefHandle) hTarget;

        switch (property.getName())
            {
            case "ActualType":
                switch (hThis.get(frame, Frame.RET_LOCAL))
                    {
                    case Op.R_NEXT:
                        return frame.assignValue(iReturn,
                            frame.getFrameLocal().getType().getTypeHandle());

                    case Op.R_CALL:
                        frame.setContinuation(frameCaller ->
                            frameCaller.assignValue(iReturn,
                                frameCaller.getFrameLocal().getType().getTypeHandle()));
                        return Op.R_CALL;

                    case Op.R_BLOCK:
                        return frame.raiseException(xException.makeHandle("Unassigned reference"));

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }

            case "assigned":
                return frame.assignValue(iReturn, xBoolean.makeHandle(hThis.isAssigned()));

            case "name":
                String sName = hThis.m_sName;
                return frame.assignValue(iReturn, sName == null ?
                    xNullable.NULL : xString.makeHandle(sName));

            case "byteLength":
                // TODO: deferred
                return frame.assignValue(iReturn, xInt64.makeHandle(0));

            case "selfContained":
                return frame.assignValue(iReturn, xBoolean.makeHandle(hThis.isSelfContained()));

            case "service_":
                switch (hThis.get(frame, Frame.RET_LOCAL))
                    {
                    case Op.R_NEXT:
                        return frame.assignValue(iReturn,
                            xBoolean.makeHandle(frame.getFrameLocal().getComposition().isService()));

                    case Op.R_CALL:
                        frame.setContinuation(frameCaller ->
                            frameCaller.assignValue(iReturn,
                                xBoolean.makeHandle(frame.getFrameLocal().getComposition().isService())));
                        return Op.R_CALL;

                    case Op.R_BLOCK:
                        return frame.raiseException(xException.makeHandle("Unassigned reference"));

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }

            case "const_":
                switch (hThis.get(frame, Frame.RET_LOCAL))
                    {
                    case Op.R_NEXT:
                        return frame.assignValue(iReturn,
                            xBoolean.makeHandle(frame.getFrameLocal().getComposition().isConst()));

                    case Op.R_CALL:
                        frame.setContinuation(frameCaller ->
                            frameCaller.assignValue(iReturn,
                                xBoolean.makeHandle(frame.getFrameLocal().getComposition().isConst())));
                        return Op.R_CALL;

                    case Op.R_BLOCK:
                        return frame.raiseException(xException.makeHandle("Unassigned reference"));

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }

            case "immutable_":
                switch (hThis.get(frame, Frame.RET_LOCAL))
                    {
                    case Op.R_NEXT:
                        return frame.assignValue(iReturn,
                            xBoolean.makeHandle(!frame.getFrameLocal().isMutable()));

                    case Op.R_CALL:
                        frame.setContinuation(frameCaller ->
                            frameCaller.assignValue(iReturn,
                                xBoolean.makeHandle(!frame.getFrameLocal().isMutable())));
                        return Op.R_CALL;

                    case Op.R_BLOCK:
                        return frame.raiseException(xException.makeHandle("Unassigned reference"));

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
            }
        return super.invokeNativeGet(frame, property, hTarget, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        RefHandle hThis = (RefHandle) hTarget;

        switch (ahArg.length)
            {
            case 0:
                switch (method.getName())
                    {
                    case "get":
                        return hThis.get(frame, iReturn);
                    }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        RefHandle hThis = (RefHandle) hTarget;

        switch (method.getName())
            {
            case "peek":
                if (hThis.isAssigned())
                    {
                    switch (hThis.get(frame, Frame.RET_LOCAL))
                        {
                        case Op.R_NEXT:
                            return frame.assignValues(aiReturn, xBoolean.TRUE, frame.getFrameLocal());

                        case Op.R_CALL:
                            frame.setContinuation(frameCaller ->
                                frame.assignValues(aiReturn, xBoolean.TRUE, frame.getFrameLocal()));
                            return Op.R_CALL;

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }
                    }
                 else
                    {
                    return frame.assignValue(aiReturn[0], xBoolean.FALSE);
                    }
            }
        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    @Override
    public RefHandle createRefHandle(TypeComposition clazz, String sName)
        {
        return new RefHandle(clazz, sName);
        }

    // a simple reference handle
    public static class RefHandle
            extends ObjectHandle
        {
        protected String m_sName;
        protected Frame m_frame;
        protected int m_iVar;

        protected ObjectHandle m_hDelegate; // can point to another Ref for the same referent

        // indicates that the m_hDelegate field holds a referent
        private static final int REF_REFERENT = -1;

        // indicates that the m_hDelegate field holds a Ref that this Ref is "chained" to
        private static final int REF_REF = -2;

        // indicates that the m_hDelegate field holds a property target
        private static final int REF_PROPERTY = -3;

        /**
         * Create an unassigned RefHandle for a given clazz.
         *
         * @param clazz  the class of the Ref (e.g. FutureRef<String>)
         * @param sName  optional name
         */
        protected RefHandle(TypeComposition clazz, String sName)
            {
            super(clazz);

            m_sName = sName;
            m_fMutable = true;
            m_iVar = REF_REFERENT;
            }

        /**
         * Create a RefHandle for a given property.
         *
         * @param clazz      the class of the Ref (e.g. FutureRef<String>)
         * @param hTarget    the target object
         * @param sPropName  the property name
         */
        public RefHandle(TypeComposition clazz, ObjectHandle hTarget, String sPropName)
            {
            super(clazz);

            assert hTarget != null;

            m_hDelegate = hTarget;
            m_sName = sPropName;
            m_fMutable = hTarget.isMutable();
            m_iVar = REF_PROPERTY;
            }

        /**
         * Create a RefHandle for a frame register.
         *
         * @param clazz  the class of the Ref
         * @param frame  the frame
         * @param iVar   the register index
         */
        public RefHandle(TypeComposition clazz, Frame frame, int iVar)
            {
            super(clazz);

            m_fMutable = true;

            assert iVar >= 0;

            Frame.VarInfo infoSrc = frame.getVarInfo(iVar);

            RefHandle refCurrent = infoSrc.getRef();
            if (refCurrent == null)
                {
                infoSrc.setRef(this);
                }
            else
                {
                // there is already a Ref pointing to that register;
                // simply link to it
                iVar = REF_REF;
                frame = null;
                m_hDelegate = refCurrent;
                }

            m_frame = frame;
            m_iVar = iVar;
            }

        public VarSupport getVarSupport()
            {
            return (VarSupport) getOpSupport();
            }

        public boolean isAssigned()
            {
            switch (m_iVar)
                {
                case REF_REFERENT:
                case REF_PROPERTY:
                    return m_hDelegate != null;

                case REF_REF:
                    return ((RefHandle) m_hDelegate).isAssigned();

                default: // assertion m_iVar >= 0
                    return m_frame.f_ahVar[m_iVar] != null;
                }
            }

        public boolean isSelfContained()
            {
            return m_hDelegate == null || m_hDelegate.isSelfContained();
            }

        public int get(Frame frame, int iReturn)
            {
            switch (m_iVar)
                {
                case REF_REFERENT:
                    return getInternal(frame, iReturn);

                case REF_REF:
                    return ((RefHandle) m_hDelegate).get(frame, iReturn);

                case REF_PROPERTY:
                    return m_hDelegate.getTemplate().getPropertyValue(
                        frame, m_hDelegate, m_sName, iReturn);

                default: // assertion m_iVar >= 0
                    return frame.assignValue(iReturn, m_frame.f_ahVar[m_iVar]);
                }
            }

        protected int getInternal(Frame frame, int iReturn)
            {
            return m_hDelegate == null
                ? frame.raiseException(xException.makeHandle("Unassigned reference"))
                : frame.assignValue(iReturn, m_hDelegate);
            }

        public int set(Frame frame, ObjectHandle handle)
            {
            switch (m_iVar)
                {
                case REF_REFERENT:
                    return setInternal(frame, handle);

                case REF_REF:
                    return ((RefHandle) m_hDelegate).set(frame, handle);

                case REF_PROPERTY:
                    return m_hDelegate.getTemplate().setPropertyValue(
                        frame, m_hDelegate, m_sName, handle);

                default: // assertion m_iVar >= 0
                    m_frame.f_ahVar[m_iVar] = handle;
                    return Op.R_NEXT;
                }
            }

        protected int setInternal(Frame frame, ObjectHandle handle)
            {
            m_hDelegate = handle;
            return Op.R_NEXT;
            }

        // dereference the Ref from a register-bound to a handle-bound
        public void dereference()
            {
            assert m_iVar >= 0;

            m_hDelegate = m_frame.f_ahVar[m_iVar];
            m_iVar = REF_REFERENT;
            m_frame = null;
            }

        @Override
        public String toString()
            {
            String s = super.toString();
            switch (m_iVar)
                {
                case REF_REFERENT:
                    return m_hDelegate == null ? s : s + m_hDelegate;

                case REF_REF:
                    return s + "--> " + m_hDelegate;

                case REF_PROPERTY:
                    return s + "-> " + m_hDelegate.getComposition() + "#" + m_sName;

                default:
                    return s + "-> " + m_frame.f_ahVar[m_iVar];
                }
            }
        }

    // Ref handle for an indexed element
    public static class IndexedRefHandle
            extends RefHandle
        {
        protected final ObjectHandle f_hTarget;
        protected final long f_lIndex;

        public IndexedRefHandle(TypeComposition clazz, ObjectHandle hTarget, long lIndex)
            {
            super(clazz, null);

            f_hTarget = hTarget;
            f_lIndex = lIndex;
            }

        @Override
        protected int getInternal(Frame frame, int iReturn)
            {
            try
                {
                return frame.assignValue(iReturn,
                    ((IndexSupport) f_hTarget.getOpSupport()).extractArrayValue(f_hTarget, f_lIndex));
                }
            catch (ExceptionHandle.WrapperException e)
                {
                return frame.raiseException(e);
                }
            }

        @Override
        protected int setInternal(Frame frame, ObjectHandle handle)
            {
            ExceptionHandle hException = ((IndexSupport) f_hTarget.getOpSupport()).
                    assignArrayValue(f_hTarget, f_lIndex, handle);
            return hException == null ? Op.R_NEXT : frame.raiseException(hException);
            }

        @Override
        public void dereference()
            {
            // no op
            }
        }
    }
