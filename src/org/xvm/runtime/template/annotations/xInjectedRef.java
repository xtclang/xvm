package org.xvm.runtime.template.annotations;


import java.util.concurrent.ExecutionException;

import org.xvm.asm.ClassStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.Ref;


/**
 * TODO:
 */
public class xInjectedRef
        extends Ref
    {
    public static xInjectedRef INSTANCE;

    public xInjectedRef(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

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
        protected int getInternal(Frame frame, int iReturn)
            {
            ObjectHandle hValue = m_hDelegate;
            if (hValue == null)
                {
                TypeConstant typeEl = getType().getGenericParamType("RefType");

                hValue = m_hDelegate = frame.f_context.f_container.getInjectable(m_sName, typeEl);
                if (hValue == null)
                    {
                    return frame.raiseException(xException.makeHandle("Unknown injectable property " + m_sName));
                    }
                }

            return frame.assignValue(iReturn, hValue);
            }

        @Override
        protected int setInternal(Frame frame, ObjectHandle handle)
            {
            return frame.raiseException(
                xException.makeHandle("InjectedRef cannot be re-assigned"));
            }

        @Override
        public String toString()
            {
            try
                {
                return "(" + m_clazz + ") " + m_hDelegate;
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
