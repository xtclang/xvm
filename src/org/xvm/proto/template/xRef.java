package org.xvm.proto.template;

import org.xvm.proto.*;

import org.xvm.proto.ObjectHandle.ExceptionHandle;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xRef
        extends TypeCompositionTemplate
    {
    public static xRef INSTANCE;

    public xRef(TypeSet types)
        {
        super(types, "x:Ref<RefType>", "x:Object", Shape.Interface);

        addImplement("x:Referent");

        INSTANCE = this;
        }

    // subclassing
    protected xRef(TypeSet types, String sName, String sSuper, Shape shape)
        {
        super(types, sName, sSuper, shape);
        }

    @Override
    public void initDeclared()
        {
        //    @ro Boolean assigned;
        //    conditional RefType peek()
        //    RefType get();
        //    Void set(RefType value);
        //    @ro Type ActualType;
        //    static Boolean equals(Ref value1, Ref value2)
        //    @ro String? name;
        //    @ro Int byteLength;
        //    @ro Boolean selfContained;

        PropertyTemplate ptAssigned = ensurePropertyTemplate("assigned", "x:Boolean");
        ptAssigned.makeReadOnly();
        ptAssigned.addGet().markNative();

        ensurePropertyTemplate("ActualType", "x:Type").makeReadOnly();
        ensurePropertyTemplate("name", "x:String|x:Nullable").makeReadOnly();
        ensurePropertyTemplate("byteLength", "x:Int").makeReadOnly();
        ensurePropertyTemplate("selfContained", "x:Boolean").makeReadOnly();

        ensureMethodTemplate("peek", VOID, new String[]{"x:ConditionalTuple<RefType>"});
        ensureMethodTemplate("get", VOID, new String[]{"RefType"}).markNative();
        ensureMethodTemplate("set", new String[]{"RefType"}, VOID).markNative();

        ensureFunctionTemplate("equals", new String[]{"x:Ref", "x:Ref"}, BOOLEAN);
        }

    @Override
    public int invokeNative(Frame frame, ObjectHandle hTarget,
                            MethodTemplate method, ObjectHandle[] ahArg, int iReturn)
        {
        RefHandle hThis = (RefHandle) hTarget;

        switch (ahArg.length)
            {
            case 0:
                switch (method.f_sName)
                    {
                    case "get":
                        try
                            {
                            return frame.assignValue(iReturn, hThis.get());
                            }
                        catch (ExceptionHandle.WrapperException e)
                            {
                            frame.m_hException = e.getExceptionHandle();
                            return Op.R_EXCEPTION;
                            }
                    }
            }

        return super.invokeNative(frame, hTarget, method, ahArg, iReturn);
        }

    @Override
    public int invokeNative(Frame frame, ObjectHandle hTarget,
                            MethodTemplate method, ObjectHandle hArg, int iReturn)
        {
        RefHandle hThis = (RefHandle) hTarget;

        switch (method.f_sName)
            {
            case "set":
                ExceptionHandle hException = hThis.set(hArg);
                if (hException != null)
                    {
                    frame.m_hException = hException;
                    return Op.R_EXCEPTION;
                    }
                return Op.R_NEXT;
            }
        return super.invokeNative(frame, hTarget, method, hArg, iReturn);
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new RefHandle(clazz);
        }

    // a simple reference handle
    public static class RefHandle
            extends ObjectHandle
        {
        protected Frame m_frame;
        protected int m_iVar = REF_REFERENT;

        protected ObjectHandle m_hDelegate; // can point to another Ref for the same referent

        // indicates ath the the m_hDelegate field holds a referent
        private static final int REF_REFERENT = -1;

        // indicates ath the the m_hDelegate field holds a Ref that this Ref is "chained" to
        private static final int REF_REF = -2;

        public RefHandle(TypeComposition clazz)
            {
            super(clazz);

            m_fMutable = true;
            }

        public RefHandle(TypeComposition clazz, Frame frame, int iVar)
            {
            super(clazz);

            m_fMutable = true;

            assert iVar >= 0;

            Frame.VarInfo infoSrc = frame.getVarInfo(iVar);

            RefHandle refCurrent = infoSrc.m_ref;
            if (refCurrent == null)
                {
                infoSrc.m_ref = this;
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

        public ObjectHandle get()
                throws ExceptionHandle.WrapperException
            {
            switch (m_iVar)
                {
                case REF_REFERENT:
                    return getInternal();

                case REF_REF:
                    return ((RefHandle) m_hDelegate).get();

                default: // assertion m_iVar >= 0
                    return m_frame.f_ahVar[m_iVar];
                }
            }

        protected ObjectHandle getInternal()
                throws ExceptionHandle.WrapperException
            {
            if (m_hDelegate == null)
                {
                throw xException.makeHandle("Unassigned reference").getException();
                }
            return m_hDelegate;
            }

        public ExceptionHandle set(ObjectHandle handle)
            {
            switch (m_iVar)
                {
                case REF_REFERENT:
                    return setInternal(handle);

                case REF_REF:
                    return ((RefHandle) m_hDelegate).set(handle);

                default: // assertion m_iVar >= 0
                    m_frame.f_ahVar[m_iVar] = handle;
                    return null;
                }
            }

        protected ExceptionHandle setInternal(ObjectHandle handle)
            {
            m_hDelegate = handle;
            return null;
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
            return super.toString() +
                    (m_iVar >= 0 ? " -> " + m_frame.f_ahVar[m_iVar] : m_hDelegate);
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
            super(clazz);

            f_hTarget = hTarget;
            f_lIndex = lIndex;
            }

        @Override
        public ObjectHandle get()
                throws ExceptionHandle.WrapperException
            {
            return ((IndexSupport) f_hTarget.f_clazz.f_template).
                    extractArrayValue(f_hTarget, f_lIndex);
            }

        @Override
        public ExceptionHandle set(ObjectHandle handle)
            {
            return ((IndexSupport) f_hTarget.f_clazz.f_template).
                    assignArrayValue(f_hTarget, f_lIndex, handle);
            }

        @Override
        public void dereference()
            {
            // no op
            }
        }

    }
