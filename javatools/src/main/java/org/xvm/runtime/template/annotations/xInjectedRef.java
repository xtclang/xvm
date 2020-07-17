package org.xvm.runtime.template.annotations;


import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Op;

import org.xvm.asm.constants.AnnotatedTypeConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.reflect.xRef;


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
    public void initNative()
        {
        }

    @Override
    public RefHandle createRefHandle(TypeComposition clazz, String sName)
        {
        if (sName == null)
            {
            throw new IllegalArgumentException("Name is not present");
            }

        AnnotatedTypeConstant typeInject = (AnnotatedTypeConstant) clazz.getType();
        Annotation            anno       = typeInject.getAnnotation();
        Constant[]            aParams    = anno.getParams();
        String                sResource  = aParams.length > 0
                ? ((StringConstant) aParams[0]).getValue()
                : sName;

        return new InjectedHandle(clazz, sName, sResource);
        }

    @Override
    public int getReferent(Frame frame, RefHandle hTarget, int iReturn)
        {
        InjectedHandle hInjected = (InjectedHandle) hTarget;
        ObjectHandle   hValue    = hInjected.getReferent();
        if (hValue == null)
            {
            TypeConstant typeEl = hInjected.getType().resolveGenericType("Referent");

            hValue = frame.f_context.f_container.getInjectable(frame, hInjected.getResourceName(), typeEl);
            if (hValue == null)
                {
                return frame.raiseException(
                    "Unknown injectable property \"" + hInjected.getResourceName() +'"');
                }

            if (Op.isDeferred(hValue))
                {
                return hValue.proceed(frame, frameCaller ->
                    {
                    ObjectHandle hVal = frameCaller.popStack();
                    hInjected.setReferent(hVal);
                    return frameCaller.assignValue(iReturn, hVal);
                    });
                }

            hInjected.setReferent(hValue);
            }

        return frame.assignValue(iReturn, hValue);
        }


    // ----- handle class -----

    public static class InjectedHandle
            extends RefHandle
        {
        protected InjectedHandle(TypeComposition clazz, String sVarName, String sResourceName)
            {
            super(clazz, sVarName);

            f_sResource = sResourceName;
            m_fMutable  = false;
            }

        public String getResourceName()
            {
            return f_sResource;
            }

        @Override
        public ObjectHandle getReferent()
            {
            return m_hReferent;
            }

        public void setReferent(ObjectHandle hReferent)
            {
            assert m_hReferent == null;

            m_hReferent = hReferent;
            }

        // ----- fields ----------------------------------------------------------------------------

        private final String f_sResource;
        }
    }
