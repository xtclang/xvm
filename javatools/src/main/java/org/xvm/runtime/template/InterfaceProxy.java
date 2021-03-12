package org.xvm.runtime.template;


import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ProxyComposition;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template._native.reflect.xRTFunction;


/**
 * Template for proxy-able interfaces.
 */
public class InterfaceProxy
        extends xService
    {
    public static InterfaceProxy INSTANCE;

    public InterfaceProxy(TemplateRegistry templates)
        {
        super(templates, xObject.INSTANCE.getStructure(), false);

        INSTANCE = this;
        }

    @Override
    public void initNative()
        {
        }

    @Override
    public ClassComposition ensureClass(TypeConstant typeActual)
        {
        throw new UnsupportedOperationException();
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahArg, int iReturn)
        {
        throw new IllegalStateException();
        }

    @Override
    public ObjectHandle createStruct(Frame frame, TypeComposition clazz)
        {
        throw new IllegalStateException();
        }

    @Override
    protected int makeImmutable(Frame frame, ObjectHandle hTarget)
        {
        return frame.raiseException(xException.unsupportedOperation(frame, "makeImmutable"));
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        InterfaceProxyHandle hProxy = (InterfaceProxyHandle) hTarget;
        if (frame.f_context == hProxy.f_context)
            {
            hTarget = hProxy.f_hTarget;
            return hTarget.getTemplate().invokeNativeN(frame, method, hTarget, ahArg, iReturn);
            }
        return xRTFunction.makeAsyncNativeHandle(method).call1(frame, hTarget, ahArg, iReturn);
        }

    @Override
    public int invoke1(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        InterfaceProxyHandle hProxy = (InterfaceProxyHandle) hTarget;
        if (frame.f_context == hProxy.f_context)
            {
            hTarget = hProxy.f_hTarget;
            return hTarget.getTemplate().invoke1(frame, chain, hTarget, ahVar, iReturn);
            }
        return xRTFunction.makeAsyncHandle(chain).call1(frame, hTarget, ahVar, iReturn);
        }

    @Override
    public int invokeT(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        InterfaceProxyHandle hProxy = (InterfaceProxyHandle) hTarget;
        if (frame.f_context == hProxy.f_context)
            {
            hTarget = hProxy.f_hTarget;
            return hTarget.getTemplate().invokeT(frame, chain, hTarget, ahVar, iReturn);
            }
        return xRTFunction.makeAsyncHandle(chain).callT(frame, hTarget, ahVar, iReturn);
        }

    @Override
    public int invokeN(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        InterfaceProxyHandle hProxy = (InterfaceProxyHandle) hTarget;
        if (frame.f_context == hProxy.f_context)
            {
            hTarget = hProxy.f_hTarget;
            return hTarget.getTemplate().invokeN(frame, chain, hTarget, ahVar, aiReturn);
            }
        return xRTFunction.makeAsyncHandle(chain).callN(frame, hTarget, ahVar, aiReturn);
        }

    @Override
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        InterfaceProxyHandle hProxy = (InterfaceProxyHandle) hTarget;
        if (frame.f_context == hProxy.f_context)
            {
            hTarget = hProxy.f_hTarget;
            return hTarget.getTemplate().getPropertyValue(frame, hTarget, idProp, iReturn);
            }

        return hProxy.f_context.sendProperty01Request(frame, hTarget, idProp, iReturn, this::getPropertyValue);
        }

    @Override
    public int getFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        InterfaceProxyHandle hProxy = (InterfaceProxyHandle) hTarget;
        if (frame.f_context == hProxy.f_context)
            {
            hTarget = hProxy.f_hTarget;
            return hTarget.getTemplate().getFieldValue(frame, hTarget, idProp, iReturn);
            }

        throw new IllegalStateException("Invalid context");
        }

    @Override
    public int setPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp,
                                ObjectHandle hValue)
        {
        InterfaceProxyHandle hProxy = (InterfaceProxyHandle) hTarget;
        if (frame.f_context == hProxy.f_context)
            {
            hTarget = hProxy.f_hTarget;
            return hTarget.getTemplate().setPropertyValue(frame, hTarget, idProp, hValue);
            }

        return hProxy.f_context.sendProperty10Request(frame, hTarget, idProp, hValue, this::setPropertyValue);
        }

    @Override
    public int setFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp,
                             ObjectHandle hValue)
        {
        InterfaceProxyHandle hProxy = (InterfaceProxyHandle) hTarget;
        if (frame.f_context == hProxy.f_context)
            {
            hTarget = hProxy.f_hTarget;
            return hTarget.getTemplate().setFieldValue(frame, hTarget, idProp, hValue);
            }

        throw new IllegalStateException("Invalid context");
        }

    @Override
    public int createPropertyRef(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, boolean fRO, int iReturn)
        {
        InterfaceProxyHandle hProxy = (InterfaceProxyHandle) hTarget;
        if (frame.f_context == hProxy.f_context)
            {
            hTarget = hProxy.f_hTarget;
            return hTarget.getTemplate().createPropertyRef(frame, hTarget, idProp, fRO, iReturn);
            }

        throw new IllegalStateException("Invalid context");
        }

    public static ObjectHandle makeHandle(ProxyComposition clzProxy, ServiceContext ctx,
                                          ObjectHandle hTarget)
        {
        return new InterfaceProxyHandle(clzProxy, ctx, hTarget);
        }


    // ----- ObjectHandle -----

    public static class InterfaceProxyHandle
            extends ServiceHandle
        {
        protected final ObjectHandle f_hTarget;

        public InterfaceProxyHandle(TypeComposition clazz, ServiceContext context, ObjectHandle hTarget)
            {
            super(clazz, context);

            f_hTarget = hTarget;
            }

        public ObjectHandle getTarget()
            {
            return f_hTarget;
            }

        @Override
        public int hashCode()
            {
            return System.identityHashCode(this);
            }

        @Override
        public boolean equals(Object obj)
            {
            if (this == obj)
                {
                return true;
                }

            return obj instanceof InterfaceProxyHandle &&
                ((InterfaceProxyHandle) obj).f_hTarget == f_hTarget;
            }
        }
    }
