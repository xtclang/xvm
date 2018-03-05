package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;

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
        implements VarSupport
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
        RefHandle hRef = (RefHandle) hTarget;

        switch (property.getName())
            {
            case "ActualType":
                switch (get(frame, hRef, Frame.RET_LOCAL))
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
                return frame.assignValue(iReturn, xBoolean.makeHandle(hRef.isAssigned(frame)));

            case "name":
                String sName = hRef.m_sName;
                return frame.assignValue(iReturn, sName == null ?
                    xNullable.NULL : xString.makeHandle(sName));

            case "byteLength":
                // TODO: deferred
                return frame.assignValue(iReturn, xInt64.makeHandle(0));

            case "selfContained":
                return frame.assignValue(iReturn, xBoolean.makeHandle(hRef.isSelfContained()));

            case "service_":
                switch (get(frame, hRef, Frame.RET_LOCAL))
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
                switch (get(frame, hRef, Frame.RET_LOCAL))
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
                switch (get(frame, hRef, Frame.RET_LOCAL))
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
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        RefHandle hRef = (RefHandle) hTarget;

        switch (method.getName())
            {
            case "peek":
                if (hRef.isAssigned(frame))
                    {
                    switch (get(frame, hRef, Frame.RET_LOCAL))
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
                RefHandle hDelegate = (RefHandle) hTarget.m_hDelegate;
                return hDelegate.getVarSupport().get(frame, hDelegate, iReturn);
                }

            case RefHandle.REF_PROPERTY:
                return hTarget.m_hDelegate.getTemplate().getPropertyValue(
                    frame, hTarget.m_hDelegate, hTarget.m_sName, iReturn);

            case RefHandle.REF_ARRAY:
                {
                IndexedRefHandle hIndexedRef = (IndexedRefHandle) hTarget;
                ObjectHandle hArray = hTarget.m_hDelegate;
                IndexSupport template = (IndexSupport) hArray.getTemplate();

                return template.extractArrayValue(frame, hArray, hIndexedRef.f_lIndex, iReturn);
                }

            default:
                assert hTarget.m_iVar >= 0;
                return frame.assignValue(iReturn, frame.f_ahVar[hTarget.m_iVar]);
            }
        }

    protected int getInternal(Frame frame, RefHandle hRef, int iReturn)
        {
        ObjectHandle hValue = hRef.m_hDelegate;
        return hValue == null
            ? frame.raiseException(xException.makeHandle("Unassigned reference"))
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

    protected int readOnly(Frame frame)
        {
        return frame.raiseException(xException.makeHandle("Ref cannot be assigned"));
        }

    // ----- handle class -----

    public static class RefHandle
            extends ObjectHandle
        {
        public String m_sName;
        public ObjectHandle m_hDelegate; // can point to another Ref for the same referent

        protected int m_iVar;

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
                }
            else
                {
                // there is already a Ref pointing to that register;
                // simply link to it
                iVar = REF_REF;
                m_hDelegate = refCurrent;
                }

            m_iVar = iVar;
            }

        public VarSupport getVarSupport()
            {
            return (VarSupport) getOpSupport();
            }

        public boolean isAssigned(Frame frame)
            {
            switch (m_iVar)
                {
                case REF_REFERENT:
                case REF_PROPERTY:
                    return m_hDelegate != null;

                case REF_REF:
                    return ((RefHandle) m_hDelegate).isAssigned(frame);

                default: // assertion m_iVar >= 0
                    return frame.f_ahVar[m_iVar] != null;
                }
            }

        public boolean isSelfContained()
            {
            return m_hDelegate == null || m_hDelegate.isSelfContained();
            }

        // dereference the Ref from a register-bound to a handle-bound
        public void dereference(Frame frame)
            {
            assert m_iVar >= 0;

            m_hDelegate = frame.f_ahVar[m_iVar];
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

    // Ref handle for an indexed element of an Array or Tuple
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
        public void dereference(Frame frame)
            {
            // no op
            }
        }
    }
