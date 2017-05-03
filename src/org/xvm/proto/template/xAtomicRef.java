package org.xvm.proto.template;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.ObjectHandle.ExceptionHandle;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;

import java.util.concurrent.atomic.AtomicReference;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xAtomicRef
        extends xRef
    {
    public static xAtomicRef INSTANCE;

    public xAtomicRef(TypeSet types)
        {
        super(types, "x:AtomicRef<RefType>", "x:Ref", Shape.Mixin);

        INSTANCE = this;
        }

    // subclassing
    protected xAtomicRef(TypeSet types, String sName, String sSuper, Shape shape)
        {
        super(types, sName, sSuper, shape);
        }

    @Override
    public void initDeclared()
        {
        //    Boolean replace(RefType oldValue, RefType newValue)
        //    conditional RefType replaceFailed(RefType oldValue, RefType newValue)
        //

        MethodTemplate mtReplace = ensureMethodTemplate("replace",
                new String[]{"RefType", "RefType"}, new String[]{"x:Boolean"});
        mtReplace.markNative();
        }

    @Override
    public ExceptionHandle invokeNative(Frame frame, ObjectHandle hTarget,
                                        MethodTemplate method, ObjectHandle[] ahArg, int iReturn)
        {
        AtomicHandle hThis = (AtomicHandle) hTarget;

        switch (ahArg.length)
            {
            case 2:
                switch (method.f_sName)
                    {
                    case "replace":
                        boolean fSuccess = hThis.m_atomic.compareAndSet(ahArg[0], ahArg[1]);

                        return frame.assignValue(iReturn,
                                fSuccess ? xBoolean.TRUE : xBoolean.FALSE);
                    }
            }

        return super.invokeNative(frame, hTarget, method, ahArg, iReturn);
        }

    @Override
    public ObjectHandle createHandle(TypeComposition clazz)
        {
        return new AtomicHandle(clazz, null);
        }

    public static class AtomicHandle
            extends RefHandle
        {
        protected AtomicReference<ObjectHandle> m_atomic = new AtomicReference<>();

        protected AtomicHandle(TypeComposition clazz, ObjectHandle hValue)
            {
            super(clazz, null);

            if (hValue != null)
                {
                m_atomic.set(hValue);
                }
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

    public static AtomicHandle makeHandle(ObjectHandle hValue)
        {
        return new AtomicHandle(INSTANCE.f_clazzCanonical, hValue);
        }
    }
