package org.xvm.runtime.template.annotations;


import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.Op;

import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.text.xString;

import org.xvm.runtime.template.reflect.xRef;


/**
 * Native InjectedRef implementation.
 */
public class xInjectedRef
        extends xRef
    {
    public static xInjectedRef INSTANCE;

    public xInjectedRef(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

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
    public ClassTemplate getTemplate(TypeConstant type)
        {
        return this;
        }

    @Override
    public RefHandle createRefHandle(Frame frame, TypeComposition clazz, String sName)
        {
        if (sName == null)
            {
            throw new IllegalArgumentException("Name is not present");
            }

        Annotation anno    = clazz.getType().getAnnotations()[0];
        Constant[] aParams = anno.getParams();

        Constant constName = aParams.length == 0 ? null : aParams[0];
        String   sResource = constName instanceof StringConstant constString
                                ? constString.getValue()
                                : sName;
        if (aParams.length < 2)
            {
            // opts are not specified; the handle could be trivially initialized on-the-spot
            InjectedHandle hInject = new InjectedHandle(clazz.ensureAccess(Access.PUBLIC), sName, sResource);
            hInject.setField(frame, "resourceName", xString.makeHandle(sResource));
            hInject.setField(frame, "opts", xNullable.NULL);
            hInject.makeImmutable();
            return hInject;
            }

        // arguments initialization and assignment will be handled in the generic manner
        assert clazz.isStruct();
        return new InjectedHandle(clazz, sName, sResource);
        }

    @Override
    protected int postValidate(Frame frame, ObjectHandle hStruct)
        {
        if (hStruct.isMutable())
            {
            hStruct.makeImmutable();
            }
        return Op.R_NEXT;
        }

    @Override
    public int getReferent(Frame frame, RefHandle hTarget, int iReturn)
        {
        InjectedHandle hInjected = (InjectedHandle) hTarget;
        ObjectHandle   hValue    = hInjected.getReferent();
        if (hValue == null)
            {
            TypeConstant typeResource = hInjected.getType().resolveGenericType("Referent");
            String       sResource    = hInjected.getResourceName();
            ObjectHandle hOpts        = hInjected.getField(frame, "opts");

            hValue = frame.getInjected(sResource, typeResource, hOpts);
            if (hValue == null)
                {
                return frame.raiseException(
                        xException.unknownInjectable(frame, typeResource, sResource));
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

    @Override
    protected int getPropertyAssigned(Frame frame, RefHandle hRef, int iReturn)
        {
        switch (getReferent(frame, hRef, Op.A_STACK))
            {
            case Op.R_NEXT:
                frame.popStack();
                return frame.assignValue(iReturn, xBoolean.TRUE);

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    {
                    if (frameCaller.clearException() == null)
                        {
                        frameCaller.popStack();
                        return frameCaller.assignValue(iReturn, xBoolean.TRUE);
                        }
                    else
                        {
                        frameCaller.clearException();
                        return frameCaller.assignValue(iReturn, xBoolean.FALSE);
                        }
                    });
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                frame.clearException();
                return frame.assignValue(iReturn, xBoolean.FALSE);

            default:
                throw new IllegalStateException();
            }
        }


    // ----- handle class --------------------------------------------------------------------------

    public static class InjectedHandle
            extends RefHandle
        {
        protected InjectedHandle(TypeComposition clazz, String sVarName, String sResourceName)
            {
            super(clazz, sVarName);

            f_sResource = sResourceName;
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

        @Override
        public String toString()
            {
            return super.toString() + " " + f_sResource;
            }

        // ----- fields ----------------------------------------------------------------------------

        private final String f_sResource;
        }
    }