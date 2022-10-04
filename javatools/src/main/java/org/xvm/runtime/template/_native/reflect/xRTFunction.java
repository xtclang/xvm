package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xTuple;
import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xOrdered;
import org.xvm.runtime.template.xService;
import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xArray.Mutability;
import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template._native.reflect.xRTType.TypeHandle;


/**
 * Native Function implementation.
 */
public class xRTFunction
        extends xRTSignature
    {
    public static xRTFunction INSTANCE;

    public xRTFunction(Container container, ClassStructure structure, boolean fInstance)
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
        ConstantPool pool = f_container.getConstantPool();

        TO_ARRAY = getStructure().findMethod("toArray", 1);

        FUNCTION_ARRAY_TYPE  = pool.ensureArrayType(pool.typeFunction());
        EMPTY_FUNCTION_ARRAY = pool.ensureArrayConstant(FUNCTION_ARRAY_TYPE, Constant.NO_CONSTS);

        markNativeMethod("bind", new String[] {"reflect.Type<Object>", "reflect.Parameter", "Object"}, null);
        markNativeMethod("bind", new String[] {"collections.Map<reflect.Parameter, Object>"}, null);
        markNativeMethod("invoke", null, null);
        markNativeMethod("isFunction", null, null);
        markNativeMethod("isMethod", null, null);

        super.initNative();
        }

    @Override
    public TypeComposition ensureClass(Container container, TypeConstant typeActual)
        {
        // from the run-time perspective, a function type is equivalent to its "full bound" type
        // (where there are no parameters) and the responsibility to check the parameter types
        // lies on the "invoke" implementation
        ConstantPool pool = container.getConstantPool();

        assert typeActual.isA(pool.typeFunction());

        TypeConstant typeP   = pool.typeTuple0();
        TypeConstant typeR   = typeActual.getParamType(1);
        TypeConstant typeClz = pool.ensureParameterizedTypeConstant(pool.typeFunction(), typeP, typeR);

        return super.ensureClass(container, typeClz);
        }

    /**
     * @return a TypeComposition for the specified annotated function.
     */
    protected TypeComposition ensureClass(Container container, MethodStructure function)
        {
        ConstantPool pool = container.getConstantPool();

        TypeConstant[] atypeR = function.getIdentityConstant().getRawReturns();

        TypeConstant typeP   = pool.typeTuple0();
        TypeConstant typeR   = atypeR.length == 0 ? pool.typeTuple0() : pool.ensureTupleType(atypeR);
        TypeConstant typeClz = pool.ensureParameterizedTypeConstant(pool.typeFunction(), typeP, typeR);

        Annotation[] aAnno = function.getAnnotations();
        if (aAnno.length > 0)
            {
            typeClz = pool.ensureAnnotatedTypeConstant(typeClz, function.getAnnotations());
            }

        return super.ensureClass(container, typeClz);
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof MethodConstant idFunc)
            {
            MethodStructure structFunc = (MethodStructure) idFunc.getComponent();

            assert structFunc.isFunction();

            return frame.pushStack(new FunctionHandle(frame.f_context.f_container, structFunc));
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
                return invokeBind(frame, hFunc, hArg, iReturn);

            case "invoke":
                return invokeInvoke(frame, hFunc, hArg, iReturn);
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
                return invokeBind(frame, hFunc, ahArg, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method,
                              ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        FunctionHandle hFunc = (FunctionHandle) hTarget;
        switch (method.getName())
            {
            case "isFunction":
                return invokeIsFunction(frame, hFunc, aiReturn);

            case "isMethod":
                return invokeIsMethod(frame, hFunc, aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    @Override
    protected int callEqualsImpl(Frame frame, TypeComposition clazz,
                                 ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return frame.assignValue(iReturn, xBoolean.makeHandle(hValue1 == hValue2));
        }

    @Override
    protected int callCompareImpl(Frame frame, TypeComposition clazz,
                                  ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return frame.assignValue(iReturn,
            xOrdered.makeHandle(hValue1.hashCode() - hValue2.hashCode()));
        }

    @Override
    protected int buildHashCode(Frame frame, TypeComposition clazz, ObjectHandle hTarget, int iReturn)
        {
        FunctionHandle hFunc = (FunctionHandle) hTarget;

        // for now, simply use an identity hash code
        return frame.assignValue(iReturn, xInt64.makeHandle(hFunc.hashCode()));
        }


    // ----- method implementations --------------------------------------------------------------

    /**
     * Method implementation: `Function!<> bind(Map<Parameter, Object> params)`
     */
    public int invokeBind(Frame frame, FunctionHandle hFunc, ObjectHandle hArg, int iReturn)
        {
        Frame.Continuation stepBind = frameCaller ->
            {
            try
                {
                ArrayHandle haValues   = (ArrayHandle) frameCaller.popStack();
                ArrayHandle haOrdinals = (ArrayHandle) frameCaller.popStack();
                FunctionHandle hFuncR  = hFunc;
                int            ixPrev  = Integer.MAX_VALUE;

                ObjectHandle[] ahOrdinal = haOrdinals.getTemplate().toArray(frame, haOrdinals);
                ObjectHandle[] ahValue   = haValues.getTemplate().toArray(frame, haValues);
                for (int i = 0, c = ahOrdinal.length; i < c; i++)
                    {
                    int     ix      = (int) ((JavaLong) ahOrdinal[i]).getValue();
                    boolean fAdjust = ix > ixPrev;

                    ixPrev = ix;
                    if (fAdjust)
                        {
                        ix--;
                        }
                    hFuncR = hFuncR.bind(frameCaller, ix, ahValue[i]);
                    }
                return frameCaller.assignValue(iReturn, hFuncR);
                }
            catch (Exception e)
                {
                return frameCaller.raiseException(e.getMessage());
                }
            };

        ObjectHandle[] ahArg = new ObjectHandle[TO_ARRAY.getMaxVars()];
        ahArg[0] = hArg;

        Frame frameNext = frame.createFrameN(TO_ARRAY, null, ahArg, new int[] {Op.A_STACK, Op.A_STACK});
        frameNext.addContinuation(stepBind);
        return frame.callInitialized(frameNext);
        }

    /**
     * Method implementation: `Function!<> bind(Parameter<ParamType> param, ParamType value)`
     */
    public int invokeBind(Frame frame, FunctionHandle hFunc, ObjectHandle[] ahArg, int iReturn)
        {
        // (TypeHandle) ahArg[0] -- unused
        GenericHandle hParam = (GenericHandle) ahArg[1];
        ObjectHandle  hValue = ahArg[2];

        long nOrdinal = ((JavaLong) hParam.getField(frame, "ordinal")).getValue();

        FunctionHandle hFuncR = hFunc.bind(frame, (int) nOrdinal, hValue);
        return frame.assignValue(iReturn, hFuncR);
        }

    /**
     * Method implementation: `@Op("()") ReturnTypes invoke(ParamTypes args)`
     */
    public int invokeInvoke(Frame frame, FunctionHandle hFunc, ObjectHandle hArg, int iReturn)
        {
        TupleHandle    hTuple  = (TupleHandle) hArg;
        ObjectHandle[] ahArg   = hTuple.m_ahValue;
        int            cArgs   = ahArg.length;
        int            cParams = hFunc.getParamCount();
        int            cVars   = hFunc.getVarCount();
        ObjectHandle[] ahVar   = cArgs == cVars ? ahArg.clone() : Utils.ensureSize(ahArg, cVars);

        if (cArgs != cParams)
            {
            boolean fValid = cArgs < cParams;
            if (fValid)
                {
                // check if there are default args of the function
                MethodStructure method = hFunc.getMethod();
                if (method != null)
                    {
                    for (int i = cArgs; i < cParams; i++)
                        {
                        Parameter param = hFunc.getParam(i);
                        if (param.hasDefaultValue())
                            {
                            ahVar[i] = ObjectHandle.DEFAULT;
                            }
                        else
                            {
                            fValid = false;
                            break;
                            }
                        }
                    }
                }

            if (!fValid)
                {
                return frame.raiseException("Invalid tuple argument");
                }
            }

        for (int i = 0; i < cArgs; i++)
            {
            TypeConstant typeParam = hFunc.getParamType(i);
            TypeConstant typeArg   = ahArg[i].getType();
            if (!typeArg.isA(typeParam))
                {
                return frame.raiseException(
                    xException.illegalCast(frame, typeArg.getValueString()));
                }
            }

        return hFunc.callT(frame, null, ahVar, iReturn);
        }

    /**
     * Method implementation: `conditional (MethodTemplate, Function!<>, Map<Parameter, Object>) isFunction()`
     */
    public int invokeIsFunction(Frame frame, FunctionHandle hFunc, int[] aiReturn)
        {
        MethodStructure method = hFunc.getMethod();
        if (method != null && method.isFunction())
            {
            int            cParams = method.getParamCount();
            ObjectHandle[] ahParam = new ObjectHandle[cParams];
            ObjectHandle[] ahValue = new ObjectHandle[cParams];

            hFunc.addBoundArguments(ahValue);

            // TODO: what if any of the assigns below return a deferred handle?
            frame.assignValue(aiReturn[0], xBoolean.TRUE);
            frame.assignValue(aiReturn[1], xRTMethodTemplate.makeHandle(method));
            frame.assignValue(aiReturn[2], makeHandle(frame, method));

            Frame.Continuation stepNext = frameCaller ->
                constructListMap(frameCaller, ahParam, ahValue, aiReturn[3]);
            return new Utils.CreateParameters(method.getParamArray(), ahParam, stepNext).doNext(frame);
            }

        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    private int constructListMap(Frame frame,
                                 ObjectHandle[] ahParam, ObjectHandle[] ahValue, int iReturn)
        {
        ObjectHandle haParams = xArray.createImmutableArray(xRTSignature.ensureParamArray(), ahParam);

        ObjectHandle haValues = xArray.makeObjectArrayHandle(ahValue, Mutability.Constant);

        return Utils.constructListMap(frame, ensureListMap(), haParams, haValues, iReturn);
        }

    /**
     * Method implementation: `<Target> conditional (Target, Method<Target>, Map<Parameter, Object>) isMethod();`
     */
    public int invokeIsMethod(Frame frame, FunctionHandle hFunc, int[] aiReturn)
        {
        MethodStructure method = hFunc.getMethod();
        if (method != null && !method.isFunction())
            {
            ObjectHandle[] ahValue = new ObjectHandle[method.getParamCount()];

            hFunc.addBoundArguments(ahValue);

            ObjectHandle hTarget = hFunc.getTarget();
            ObjectHandle hMethod = xRTMethod.makeHandle(frame,
                    hTarget.getType(), method.getIdentityConstant());

            return Op.isDeferred(hMethod)
                ? hMethod.proceed(frame.m_frameNext, frameCaller ->
                        createMethodParams(frameCaller, method, hMethod, hTarget, ahValue, aiReturn))
                : createMethodParams(frame, method, hMethod, hTarget, ahValue, aiReturn);
            }

        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    private int createMethodParams(Frame frame, MethodStructure method, ObjectHandle hMethod,
                                   ObjectHandle hTarget, ObjectHandle[] ahValue, int[] aiReturn)
        {
        ObjectHandle[] ahParam = new ObjectHandle[method.getParamCount()];

        // TODO: what if any of the assigns below return R_CALL?
        frame.assignValue(aiReturn[0], xBoolean.TRUE);
        frame.assignValue(aiReturn[1], hTarget);
        frame.assignValue(aiReturn[2], hMethod);

        Frame.Continuation stepNext = frameCaller ->
            constructListMap(frameCaller, ahParam, ahValue, aiReturn[3]);
        return new Utils.CreateParameters(method.getParamArray(), ahParam, stepNext).doNext(frame);
        }


    // ----- Object handle -------------------------------------------------------------------------

    /**
     * Function handle.
     * <p>
     * Function types have quite specialized "isA" rules mostly due to the fact that functions
     * may allow default parameters, but the type itself has no knowledge about that.
     * <p>
     * As a result, all Function handles are based on a "fully bound" type, but carry the actual
     * type as a part of their state,
     */
    public static class FunctionHandle
            extends SignatureHandle
        {
        /**
         * Instantiate an immutable FunctionHandle for a function.
         */
        protected FunctionHandle(Container container, MethodStructure function)
            {
            this(container, function.getIdentityConstant().getSignature().asFunctionType(), function);

            m_fMutable = false;
            }

        /**
         * Instantiate an immutable FunctionHandle for a method.
         */
        protected FunctionHandle(Container container, CallChain chain, int nDepth)
            {
            super(INSTANCE.ensureClass(container, chain.getMethod(nDepth).getIdentityConstant().
                    getSignature().asFunctionType()), chain, nDepth);

            m_fMutable = false;
            }

        /**
         * Instantiate a mutable FunctionHandle for a method or function.
         */
        protected FunctionHandle(Container container, TypeConstant type, MethodStructure function)
            {
            this(INSTANCE.ensureClass(container, type), type, function);
            }

        /**
         * Instantiate a mutable FunctionHandle for a method or function.
         */
        protected FunctionHandle(TypeComposition clz, TypeConstant type, MethodStructure function)
            {
            super(clz, function == null ? null : function.getIdentityConstant(), function, type);

            m_fMutable = clz.isStruct();
            }

        @Override
        public ObjectHandle revealOrigin()
            {
            return this;
            }

        @Override
        public boolean isPassThrough(Container container)
            {
            // function is pass through iff its "exposed" type and all the args are pass through
            if (container != null &&
                    !getType().isShared(container.getModule().getConstantPool()))
                {
                return false;
                }
            return checkArgumentsPassThrough(container);
            }

        /**
         * @return true iff all the functions arguments are pass through
         */
        protected boolean checkArgumentsPassThrough(Container container)
            {
            return true;
            }


        // ----- FunctionHandle interface ----------------------------------------------------------

        // call with one return value to be placed into the specified slot
        // return either R_CALL, R_NEXT or R_EXCEPTION
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
         * @param frame    (optional) the current frame
         * @param hTarget  the argument to bind the target to
         *
         * @return a bound FunctionHandle
         */
        public FunctionHandle bindTarget(Frame frame, ObjectHandle hTarget)
            {
            TypeConstant type = frame == null
                    ? f_type
                    : f_type.resolveGenerics(frame.poolContext(), hTarget.getType());
            return new SingleBoundHandle(hTarget.getComposition().getContainer(), type, this, -1, hTarget);
            }

        /**
         * Bind the specified argument.
         *
         * @param frame  the current frame
         * @param iArg   the argument's index
         * @param hArg   the argument to bind the argument to
         *
         * @return a bound FunctionHandle
         */
        public FunctionHandle bind(Frame frame, int iArg, ObjectHandle hArg)
            {
            assert iArg >= 0;

            ConstantPool        pool     = frame.poolContext();
            GenericTypeResolver resolver = frame.getGenericsResolver(true);
            MethodStructure     method   = getMethod();
            if (method != null)
                {
                Parameter parameter = getParam(iArg);
                if (parameter.isTypeParameter())
                    {
                    TypeHandle hType = (TypeHandle) hArg;
                    resolver = sName ->
                        sName.equals(parameter.getName())
                            ? hType.getDataType()
                            : null;
                    }
                }

            return new SingleBoundHandle(frame.f_context.f_container,
                pool.bindFunctionParam(f_type, iArg, resolver), this, iArg, hArg);
            }

        /**
         * Calculate a shift for a given argument index indicating the difference between
         * the specified argument index and the actual index of the function parameter that
         * corresponds to this argument. This allows to retrieve the parameter info as follows:
         * <p/>
         * {@code Parameter param = getMethod().getParam(iArg + calculateShift(iArg));}
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
        public FullyBoundHandle bindArguments(ObjectHandle... ahArg)
            {
            return new FullyBoundHandle(this.getComposition().getContainer(), this, ahArg);
            }

        // ----- internal implementation -----------------------------------------------------------

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

        /**
         * @return a target this function has bound
         */
        protected ObjectHandle getTarget()
            {
            return null;
            }

        /**
         * Place all bounds arguments to the specified array.
         *
         * @param ahVar  the argument array to place the arguments to
         *
         * @return the number of arguments that has been added
         */
        protected int addBoundArguments(ObjectHandle[] ahVar)
            {
            return 0;
            }

        protected FunctionHandle createProxyHandle(ServiceContext ctx)
            {
            // overridden by SingleBoundHandle
            return null;
            }

        @Override
        public String toString()
            {
            StringBuilder sb = new StringBuilder();
            sb.append(getName())
              .append('(');
            for (int i = 0, c = getParamCount(); i < c; ++i)
                {
                if (i > 0)
                    {
                    sb.append(", ");
                    }
                sb.append(getParam(i).getName());
                }
            sb.append(")");
            return sb.toString();
            }
        }

    protected abstract static class DelegatingHandle
            extends FunctionHandle
        {
        protected FunctionHandle m_hDelegate;

        protected DelegatingHandle(Container container, TypeConstant type, FunctionHandle hDelegate)
            {
            super(container, type, hDelegate == null ? null : hDelegate.getMethod());

            m_hDelegate = hDelegate;
            m_fMutable  = hDelegate != null && hDelegate.isMutable();
            }

        @Override
        protected boolean checkArgumentsPassThrough(Container container)
            {
            return m_hDelegate == null || m_hDelegate.checkArgumentsPassThrough(container);
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
        public ObjectHandle getTarget()
            {
            return m_hDelegate.getTarget();
            }

        @Override
        public int addBoundArguments(ObjectHandle[] ahVar)
            {
            return m_hDelegate.addBoundArguments(ahVar);
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
        public Parameter getParam(int iArg)
            {
            return m_hDelegate.getParam(iArg);
            }

        @Override
        public TypeConstant getParamType(int iArg)
            {
            return m_hDelegate.getParamType(iArg);
            }

        @Override
        public int getReturnCount()
            {
            return m_hDelegate.getReturnCount();
            }

        @Override
        public Parameter getReturn(int iArg)
            {
            return m_hDelegate.getReturn(iArg);
            }

        @Override
        public TypeConstant getReturnType(int iArg)
            {
            return m_hDelegate.getReturnType(iArg);
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
        public boolean makeImmutable()
            {
            if (m_fMutable)
                {
                if (!m_hDelegate.makeImmutable())
                    {
                    return false;
                    }
                m_fMutable = false;
                }
            return true;
            }

        @Override
        public String toString()
            {
            return getClass().getSimpleName() + " -> " + m_hDelegate;
            }
        }

    /**
     * Native function handle is always fully bound.
     */
    public static class NativeFunctionHandle
            extends FunctionHandle
        {
        public NativeFunctionHandle(xService.NativeOperation op)
            {
            super(INSTANCE.f_container, INSTANCE.getCanonicalType(), null);

            f_op       = op;
            m_fMutable = false;
            }

        @Override
        public int call1(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
            {
            return f_op.invoke(frame, ahArg, iReturn);
            }

        @Override
        public String toString()
            {
            return "NativeFunctionHandle";
            }

        final protected xService.NativeOperation f_op;
        }

    // one parameter bound function
    public static class SingleBoundHandle
            extends DelegatingHandle
        {
        protected SingleBoundHandle(Container container, TypeConstant type, FunctionHandle hDelegate,
                                    int iArg, ObjectHandle hArg)
            {
            super(container, type, hDelegate);

            m_iArg     = iArg;
            m_hArg     = hArg;
            m_fMutable = hDelegate.isMutable() || hArg.isMutable();
            }

        @Override
        protected boolean checkArgumentsPassThrough(Container container)
            {
            return m_hDelegate.checkArgumentsPassThrough(container) &&
                    m_hArg.isPassThrough(container);
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
        public ObjectHandle getTarget()
            {
            return m_iArg == -1 ? m_hArg : super.getTarget();
            }

        @Override
        public int addBoundArguments(ObjectHandle[] ahVar)
            {
            int cArgs;
            if (m_iArg >= 0)
                {
                int cMove = super.getParamCount() - (m_iArg + 1); // number of args to move to the right
                if (cMove > 0)
                    {
                    System.arraycopy(ahVar, m_iArg, ahVar, m_iArg + 1, cMove);
                    }
                ahVar[m_iArg] = m_hArg;
                cArgs = 1;
                }
            else
                {
                cArgs = 0;
                }

            return cArgs + super.addBoundArguments(ahVar);
            }

        @Override
        public int getParamCount()
            {
            int cParams = super.getParamCount();
            return m_iArg == -1 ? cParams : cParams - 1;
            }

        @Override
        public Parameter getParam(int iArg)
            {
            return getMethod().getParam(iArg + calculateShift(iArg));
            }

        @Override
        public TypeConstant getParamType(int iArg)
            {
            TypeConstant typeFn = getType(); // the type already reflects bound arguments
            return typeFn.getConstantPool().extractFunctionParams(typeFn)[iArg];
            }

        @Override
        public boolean makeImmutable()
            {
            if (m_fMutable)
                {
                if (!m_hArg.isService() && !m_hArg.makeImmutable())
                    {
                    return false;
                    }
                return super.makeImmutable();
                }
            return true;
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
        }

    /**
     * A function handle for which all parameters are bound.
     */
    public static class FullyBoundHandle
            extends DelegatingHandle
        {
        protected final ObjectHandle[] f_ahArg;
        protected FullyBoundHandle m_next;

        protected FullyBoundHandle(Container container, FunctionHandle hDelegate, ObjectHandle[] ahArg)
            {
            super(container, hDelegate == null ? INSTANCE.getCanonicalType() : hDelegate.getType(), hDelegate);

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
        public boolean makeImmutable()
            {
            if (isMutable())
                {
                for (ObjectHandle hArg : f_ahArg)
                    {
                    if (!hArg.makeImmutable())
                        {
                        return false;
                        }
                    }
                }
            return super.makeImmutable();
            }

        @Override
        protected boolean checkArgumentsPassThrough(Container container)
            {
            for (ObjectHandle hArg : f_ahArg)
                {
                if (!hArg.isPassThrough(container))
                    {
                    return false;
                    }
                }
            return true;
            }

        @Override
        public int addBoundArguments(ObjectHandle[] ahVar)
            {
            int cArgs = super.addBoundArguments(ahVar);

            // to avoid extra array creation, the argument array may contain unused null elements
            System.arraycopy(f_ahArg, 0, ahVar, cArgs, Math.min(ahVar.length - cArgs, f_ahArg.length));
            return ahVar.length;
            }

        public FullyBoundHandle chain(FullyBoundHandle handle)
            {
            if (handle != NO_OP)
                {
                assert m_next == null;
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

        public static FullyBoundHandle NO_OP =
                new FullyBoundHandle(INSTANCE.f_container, null, Utils.OBJECTS_NONE)
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

    /**
     * An asynchronous function handle.
     */
    public static class AsyncHandle
            extends FunctionHandle
        {
        /**
         * Create an asynchronous method handle.
         *
         * @param chain   the call chain
         */
        protected AsyncHandle(Container container, CallChain chain)
            {
            super(container, chain, 0);
            }

        /**
         * Create an asynchronous native method handle.
         *
         * @param method  the native method
         */
        protected AsyncHandle(Container container, MethodStructure method)
            {
            super(container, method);

            assert method.isNative();
            }

        /**
         * Obtain a target handle to invoke this function for when the control is passed to the
         * service context.
         *
         * @param frame     the current frame
         * @param hService  the object for which the initial async method call was created for;
         *                  it must be an object for which {@link ObjectHandle#isService()
         *                  all method invocations need to be proxied}
         *
         * @return an ObjectHandle for the target (may be deferred)
         */
        protected ObjectHandle getContextTarget(Frame frame, ObjectHandle hService)
            {
            return hService;
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
            assert hTarget.isService();

            ServiceContext ctxTarget = hTarget.getService().f_context;

            if (frame.f_context == ctxTarget)
                {
                hTarget = getContextTarget(frame, hTarget);

                return Op.isDeferred(hTarget)
                        ? hTarget.proceed(frame, frameCaller ->
                            super.call1Impl(frameCaller, frameCaller.popStack(), ahVar, iReturn))
                        : super.call1Impl(frame, hTarget, ahVar, iReturn);
                }

            if (!frame.f_context.validatePassThrough(ctxTarget, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            return ctxTarget.sendInvoke1Request(frame, this, hTarget, ahVar, false, iReturn);
            }

        @Override
        protected int callTImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            assert hTarget.isService();

            ServiceContext ctxTarget = hTarget.getService().f_context;

            if (frame.f_context == ctxTarget)
                {
                hTarget = getContextTarget(frame, hTarget);

                return Op.isDeferred(hTarget)
                        ? hTarget.proceed(frame, frameCaller ->
                            super.callTImpl(frameCaller, frameCaller.popStack(), ahVar, iReturn))
                        : super.callTImpl(frame, hTarget, ahVar, iReturn);
                }

            if (!frame.f_context.validatePassThrough(ctxTarget, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            return ctxTarget.sendInvoke1Request(frame, this, hTarget, ahVar, true, iReturn);
            }

        @Override
        protected int callNImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
            {
            assert hTarget.isService();

            ServiceContext ctxTarget = hTarget.getService().f_context;

            if (frame.f_context == ctxTarget)
                {
                hTarget = getContextTarget(frame, hTarget);

                return Op.isDeferred(hTarget)
                        ? hTarget.proceed(frame, frameCaller ->
                            super.callNImpl(frameCaller, frameCaller.popStack(), ahVar, aiReturn))
                        : super.callNImpl(frame, hTarget, ahVar, aiReturn);
                }

            if (!frame.f_context.validatePassThrough(ctxTarget, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            return ctxTarget.sendInvokeNRequest(frame, this, hTarget, ahVar, aiReturn);
            }
        }

    /**
     * An asynchronous delegating function handle.
     */
    public static class AsyncDelegatingHandle
            extends DelegatingHandle
        {
        private final ServiceHandle f_hService;

        /**
         * Create an asynchronous delegating handle.
         */
        protected AsyncDelegatingHandle(ServiceHandle hService, FunctionHandle hDelegate)
            {
            super(hService.f_context.f_container, hDelegate.getType(), hDelegate);

            f_hService = hService;
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
            ServiceHandle hService = f_hService;

            if (frame.f_context == hService.f_context)
                {
                return super.call1Impl(frame, null, ahVar, iReturn);
                }

            if (!frame.f_context.validatePassThrough(hService.f_context, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            return hService.f_context.sendInvoke1Request(frame, this, hService, ahVar, false, iReturn);
            }

        @Override
        protected int callTImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            ServiceHandle hService = f_hService;

            if (frame.f_context == hService.f_context)
                {
                return super.callTImpl(frame, null, ahVar, iReturn);
                }

            if (!frame.f_context.validatePassThrough(hService.f_context, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            return hService.f_context.sendInvoke1Request(frame, this, hService, ahVar, true, iReturn);
            }

        @Override
        protected int callNImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
            {
            ServiceHandle hService = f_hService;

            if (frame.f_context == hService.f_context)
                {
                return super.callNImpl(frame, null, ahVar, aiReturn);
                }

            if (!frame.f_context.validatePassThrough(hService.f_context, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            return hService.f_context.sendInvokeNRequest(frame, this, hService, ahVar, aiReturn);
            }
        }

    /**
     * A function proxy handle.
     */
    public static class FunctionProxyHandle
            extends DelegatingHandle
        {
        // the origin context of the mutable FunctionHandle
        final private ServiceContext f_ctx;

        protected FunctionProxyHandle(FunctionHandle fn, ServiceContext ctx)
            {
            super(fn.getComposition().getContainer(), fn.getType(), fn);

            f_ctx = ctx;
            }

        @Override
        public boolean isService()
            {
            return true;
            }

        @Override
        public boolean isPassThrough(Container container)
            {
            // the args will be validated on the call
            return true;
            }


        // ----- FunctionHandle interface ----------------------------------------------------------

        @Override
        public int call1(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            if (frame.f_context == f_ctx)
                {
                return super.call1(frame, hTarget, ahVar, iReturn);
                }

            if (!frame.f_context.validatePassThrough(f_ctx, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            return f_ctx.sendInvoke1Request(frame, this, hTarget, ahVar, false, iReturn);
            }

        @Override
        public int callT(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            if (frame.f_context == f_ctx)
                {
                return super.callT(frame, hTarget, ahVar, iReturn);
                }

            if (!frame.f_context.validatePassThrough(f_ctx, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            return f_ctx.sendInvoke1Request(frame, this, hTarget, ahVar, true, iReturn);
            }

        @Override
        public int callN(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
            {
            if (frame.f_context == f_ctx)
                {
                return super.callN(frame, hTarget, ahVar, aiReturn);
                }

            if (!frame.f_context.validatePassThrough(f_ctx, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            return f_ctx.sendInvokeNRequest(frame, this, hTarget, ahVar, aiReturn);
            }
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Create a function handle representing an asynchronous (service) call.
     *
     *
     * @param frame
     * @param chain the method chain
     *
     * @return the corresponding function handle
     */
    public static AsyncHandle makeAsyncHandle(Frame frame, CallChain chain)
        {
        return new AsyncHandle(frame.f_context.f_container, chain);
        }

    /**
     * Create a function handle representing an asynchronous (service) call.
     *
     * @param hService  the service in which context the function should be called
     * @param hDelegate the function to delegate calls to
     *
     * @return the corresponding delegating function handle
     */
    public static AsyncDelegatingHandle makeAsyncDelegatingHandle(
            ServiceHandle hService, FunctionHandle hDelegate)
        {
        return new AsyncDelegatingHandle(hService, hDelegate);
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

        return new AsyncHandle(INSTANCE.f_container, method);
        }

    /**
     * Create an immutable FunctionHandle for a given method chain.
     */
    public static FunctionHandle makeHandle(Frame frame, CallChain chain, int nDepth)
        {
        return new FunctionHandle(frame.f_context.f_container, chain, nDepth);
        }

    /**
     * Create an immutable FunctionHandle for a given function.
     *
     * The returned handle will not carry any annotations
     */
    public static FunctionHandle makeInternalHandle(Frame frame, MethodStructure function)
        {
        Container container = frame == null ? INSTANCE.f_container : frame.f_context.f_container;
        return new FunctionHandle(container, function);
        }

    /**
     * Create an immutable FunctionHandle for a given function.
     *
     * The returned handle could be deferred.
     */
    public static ObjectHandle makeHandle(Frame frame, MethodStructure function)
        {
        Container container = frame == null ? INSTANCE.f_container : frame.f_context.f_container;

        Annotation[] aAnno = function.getAnnotations();

        if (aAnno.length > 0)
            {
            TypeConstant type = function.getIdentityConstant().getSignature().asFunctionType();
            type = container.getConstantPool().ensureAnnotatedTypeConstant(type, aAnno);

            TypeComposition clzFunction = INSTANCE.ensureClass(container, function);
            FunctionHandle  hStruct     = new FunctionHandle(clzFunction.
                                            ensureAccess(Constants.Access.STRUCT), type, function);

            switch (hStruct.getTemplate().
                    proceedConstruction(frame, null, true, hStruct, Utils.OBJECTS_NONE, Op.A_STACK))
                {
                case Op.R_NEXT:
                    {
                    FunctionHandle hF = (FunctionHandle) frame.popStack();
                    hF.makeImmutable();
                    return hF;
                    }

                case Op.R_CALL:
                    DeferredCallHandle hDeferred = new DeferredCallHandle(frame.m_frameNext);
                    hDeferred.addContinuation(frameCaller ->
                        {
                        frameCaller.peekStack().makeImmutable();
                        return Op.R_NEXT;
                        });
                    return hDeferred;

                case Op.R_EXCEPTION:
                    return new DeferredCallHandle(frame.m_hException);
                }
            }

        return new FunctionHandle(container, function);
        }

    /**
     * Create an immutable FunctionHandle for a given constructor. Note, that the constructor
     * may be null for synthetic constructor function.
     *
     * The returned handle could be deferred.
     */
    public static ObjectHandle makeConstructorHandle(Frame frame, MethodStructure constructor,
                                                     TypeConstant typeConstructor,
                                                     TypeComposition clzTarget,
                                                     Parameter[] aParams, boolean fParent)
        {
        Container       container = frame.f_context.f_container;
        TypeComposition clzConstruct;

        if (constructor == null)
            {
            clzConstruct = INSTANCE.ensureClass(container, typeConstructor);
            }
        else
            {
            clzConstruct = INSTANCE.ensureClass(container, constructor);

            Annotation[] aAnno = constructor.getAnnotations();
            if (aAnno.length > 0)
                {
                typeConstructor = container.getConstantPool().
                        ensureAnnotatedTypeConstant(typeConstructor, aAnno);

                TypeComposition clzStruct = INSTANCE.ensureClass(container, constructor).
                        ensureAccess(Constants.Access.STRUCT);

                ConstructorHandle hStruct = new ConstructorHandle(
                        clzStruct, clzTarget, typeConstructor, constructor, aParams, fParent);

                switch (hStruct.getTemplate().
                        proceedConstruction(frame, null, true, hStruct, Utils.OBJECTS_NONE, Op.A_STACK))
                    {
                    case Op.R_NEXT:
                        {
                        FunctionHandle hF = (FunctionHandle) frame.popStack();
                        hF.makeImmutable();
                        return hF;
                        }

                    case Op.R_CALL:
                        DeferredCallHandle hDeferred = new DeferredCallHandle(frame.m_frameNext);
                        hDeferred.addContinuation(frameCaller ->
                            {
                            frameCaller.peekStack().makeImmutable();
                            return Op.R_NEXT;
                            });
                        return hDeferred;

                    case Op.R_EXCEPTION:
                        return new DeferredCallHandle(frame.m_hException);
                    }
                }
            }

        return new ConstructorHandle(
                clzConstruct, clzTarget, typeConstructor, constructor, aParams, fParent);
        }

    /**
     * FunctionHandle that represents a constructor function.
     */
    public static class ConstructorHandle
            extends FunctionHandle
        {
        public ConstructorHandle(TypeComposition clzConstruct, TypeComposition clzTarget,
                                 TypeConstant typeConstruct, MethodStructure constructor,
                                 Parameter[] aParams, boolean fParent)
            {
            super(clzConstruct, typeConstruct, constructor);

            f_clzTarget   = clzTarget;
            f_constructor = constructor;
            f_aParams     = aParams;
            f_fParent     = fParent;
            m_fMutable    = clzConstruct.isStruct();
            }

        @Override
        public int call1(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
            {
            // this can only a call from Call_01
            return callImpl(frame, ahArg, iReturn);
            }

        @Override
        public int callT(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
            {
            TypeConstant    typeTuple = frame.poolContext().ensureTupleType(f_clzTarget.getType());
            TypeComposition clzTuple  = xTuple.INSTANCE.ensureClass(frame.f_context.f_container, typeTuple);

            switch (callImpl(frame, ahArg, Op.A_STACK))
                {
                case Op.R_NEXT:
                    return frame.assignValue(iReturn,
                        xTuple.makeImmutableHandle(clzTuple, frame.popStack()));

                case Op.R_CALL:
                    frame.m_frameNext.addContinuation(frameCaller ->
                        frameCaller.assignValue(iReturn,
                            xTuple.makeImmutableHandle(clzTuple, frameCaller.popStack())));
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }

        /**
         * Call the constructor.
         */
        private int callImpl(Frame frame, ObjectHandle[] ahArg, int iReturn)
            {
            ObjectHandle hParent = null;
            if (f_fParent)
                {
                hParent = ahArg[0];
                System.arraycopy(ahArg, 1, ahArg, 0, ahArg.length-1);
                }

            TypeComposition clzTarget   = f_clzTarget;
            ClassTemplate template    = clzTarget.getTemplate();
            MethodStructure constructor = f_constructor;

            // constructor could be null for a synthetic run-time structure-based constructor
            // created above by "getPropertyConstructors" method
            return constructor == null
                ? template.proceedConstruction(frame, null, false, ahArg[0], Utils.OBJECTS_NONE, iReturn)
                : template.construct(frame, constructor, clzTarget, hParent, ahArg, iReturn);
            }

        @Override
        protected ObjectHandle[] prepareVars(ObjectHandle[] ahArg)
            {
            throw new IllegalStateException();
            }

        @Override
        public String getName()
            {
            return "construct";
            }

        @Override
        public int getParamCount()
            {
            return f_aParams.length;
            }

        @Override
        public Parameter getParam(int iArg)
            {
            return f_aParams[iArg];
            }

        @Override
        public int getReturnCount()
            {
            return 1;
            }

        @Override
        public Parameter getReturn(int iArg)
            {
            assert iArg == 0;
            TypeConstant typeTarget = f_clzTarget.getType();
            return new Parameter(typeTarget.getConstantPool(), typeTarget, null, null, true, 0, false);
            }

        @Override
        public TypeConstant getReturnType(int iArg)
            {
            assert iArg == 0;
            return f_clzTarget.getType();
            }

        @Override
        public int getVarCount()
            {
            int cVars = super.getVarCount();
            return Math.max(cVars, f_aParams.length);
            }

        final private TypeComposition f_clzTarget;
        final private MethodStructure f_constructor;
        final protected Parameter[]   f_aParams;
        final private boolean         f_fParent;
        }


    // ----- Composition and handle caching --------------------------------------------------------

    /**
     * @return the TypeComposition for an Array of Function
     */
    public static TypeComposition ensureArrayComposition(Container container)
        {
        return container.ensureClassComposition(FUNCTION_ARRAY_TYPE, xArray.INSTANCE);
        }

    /**
     * @return the handle for an empty Array of Function
     */
    public static ArrayHandle ensureEmptyArray(Container container)
        {
        ArrayHandle haEmpty = (ArrayHandle) container.f_heap.getConstHandle(EMPTY_FUNCTION_ARRAY);
        if (haEmpty == null)
            {
            haEmpty = xArray.createImmutableArray(ensureArrayComposition(container), Utils.OBJECTS_NONE);
            container.f_heap.saveConstHandle(EMPTY_FUNCTION_ARRAY, haEmpty);
            }
        return haEmpty;
        }

    /**
     * @return the TypeComposition for a ListMap<Parameter, Object>
     */
    public static TypeComposition ensureListMap() // TODO: use the container
        {
        TypeComposition clz = LISTMAP_CLZCOMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeListMap   = pool.ensureEcstasyTypeConstant("collections.ListMap");
            TypeConstant typeParameter = pool.typeParameter();

            typeListMap = pool.ensureParameterizedTypeConstant(typeListMap, typeParameter, pool.typeObject());
            LISTMAP_CLZCOMP = clz = INSTANCE.f_container.resolveClass(typeListMap);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the TypeComposition for an Array of Constructor (function type)
     */
    public static TypeComposition ensureConstructorArray(
            Frame frame, TypeConstant typeTarget, TypeConstant typeParent)
        {
        assert typeTarget != null;

        ConstantPool pool = frame.poolContext();

        TypeConstant typeParams = typeParent == null
                ? pool.typeTuple0()
                : pool.ensureTupleType(typeParent);
        TypeConstant typeReturns = pool.ensureTupleType(typeTarget);
        TypeConstant typeCtor    = pool.ensureParameterizedTypeConstant(
                                        pool.typeFunction(), typeParams, typeReturns);

        TypeConstant typeArray = pool.ensureArrayType(typeCtor);
        return frame.f_context.f_container.resolveClass(typeArray);
        }


    // ----- data members --------------------------------------------------------------------------

    private static TypeConstant  FUNCTION_ARRAY_TYPE;
    private static ArrayConstant EMPTY_FUNCTION_ARRAY;

    private static TypeComposition LISTMAP_CLZCOMP;

    /**
     * RTFunction:
     *      static (Int[], Object[]) toArray(Map<Parameter, Object> params)
     */
    private static MethodStructure TO_ARRAY;
    }