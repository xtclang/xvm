package org.xvm.proto.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.PropertyStructure;

import org.xvm.proto.ClassTemplate;
import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.Op;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class Ref
        extends ClassTemplate
    {
    public static Ref INSTANCE;

    public Ref(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);

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
        markNativeMethod("set", new String[]{"RefType"}, VOID);
        markNativeGetter("name");
        markNativeGetter("selfContained");

        // extends Referent
        markNativeGetter("ActualType");
        markNativeGetter("isService");
        markNativeGetter("isConst");
        markNativeGetter("isImmutable");
        }

    @Override
    public int invokeNativeGet(Frame frame, PropertyStructure property, ObjectHandle hTarget, int iReturn)
        {
        RefHandle hThis = (RefHandle) hTarget;

        switch (property.getName())
            {
            case "ActualType":
                try
                    {
                    ObjectHandle hReferent = hThis.get();
                    return frame.assignValue(iReturn, xType.makeHandle(hReferent.m_type));
                    }
                catch (ExceptionHandle.WrapperException e)
                    {
                    return frame.raiseException(e);
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

            case "isService":
                try
                    {
                    ObjectHandle hReferent = hThis.get();
                    return frame.assignValue(iReturn, xBoolean.makeHandle(
                            hReferent.f_clazz.f_template.isService()));
                    }
                catch (ExceptionHandle.WrapperException e)
                    {
                    return frame.raiseException(e);
                    }

            case "isConst":
                try
                    {
                    ObjectHandle hReferent = hThis.get();
                    return frame.assignValue(iReturn, xBoolean.makeHandle(
                            hReferent.f_clazz.f_template.isConst()));
                    }
                catch (ExceptionHandle.WrapperException e)
                    {
                    return frame.raiseException(e);
                    }

            case "isImmutable":
                try
                    {
                    ObjectHandle hReferent = hThis.get();
                    return frame.assignValue(iReturn, xBoolean.makeHandle(
                            !hReferent.isMutable()));
                    }
                catch (ExceptionHandle.WrapperException e)
                    {
                    return frame.raiseException(e);
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
                        try
                            {
                            return frame.assignValue(iReturn, hThis.get());
                            }
                        catch (ExceptionHandle.WrapperException e)
                            {
                            return frame.raiseException(e);
                            }
                    }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        RefHandle hThis = (RefHandle) hTarget;

        switch (method.getName())
            {
            case "set":
                ExceptionHandle hException = hThis.set(hArg);
                return hException == null ? Op.R_NEXT : frame.raiseException(hException);
            }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
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
                    try
                        {
                        return frame.assignValues(aiReturn, xBoolean.TRUE, hThis.get());
                        }
                    catch (ExceptionHandle.WrapperException e)
                        {
                        return frame.raiseException(e);
                        }
                    }
                 else
                    {
                    return frame.assignValues(aiReturn, xBoolean.FALSE, null);
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

        // indicates ath the the m_hDelegate field holds a referent
        private static final int REF_REFERENT = -1;

        // indicates ath the the m_hDelegate field holds a Ref that this Ref is "chained" to
        private static final int REF_REF = -2;

        public RefHandle(TypeComposition clazz, String sName)
            {
            super(clazz);

            m_sName = sName;
            m_fMutable = true;
            m_iVar = REF_REFERENT;
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

        public boolean isAssigned()
            {
            switch (m_iVar)
                {
                case REF_REFERENT:
                    return m_hDelegate != null;

                case REF_REF:
                    return ((RefHandle) m_hDelegate).isAssigned();

                default: // assertion m_iVar >= 0
                    return m_frame.f_ahVar[m_iVar] != null;
                }
            }

        public boolean isSelfContained()
            {
            return m_iVar == REF_REFERENT;
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
                    (m_iVar >= 0 ? "-> " + m_frame.f_ahVar[m_iVar] : m_hDelegate);
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
