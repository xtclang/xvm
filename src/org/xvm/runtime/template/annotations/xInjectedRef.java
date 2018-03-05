package org.xvm.runtime.template.annotations;


import java.util.concurrent.ExecutionException;

import org.xvm.asm.ClassStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xRef;


/**
 * TODO:
 */
public class xInjectedRef
        extends xRef
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

    @Override
    protected int getInternal(Frame frame, RefHandle hTarget, int iReturn)
        {
        InjectedHandle hInjected = (InjectedHandle) hTarget;
        ObjectHandle hValue = hInjected.m_hDelegate;
        if (hValue == null)
            {
            TypeConstant typeEl = hInjected.getType().getGenericParamType("RefType");

            hInjected.m_hDelegate = hValue =
                frame.f_context.f_container.getInjectable(hInjected.m_sName, typeEl);
            if (hValue == null)
                {
                return frame.raiseException(
                    xException.makeHandle("Unknown injectable property " + hInjected.m_sName));
                }
            }

        return frame.assignValue(iReturn, hValue);
        }


    // ----- handle class -----

    public static class InjectedHandle
            extends RefHandle
        {
        protected InjectedHandle(TypeComposition clazz, String sName)
            {
            super(clazz, sName);

            m_fMutable = false;
            }

        @Override
        public boolean isAssigned(Frame frame)
            {
            return m_hDelegate != null;
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
