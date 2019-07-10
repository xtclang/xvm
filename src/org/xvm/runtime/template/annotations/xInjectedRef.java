package org.xvm.runtime.template.annotations;


import org.xvm.asm.ClassStructure;

import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xRef;


/**
 * Native InjectedRef implementation.
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
        ObjectHandle   hValue    = hInjected.getReferent();
        if (hValue == null)
            {
            TypeConstant typeEl = hInjected.getType().resolveGenericType("RefType");

            hValue = frame.f_context.f_container.getInjectable(frame, hInjected.getName(), typeEl);
            if (hValue == null)
                {
                return frame.raiseException(
                    xException.makeHandle("Unknown injectable property " + hInjected.getName()));
                }

            if (hValue instanceof DeferredCallHandle)
                {
                ((DeferredCallHandle) hValue).addContinuation(frameCaller ->
                    {
                    hInjected.setReferent(frameCaller.peekStack());
                    return Op.R_NEXT;
                    });
                }
            else
                {
                hInjected.setReferent(hValue);
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
        }
    }
