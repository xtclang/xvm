package org.xvm.runtime.template;


import java.util.concurrent.CompletableFuture;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xService.ServiceHandle;
import org.xvm.runtime.template.annotations.xFutureVar;


/**
 * TODO:
 */
public class xFunction
        extends ClassTemplate
    {
    public static xFunction INSTANCE;

    public xFunction(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

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
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public ObjectHandle createProxyHandle(ServiceContext ctx, ObjectHandle hTarget)
        {
        return ((FunctionHandle) hTarget).createProxyHandle(ctx);
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof MethodConstant)
            {
            MethodConstant constFunction = (MethodConstant) constant;
            MethodStructure function = (MethodStructure) constFunction.getComponent();

            assert function.isFunction();

            TypeConstant typeFunction = function.getIdentityConstant().getType();

            frame.pushStack(new FunctionHandle(ensureClass(typeFunction), function));
            return Op.R_NEXT;
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        FunctionHandle hFunc = (FunctionHandle) hTarget;

        switch (sPropName)
            {
            case "hash":
                return frame.assignValue(iReturn, xInt64.makeHandle(hFunc.getMethod().hashCode()));
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    public static class FunctionHandle
            extends ObjectHandle
        {
        // a function to call
        protected final MethodStructure f_function;

        // a method call chain (not null only if function is null)
        protected final CallChain f_chain;
        protected final int f_nDepth;

        protected FunctionHandle(TypeComposition clazz, MethodStructure function)
            {
            super(clazz);

            f_function = function;
            f_chain = null;
            f_nDepth = 0;
            }

        protected FunctionHandle(TypeComposition clazz, CallChain chain, int nDepth)
            {
            super(clazz);

            f_function = null;
            f_chain = chain;
            f_nDepth = nDepth;
            }

        public MethodStructure getMethod()
            {
            return f_function == null ? f_chain.getMethod(f_nDepth) : f_function;
            }

        public int getParamCount()
            {
            return getMethod().getParamCount();
            }

        public int getVarCount()
            {
            return getMethod().getMaxVars();
            }

        // ----- FunctionHandle interface -----

        // call with one return value to be placed into the specified slot
        // return either R_CALL, R_NEXT or R_BLOCK
        public int call1(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
            {
            ObjectHandle[] ahVar = prepareVars(ahArg);

            addBoundArguments(ahVar);

            return call1Impl(frame, hTarget, ahVar, iReturn);
            }

        // call with one return Tuple value to be placed into the specified slot
        public int callT(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
            {
            ObjectHandle[] ahVar = prepareVars(ahArg);

            addBoundArguments(ahVar);

            return callTImpl(frame, hTarget, ahVar, iReturn);
            }

        // calls with multiple return values
        public int callN(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
            {
            ObjectHandle[] ahVar = prepareVars(ahArg);

            addBoundArguments(ahVar);

            return callNImpl(frame, hTarget, ahVar, aiReturn);
            }

        /**
         * Bind the target.
         *
         * @param hArg  the argument to bind the target to
         *
         * @return a bound FunctionHandle
         */
        public FunctionHandle bindTarget(ObjectHandle hArg)
            {
            return new SingleBoundHandle(m_clazz, this, -1, hArg);
            }

        /**
         * Bind the specified argument.
         *
         * @param iArg  teh argument's index
         * @param hArg  the argument to bind the argument to
         *
         * @return a bound FunctionHandle
         */
        public FunctionHandle bind(int iArg, ObjectHandle hArg)
            {
            return new SingleBoundHandle(m_clazz, this, iArg, hArg);
            }

        /**
         * Bind all arguments.
         *
         * @param ahArg  the argument array to bind the arguments to
         *
         * @return a fully bound FunctionHandle
         */
        public FullyBoundHandle bindArguments(ObjectHandle[] ahArg)
            {
            return new FullyBoundHandle(m_clazz, this, ahArg);
            }

        // ----- internal implementation -----

        protected ObjectHandle[] prepareVars(ObjectHandle[] ahArg)
            {
            return Utils.ensureSize(ahArg, getVarCount());
            }

        // invoke with zero or one return to be placed into the specified register;
        protected int call1Impl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            return f_function == null ?
                    frame.invoke1(f_chain, f_nDepth, hTarget, ahVar, iReturn) :
                    frame.call1(f_function, hTarget, ahVar, iReturn);
            }

        // invoke with one return Tuple value to be placed into the specified register;
        protected int callTImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            return f_function == null ?
                    frame.invokeT(f_chain, f_nDepth, hTarget, ahVar, iReturn) :
                    frame.callT(f_function, hTarget, ahVar, iReturn);
            }

        // invoke with multiple return values;
        protected int callNImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
            {
            return f_function == null ?
                    frame.invokeN(f_chain, f_nDepth, hTarget, ahVar, aiReturn) :
                    frame.callN(f_function, hTarget, ahVar, aiReturn);
            }

        protected void addBoundArguments(ObjectHandle[] ahVar)
            {
            }

        protected FunctionHandle createProxyHandle(ServiceContext ctx)
            {
            // we shouldn't get here since a simple FunctionHandle is immutable
            throw new IllegalStateException();
            }

        @Override
        public int hashCode()
            {
            return getMethod().hashCode();
            }

        @Override
        public boolean equals(Object obj)
            {
            if (obj == this)
                {
                return true;
                }

            if (obj instanceof FunctionHandle)
                {
                FunctionHandle that = (FunctionHandle) obj;
                return this.getMethod().equals(that.getMethod());
                }
            return false;
            }

        @Override
        public String toString()
            {
            return "Function: " + getMethod();
            }
        }

    public static class DelegatingHandle
            extends FunctionHandle
        {
        protected FunctionHandle m_hDelegate;

        protected DelegatingHandle(TypeComposition clazz, FunctionHandle hDelegate)
            {
            super(clazz, hDelegate == null ? null : hDelegate.f_function);

            m_hDelegate = hDelegate;
            }

        @Override
        public boolean isMutable()
            {
            return m_hDelegate.isMutable();
            }

        @Override
        protected int call1Impl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            return m_hDelegate.call1Impl(frame, hTarget, ahVar, iReturn);
            }

        @Override
        protected int callTImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            return m_hDelegate.callTImpl(frame, hTarget, ahVar, iReturn);
            }

        @Override
        protected int callNImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
            {
            return m_hDelegate.callNImpl(frame, hTarget, ahVar, aiReturn);
            }

        @Override
        protected void addBoundArguments(ObjectHandle[] ahVar)
            {
            m_hDelegate.addBoundArguments(ahVar);
            }

        @Override
        public MethodStructure getMethod()
            {
            return m_hDelegate.getMethod();
            }

        @Override
        public int getParamCount()
            {
            return m_hDelegate.getParamCount();
            }

        @Override
        public int getVarCount()
            {
            return m_hDelegate.getVarCount();
            }

        @Override
        public boolean equals(Object obj)
            {
            return super.equals(obj) && obj instanceof DelegatingHandle &&
                m_hDelegate.equals(((DelegatingHandle) obj).m_hDelegate);
            }

        @Override
        public String toString()
            {
            return getClass().getSimpleName() + " -> " + m_hDelegate;
            }
        }

    public static class NativeFunctionHandle
            extends FunctionHandle
        {
        final protected xService.NativeOperation f_op;

        public NativeFunctionHandle(xService.NativeOperation op)
            {
            super(INSTANCE.getCanonicalClass(), null);

            f_op = op;
            }

        @Override
        public int call1(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
            {
            return f_op.invoke(frame, ahArg, iReturn);
            }

        @Override
        public MethodStructure getMethod()
            {
            throw new UnsupportedOperationException();
            }

        @Override
        public int getParamCount()
            {
            throw new UnsupportedOperationException();
            }

        @Override
        public int getVarCount()
            {
            return getParamCount();
            }

        @Override
        public String toString()
            {
            return "Native function: " + f_op;
            }
        }

    // one parameter bound function
    public static class SingleBoundHandle
            extends DelegatingHandle
        {
        protected int m_iArg; // the bound argument index; -1 stands for the target binding
        protected ObjectHandle m_hArg;
        protected TypeConstant m_type; // cached resolved type

        protected SingleBoundHandle(TypeComposition clazz, FunctionHandle hDelegate,
                                    int iArg, ObjectHandle hArg)
            {
            super(clazz, hDelegate);

            m_iArg = iArg;
            m_hArg = hArg;
            }

        @Override
        public boolean isMutable()
            {
            return m_hArg.isMutable() || super.isMutable();
            }

        @Override
        public TypeConstant getType()
            {
            TypeConstant type = m_type;
            if (type == null)
                {
                type = m_hDelegate.getType(); // Function<Tuple<Params>, Tuple<Returns>>

                int iArg = m_iArg;
                if (iArg >= 0)
                    {
                    ConstantPool pool = ConstantPool.getCurrentPool();

                    TypeConstant typeP = type.getParamTypesArray()[0];
                    TypeConstant typeR = type.getParamTypesArray()[1];

                    int cParamsNew = typeP.getParamsCount() - 1;
                    assert typeP.isTuple() && iArg <= cParamsNew;

                    TypeConstant[] atypeParams = typeP.getParamTypesArray();
                    if (cParamsNew == 0)
                        {
                        // canonical Tuple represents Void
                        typeP = pool.ensureParameterizedTypeConstant(pool.typeTuple());
                        }
                    else
                        {
                        TypeConstant[] atypeNew = new TypeConstant[cParamsNew];
                        if (iArg > 0)
                            {
                            System.arraycopy(atypeParams, 0, atypeNew, 0, iArg);
                            }
                        if (iArg < cParamsNew)
                            {
                            System.arraycopy(atypeParams, iArg + 1, atypeNew, iArg, cParamsNew - iArg);
                            }
                        typeP = pool.ensureParameterizedTypeConstant(pool.typeTuple(), atypeNew);
                        }
                    type = pool.ensureParameterizedTypeConstant(pool.typeFunction(), typeP, typeR);
                    }
                m_type = type;
                }
            return type;
            }

        @Override
        protected int call1Impl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            if (m_iArg == -1)
                {
                assert hTarget == null;
                hTarget = m_hArg;
                }
            return super.call1Impl(frame, hTarget, ahVar, iReturn);
            }

        @Override
        protected int callTImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            if (m_iArg == -1)
                {
                assert hTarget == null;
                hTarget = m_hArg;
                }
            return super.callTImpl(frame, hTarget, ahVar, iReturn);
            }

        @Override
        protected int callNImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
            {
            if (m_iArg == -1)
                {
                assert hTarget == null;
                hTarget = m_hArg;
                }
            return super.callNImpl(frame, hTarget, ahVar, aiReturn);
            }

        @Override
        protected FunctionHandle createProxyHandle(ServiceContext ctx)
            {
            return new ProxyHandle(this, ctx);
            }

        @Override
        protected void addBoundArguments(ObjectHandle[] ahVar)
            {
            if (m_iArg >= 0)
                {
                int cMove = getVarCount() - (m_iArg + 1); // number of args to move to the right
                if (cMove > 0)
                    {
                    System.arraycopy(ahVar, m_iArg, ahVar, m_iArg + 1, cMove);
                    }
                ahVar[m_iArg] = m_hArg;
                }

            super.addBoundArguments(ahVar);
            }

        @Override
        public boolean equals(Object obj)
            {
            if (super.equals(obj) && obj instanceof SingleBoundHandle)
                {
                ObjectHandle hArgThis = m_hArg;
                ObjectHandle hArgThat = ((SingleBoundHandle) obj).m_hArg;

                return hArgThis.isNativeEqual() && hArgThat.isNativeEqual() &&
                    hArgThis.equals(hArgThat);
                }
            return false;
            }
        }

    // all parameter bound method or function (the target is not bound)
    public static class FullyBoundHandle
            extends DelegatingHandle
        {
        protected final ObjectHandle[] f_ahArg;
        protected FullyBoundHandle m_next;

        protected FullyBoundHandle(TypeComposition clazz, FunctionHandle hDelegate, ObjectHandle[] ahArg)
            {
            super(clazz, hDelegate);

            f_ahArg = ahArg;
            }

        @Override
        public boolean isMutable()
            {
            for (ObjectHandle hArg : f_ahArg)
                {
                if (hArg.isMutable())
                    {
                    return true;
                    }
                }
            return super.isMutable();
            }

        @Override
        public TypeConstant getType()
            {
            return INSTANCE.getCanonicalType();
            }

        @Override
        protected void addBoundArguments(ObjectHandle[] ahVar)
            {
            super.addBoundArguments(ahVar);

            // to avoid extra array creation, the argument array may contain unused null elements
            System.arraycopy(f_ahArg, 0, ahVar, 0, Math.min(ahVar.length, f_ahArg.length));
            }

        public FullyBoundHandle chain(FullyBoundHandle handle)
            {
            if (handle != NO_OP)
                {
                m_next = handle;
                }
            return this;
            }

        // @return R_CALL or R_NEXT (see NO_OP override)
        public int callChain(Frame frame, ObjectHandle hTarget, Frame.Continuation continuation)
            {
            Frame frameNext = chainFrames(frame, hTarget, continuation);

            return frame.call(frameNext);
            }

        // @return the very first frame to be called
        protected Frame chainFrames(Frame frame, ObjectHandle hTarget, Frame.Continuation continuation)
            {
            Frame frameSave = frame.m_frameNext;

            call1(frame, hTarget, Utils.OBJECTS_NONE, Op.A_IGNORE);

            // TODO: what if this function is async and frameThis is null
            Frame frameThis = frame.m_frameNext;

            frame.m_frameNext = frameSave;

            if (m_next == null)
                {
                frameThis.addContinuation(continuation);
                return frameThis;
                }

            Frame frameNext = m_next.chainFrames(frame, hTarget, continuation);
            frameThis.addContinuation(frameCaller -> frameCaller.call(frameNext));
            return frameThis;
            }

        public static FullyBoundHandle NO_OP = new FullyBoundHandle(
                INSTANCE.getCanonicalClass(), null, null)
            {
            @Override
            public int callChain(Frame frame, ObjectHandle hTarget, Frame.Continuation continuation)
                {
                return continuation.proceed(frame);
                }

            @Override
            public FullyBoundHandle chain(FullyBoundHandle handle)
                {
                return handle;
                }

            @Override
            public String toString()
                {
                return "NO_OP";
                }
            };
        }

    public static class AsyncHandle
            extends FunctionHandle
        {
        /**
         * Create an asynchronous method handle.
         *
         * @param clazz   the class composition for this handle
         * @param chain   the call chain
         */
        protected AsyncHandle(ClassComposition clazz, CallChain chain)
            {
            super(clazz, chain, 0);
            }

        /**
         * Create an asynchronous native method handle.
         *
         * @param clazz   the class composition for this handle
         * @param method  the native method
         */
        protected AsyncHandle(ClassComposition clazz, MethodStructure method)
            {
            super(clazz, method);

            assert method.isNative();
            }

        // ----- FunctionHandle interface ----------------------------------------------------------

        @Override
        protected int call1Impl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            ServiceHandle hService = (ServiceHandle) hTarget;

            if (frame.f_context == hService.m_context)
                {
                return f_function != null && f_function.isNative()
                    ? ahVar.length == 1
                        ? hService.getTemplate().invokeNative1(frame, f_function, hTarget, ahVar[0], iReturn)
                        : hService.getTemplate().invokeNativeN(frame, f_function, hTarget, ahVar, iReturn)
                    : super.call1Impl(frame, hTarget, ahVar, iReturn);
                }

            if (!validateImmutable(frame.f_context, ahVar))
                {
                return frame.raiseException(xException.mutableObject());
                }

            int cReturns = iReturn == Op.A_IGNORE ? 0 : 1;

            CompletableFuture<ObjectHandle> cfResult = hService.m_context.sendInvoke1Request(
                frame, this, ahVar, cReturns);

            // in the case of zero returns - fire and forget
            return cReturns == 0 ? Op.R_NEXT : assignResult(frame, iReturn, cfResult);
            }

        @Override
        protected int callTImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            ServiceHandle hService = (ServiceHandle) hTarget;

            if (frame.f_context == hService.m_context)
                {
                return f_function != null && f_function.isNative()
                    ? hService.getTemplate().invokeNativeT(frame, f_function, hTarget, ahVar, iReturn)
                    : super.callTImpl(frame, hTarget, ahVar, iReturn);
                }

            if (!validateImmutable(frame.f_context, ahVar))
                {
                return frame.raiseException(xException.mutableObject());
                }

            if (true)
                {
                // TODO: add a "return a Tuple back" flag
                throw new UnsupportedOperationException();
                }

            CompletableFuture<ObjectHandle> cfResult = hService.m_context.sendInvoke1Request(
                    frame, this, ahVar, 1);

            return assignResult(frame, iReturn, cfResult);
            }

        @Override
        protected int callNImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
            {
            ServiceHandle hService = (ServiceHandle) hTarget;

            if (frame.f_context == hService.m_context)
                {
                return f_function != null && f_function.isNative()
                    ? hService.getTemplate().invokeNativeNN(frame, f_function, hTarget, ahVar, aiReturn)
                    : super.callNImpl(frame, hTarget, ahVar, aiReturn);
                }

            if (!validateImmutable(frame.f_context, ahVar))
                {
                return frame.raiseException(xException.mutableObject());
                }

            int cReturns = aiReturn.length;

            CompletableFuture<ObjectHandle[]> cfResult = hService.m_context.sendInvokeNRequest(
                frame, this, ahVar, cReturns);

            if (cReturns == 0)
                {
                // fire and forget
                return Op.R_NEXT;
                }

            if (cReturns == 1)
                {
                CompletableFuture<ObjectHandle> cfReturn =
                    cfResult.thenApply(ahResult -> ahResult[0]);
                return assignResult(frame, aiReturn[0], cfReturn);
                }

            return frame.call(Utils.createWaitFrame(frame, cfResult, aiReturn));
            }
        }

    public static class ProxyHandle
            extends DelegatingHandle
        {
        // the origin context of the mutable FunctionHandle
        final private ServiceContext f_ctx;

        protected ProxyHandle(FunctionHandle fn, ServiceContext ctx)
            {
            super(fn.getComposition(), fn);

            f_ctx = ctx;
            }

        // ----- FunctionHandle interface ----------------------------------------------------------

        @Override
        protected int call1Impl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            // hTarget is the service handle that is of no use for us now

            if (frame.f_context == f_ctx)
                {
                return super.call1Impl(frame, null, ahVar, iReturn);
                }

            if (!validateImmutable(frame.f_context, ahVar))
                {
                return frame.raiseException(xException.mutableObject());
                }

            int cReturns = iReturn == Op.A_IGNORE ? 0 : 1;

            CompletableFuture<ObjectHandle> cfResult = f_ctx.sendInvoke1Request(
                frame, this, ahVar, cReturns);

            // in the case of zero returns - fire and forget
            return cReturns == 0 ? Op.R_NEXT : assignResult(frame, iReturn, cfResult);
            }

        @Override
        protected int callTImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            // hTarget is the service handle that is of no use for us now

            if (frame.f_context == f_ctx)
                {
                return super.callTImpl(frame, null, ahVar, iReturn);
                }

            if (!validateImmutable(frame.f_context, ahVar))
                {
                return frame.raiseException(xException.mutableObject());
                }

            if (true)
                {
                // TODO: add a "return a Tuple back" flag
                throw new UnsupportedOperationException();
                }

            CompletableFuture<ObjectHandle> cfResult = f_ctx.sendInvoke1Request(
                    frame, this, ahVar, 1);

            return assignResult(frame, iReturn, cfResult);
            }

        @Override
        protected int callNImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
            {
            // hTarget is the service handle that is of no use for us now

            if (frame.f_context == f_ctx)
                {
                return super.callNImpl(frame, null, ahVar, aiReturn);
                }

            if (!validateImmutable(frame.f_context, ahVar))
                {
                return frame.raiseException(xException.mutableObject());
                }

            int cReturns = aiReturn.length;

            CompletableFuture<ObjectHandle[]> cfResult = f_ctx.sendInvokeNRequest(
                frame, this, ahVar, cReturns);

            if (cReturns == 0)
                {
                // fire and forget
                return Op.R_NEXT;
                }

            if (cReturns == 1)
                {
                CompletableFuture<ObjectHandle> cfReturn =
                    cfResult.thenApply(ahResult -> ahResult[0]);
                return assignResult(frame, aiReturn[0], cfReturn);
                }

            return frame.call(Utils.createWaitFrame(frame, cfResult, aiReturn));
            }
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return true iff all the arguments are immutable
     */
    static private boolean validateImmutable(ServiceContext ctx, ObjectHandle[] ahArg)
        {
        // Note: this logic could be moved to ServiceContext.sendInvokeXXX()
        for (int i = 0, c = ahArg.length; i < c; i++)
            {
            ObjectHandle hArg = ahArg[i];
            if (hArg == null)
                {
                // arguments tail is always empty
                break;
                }

            if (hArg.isMutable() && !hArg.getTemplate().isService())
                {
                hArg = hArg.getTemplate().createProxyHandle(ctx, hArg);
                if (hArg == null)
                    {
                    return false;
                    }
                ahArg[i] = hArg;
                }
            }
        return true;
        }

    static private int assignResult(Frame frame, int iReturn, CompletableFuture<ObjectHandle> cfResult)
        {
        if (iReturn >= 0)
            {
            return frame.assignValue(iReturn, xFutureVar.makeHandle(cfResult), true);
            }

        // the return value is either a A_LOCAL or a local property;
        // in either case there is no "VarInfo" to mark as "waiting", so we need to create
        // a pseudo frame to deal with the wait
        return frame.call(Utils.createWaitFrame(frame, cfResult, iReturn));
        }

    /**
     * Create a function handle representing an asynchronous (service) call.
     *
     * @param chain the method chain
     *
     * @return the corresponding function handle
     */
    public static AsyncHandle makeAsyncHandle(CallChain chain)
        {
        TypeConstant typeFunction = chain.getMethod(0).getIdentityConstant().getType();

        return new AsyncHandle(INSTANCE.ensureClass(typeFunction), chain);
        }

    /**
     * Create a function handle representing an asynchronous (service) native call.
     *
     * @param method  the method structure
     *
     * @return the corresponding function handle
     */
    public static AsyncHandle makeAsyncNativeHandle(MethodStructure method)
        {
        assert method.isNative();

        TypeConstant typeFunction = method.getIdentityConstant().getType();

        return new AsyncHandle(INSTANCE.ensureClass(typeFunction), method);
        }

    public static FunctionHandle makeHandle(CallChain chain, int nDepth)
        {
        TypeConstant typeFunction = chain.getMethod(nDepth).getIdentityConstant().getType();

        return new FunctionHandle(INSTANCE.ensureClass(typeFunction), chain, nDepth);
        }

    public static FunctionHandle makeHandle(MethodStructure function)
        {
        TypeConstant typeFunction = function.getIdentityConstant().getType();

        return new FunctionHandle(INSTANCE.ensureClass(typeFunction), function);
        }
    }
