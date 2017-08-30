package org.xvm.proto.template.annotations;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.proto.Frame;
import org.xvm.proto.ObjectHandle;
import org.xvm.proto.TypeComposition;
import org.xvm.proto.TypeSet;

import org.xvm.proto.template.xBoolean;
import org.xvm.proto.template.xException;
import org.xvm.proto.template.Ref;

import java.util.concurrent.atomic.AtomicReference;

/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xAtomicRef
        extends Ref
    {
    public xAtomicRef(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure, false);
        }


    @Override
    public void initDeclared()
        {
        markNativeMethod("replace", new String[]{"RefType", "RefType"});
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
                        boolean fSuccess = hThis.m_atomic.compareAndSet(ahArg[0], ahArg[1]);

                        return frame.assignValue(iReturn,
                                fSuccess ? xBoolean.TRUE : xBoolean.FALSE);
                    }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
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
    }
