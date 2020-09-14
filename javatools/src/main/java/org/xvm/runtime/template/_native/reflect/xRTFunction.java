package org.xvm.runtime.template._native.reflect;


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
import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.ServiceContext;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xOrdered;
import org.xvm.runtime.template.xService;
import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xIntArray.IntArrayHandle;
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

    public xRTFunction(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        TO_ARRAY = getStructure().findMethod("toArray", 1);

        markNativeMethod("bind", new String[] {"reflect.Type<Object>", "reflect.Parameter", "Object"}, null);
        markNativeMethod("bind", new String[] {"collections.Map<reflect.Parameter, Object>"}, null);
        markNativeMethod("invoke", null, null);
        markNativeMethod("isFunction", null, null);
        markNativeMethod("isMethod", null, null);

        super.initNative();
        }

    @Override
    public ClassComposition ensureClass(TypeConstant typeActual)
        {
        // from the run-time perspective, a function type is equivalent to its "full bound" type
        // (where there are no parameters) and the responsibility to check the parameter types
        // lies on the "invoke" implementation
        ConstantPool pool = typeActual.getConstantPool();

        assert typeActual.isA(pool.typeFunction());

        TypeConstant typeP   = pool.ensureParameterizedTypeConstant(pool.typeTuple());
        TypeConstant typeR   = typeActual.getParamType(1);
        TypeConstant typeClz = pool.ensureParameterizedTypeConstant(pool.typeFunction(), typeP, typeR);

        return super.ensureClass(typeClz);
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof MethodConstant)
            {
            MethodConstant  idFunc     = (MethodConstant) constant;
            MethodStructure structFunc = (MethodStructure) idFunc.getComponent();

            assert structFunc.isFunction();

            return frame.pushStack(new FunctionHandle(structFunc));
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
    protected int callEqualsImpl(Frame frame, ClassComposition clazz,
                                 ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return frame.assignValue(iReturn, xBoolean.makeHandle(hValue1 == hValue2));
        }

    @Override
    protected int callCompareImpl(Frame frame, ClassComposition clazz,
                                  ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        return frame.assignValue(iReturn,
            xOrdered.makeHandle(hValue1.hashCode() - hValue2.hashCode()));
        }

    @Override
    protected int buildHashCode(Frame frame, ClassComposition clazz, ObjectHandle hTarget, int iReturn)
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
                ArrayHandle    haValues   = (ArrayHandle) frameCaller.popStack();
                IntArrayHandle haOrdinals = (IntArrayHandle) frameCaller.popStack();
                FunctionHandle hFuncR     = hFunc;

                int ixPrev = Integer.MAX_VALUE;
                for (int i = 0, c = haOrdinals.m_cSize; i < c; i++)
                    {
                    int     ix      = (int) haOrdinals.m_alValue[i];
                    boolean fAdjust = ix > ixPrev;

                    ixPrev = ix;
                    if (fAdjust)
                        {
                        ix--;
                        }
                    hFuncR = hFuncR.bind(frameCaller, ix, haValues.getElement(i));
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

        Frame frameNext = frame.createFrameN(TO_ARRAY, null, ahArg,
            new int[] {Op.A_STACK, Op.A_STACK});
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

        long nOrdinal = ((JavaLong) hParam.getField("ordinal")).getValue();

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

            frame.assignValue(aiReturn[0], xBoolean.TRUE);
            frame.assignValue(aiReturn[1], xRTComponentTemplate.makeMethodHandle(method));
            frame.assignValue(aiReturn[2], makeHandle(method));

            Frame.Continuation stepNext = frameCaller ->
                constructListMap(frameCaller, ahParam, ahValue, aiReturn[3]);
            return new Utils.CreateParameters(method.getParamArray(), ahParam, stepNext).doNext(frame);
            }

        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
        }

    private int constructListMap(Frame frame,
                                 ObjectHandle[] ahParam, ObjectHandle[] ahValue, int iReturn)
        {
        ArrayHandle haParams = xArray.INSTANCE.createArrayHandle(
                xRTSignature.ensureParamArray(), ahParam);

        ArrayHandle haValues = xArray.makeObjectArrayHandle(
                ahValue, xArray.Mutability.Constant);

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
            int            cParams = method.getParamCount();
            ObjectHandle[] ahParam = new ObjectHandle[cParams];
            ObjectHandle[] ahValue = new ObjectHandle[cParams];

            hFunc.addBoundArguments(ahValue);

            ObjectHandle hTarget = hFunc.getTarget();

            frame.assignValue(aiReturn[0], xBoolean.TRUE);
            frame.assignValue(aiReturn[1], hTarget);
            frame.assignValue(aiReturn[2],
                    xRTMethod.makeHandle(hTarget.getType(), method.getIdentityConstant()));

            Frame.Continuation stepNext = frameCaller ->
                constructListMap(frameCaller, ahParam, ahValue, aiReturn[3]);
            return new Utils.CreateParameters(method.getParamArray(), ahParam, stepNext).doNext(frame);
            }

        return frame.assignValue(aiReturn[0], xBoolean.FALSE);
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
        protected FunctionHandle(MethodStructure function)
            {
            this(function.getIdentityConstant().getType(), function);

            m_fMutable = false;
            }

        /**
         * Instantiate an immutable FunctionHandle for a method.
         */
        protected FunctionHandle(CallChain chain, int nDepth)
            {
            super(INSTANCE.ensureClass(chain.getMethod(nDepth).getIdentityConstant().getType()), chain, nDepth);

            m_fMutable = false;
            }

        /**
         * Instantiate a mutable FunctionHandle for a method or function.
         */
        protected FunctionHandle(TypeConstant type, MethodStructure function)
            {
            super(INSTANCE.ensureClass(type),
                    function == null ? null : function.getIdentityConstant(), function, type);
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
            GenericTypeResolver resolver = frame.getGenericsResolver();
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

            return new SingleBoundHandle(
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
        public FullyBoundHandle bindArguments(ObjectHandle[] ahArg)
            {
            return new FullyBoundHandle(this, ahArg);
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
         */
        protected void addBoundArguments(ObjectHandle[] ahVar)
            {
            }

        protected FunctionHandle createProxyHandle(ServiceContext ctx)
            {
            // we shouldn't get here since a simple FunctionHandle is immutable
            throw new IllegalStateException();
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

        protected DelegatingHandle(TypeConstant type, FunctionHandle hDelegate)
            {
            super(type, hDelegate == null ? null : hDelegate.getMethod());

            m_hDelegate = hDelegate;
            m_fMutable  = hDelegate != null && hDelegate.isMutable();
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
        public void addBoundArguments(ObjectHandle[] ahVar)
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
            super(INSTANCE.getCanonicalType(), null);

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
        protected SingleBoundHandle(TypeConstant type, FunctionHandle hDelegate,
                                    int iArg, ObjectHandle hArg)
            {
            super(type, hDelegate);

            m_iArg     = iArg;
            m_hArg     = hArg;
            m_fMutable = hDelegate.isMutable() || hArg.isMutable();
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
        public void addBoundArguments(ObjectHandle[] ahVar)
            {
            if (m_iArg >= 0)
                {
                int cMove = super.getParamCount() - (m_iArg + 1); // number of args to move to the right
                if (cMove > 0)
                    {
                    System.arraycopy(ahVar, m_iArg, ahVar, m_iArg + 1, cMove);
                    }
                ahVar[m_iArg] = m_hArg;
                }

            super.addBoundArguments(ahVar);
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

        protected FullyBoundHandle(FunctionHandle hDelegate, ObjectHandle[] ahArg)
            {
            super(hDelegate == null ? INSTANCE.getCanonicalType() : hDelegate.getType(), hDelegate);

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
        public void addBoundArguments(ObjectHandle[] ahVar)
            {
            super.addBoundArguments(ahVar);

            // to avoid extra array creation, the argument array may contain unused null elements
            System.arraycopy(f_ahArg, 0, ahVar, 0, Math.min(ahVar.length, f_ahArg.length));
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

        /**
         * Obtain a target handle to invoke this function for when the control is passed to the
         * service context.
         *
         * @param frame     the current frame
         * @param hService  the service for which the initial async method call was created for
         *
         * @return an ObjectHandle for the target (may be deferred)
         */
        protected ObjectHandle getContextTarget(Frame frame, ServiceHandle hService)
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
            ServiceHandle hService = (ServiceHandle) hTarget;

            if (frame.f_context == hService.f_context)
                {
                hTarget = getContextTarget(frame, hService);

                return Op.isDeferred(hTarget)
                        ? hTarget.proceed(frame, frameCaller ->
                            super.call1Impl(frame, frameCaller.popStack(), ahVar, iReturn))
                        : super.call1Impl(frame, hTarget, ahVar, iReturn);
                }

            if (!validateImmutable(frame.f_context, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            return hService.f_context.sendInvoke1Request(frame, this, hService, ahVar, false, iReturn);
            }

        @Override
        protected int callTImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int iReturn)
            {
            ServiceHandle hService = (ServiceHandle) hTarget;

            if (frame.f_context == hService.f_context)
                {
                hTarget = getContextTarget(frame, hService);

                return Op.isDeferred(hTarget)
                        ? hTarget.proceed(frame, frameCaller ->
                            super.callTImpl(frame, frameCaller.popStack(), ahVar, iReturn))
                        : super.callTImpl(frame, hTarget, ahVar, iReturn);
                }

            if (!validateImmutable(frame.f_context, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            return hService.f_context.sendInvoke1Request(frame, this, hService, ahVar, true, iReturn);
            }

        @Override
        protected int callNImpl(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahVar, int[] aiReturn)
            {
            ServiceHandle hService = (ServiceHandle) hTarget;

            if (frame.f_context == hService.f_context)
                {
                hTarget = getContextTarget(frame, hService);

                return Op.isDeferred(hTarget)
                        ? hTarget.proceed(frame, frameCaller ->
                            super.callNImpl(frame, frameCaller.popStack(), ahVar, aiReturn))
                        : super.callNImpl(frame, hTarget, ahVar, aiReturn);
                }

            if (!validateImmutable(frame.f_context, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            return hService.f_context.sendInvokeNRequest(frame, this, hService, ahVar, aiReturn);
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
            if (frame.f_context == f_ctx)
                {
                return super.call1(frame, hTarget, ahVar, iReturn);
                }

            if (!validateImmutable(frame.f_context, getMethod(), ahVar))
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

            if (!validateImmutable(frame.f_context, getMethod(), ahVar))
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

            if (!validateImmutable(frame.f_context, getMethod(), ahVar))
                {
                return frame.raiseException(xException.mutableObject(frame));
                }

            return f_ctx.sendInvokeNRequest(frame, this, hTarget, ahVar, aiReturn);
            }
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return true iff all the arguments are immutable
     */
    private static boolean validateImmutable(ServiceContext ctx, MethodStructure method,
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

    /**
     * Create an immutable FunctionHandle for a given method chain.
     */
    public static FunctionHandle makeHandle(CallChain chain, int nDepth)
        {
        return new FunctionHandle(chain, nDepth);
        }

    /**
     * Create an immutable FunctionHandle for a given function.
     */
    public static FunctionHandle makeHandle(MethodStructure function)
        {
        return new FunctionHandle(function);
        }


    // ----- Template, Composition, and handle caching ---------------------------------------------

    /**
     * @return the ClassTemplate for an Array of Function
     */
    public static xArray ensureArrayTemplate()
        {
        xArray template = ARRAY_TEMPLATE;
        if (template == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeFunctionArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeFunction());
            ARRAY_TEMPLATE = template = ((xArray) INSTANCE.f_templates.getTemplate(typeFunctionArray));
            assert template != null;
            }
        return template;
        }

    /**
     * @return the ClassComposition for an Array of Function
     */
    public static ClassComposition ensureArrayComposition()
        {
        ClassComposition clz = ARRAY_CLZCOMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeFunctionArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeFunction());
            ARRAY_CLZCOMP = clz = INSTANCE.f_templates.resolveClass(typeFunctionArray);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the ClassComposition for a ListMap<Parameter, Object>
     */
    public static ClassComposition ensureListMap()
        {
        ClassComposition clz = LISTMAP_CLZCOMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeListMap   = pool.ensureEcstasyTypeConstant("collections.ListMap");
            TypeConstant typeParameter = pool.ensureEcstasyTypeConstant("reflect.Parameter");

            typeListMap = pool.ensureParameterizedTypeConstant(typeListMap, typeParameter, pool.typeObject());
            LISTMAP_CLZCOMP = clz = INSTANCE.f_templates.resolveClass(typeListMap);
            assert clz != null;
            }
        return clz;
        }


    /**
     * @return the TypeConstant for a default Constructor on the specified target
     */
    public static TypeConstant ensureConstructorType(TypeConstant typeTarget, TypeConstant typeParent)
        {
        assert typeTarget != null;

        ConstantPool pool        = INSTANCE.pool();
        TypeConstant typeParams  = typeParent == null
                ? pool.ensureParameterizedTypeConstant(pool.typeTuple(), TypeConstant.NO_TYPES)
                : pool.ensureParameterizedTypeConstant(pool.typeTuple(), typeParent);
        TypeConstant typeReturns = pool.ensureParameterizedTypeConstant(pool.typeTuple(), typeTarget);
        return pool.ensureParameterizedTypeConstant(pool.typeFunction(), typeParams, typeReturns);
        }

    /**
     * @return the ClassComposition for an Array of Constructor
     */
    public static ClassComposition ensureConstructorArray(TypeConstant typeTarget, TypeConstant typeParent)
        {
        assert typeTarget != null;

        ConstantPool pool      = INSTANCE.pool();
        TypeConstant typeArray = pool.ensureParameterizedTypeConstant(pool.typeArray(),
                ensureConstructorType(typeTarget, typeParent));
        return INSTANCE.f_templates.resolveClass(typeArray);
        }


    // ----- data members --------------------------------------------------------------------------

    private static xArray           ARRAY_TEMPLATE;
    private static ClassComposition ARRAY_CLZCOMP;
    private static ClassComposition LISTMAP_CLZCOMP;

    /**
     * RTFunction:
     *      static (Int[], Object[]) toArray(Map<Parameter, Object> params)
     */
    private static MethodStructure TO_ARRAY;
    }
