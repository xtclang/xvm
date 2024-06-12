package org.xvm.runtime.template;


import java.util.Arrays;
import java.util.Map;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ProxyComposition;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xTuple;
import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction.AsyncHandle;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * Template for proxied objects.
 */
public class Proxy
        extends xService
    {
    public static Proxy INSTANCE;

    public Proxy(Container container)
        {
        super(container, xObject.INSTANCE.getStructure(), false);

        INSTANCE = this;
        }

    @Override
    public void initNative()
        {
        }

    @Override
    public ClassComposition ensureClass(Container container, TypeConstant typeActual)
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
    public int createProxyHandle(Frame frame, ServiceContext ctxTarget, ObjectHandle hTarget,
                                 TypeConstant typeProxy)
        {
        ProxyHandle hProxy = (ProxyHandle) hTarget;
        if (ctxTarget != hProxy.f_context)
            {
            return frame.raiseException("Out of context \"" + hProxy.f_context.f_sName + "\" service");
            }

        if (hProxy.getType().equals(typeProxy))
            {
            return frame.assignValue(Op.A_STACK, hProxy);
            }

        ProxyComposition clzProxy = new ProxyComposition(
                hProxy.getComposition().getOrigin(), typeProxy);
        return frame.assignValue(Op.A_STACK,
                Proxy.makeHandle(clzProxy, ctxTarget, hProxy.getTarget(), hProxy.f_fStrict));
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        ProxyHandle hProxy = (ProxyHandle) hTarget;

        hTarget = hProxy.f_hTarget;

        return frame.f_context == hProxy.f_context
            ? hTarget.getTemplate().invokeNative1(frame, method, hTarget, hArg, iReturn)
            : makeAsyncNativeHandle(hTarget, method).
                call1(frame, hProxy, new ObjectHandle[]{hArg}, iReturn);
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
            : makeAsyncHandle(hProxy, chain).call1(frame, hProxy, ahVar, iReturn);
        }

    @Override
    public int invokeT(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
        {
        ProxyHandle hProxy = (ProxyHandle) hTarget;

        hTarget = hProxy.f_hTarget;

        return frame.f_context == hProxy.f_context
            ? hTarget.getTemplate().invokeT(frame, chain, hTarget, ahVar, iReturn)
            : makeAsyncHandle(hProxy, chain).callT(frame, hProxy, ahVar, iReturn);
        }

    @Override
    public int invokeN(Frame frame, CallChain chain, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
        {
        ProxyHandle hProxy = (ProxyHandle) hTarget;

        hTarget = hProxy.f_hTarget;

        return frame.f_context == hProxy.f_context
            ? hTarget.getTemplate().invokeN(frame, chain, hTarget, ahVar, aiReturn)
            : makeAsyncHandle(hProxy, chain).callN(frame, hProxy, ahVar, aiReturn);
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

    public static ProxyHandle makeHandle(ProxyComposition clzProxy, ServiceContext ctx,
                                          ObjectHandle hTarget, Boolean fStrict)
        {
        return new ProxyHandle(clzProxy, ctx, hTarget, fStrict);
        }

    /**
     * Create a function handle representing a native invocation against a proxy handle across
     * service boundaries.
     */
    private FunctionHandle makeAsyncNativeHandle(ObjectHandle hTarget, MethodStructure method)
        {
        return new AsyncHandle(INSTANCE.f_container, method)
            {
            @Override
            protected ObjectHandle getContextTarget(Frame frame, ObjectHandle hService)
                {
                return hTarget;
                }
            };
        }

    /**
     * Create a function handle representing an invocation against a proxy handle across service
     * boundaries.
     */
    private FunctionHandle makeAsyncHandle(ProxyHandle hProxy, CallChain chain)
        {
        ObjectHandle hTarget = hProxy.f_hTarget;

        return new AsyncHandle(hTarget.getComposition().getContainer(), chain)
            {
            @Override
            protected ObjectHandle getContextTarget(Frame frame, ObjectHandle hService)
                {
                return hTarget;
                }

            @Override
            protected int call1Impl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
                {
                assert hTarget.isService();

                if (frame.f_context == hTarget.getService().f_context)
                    {
                    switch (super.call1Impl(frame, hTarget, ahVar, Op.A_STACK))
                        {
                        case Op.R_NEXT:
                            return convertResult(frame, hProxy, iReturn);

                        case Op.R_CALL:
                            {
                            Frame.Continuation stepNext = frameCaller ->
                                convertResult(frameCaller, hProxy, iReturn);
                            frame.m_frameNext.addContinuation(stepNext);
                            return Op.R_CALL;
                            }

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }
                    }
                else
                    {
                    return super.call1Impl(frame, hTarget, ahVar, iReturn);
                    }
                }

            @Override
            protected int callTImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
                {
                assert hTarget.isService();

                if (frame.f_context == hTarget.getService().f_context)
                    {
                    switch (super.callTImpl(frame, hTarget, ahVar, Op.A_STACK))
                        {
                        case Op.R_NEXT:
                            return convertTupleResult(frame, hProxy, iReturn);

                        case Op.R_CALL:
                            {
                            Frame.Continuation stepNext = frameCaller ->
                                convertTupleResult(frameCaller, hProxy, iReturn);
                            frame.m_frameNext.addContinuation(stepNext);
                            return Op.R_CALL;
                            }

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }
                    }
                else
                    {
                    return super.callTImpl(frame, hTarget, ahVar, iReturn);
                    }
                }

            @Override
            protected int callNImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
                {
                assert hTarget.isService();

                if (frame.f_context == hTarget.getService().f_context)
                    {
                    int[] aiStack = new int[aiReturn.length];
                    Arrays.fill(aiStack, Op.A_STACK);

                    switch (super.callNImpl(frame, hTarget, ahVar, aiStack))
                        {
                        case Op.R_NEXT:
                            return convertResults(frame, getMethod(), hProxy, aiReturn);

                        case Op.R_CALL:
                            {
                            Frame.Continuation stepNext = frameCaller ->
                                convertResults(frameCaller, getMethod(), hProxy, aiReturn);
                            frame.m_frameNext.addContinuation(stepNext);
                            return Op.R_CALL;
                            }

                        case Op.R_EXCEPTION:
                            return Op.R_EXCEPTION;

                        default:
                            throw new IllegalStateException();
                        }
                    }
                else
                    {
                    return super.callNImpl(frame, hTarget, ahVar, aiReturn);
                    }
                }

            /**
             * Convert the result of the proxy target call (on the frame's stack), replacing the
             * target handle in the result (e.g.: "return this") with the proxy handle.
             */
            private int convertResult(Frame frame, ProxyHandle hProxy, int iReturn)
                {
                return frame.assignValue(iReturn, convertValue(hProxy, frame.popStack()));
                }

            /**
             * Convert the Tuple result of the proxy target call (on the frame's stack), replacing the
             * target handle in the result (e.g.: "return (this, this)") with the proxy handle.
             */
            private int convertTupleResult(Frame frame, ProxyHandle hProxy, int iReturn)
                {
                TupleHandle    hTuple     = (TupleHandle) frame.popStack();
                ObjectHandle[] ahValueOld = hTuple.m_ahValue;
                ObjectHandle[] ahValueNew = ahValueOld;

                for (int i = 0, c = ahValueOld.length; i < c; i++)
                    {
                    ObjectHandle hValueOld = ahValueOld[i];
                    ObjectHandle hValueNew = convertValue(hProxy, hValueOld);
                    if (hValueNew != hValueOld)
                        {
                        if (ahValueNew == ahValueOld)
                            {
                            ahValueNew = ahValueOld.clone();
                            }
                        ahValueNew[i] = hValueNew;
                        }
                    }
                if (ahValueNew != ahValueOld)
                    {
                    hTuple = xTuple.makeHandle(hTuple.getComposition(), ahValueNew);
                    }
                return frame.assignValue(iReturn, hTuple);
                }

            /**
             * Convert the results of the proxy target call (on the frame's stack), replacing the
             * target handle in the results (e.g.: "return True, this") with the proxy handle.
             */
            private int convertResults(Frame frame, MethodStructure method,
                                       ProxyHandle hProxy, int[] aiReturn)
                {
                int            cReturns = aiReturn.length;
                ObjectHandle[] ahReturn = new ObjectHandle[cReturns];
                for (int i = cReturns - 1; i >= 0; i--)
                    {
                    ObjectHandle hReturn = frame.popStack();
                    if (hReturn == xBoolean.FALSE && i == cReturns-1 &&
                            method.isConditionalReturn())
                        {
                        // conditional False
                        return frame.assignValue(aiReturn[0], hReturn);
                        }
                    ahReturn[i] = convertValue(hProxy, hReturn);
                    }
                return frame.assignValues(aiReturn, ahReturn);
                }

            /**
             * Given a proxy handle and a result of an invocation against its target, replace
             * the instances of the target in the result with the proxy.
             */
            private ObjectHandle convertValue(ProxyHandle hProxy, ObjectHandle hResult)
                {
                ObjectHandle hTarget = hProxy.f_hTarget;
                if (hResult == hTarget)
                    {
                    hResult = hProxy;
                    }
                // TODO scan the result's structure (e.g.: an array) and replace sub-elements
                return hResult;
                }
            };
        }


    // ----- ObjectHandle --------------------------------------------------------------------------

    public static class ProxyHandle
            extends ServiceHandle
        {
        /**
         * The underlying target.
         */
        protected final ObjectHandle f_hTarget;

        /**
         * If true, the proxy can *only* be seen/used as the "proxy type". Otherwise, if the caller
         * [container] can cast the underlying target to a type that is a part of its type system,
         * the proxy handle will allow it.
         */
        protected final boolean f_fStrict;

        protected ProxyHandle(ProxyComposition clazz, ServiceContext context,
                              ObjectHandle hTarget, boolean fStrict)
            {
            super(clazz, context);

            f_hTarget = hTarget;
            f_fStrict = fStrict;
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
        protected TypeConstant augmentType(TypeConstant type)
            {
            // don't augment the proxy type
            return type;
            }

        @Override
        public TypeConstant getUnsafeType()
            {
            return f_fStrict ? super.getUnsafeType() : f_hTarget.getUnsafeType();
            }

        @Override
        public ObjectHandle cloneAs(TypeComposition clazz)
            {
            return f_hTarget.cloneAs(clazz);
            }

        @Override
        public boolean isShared(ConstantPool poolThat, Map<ObjectHandle, Boolean> mapVisited)
            {
            return true;
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

            return obj instanceof ProxyHandle that && this.f_hTarget == that.f_hTarget;
            }

        @Override
        public String toString()
            {
            return "Proxy: " + f_hTarget.toString() +
                    (f_fStrict ? " as " + getType().getValueString() : "");
            }
        }
    }