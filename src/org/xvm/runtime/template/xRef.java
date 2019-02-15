package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.NativeRebaseConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;
import org.xvm.runtime.VarSupport;


/**
 * TODO:
 */
public class xRef
        extends ClassTemplate
        implements VarSupport
    {
    public static xRef INSTANCE;
    public static ClassConstant INCEPTION_CLASS;

    public xRef(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            INCEPTION_CLASS = new NativeRebaseConstant(
                (ClassConstant) structure.getIdentityConstant());
            }
        }

    @Override
    public void initDeclared()
        {
        }

    @Override
    protected ClassConstant getInceptionClassConstant()
        {
        return INCEPTION_CLASS;
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName,
                               ObjectHandle hTarget, int iReturn)
        {
        RefHandle hRef = (RefHandle) hTarget;

        switch (sPropName)
            {
            case "ActualType":
                switch (get(frame, hRef, Op.A_STACK))
                    {
                    case Op.R_NEXT:
                        return frame.assignValue(iReturn,
                            frame.popStack().getType().getTypeHandle());

                    case Op.R_CALL:
                        frame.setContinuation(frameCaller ->
                            frameCaller.assignValue(iReturn,
                                frameCaller.popStack().getType().getTypeHandle()));
                        return Op.R_CALL;

                    case Op.R_BLOCK:
                        return frame.raiseException(xException.makeHandle("Unassigned reference"));

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }

            case "assigned":
                return frame.assignValue(iReturn, xBoolean.makeHandle(hRef.isAssigned()));

            case "refName":
                String sName = hRef.getName();
                return frame.assignValue(iReturn, sName == null ?
                    xNullable.NULL : xString.makeHandle(sName));

            case "byteLength":
                // TODO: deferred
                return frame.assignValue(iReturn, xInt64.makeHandle(0));

            case "selfContained":
                return frame.assignValue(iReturn, xBoolean.makeHandle(hRef.isSelfContained()));

            case "service_":
                switch (get(frame, hRef, Op.A_STACK))
                    {
                    case Op.R_NEXT:
                        return frame.assignValue(iReturn,
                            xBoolean.makeHandle(frame.popStack().getComposition().isService()));

                    case Op.R_CALL:
                        frame.setContinuation(frameCaller ->
                            frameCaller.assignValue(iReturn,
                                xBoolean.makeHandle(frame.popStack().getComposition().isService())));
                        return Op.R_CALL;

                    case Op.R_BLOCK:
                        return frame.raiseException(xException.makeHandle("Unassigned reference"));

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }

            case "const_":
                switch (get(frame, hRef, Op.A_STACK))
                    {
                    case Op.R_NEXT:
                        return frame.assignValue(iReturn,
                            xBoolean.makeHandle(frame.popStack().getComposition().isConst()));

                    case Op.R_CALL:
                        frame.setContinuation(frameCaller ->
                            frameCaller.assignValue(iReturn,
                                xBoolean.makeHandle(frame.popStack().getComposition().isConst())));
                        return Op.R_CALL;

                    case Op.R_BLOCK:
                        return frame.raiseException(xException.makeHandle("Unassigned reference"));

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }

            case "immutable_":
                switch (get(frame, hRef, Op.A_STACK))
                    {
                    case Op.R_NEXT:
                        return frame.assignValue(iReturn,
                            xBoolean.makeHandle(!frame.popStack().isMutable()));

                    case Op.R_CALL:
                        frame.setContinuation(frameCaller ->
                            frameCaller.assignValue(iReturn,
                                xBoolean.makeHandle(!frame.popStack().isMutable())));
                        return Op.R_CALL;

                    case Op.R_BLOCK:
                        return frame.raiseException(xException.makeHandle("Unassigned reference"));

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
            }
        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        RefHandle hRef = (RefHandle) hTarget;

        switch (ahArg.length)
            {
            case 0:
                switch (method.getName())
                    {
                    case "get":
                        return get(frame, hRef, iReturn);
                    }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        RefHandle hRef = (RefHandle) hTarget;

        switch (method.getName())
            {
            case "peek":
                if (hRef.isAssigned())
                    {
                    switch (get(frame, hRef, Op.A_STACK))
                        {
                        case Op.R_NEXT:
                            return frame.assignValues(aiReturn, xBoolean.TRUE, frame.popStack());

                        case Op.R_CALL:
                            frame.setContinuation(frameCaller ->
                                frame.assignValues(aiReturn, xBoolean.TRUE, frame.popStack()));
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

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        RefHandle hRef1 = (RefHandle) hValue1;
        RefHandle hRef2 = (RefHandle) hValue2;

        return new CompareReferents(hRef1, hRef2, this, iReturn).doNext(frame);
        }


    // ----- VarSupport implementation -------------------------------------------------------------

    @Override
    public int get(Frame frame, RefHandle hTarget, int iReturn)
        {
        switch (hTarget.m_iVar)
            {
            case RefHandle.REF_REFERENT:
                return getInternal(frame, hTarget, iReturn);

            case RefHandle.REF_REF:
                {
                RefHandle hDelegate = (RefHandle) hTarget.getValue();
                return hTarget.getVarSupport().get(frame, hDelegate, iReturn);
                }

            case RefHandle.REF_PROPERTY:
                {
                ObjectHandle hDelegate = hTarget.getValue();
                return hDelegate.getTemplate().getPropertyValue(
                    frame, hDelegate, hTarget.getName(), iReturn);
                }

            case RefHandle.REF_ARRAY:
                {
                IndexedRefHandle hIndexedRef = (IndexedRefHandle) hTarget;
                ObjectHandle hArray = hTarget.getValue();
                IndexSupport template = (IndexSupport) hArray.getTemplate();

                return template.extractArrayValue(frame, hArray, hIndexedRef.f_lIndex, iReturn);
                }

            default:
                {
                Frame frameRef = hTarget.m_frame;
                int   nVar     = hTarget.m_iVar;
                assert frameRef != null && nVar >= 0;

                ObjectHandle hValue = frameRef.f_ahVar[nVar];
                return hValue == null
                    ? frame.raiseException(xException.unassignedReference())
                    : frame.assignValue(iReturn, hValue);
                }
            }
        }

    protected int getInternal(Frame frame, RefHandle hRef, int iReturn)
        {
        ObjectHandle hValue = hRef.getValue();
        return hValue == null
            ? frame.raiseException(xException.unassignedReference())
            : frame.assignValue(iReturn, hValue);
        }

    @Override
    public int invokeVarPreInc(Frame frame, RefHandle hTarget, int iReturn)
        {
        return readOnly(frame);
        }

    @Override
    public int invokeVarPostInc(Frame frame, RefHandle hTarget, int iReturn)
        {
        return readOnly(frame);
        }

    @Override
    public int invokeVarPreDec(Frame frame, RefHandle hTarget, int iReturn)
        {
        return readOnly(frame);
        }

    @Override
    public int invokeVarPostDec(Frame frame, RefHandle hTarget, int iReturn)
        {
        return readOnly(frame);
        }

    @Override
    public int set(Frame frame, RefHandle hTarget, ObjectHandle hValue)
        {
        return readOnly(frame);
        }

    @Override
    public int invokeVarAdd(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        return readOnly(frame);
        }

    @Override
    public int invokeVarSub(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        return readOnly(frame);
        }

    @Override
    public int invokeVarMul(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        return readOnly(frame);
        }

    @Override
    public int invokeVarDiv(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        return readOnly(frame);
        }

    @Override
    public int invokeVarMod(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        return readOnly(frame);
        }

    @Override
    public int invokeVarShl(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        return readOnly(frame);
        }

    @Override
    public int invokeVarShr(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        return readOnly(frame);
        }

    @Override
    public int invokeVarShrAll(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        return readOnly(frame);
        }

    @Override
    public int invokeVarAnd(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        return readOnly(frame);
        }

    @Override
    public int invokeVarOr(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        return readOnly(frame);
        }

    @Override
    public int invokeVarXor(Frame frame, RefHandle hTarget, ObjectHandle hArg)
        {
        return readOnly(frame);
        }

    protected int readOnly(Frame frame)
        {
        return frame.raiseException(xException.makeHandle("Ref cannot be assigned"));
        }

    // ----- handle class -----

    public static class RefHandle
            extends GenericHandle
        {
        protected ObjectHandle m_hDelegate; // can point to another Ref for the same referent
        protected String m_sName;
        protected Frame m_frame;
        protected int m_iVar;
        protected boolean m_fInit;

        // indicates that the m_hDelegate field holds a referent
        protected static final int REF_REFERENT = -1;

        // indicates that the m_hDelegate field holds a Ref that this Ref is "chained" to
        protected static final int REF_REF = -2;

        // indicates that the m_hDelegate field holds a property target
        protected static final int REF_PROPERTY = -3;

        // indicates that the m_hDelegate field holds an array target
        protected static final int REF_ARRAY = -4;

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
                m_frame = frame;
                m_iVar = iVar;
                }
            else
                {
                // there is already a Ref pointing to that register;
                // simply link to it
                m_iVar = REF_REF;
                m_hDelegate = refCurrent;
                }
            }

        /**
         * Ensure the RefHandle fields are initialized (only necessary for stateful Ref/Var mixins).
         *
         * @param frame  the current frame
         *
         * @return R_NEXT, R_CALL or R_EXCEPTION
         */
        public int ensureInitialized(Frame frame)
            {
            if (m_fInit)
                {
                return Op.R_NEXT;
                }

            m_fInit = true;

            MethodStructure methodInit = getComposition().ensureAutoInitializer();
            if (methodInit.isAbstract())
                {
                return Op.R_NEXT;
                }

            // strictly speaking, we need to pass a "struct" clone
            //      this.ensureAccess(Access.STRUCT);
            // but since the auto-initializer is auto-generated, we can skip that expense
            Frame frameID = frame.createFrame1(methodInit, this, Utils.OBJECTS_NONE, Op.A_IGNORE);

            frameID.setContinuation(frameCaller ->
                this.validateFields() ?
                    Op.R_NEXT : frameCaller.raiseException(xException.unassignedFields()));

            return frame.call(frame.ensureInitialized(methodInit, frameID));
            }

        public ObjectHandle getValue()
            {
            return m_hDelegate;
            }

        public void setValue(ObjectHandle hDelegate)
            {
            m_hDelegate = hDelegate;
            }

        public String getName()
            {
            String sName = m_sName;
            if (sName == null && m_iVar >= 0)
                {
                m_sName = sName = m_frame.f_aInfo[m_iVar].getName();
                }
            return sName;
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

                default: // assertion m_frame != null && m_iVar >= 0
                    return m_frame.f_ahVar[m_iVar] != null;
                }
            }

        public boolean isSelfContained()
            {
            return m_hDelegate == null || m_hDelegate.isSelfContained();
            }

        // dereference the Ref from a register-bound to a handle-bound
        public void dereference()
            {
            assert m_frame != null && m_iVar >= 0;

            m_hDelegate = m_frame.f_ahVar[m_iVar];
            m_frame = null;
            m_iVar = REF_REFERENT;
            }

        @Override
        public String toString()
            {
            String s = super.toString();
            switch (m_iVar)
                {
                case REF_REFERENT:
                case REF_ARRAY:
                    return m_hDelegate == null ? s : s + m_hDelegate;

                case REF_REF:
                    return s + "--> " + m_hDelegate;

                case REF_PROPERTY:
                    return s + "-> " + m_hDelegate.getComposition() + "#" + m_sName;

                default:
                    return s + "-> #" + m_iVar;
                }
            }
        }

    /***
     * RefHandle for an indexed element of an Array or a Tuple.
     */
    public static class IndexedRefHandle
            extends RefHandle
        {
        protected final long f_lIndex;

        public IndexedRefHandle(TypeComposition clazz, ObjectHandle hTarget, long lIndex)
            {
            super(clazz, null);

            m_iVar = REF_ARRAY;
            m_hDelegate = hTarget;
            f_lIndex = lIndex;
            }

        @Override
        public void dereference()
            {
            // no op
            }
        }

    /**
     * Helper class for calculating the referent equality.
     */
    static protected class CompareReferents
            implements Frame.Continuation
        {
        public CompareReferents(RefHandle hRef1, RefHandle hRef2, xRef template, int iReturn)
            {
            this.hRef1 = hRef1;
            this.hRef2 = hRef2;
            this.template = template;
            this.iReturn = iReturn;
            }

        @Override
        public int proceed(Frame frameCaller)
            {
            updateResult(frameCaller);

            return doNext(frameCaller);
            }

        protected void updateResult(Frame frameCaller)
            {
            if (index == 0)
                {
                hReferent1 = frameCaller.popStack();
                }
            else
                {
                hReferent2 = frameCaller.popStack();
                }
            }

        public int doNext(Frame frameCaller)
            {
            while (++index < 2)
                {
                RefHandle hRef = index == 0 ? hRef1 : hRef2;

                switch (template.get(frameCaller, hRef, Op.A_STACK))
                    {
                    case Op.R_NEXT:
                        updateResult(frameCaller);
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.setContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    default:
                        throw new IllegalStateException();
                    }
                }

            ClassTemplate template = hReferent1.getTemplate();
            boolean fEquals = template == hReferent2.getTemplate()
                && template.compareIdentity(hReferent1, hReferent2);

            return frameCaller.assignValue(iReturn, xBoolean.makeHandle(fEquals));
            }

        private final RefHandle hRef1;
        private final RefHandle hRef2;
        private final xRef template;
        private final int iReturn;

        private ObjectHandle hReferent1;
        private ObjectHandle hReferent2;
        private int index = -1;
        }
    }
