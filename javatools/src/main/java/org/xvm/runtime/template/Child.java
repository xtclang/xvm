package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * Native functionality for virtual child classes.
 */
public class Child
        extends xObject
    {
    public Child(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        assert structure.isVirtualChild();
        }

    @Override
    public void initNative()
        {
        }

    @Override
    public boolean isService()
        {
        ClassStructure parent = getStructure().getVirtualParent();
        while (parent.isVirtualChild())
            {
            parent = parent.getVirtualParent();
            }

        return parent.getFormat() == Component.Format.SERVICE;
        }

    @Override
    public int invoke1(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        ServiceHandle hService = hTarget.getService();
        return hService == null || hService.f_context == frame.f_context || chain.isAtomic()
            ? super.invoke1(frame, chain, hTarget, ahVar, iReturn)
            : makeAsyncHandle(hTarget, chain).call1(frame, hService, ahVar, iReturn);
        }

    @Override
    public int invokeN(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        ServiceHandle hService = hTarget.getService();
        return hService == null || hService.f_context == frame.f_context || chain.isAtomic()
            ? super.invokeN(frame, chain, hTarget, ahVar, aiReturn)
            : makeAsyncHandle(hTarget, chain).callN(frame, hService, ahVar, aiReturn);
        }

    @Override
    public int invokeT(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        ServiceHandle hService = hTarget.getService();
        return hService == null || hService.f_context == frame.f_context || chain.isAtomic()
            ? super.invokeT(frame, chain, hTarget, ahVar, iReturn)
            : makeAsyncHandle(hTarget, chain).callT(frame, hService, ahVar, iReturn);
        }

    @Override
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = hTarget.getService();
        return hService == null || hService.f_context == frame.f_context || hTarget.isAtomic(idProp)
            ? super.getPropertyValue(frame, hTarget, idProp, iReturn)
            : hService.f_context.sendProperty01Request(frame, idProp, iReturn,
                    (frameCaller, hSvc, id, hVal) ->
                        super.getPropertyValue(frameCaller, hTarget, id, hVal));
        }

    @Override
    public int getFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = hTarget.getService();
        if (hService == null || hService.f_context == frame.f_context || hTarget.isAtomic(idProp))
            {
            return super.getFieldValue(frame, hTarget, idProp, iReturn);
            }
        throw new IllegalStateException("Invalid context");
        }

    @Override
    public int setPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp,
                                ObjectHandle hValue)
        {
        ServiceHandle hService = hTarget.getService();
        return hService == null || hService.f_context == frame.f_context || hTarget.isAtomic(idProp)
            ? super.setPropertyValue(frame, hTarget, idProp, hValue)
            : hService.f_context.sendProperty10Request(frame, idProp, hValue,
                    (frameCaller, hSvc, id, hVal) ->
                        super.setPropertyValue(frameCaller, hTarget, id, hVal));
        }

    @Override
    public int setFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp,
                             ObjectHandle hValue)
        {
        ServiceHandle hService = hTarget.getService();

        if (hService == null || hService.f_context == frame.f_context || hTarget.isAtomic(idProp))
            {
            return super.setFieldValue(frame, hTarget, idProp, hValue);
            }

        throw new IllegalStateException("Invalid context");
        }

    private FunctionHandle makeAsyncHandle(ObjectHandle hTarget, CallChain chain)
        {
        return new xRTFunction.AsyncHandle(chain)
            {
            @Override
            protected ObjectHandle getContextTarget(Frame frame, ObjectHandle hService)
                {
                return hTarget;
                }
            };
        }
    }
