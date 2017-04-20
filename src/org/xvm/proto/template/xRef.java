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

        ensurePropertyTemplate("assigned", "x:Boolean").makeReadOnly();
        ensureMethodTemplate("peek", VOID, new String[]{"x:ConditionalTuple<RefType>"});
        ensureMethodTemplate("get", VOID, new String[]{"RefType"}).markNative();
        ensureMethodTemplate("set", new String[]{"RefType"}, VOID).markNative();
        ensurePropertyTemplate("ActualType", "x:Type").makeReadOnly();
        ensurePropertyTemplate("name", "x:String|x:Nullable").makeReadOnly();
        ensurePropertyTemplate("byteLength", "x:Int").makeReadOnly();
        ensurePropertyTemplate("selfContained", "x:Boolean").makeReadOnly();

        ensureFunctionTemplate("equals", new String[]{"x:Ref", "x:Ref"}, BOOLEAN);
        }

    @Override
    public ExceptionHandle invokeNative01(Frame frame, ObjectHandle hTarget,
                                          MethodTemplate method, int iRet)
        {
        RefHandle hThis = (RefHandle) hTarget;

        switch (method.f_sName)
            {
            case "get":
                ObjectHandle hValue = hThis.get();
                return frame.assignValue(iRet, hValue);

            default:
                throw new IllegalStateException("Unknown method: " + method);
            }
        }

    @Override
    public ExceptionHandle invokeNative10(Frame frame, ObjectHandle hTarget,
                                          MethodTemplate method, ObjectHandle hArg)
        {
        RefHandle hThis = (RefHandle) hTarget;

        switch (method.f_sName)
            {
            case "set":
                hThis.set(hArg);
                return null;

            default:
                throw new IllegalStateException("Unknown method: " + method);
            }
        }

    // a reference
    public interface Ref
        {
        ObjectHandle get()
                throws ExceptionHandle.WrapperException;

        // return exception
        ExceptionHandle set(ObjectHandle handle);
        }

    // a simple reference handle
    public static class RefHandle
            extends ObjectHandle
            implements Ref
        {
        protected Frame m_frame;
        protected int m_iVar = REF_REFERENT;
        protected ObjectHandle m_hDelegate; // can point to another Ref for the same referent

        // indicates ath the the m_hDelegate field holds a referent
        private static final int REF_REFERENT = -1;

        // indicates ath the the m_hDelegate field holds a Ref that this Ref is "chained" to
        private static final int REF_REF = -2;

        public RefHandle(TypeComposition clazz, ObjectHandle handle)
            {
            super(clazz);

            m_hDelegate = handle;
            }

        public RefHandle(TypeComposition clazz, Frame frame, int iVar)
            {
            super(clazz);

            assert iVar >= 0;

            Frame.VarInfo infoSrc = frame.f_aInfo[iVar];

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
            {
            if (m_iVar >= 0)
                {
                return m_frame.f_ahVar[m_iVar];
                }
            else if (m_iVar == REF_REFERENT)
                {
                return m_hDelegate;
                }
            else  // REF_REF
                {
                return ((RefHandle) m_hDelegate).get();
                }
            }

        public ExceptionHandle set(ObjectHandle handle)
            {
            // TODO: assert type compatibility
            if (m_iVar >= 0)
                {
                m_frame.f_ahVar[m_iVar] = handle;
                }
            else if (m_iVar == REF_REFERENT)
                {
                m_hDelegate = handle;
                }
            else // REF_REF
                {
                ((RefHandle) m_hDelegate).set(handle);
                }
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
                    (m_iVar >= 0 ? "-> " + m_frame.f_ahVar[m_iVar] : m_hDelegate);
            }
        }

    protected static class DelegatingRef
            extends ObjectHandle
            implements Ref
        {
        public Ref m_hDelegate;

        protected DelegatingRef(TypeComposition clazz, Ref referent)
            {
            super(clazz);

            m_hDelegate = referent;
            }

        @Override
        public ObjectHandle get()
                throws ExceptionHandle.WrapperException
            {
            return m_hDelegate.get();
            }

        @Override
        public ExceptionHandle set(ObjectHandle handle)
            {
            return m_hDelegate.set(handle);
            }
        }

    public static RefHandle makeHandle(ObjectHandle handle)
        {
        return new RefHandle(INSTANCE.f_clazzCanonical, handle);
        }
    }
