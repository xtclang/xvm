package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * Native functionality for virtual child classes.
 */
public class Child
        extends xObject
    {
    public Child(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        assert structure.isVirtualChild();
        }

    @Override
    public void initNative()
        {
        }

    @Override
    public int invoke1(Frame frame, CallChain chain, ObjectHandle hChild, ObjectHandle[] ahVar, int iReturn)
        {
        ServiceHandle hService = hChild.getService();
        return hService == null || hService.f_context == frame.f_context || chain.isAtomic()
            ? super.invoke1(frame, chain, hChild, ahVar, iReturn)
            : makeAsyncHandle(hChild, chain).call1(frame, hService, ahVar, iReturn);
        }

    @Override
    public int invokeN(Frame frame, CallChain chain, ObjectHandle hChild, ObjectHandle[] ahVar, int[] aiReturn)
        {
        ServiceHandle hService = hChild.getService();
        return hService == null || hService.f_context == frame.f_context || chain.isAtomic()
            ? super.invokeN(frame, chain, hChild, ahVar, aiReturn)
            : makeAsyncHandle(hChild, chain).callN(frame, hService, ahVar, aiReturn);
        }

    @Override
    public int invokeT(Frame frame, CallChain chain, ObjectHandle hChild, ObjectHandle[] ahVar, int iReturn)
        {
        ServiceHandle hService = hChild.getService();
        return hService == null || hService.f_context == frame.f_context || chain.isAtomic()
            ? super.invokeT(frame, chain, hChild, ahVar, iReturn)
            : makeAsyncHandle(hChild, chain).callT(frame, hService, ahVar, iReturn);
        }

    @Override
    public int getPropertyValue(Frame frame, ObjectHandle hChild, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = hChild.getService();
        return hService == null || hService.f_context == frame.f_context || hChild.isAtomic(idProp)
            ? super.getPropertyValue(frame, hChild, idProp, iReturn)
            : hService.f_context.sendProperty01Request(frame, hChild, idProp, iReturn, super::getPropertyValue);
        }

    @Override
    public int getFieldValue(Frame frame, ObjectHandle hChild, PropertyConstant idProp, int iReturn)
        {
        ServiceHandle hService = hChild.getService();
        if (hService == null || hService.f_context == frame.f_context || hChild.isAtomic(idProp))
            {
            return super.getFieldValue(frame, hChild, idProp, iReturn);
            }
        throw new IllegalStateException("Invalid context");
        }

    @Override
    public int setPropertyValue(Frame frame, ObjectHandle hChild, PropertyConstant idProp,
                                ObjectHandle hValue)
        {
        ServiceHandle hService = hChild.getService();
        return hService == null || hService.f_context == frame.f_context || hChild.isAtomic(idProp)
            ? super.setPropertyValue(frame, hChild, idProp, hValue)
            : hService.f_context.sendProperty10Request(frame, hChild, idProp, hValue, super::setPropertyValue);
        }

    @Override
    public int setFieldValue(Frame frame, ObjectHandle hChild, PropertyConstant idProp,
                             ObjectHandle hValue)
        {
        ServiceHandle hService = hChild.getService();

        if (hService == null || hService.f_context == frame.f_context || hChild.isAtomic(idProp))
            {
            return super.setFieldValue(frame, hChild, idProp, hValue);
            }

        throw new IllegalStateException("Invalid context");
        }

    private FunctionHandle makeAsyncHandle(ObjectHandle hChild, CallChain chain)
        {
        return new xRTFunction.AsyncHandle(chain)
            {
            @Override
            protected ObjectHandle getContextTarget(Frame frame, ObjectHandle hService)
                {
                return hChild;
                }
            };
        }
    }