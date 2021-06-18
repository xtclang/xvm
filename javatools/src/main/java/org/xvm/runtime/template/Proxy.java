package org.xvm.runtime.template;


import java.util.Map;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ProxyComposition;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template._native.reflect.xRTFunction.AsyncHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * Template for proxied objects.
 */
public class Proxy
        extends xService
    {
    public static Proxy INSTANCE;

    public Proxy(TemplateRegistry templates)
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
    public ObjectHandle createProxyHandle(ServiceContext ctx, ObjectHandle hTarget,
                                          TypeConstant typeProxy)
        {
        ProxyHandle hProxy = (ProxyHandle) hTarget;
        if (ctx != hProxy.f_context)
            {
            return null;
            }

        if (hProxy.getType().equals(typeProxy))
            {
            return hProxy;
            }

        ProxyComposition clzProxy = new ProxyComposition(
                hProxy.getComposition().getOrigin(), typeProxy);
        return Proxy.makeHandle(clzProxy, ctx, hProxy.getTarget());
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        ProxyHandle hProxy = (ProxyHandle) hTarget;

        hTarget = hProxy.f_hTarget;

        return frame.f_context == hProxy.f_context
            ? hTarget.getTemplate().invokeNativeN(frame, method, hTarget, ahArg, iReturn)
            : makeAsyncNativeHandle(hTarget, method).call1(frame, hProxy, ahArg, iReturn);
        }

    @Override
    public int invoke1(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        ProxyHandle hProxy = (ProxyHandle) hTarget;

        hTarget = hProxy.f_hTarget;

        return frame.f_context == hProxy.f_context
            ? hTarget.getTemplate().invoke1(frame, chain, hTarget, ahVar, iReturn)
            : makeAsyncHandle(hTarget, chain).call1(frame, hProxy, ahVar, iReturn);
        }

    @Override
    public int invokeT(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        ProxyHandle hProxy = (ProxyHandle) hTarget;

        hTarget = hProxy.f_hTarget;

        return frame.f_context == hProxy.f_context
            ? hTarget.getTemplate().invokeT(frame, chain, hTarget, ahVar, iReturn)
            : makeAsyncHandle(hTarget, chain).callT(frame, hProxy, ahVar, iReturn);
        }

    @Override
    public int invokeN(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        ProxyHandle hProxy = (ProxyHandle) hTarget;

        hTarget = hProxy.f_hTarget;

        return frame.f_context == hProxy.f_context
            ? hTarget.getTemplate().invokeN(frame, chain, hTarget, ahVar, aiReturn)
            : makeAsyncHandle(hTarget, chain).callN(frame, hProxy, ahVar, aiReturn);
        }

    @Override
    public int getPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ProxyHandle hProxy = (ProxyHandle) hTarget;

        hTarget = hProxy.f_hTarget;

        ClassTemplate template = hTarget.getTemplate();
        return frame.f_context == hProxy.f_context
            ? template.getPropertyValue(frame, hTarget, idProp, iReturn)
            : hProxy.f_context.sendProperty01Request(frame, hTarget, idProp, iReturn, template::getPropertyValue);
        }

    @Override
    public int getFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        ProxyHandle hProxy = (ProxyHandle) hTarget;

        hTarget = hProxy.f_hTarget;

        if (frame.f_context == hProxy.f_context)
            {
            return hTarget.getTemplate().getFieldValue(frame, hTarget, idProp, iReturn);
            }

        throw new IllegalStateException("Invalid context");
        }

    @Override
    public int setPropertyValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp,
                                ObjectHandle hValue)
        {
        ProxyHandle hProxy = (ProxyHandle) hTarget;

        hTarget = hProxy.f_hTarget;

        ClassTemplate template = hTarget.getTemplate();
        return frame.f_context == hProxy.f_context
            ? template.setPropertyValue(frame, hTarget, idProp, hValue)
            : hProxy.f_context.sendProperty10Request(frame, hTarget, idProp, hValue, template::setPropertyValue);
        }

    @Override
    public int setFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp,
                             ObjectHandle hValue)
        {
        ProxyHandle hProxy = (ProxyHandle) hTarget;

        hTarget = hProxy.f_hTarget;

        if (frame.f_context == hProxy.f_context)
            {
            return hTarget.getTemplate().setFieldValue(frame, hTarget, idProp, hValue);
            }

        throw new IllegalStateException("Invalid context");
        }

    @Override
    public int createPropertyRef(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, boolean fRO, int iReturn)
        {
        ProxyHandle hProxy = (ProxyHandle) hTarget;

        hTarget = hProxy.f_hTarget;

        if (frame.f_context == hProxy.f_context)
            {
            return hTarget.getTemplate().createPropertyRef(frame, hTarget, idProp, fRO, iReturn);
            }

        throw new IllegalStateException("Invalid context");
        }

    public static ObjectHandle makeHandle(ProxyComposition clzProxy, ServiceContext ctx,
                                          ObjectHandle hTarget)
        {
        return new ProxyHandle(clzProxy, ctx, hTarget);
        }

    private FunctionHandle makeAsyncNativeHandle(ObjectHandle hTarget, MethodStructure method)
        {
        return new AsyncHandle(method)
            {
            @Override
            protected ObjectHandle getContextTarget(Frame frame, ObjectHandle hService)
                {
                return hTarget;
                }
            };
        }

    private FunctionHandle makeAsyncHandle(ObjectHandle hTarget, CallChain chain)
        {
        return new AsyncHandle(chain)
            {
            @Override
            protected ObjectHandle getContextTarget(Frame frame, ObjectHandle hService)
                {
                return hTarget;
                }
            };
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    public static class ProxyHandle
            extends ServiceHandle
        {
        protected final ObjectHandle f_hTarget;

        protected ProxyHandle(ProxyComposition clazz, ServiceContext context,
                              ObjectHandle hTarget)
            {
            super(clazz, context);

            f_hTarget = hTarget;
            }

        public ObjectHandle getTarget()
            {
            return f_hTarget;
            }

        @Override
        public ProxyComposition getComposition()
            {
            return (ProxyComposition) super.getComposition();
            }

        @Override
        public boolean isShared(ConstantPool poolThat, Map<ObjectHandle, Boolean> mapVisited)
            {
            return false;
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

            return obj instanceof ProxyHandle && ((ProxyHandle) obj).f_hTarget == f_hTarget;
            }

        @Override
        public String toString()
            {
            return "Proxy: " + f_hTarget.toString() + " as " + getType().getValueString();
            }
        }
    }
