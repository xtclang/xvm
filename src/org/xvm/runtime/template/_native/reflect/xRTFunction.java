package org.xvm.runtime.template._native.reflect;


import java.util.concurrent.CompletableFuture;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService;
import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;


/**
 * Native Function implementation.
 */
public class xRTFunction
        extends xRTSignature
    {
    public static xRTFunction INSTANCE;

    public xRTFunction(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        markNativeMethod("bind", new String[] {"Type<Object>", "reflect.Parameter", "Object"}, null);
        markNativeMethod("bind", new String[] {"collections.Map<reflect.Parameter, Object>"}, null);
        markNativeMethod("invoke", null, null);
        markNativeMethod("invokeAsync", null, null);

        super.initDeclared();
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof MethodConstant)
            {
            MethodConstant  idFunc     = (MethodConstant) constant;
            MethodStructure structFunc = (MethodStructure) idFunc.getComponent();

            assert structFunc.isFunction();

            frame.pushStack(new FunctionHandle(structFunc));
            return Op.R_NEXT;
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public ObjectHandle createProxyHandle(ServiceContext ctx, ObjectHandle hTarget,
                                          TypeConstant typeProxy)
        {
        assert typeProxy == null || typeProxy.isA(pool().typeFunction());

        return ((FunctionHandle) hTarget).createProxyHandle(ctx);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        FunctionHandle hFunc = (FunctionHandle) hTarget;
        switch (method.getName())
            {
            case "bind":
                return doBind(frame, hFunc, hArg, iReturn);
            case "invoke":
                return doInvoke(frame, hFunc, hArg, iReturn);
            case "invokeAsync":
                return doInvokeAsync(frame, hFunc, hArg, iReturn);
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        FunctionHandle hFunc = (FunctionHandle) hTarget;
        switch (method.getName())
            {
            case "bind":
                return doBind(frame, hFunc, ahArg, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }


    // ----- method implementations --------------------------------------------------------------

    /**
     * Method implementation: `Function!<> bind(Map<Parameter, Object> params)`
     */
    public int doBind(Frame frame, FunctionHandle hFunc, ObjectHandle hArg, int iReturn)
        {
        // TODO
        throw new UnsupportedOperationException();
        }

    /**
     * Method implementation: `Function!<> bind(Parameter<ParamType> param, ParamType value)`
     */
    public int doBind(Frame frame, FunctionHandle hFunc, ObjectHandle[] ahArg, int iReturn)
        {
        // TODO
        throw new UnsupportedOperationException();
        }

    /**
     * Method implementation: `@Op("()") ReturnTypes invoke(ParamTypes args)`
     */
    public int doInvoke(Frame frame, FunctionHandle hFunc, ObjectHandle hArg, int iReturn)
        {
        // TODO
        throw new UnsupportedOperationException();
        }

    /**
     * Method implementation: `FutureVar<ReturnTypes> invokeAsync(ParamTypes args)`
     */
    public int doInvokeAsync(Frame frame, FunctionHandle hFunc, ObjectHandle hArg, int iReturn)
        {
        // TODO
        throw new UnsupportedOperationException();
        }


    // ----- Object handle -------------------------------------------------------------------------

    /**
     * Function handle.
     *
     * Note that while for any other type it holds true that a canonical type is assignable from
     * any parameterized type based on the same defining constant, it's not so for functions.
     * For example, Function<<><>> is not assignable from Function<<Int><>>.
     *
     * As a result, all Function handles are based on the same canonical ClassComposition, but
     * carry the actual type as a part of their state,
     */
    public static class FunctionHandle
            extends SignatureHandle
        {
        protected FunctionHandle(MethodStructure function)
            {
            this(function.getIdentityConstant().getType(), function);
            }

        protected FunctionHandle(TypeConstant type, MethodStructure function)
            {
            super(INSTANCE.getCanonicalClass(), function == null ? null : function.getIdentityConstant(), function, type);
            }

        protected FunctionHandle(CallChain chain, int nDepth)
            {
            super(INSTANCE.getCanonicalClass(), chain, nDepth);
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
            return new SingleBoundHandle(f_type, this, -1, hArg);
            }

        /**
         * Bind the specified argument.
         *
         *
         * @param pool  the constant pool to use for creation a new function type
         * @param iArg  the argument's index
         * @param hArg  the argument to bind the argument to
         *
         * @return a bound FunctionHandle
         */
        public FunctionHandle bind(ConstantPool pool, int iArg, ObjectHandle hArg)
            {
            assert iArg >= 0;

            GenericTypeResolver resolver = null;
            MethodStructure     method   = getMethod();
            if (method != null)
                {
                Parameter parameter = method.getParam(iArg + calculateShift(iArg));
                if (parameter.isTypeParameter())
                    {
                    TypeHandle hType = (TypeHandle) hArg;
                    resolver = sName ->
                        sName.equals(parameter.getName())
                        ? hType.getDataType()
                        : null;
                    }
                }

            return new SingleBoundHandle(
                pool.bindFunctionParam(f_type, iArg, resolver), this, iArg, hArg);
            }

        /**
         * Calculate a shift for a given argument index indicating the difference between
         * the specified argument index and the actual index of the function parameter that
         * corresponds to this argument.
         *
         * @param iArg the argument to calculate the shift of
         *
         * @return the shift
         */
        protected int calculateShift(int iArg)
            {
            return 0;
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
            return new FullyBoundHandle(this, ahArg);
            }

        // ----- internal implementation -----

        protected ObjectHandle[] prepareVars(ObjectHandle[] ahArg)
            {
            return Utils.ensureSize(ahArg, getVarCount());
            }

        // invoke with zero or one return to be placed into the specified register;
        protected int call1Impl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            return f_method == null
                ? f_chain.isNative()
                    ? ahVar.length == 1
                        ? hTarget.getTemplate().invokeNative1(frame, f_chain.getTop(), hTarget, ahVar[0], iReturn)
                        : hTarget.getTemplate().invokeNativeN(frame, f_chain.getTop(), hTarget, ahVar, iReturn)
                    : frame.invoke1(f_chain, f_nDepth, hTarget, ahVar, iReturn)
                : f_method.isNative()
                    ? ahVar.length == 1
                        ? hTarget.getTemplate().invokeNative1(frame, f_method, hTarget, ahVar[0], iReturn)
                        : hTarget.getTemplate().invokeNativeN(frame, f_method, hTarget, ahVar, iReturn)
                    : frame.call1(f_method, hTarget, ahVar, iReturn);
            }

        // invoke with one return Tuple value to be placed into the specified register;
        protected int callTImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            return f_method == null
                ? f_chain.isNative()
                    ? hTarget.getTemplate().invokeNativeT(frame, f_chain.getTop(), hTarget, ahVar, iReturn)
                    : frame.invokeT(f_chain, f_nDepth, hTarget, ahVar, iReturn)
                : f_method.isNative()
                    ? hTarget.getTemplate().invokeNativeT(frame, f_method, hTarget, ahVar, iReturn)
                    : frame.callT(f_method, hTarget, ahVar, iReturn);
            }

        // invoke with multiple return values;
        protected int callNImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
            {
            return f_method == null
                ? f_chain.isNative()
                    ? hTarget.getTemplate().invokeNativeNN(frame, f_chain.getTop(), hTarget, ahVar, aiReturn)
                    : frame.invokeN(f_chain, f_nDepth, hTarget, ahVar, aiReturn)
                : f_method.isNative()
                    ? hTarget.getTemplate().invokeNativeNN(frame, f_method, hTarget, ahVar, aiReturn)
                    : frame.callN(f_method, hTarget, ahVar, aiReturn);
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

    protected abstract static class DelegatingHandle
            extends FunctionHandle
        {
        protected FunctionHandle m_hDelegate;

        protected DelegatingHandle(TypeConstant type, FunctionHandle hDelegate)
            {
            super(type, hDelegate == null ? null : hDelegate.f_method);

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

        public int getUnboundParamCount()
            {
            return m_hDelegate.getUnboundParamCount();
            }

        public Parameter getUnboundParam(int i)
            {
            return m_hDelegate.getUnboundParam(i);
            }

        @Override
        public int getVarCount()
            {
            return m_hDelegate.getVarCount();
            }

        @Override
        public boolean isAsync()
            {
            return m_hDelegate.isAsync();
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

    /**
     * TODO GG explain why this thing exists and where it is used and why it can't answer reflection questions
     */
    public static class NativeFunctionHandle
            extends FunctionHandle
        {
        final protected xService.NativeOperation f_op;

        public NativeFunctionHandle(xService.NativeOperation op)
            {
            super(INSTANCE.getCanonicalType(), null);

            f_op = op;
            }

        @Override
        public boolean isAsync()
            {
            return false;
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

        public int getUnboundParamCount()
            {
            throw new UnsupportedOperationException();
            }

        public Parameter getUnboundParam(int i)
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
        protected SingleBoundHandle(TypeConstant type, FunctionHandle hDelegate,
                                    int iArg, ObjectHandle hArg)
            {
            super(type, hDelegate);

            m_iArg = iArg;
            m_hArg = hArg;
            }

        @Override
        public boolean isMutable()
            {
            return m_hArg.isMutable() || super.isMutable();
            }

        @Override
        protected int calculateShift(int iArg)
            {
            int nShift = m_iArg == -1 || iArg < m_iArg ? 0 : 1;

            return nShift + m_hDelegate.calculateShift(iArg);
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
            return new FunctionProxyHandle(this, ctx);
            }

        @Override
        protected void addBoundArguments(ObjectHandle[] ahVar)
            {
            if (m_iArg >= 0)
                {
                int cMove = getParamCount() - (m_iArg + 1); // number of args to move to the right
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

        /**
         * The bound argument index indicating what position to inject the argument value at
         * during the {@link #addBoundArguments} call.
         * Value of -1 indicates the target binding.
         */
        protected int m_iArg;

        /**
         * The bound argument value.
         */
        protected ObjectHandle m_hArg;

        /**
         * Cached resolved type of a partially bound function represented by this handler.
         */
        protected TypeConstant m_type;
        }

    /**
     * A function handle for which all parameters are bound.
     */
    public static class FullyBoundHandle
            extends DelegatingHandle
        {
        protected final ObjectHandle[] f_ahArg;
        protected FullyBoundHandle m_next;

        protected FullyBoundHandle(FunctionHandle hDelegate, ObjectHandle[] ahArg)
            {
            super(INSTANCE.getCanonicalType(), hDelegate);

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

        public static FullyBoundHandle NO_OP = new FullyBoundHandle(null, null)
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
         * @param chain   the call chain
         */
        protected AsyncHandle(CallChain chain)
            {
            super(chain, 0);
            }

        /**
         * Create an asynchronous native method handle.
         *
         * @param method  the native method
         */
        protected AsyncHandle(MethodStructure method)
            {
            super(method);

            assert method.isNative();
            }

        // ----- FunctionHandle interface ----------------------------------------------------------

        @Override
        public boolean isAsync()
            {
            return true;
            }

        @Override
        protected int call1Impl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            ServiceHandle hService = (ServiceHandle) hTarget;

            if (frame.f_context == hService.m_context)
                {
                return super.call1Impl(frame, hTarget, ahVar, iReturn);
                }

            if (!validateImmutable(frame.f_context, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            int cReturns = iReturn == Op.A_IGNORE ? 0 : 1;

            CompletableFuture<ObjectHandle> cfResult = hService.m_context.sendInvoke1Request(
                frame, this, hService, ahVar, cReturns);

            // in the case of zero returns - fire and forget
            return cReturns == 0 ? Op.R_NEXT : frame.assignFutureResult(iReturn, cfResult);
            }

        @Override
        protected int callTImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            ServiceHandle hService = (ServiceHandle) hTarget;

            if (frame.f_context == hService.m_context)
                {
                return super.callTImpl(frame, hTarget, ahVar, iReturn);
                }

            if (!validateImmutable(frame.f_context, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            if (true)
                {
                // TODO: add a "return a Tuple back" flag
                throw new UnsupportedOperationException();
                }

            CompletableFuture<ObjectHandle> cfResult = hService.m_context.sendInvoke1Request(
                    frame, this, hService, ahVar, 1);

            return frame.assignFutureResult(iReturn, cfResult);
            }

        @Override
        protected int callNImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
            {
            ServiceHandle hService = (ServiceHandle) hTarget;

            if (frame.f_context == hService.m_context)
                {
                return super.callNImpl(frame, hTarget, ahVar, aiReturn);
                }

            if (!validateImmutable(frame.f_context, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            int cReturns = aiReturn.length;

            CompletableFuture<ObjectHandle[]> cfResult = hService.m_context.sendInvokeNRequest(
                frame, this, hService, ahVar, cReturns);

            if (cReturns == 0)
                {
                // fire and forget
                return Op.R_NEXT;
                }

            if (cReturns == 1)
                {
                CompletableFuture<ObjectHandle> cfReturn =
                    cfResult.thenApply(ahResult -> ahResult[0]);
                return frame.assignFutureResult(aiReturn[0], cfReturn);
                }

            // TODO replace with: assignFutureResults()
            return frame.call(Utils.createWaitFrame(frame, cfResult, aiReturn));
            }
        }

    public static class FunctionProxyHandle
            extends DelegatingHandle
        {
        // the origin context of the mutable FunctionHandle
        final private ServiceContext f_ctx;

        protected FunctionProxyHandle(FunctionHandle fn, ServiceContext ctx)
            {
            super(fn.getType(), fn);

            f_ctx = ctx;
            }

        // ----- FunctionHandle interface ----------------------------------------------------------

        @Override
        public int call1(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            // hTarget is the service handle that is of no use for us now

            if (frame.f_context == f_ctx)
                {
                return super.call1(frame, null, ahVar, iReturn);
                }

            if (!validateImmutable(frame.f_context, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            int cReturns = iReturn == Op.A_IGNORE ? 0 : 1;

            CompletableFuture<ObjectHandle> cfResult = f_ctx.sendInvoke1Request(
                frame, this, null, ahVar, cReturns);

            // in the case of zero returns - fire and forget
            return cReturns == 0 ? Op.R_NEXT : frame.assignFutureResult(iReturn, cfResult);
            }

        @Override
        public int callT(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            // hTarget is the service handle that is of no use for us now

            if (frame.f_context == f_ctx)
                {
                return super.callT(frame, null, ahVar, iReturn);
                }

            if (!validateImmutable(frame.f_context, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            if (true)
                {
                // TODO: add a "return a Tuple back" flag
                throw new UnsupportedOperationException();
                }

            CompletableFuture<ObjectHandle> cfResult = f_ctx.sendInvoke1Request(
                    frame, this, null, ahVar, 1);

            return frame.assignFutureResult(iReturn, cfResult);
            }

        @Override
        public int callN(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
            {
            // hTarget is the service handle that is of no use for us now

            if (frame.f_context == f_ctx)
                {
                return super.callN(frame, null, ahVar, aiReturn);
                }

            if (!validateImmutable(frame.f_context, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            int cReturns = aiReturn.length;

            CompletableFuture<ObjectHandle[]> cfResult = f_ctx.sendInvokeNRequest(
                frame, this, null, ahVar, cReturns);

            if (cReturns == 0)
                {
                // fire and forget
                return Op.R_NEXT;
                }

            if (cReturns == 1)
                {
                CompletableFuture<ObjectHandle> cfReturn =
                    cfResult.thenApply(ahResult -> ahResult[0]);
                return frame.assignFutureResult(aiReturn[0], cfReturn);
                }

            // TODO replace with: assignFutureResults()
            return frame.call(Utils.createWaitFrame(frame, cfResult, aiReturn));
            }
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return true iff all the arguments are immutable
     */
    static private boolean validateImmutable(ServiceContext ctx, MethodStructure method,
                                             ObjectHandle[] ahArg)
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

            if (hArg.isMutable() && !hArg.isService())
                {
                hArg = hArg.getTemplate().createProxyHandle(ctx, hArg, method.getParamTypes()[i]);
                if (hArg == null)
                    {
                    return false;
                    }
                ahArg[i] = hArg;
                }
            }
        return true;
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
        return new AsyncHandle(chain);
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

        return new AsyncHandle(method);
        }

    public static FunctionHandle makeHandle(CallChain chain, int nDepth)
        {
        return new FunctionHandle(chain, nDepth);
        }

    public static FunctionHandle makeHandle(MethodStructure function)
        {
        return new FunctionHandle(function);
        }
    }
