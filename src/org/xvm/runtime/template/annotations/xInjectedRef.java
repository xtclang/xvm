package org.xvm.runtime.template.annotations;


import java.util.concurrent.ExecutionException;

import org.xvm.asm.ClassStructure;

import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.Type;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TypeSet;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.Ref;


/**
 * TODO:
 */
public class xInjectedRef
        extends Ref
    {
    public static xInjectedRef INSTANCE;

    public xInjectedRef(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        // TODO: how to inherit this from Ref?
        markNativeMethod("get", VOID, new String[]{"RefType"});
        markNativeMethod("set", new String[]{"RefType"}, VOID);
        }

    @Override
    public RefHandle createRefHandle(TypeComposition clazz, String sName)
        {
        if (sName == null)
            {
            throw new IllegalArgumentException("Name is not present");
            }
        return new InjectedHandle(clazz, sName);
        }

    public static class InjectedHandle
            extends RefHandle
        {
        protected InjectedHandle(TypeComposition clazz, String sName)
            {
            super(clazz, sName);
            }

        @Override
        protected ObjectHandle getInternal()
                throws ExceptionHandle.WrapperException
            {
            ObjectHandle hValue = m_hDelegate;
            if (hValue == null)
                {
                Type typeEl = f_clazz.getActualType("RefType");
                hValue = m_hDelegate =
                        f_clazz.f_template.f_types.f_container.getInjectable(m_sName, typeEl.f_clazz);
                if (hValue == null)
                    {
                    throw xException.makeHandle("Unknown injectable property " + m_sName).getException();
                    }
                }

            return hValue;
            }

        @Override
        protected ExceptionHandle setInternal(ObjectHandle handle)
            {
            return xException.makeHandle("InjectedRef cannot be re-assigned");
            }

        @Override
        public String toString()
            {
            try
                {
                return "(" + f_clazz + ") " + get();
                }
            catch (Throwable e)
                {
                if (e instanceof ExecutionException)
                    {
                    e = e.getCause();
                    }
                return e.toString();
                }
            }
        }
    }
