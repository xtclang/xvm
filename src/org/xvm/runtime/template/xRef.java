package org.xvm.runtime.template;


import java.util.List;

import java.util.function.ToIntFunction;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Contribution;
import org.xvm.asm.Component.Composition;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.NativeRebaseConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;
import org.xvm.runtime.VarSupport;

import org.xvm.runtime.template.annotations.xFutureVar.FutureHandle;
import org.xvm.runtime.template.xClass.ClassHandle;
import org.xvm.runtime.template.xType.TypeHandle;


/**
 * Native Ref implementation.
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
        markNativeMethod("equals", null, BOOLEAN);

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    protected ClassConstant getInceptionClassConstant()
        {
        return this == INSTANCE ? INCEPTION_CLASS : getClassConstant();
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName,
                               ObjectHandle hTarget, int iReturn)
        {
        RefHandle hRef = (RefHandle) hTarget;

        switch (sPropName)
            {
            case "actualType":
                return actOnReferent(frame, hRef,
                    h -> frame.assignValue(iReturn, h.getType().getTypeHandle()));

            case "assigned":
                return frame.assignValue(iReturn, xBoolean.makeHandle(hRef.isAssigned()));

            case "refName":
                {
                String sName = hRef.getName();
                return frame.assignValue(iReturn, sName == null ?
                    xNullable.NULL : xString.makeHandle(sName));
                }

            case "byteLength":
                // TODO: deferred
                return frame.assignValue(iReturn, xInt64.makeHandle(0));

            case "selfContained":
                return frame.assignValue(iReturn, xBoolean.makeHandle(hRef.isSelfContained()));

            case "isService":
                return actOnReferent(frame, hRef,
                    h -> frame.assignValue(iReturn,
                        xBoolean.makeHandle(h.getTemplate().isService())));

            case "isConst":
                return actOnReferent(frame, hRef,
                    h -> frame.assignValue(iReturn,
                        xBoolean.makeHandle(h.getComposition().isConst())));

            case "isImmutable":
                return actOnReferent(frame, hRef,
                    h -> frame.assignValue(iReturn,
                        xBoolean.makeHandle(!h.isMutable())));
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        RefHandle hRef = (RefHandle) hTarget;

        switch (method.getName())
            {
            case "extends_":
                return actOnReferent(frame, hRef,
                    h -> frame.assignValue(iReturn,
                        xBoolean.makeHandle(extends_(h, (ClassHandle) hArg))));

            case "implements_":
                return actOnReferent(frame, hRef,
                    h -> frame.assignValue(iReturn,
                        xBoolean.makeHandle(implements_(h, (ClassHandle) hArg))));

            case "incorporates_":
                return actOnReferent(frame, hRef,
                    h -> frame.assignValue(iReturn,
                        xBoolean.makeHandle(incorporates_(h, (ClassHandle) hArg))));

            case "instanceOf":
                return actOnReferent(frame, hRef,
                    h -> frame.assignValue(iReturn,
                        xBoolean.makeHandle(instanceOf(h, (TypeHandle) hArg))));

            case "maskAs":
                return actOnReferent(frame, hRef,
                    h -> maskAs(frame, h, (TypeHandle) hArg, iReturn));
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        RefHandle hRef = (RefHandle) hTarget;

        switch (method.getName())
            {
            case "get":
                return getReferent(frame, hRef, iReturn);

            case "equals":
                {
                RefHandle hRef1 = (RefHandle) ahArg[1];
                RefHandle hRef2 = (RefHandle) ahArg[2];
                return new CompareReferents(hRef1, hRef2, this, iReturn).doNext(frame);
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
                return hRef.isAssigned()
                    ? actOnReferent(frame, hRef,
                        h -> frame.assignValues(aiReturn, xBoolean.TRUE, h))
                    : frame.assignValue(aiReturn[0], xBoolean.FALSE);

            case "revealAs":
                return actOnReferent(frame, hRef,
                    h -> revealAs(frame, h, (TypeHandle) ahArg[0], aiReturn));
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    @Override
    public RefHandle createRefHandle(TypeComposition clazz, String sName)
        {
        return new RefHandle(clazz, sName);
        }

    @Override
    public int callEquals(Frame frame, ClassComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        RefHandle hRef1 = (RefHandle) hValue1;
        RefHandle hRef2 = (RefHandle) hValue2;

        // From Ref.x:
        // Reference equality is used to determine if two references are referring to the same referent
        // _identity_. Specifically, two references are equal iff they reference the same runtime
        // object, or the two objects that they reference are both immutable and structurally identical.
        return new CompareReferents(hRef1, hRef2, this, iReturn).doNext(frame);
        }


    // ----- VarSupport implementation -------------------------------------------------------------

    @Override
    public int getReferent(Frame frame, RefHandle hTarget, int iReturn)
        {
        switch (hTarget.m_iVar)
            {
            case RefHandle.REF_REFERENT:
                return getInternal(frame, hTarget, iReturn);

            case RefHandle.REF_REF:
                {
                RefHandle hReferent = (RefHandle) hTarget.getReferent();
                return hTarget.getVarSupport().getReferent(frame, hReferent, iReturn);
                }

            case RefHandle.REF_PROPERTY:
                {
                ObjectHandle hReferent = hTarget.getReferent();
                return hReferent.getTemplate().getPropertyValue(
                    frame, hReferent, hTarget.getPropertyId(), iReturn);
                }

            case RefHandle.REF_ARRAY:
                {
                IndexedRefHandle hIndexedRef = (IndexedRefHandle) hTarget;
                ObjectHandle     hArray      = hTarget.getReferent();
                IndexSupport     template    = (IndexSupport) hArray.getTemplate();

                return template.extractArrayValue(frame, hArray, hIndexedRef.f_lIndex, iReturn);
                }

            default:
                {
                Frame frameRef = hTarget.m_frame;
                int   nVar     = hTarget.m_iVar;
                assert frameRef != null && nVar >= 0;

                ObjectHandle hValue = frameRef.f_ahVar[nVar];
                return hValue == null
                    ? frame.raiseException(xException.unassignedReference(frame))
                    : frame.assignValue(iReturn, hValue);
                }
            }
        }

    /**
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL} or {@link Op#R_EXCEPTION}
     */
    protected int getInternal(Frame frame, RefHandle hRef, int iReturn)
        {
        ObjectHandle hValue = hRef.getReferent();
        return hValue == null
            ? frame.raiseException(xException.unassignedReference(frame))
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
    public int setReferent(Frame frame, RefHandle hTarget, ObjectHandle hValue)
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


    // ----- helper methods ------------------------------------------------------------------------

    protected int readOnly(Frame frame)
        {
        return frame.raiseException("Ref cannot be assigned");
        }

    /**
     * Get the referent and apply the specified action.
     *
     * @param frame   the current frame
     * @param hRef    the target RefHandle
     * @param action  the action
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION}
     */
    protected int actOnReferent(Frame frame, RefHandle hRef, ToIntFunction<ObjectHandle> action)
        {
        switch (getReferent(frame, hRef, Op.A_STACK))
            {
            case Op.R_NEXT:
                return action.applyAsInt(frame.popStack());

            case Op.R_CALL:
                frame.addContinuation(frameCaller ->
                    action.applyAsInt(frameCaller.popStack()));
                return Op.R_CALL;

            case Op.R_BLOCK:
                return frame.raiseException(xException.unassignedReference(frame));

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Mask the specified target as a wider type.
     *
     * @param frame    the current frame
     * @param hTarget  the target object
     * @param hType    the type handle to mask as
     * @param iReturn  the register to return the result into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION}
     */
    protected int maskAs(Frame frame, ObjectHandle hTarget, TypeHandle hType, int iReturn)
        {
        if (hTarget instanceof GenericHandle)
            {
            ObjectHandle hMasked =
                ((GenericHandle) hTarget).maskAs(frame, hType.getDataType());

            return hMasked == null
                ? frame.raiseException(xException.illegalCast(frame, hType.getDataType().getValueString()))
                : frame.assignValue(iReturn, hMasked);
            }
        else
            {
            return frame.raiseException(xException.unsupportedOperation(frame, "maskAs"));
            }
        }

    /**
     * Reveal the specified target as a narrower type.
     *
     * @param frame     the current frame
     * @param hTarget   the target object
     * @param hType     the type handle to reveal as
     * @param aiReturn  the register to return the conditional result into
     *
     * @return one of the {@link Op#R_NEXT}, {@link Op#R_CALL}, {@link Op#R_EXCEPTION}
     */
    protected int revealAs(Frame frame, ObjectHandle hTarget, TypeHandle hType, int[] aiReturn)
        {
        if (hTarget instanceof GenericHandle)
            {
            ObjectHandle hRevealed =
                ((GenericHandle) hTarget).revealAs(frame, hType.getDataType());

            return hRevealed == null
                ? frame.assignValue(aiReturn[0], xBoolean.FALSE)
                : frame.assignValues(aiReturn, xBoolean.TRUE, hRevealed);
            }
        else
            {
            return frame.raiseException(xException.unsupportedOperation(frame, "revealAs"));
            }
        }

    /**
     * @return true iff the specified target implements the specified class
     */
    protected boolean implements_(ObjectHandle hTarget, ClassHandle hClass)
        {
        return hTarget.getType().isA(hClass.getPublicType());
        }

    /**
     * @return true iff the specified target extends the specified class
     */
    protected boolean extends_(ObjectHandle hTarget, ClassHandle hClass)
        {
        TypeConstant typeTarget  = hTarget.getType();
        TypeConstant typeExtends = hClass.getPublicType();

        if (typeTarget .isExplicitClassIdentity(true) && typeTarget .isSingleUnderlyingClass(false) &&
            typeExtends.isExplicitClassIdentity(true) && typeExtends.isSingleUnderlyingClass(false))
            {
            ClassStructure clzTarget = (ClassStructure)
                    typeTarget.getSingleUnderlyingClass(false).getComponent();
            return clzTarget.extendsClass(typeExtends.getSingleUnderlyingClass(false));
            }
        return false;
        }

    /**
     * @return true iff the specified target is of the specified class
     */
    protected boolean incorporates_(ObjectHandle hTarget, ClassHandle hClass)
        {
        TypeConstant typeTarget  = hTarget.getType();
        TypeConstant typeExtends = hClass.getPublicType();

        if (typeTarget .isExplicitClassIdentity(true) && typeTarget .isSingleUnderlyingClass(false) &&
            typeExtends.isExplicitClassIdentity(true) && typeExtends.isSingleUnderlyingClass(false))
            {
            ClassStructure clzTarget = (ClassStructure)
                    typeTarget.getSingleUnderlyingClass(false).getComponent();
            Contribution contrib = clzTarget.findContribution(
                    typeExtends.getSingleUnderlyingClass(false));
            return contrib != null && contrib.getComposition() == Composition.Incorporates;
            }
        return false;
        }

    /**
     * @return true iff the specified target is of the specified type
     */
    protected boolean instanceOf(ObjectHandle hTarget, TypeHandle hType)
        {
        return hTarget.getType().isA(hType.getDataType());
        }


    // ----- handle class -----

    public static class RefHandle
            extends GenericHandle
        {
        /**
         * Create an unassigned RefHandle for a given clazz.
         *
         * @param clazz  the class of the Ref (e.g. FutureRef<String>)
         * @param sName  optional name
         */
        protected RefHandle(TypeComposition clazz, String sName)
            {
            super(clazz);

            m_sName    = sName;
            m_fMutable = true;
            m_iVar     = REF_REFERENT;
            }

        /**
         * Create a RefHandle for a given property.
         *
         * @param clazz    the class of the Ref (e.g. FutureRef<String>)
         * @param hTarget  the target object
         * @param idProp   the property id
         */
        public RefHandle(TypeComposition clazz, ObjectHandle hTarget, PropertyConstant idProp)
            {
            super(clazz);

            assert hTarget != null;

            m_hReferent = hTarget;
            m_idProp    = idProp;
            m_sName     = idProp.getNestedIdentity().toString();
            m_fMutable  = hTarget.isMutable();
            m_iVar      = REF_PROPERTY;
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
                m_iVar  = iVar;
                }
            else
                {
                // there is already a Ref pointing to that register;
                // simply link to it
                m_iVar      = REF_REF;
                m_hReferent = refCurrent;
                }
            }

        /**
         * Ensure the RefHandle fields are initialized (only necessary for stateful Ref/Var mixins).
         *
         * @param frame  the current frame
         *
         * @return R_NEXT, R_CALL or R_EXCEPTION
         */
        public int initializeCustomFields(Frame frame)
            {
            MethodStructure methodInit = getComposition().ensureAutoInitializer();
            if (methodInit == null)
                {
                return Op.R_NEXT;
                }

            Frame frameID = frame.createFrame1(methodInit,
                    this.ensureAccess(Access.STRUCT), Utils.OBJECTS_NONE, Op.A_IGNORE);

            frameID.addContinuation(frameCaller ->
                {
                List<String> listUnassigned;
                return (listUnassigned = this.validateFields()) == null
                    ? Op.R_NEXT
                    : frameCaller.raiseException(xException.unassignedFields(frame, listUnassigned));
                });
            return frame.callInitialized(frameID);
            }

        public ObjectHandle getReferent()
            {
            return m_hReferent;
            }

        public void setReferent(ObjectHandle hReferent)
            {
            m_hReferent = hReferent;
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

        public PropertyConstant getPropertyId()
            {
            return m_idProp;
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
                    return m_hReferent != null;

                case REF_PROPERTY:
                    {
                    GenericHandle hTarget = (GenericHandle) m_hReferent;
                    ObjectHandle  hValue  = hTarget.getField(m_idProp);
                    if (hValue == null)
                        {
                        return false;
                        }
                    if (hTarget.isInflated(m_idProp))
                        {
                        return ((RefHandle) hValue).isAssigned();
                        }
                    return true;
                    }
                case REF_REF:
                    return ((RefHandle) m_hReferent).isAssigned();

                default: // assertion m_frame != null && m_iVar >= 0
                    return m_frame.f_ahVar[m_iVar] != null;
                }
            }

        public boolean isSelfContained()
            {
            return m_hReferent == null || m_hReferent.isSelfContained();
            }

        // dereference the Ref from a register-bound to a handle-bound
        public void dereference()
            {
            assert m_frame != null && m_iVar >= 0;

            m_hReferent = m_frame.f_ahVar[m_iVar];
            m_frame     = null;
            m_iVar      = REF_REFERENT;
            }

        @Override
        public String toString()
            {
            String s = super.toString();
            switch (m_iVar)
                {
                case REF_REFERENT:
                case REF_ARRAY:
                    return m_hReferent == null ? s : s + m_hReferent;

                case REF_REF:
                    return s + "--> " + m_hReferent;

                case REF_PROPERTY:
                    return s + "-> " + m_hReferent.getComposition() + "#" + m_sName;

                default:
                    return s + "-> #" + m_iVar;
                }
            }

        protected ObjectHandle     m_hReferent; // can point to another Ref for the same referent
        protected PropertyConstant m_idProp;
        protected String           m_sName;
        protected Frame            m_frame;
        protected int              m_iVar;     // non-negative value represents a register;
                                               // negative values are described below

        // indicates that the m_hReferent field holds a referent
        protected static final int REF_REFERENT = -1;

        // indicates that the m_hReferent field holds a Ref that this Ref is "chained" to
        protected static final int REF_REF      = -2;

        // indicates that the m_hReferent field holds a property target
        protected static final int REF_PROPERTY = -3;

        // indicates that the m_hReferent field holds an array target
        protected static final int REF_ARRAY    = -4;
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

            m_iVar      = REF_ARRAY;
            m_hReferent = hTarget;
            f_lIndex    = lIndex;
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

                switch (template.getReferent(frameCaller, hRef, Op.A_STACK))
                    {
                    case Op.R_NEXT:
                        updateResult(frameCaller);
                        break;

                    case Op.R_CALL:
                        frameCaller.m_frameNext.addContinuation(this);
                        return Op.R_CALL;

                    case Op.R_EXCEPTION:
                        return Op.R_EXCEPTION;

                    case Op.R_BLOCK:
                        return ((FutureHandle) hRef).makeDeferredHandle(frameCaller).
                            proceed(frameCaller, this);

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
