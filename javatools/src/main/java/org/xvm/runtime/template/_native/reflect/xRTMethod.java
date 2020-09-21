package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ArrayHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xOrdered;

/**
 * Native Method implementation.
 */
public class xRTMethod
        extends xRTSignature
    {
    public static xRTMethod INSTANCE;

    public xRTMethod(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
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
        markNativeProperty("access");
        markNativeProperty("annotations");

        markNativeMethod("formalParamNames" , null, null);
        markNativeMethod("formalReturnNames", null, null);
        markNativeMethod("bindTarget"       , null, null);
        markNativeMethod("invoke"           , null, null);

        super.initNative();
        }

    @Override
    public ClassComposition ensureClass(TypeConstant typeActual)
        {
        // see explanation at xRTFunction.ensureClass()
        ConstantPool pool = typeActual.getConstantPool();

        assert typeActual.isA(pool.typeMethod());

        TypeConstant typeTarget = typeActual.getParamType(0);
        TypeConstant typeP      = pool.ensureParameterizedTypeConstant(pool.typeTuple());
        TypeConstant typeR      = typeActual.getParamType(2);
        TypeConstant typeClz    = pool.ensureParameterizedTypeConstant(
                                        pool.typeMethod(), typeTarget, typeP, typeR);
        return super.ensureClass(typeClz);
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof MethodConstant)
            {
            return frame.pushStack(
                    makeHandle(frame.getThis().getType(), (MethodConstant) constant));
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        MethodHandle hMethod = (MethodHandle) hTarget;
        switch (sPropName)
            {
            case "access":
                return getPropertyAccess(frame, hMethod, iReturn);

            case "annotations":
                return getPropertyAnnotations(frame, hMethod, iReturn);
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        MethodHandle hMethod = (MethodHandle) hTarget;
        switch (method.getName())
            {
            case "bindTarget":
                return invokeBindTarget(frame, hMethod, hArg, iReturn);
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        MethodHandle hMethod = (MethodHandle) hTarget;
        switch (method.getName())
            {
            case "invoke":
                return invokeInvoke(frame, hMethod, ahArg, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn)
        {
        MethodHandle hMethod = (MethodHandle) hTarget;
        switch (method.getName())
            {
            case "formalParamNames":
                return invokeFormalParamNames(frame, hMethod, aiReturn);

            case "formalReturnNames":
                return invokeFormalReturnNames(frame, hMethod, aiReturn);
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    @Override
    protected int callEqualsImpl(Frame frame, ClassComposition clazz,
                                 ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        MethodHandle hMethod1 = (MethodHandle) hValue1;
        MethodHandle hMethod2 = (MethodHandle) hValue2;

        return frame.assignValue(iReturn,
            xBoolean.makeHandle(hMethod1.getMethodId().equals(hMethod2.getMethodId())));
        }

    @Override
    protected int callCompareImpl(Frame frame, ClassComposition clazz,
                                  ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        MethodHandle hMethod1 = (MethodHandle) hValue1;
        MethodHandle hMethod2 = (MethodHandle) hValue2;

        return frame.assignValue(iReturn,
            xOrdered.makeHandle(hMethod1.getMethodId().compareTo(hMethod2.getMethodId())));
        }

    @Override
    protected int buildHashCode(Frame frame, ClassComposition clazz, ObjectHandle hTarget, int iReturn)
        {
        MethodHandle hMethod = (MethodHandle) hTarget;

        return frame.assignValue(iReturn, xInt64.makeHandle(hMethod.hashCode()));
        }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: access.get()
     */
    public int getPropertyAccess(Frame frame, MethodHandle hMethod, int iReturn)
        {
        Constants.Access access  = hMethod.getMethodInfo().getAccess();
        ObjectHandle     hAccess = xRTType.makeAccessHandle(frame, access);
        return frame.assignValue(iReturn, hAccess);
        }

    /**
     * Implements property: annotations.get()
     */
    public int getPropertyAnnotations(Frame frame, MethodHandle hMethod, int iReturn)
        {
        MethodStructure method = hMethod.getMethod();
        Annotation[]    aAnno  = method.getAnnotations();

        return aAnno.length > 0
                ? new Utils.CreateAnnos(aAnno, iReturn).doNext(frame)
                : frame.assignValue(iReturn,
                    Utils.makeAnnoArrayHandle(frame.poolContext(), Utils.OBJECTS_NONE));
        }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code Function<ParamTypes, ReturnTypes> bindTarget(Target target)}.
     */
    public int invokeBindTarget(Frame frame, MethodHandle hMethod, ObjectHandle hArg, int iReturn)
        {
        CallChain chain = hMethod.getCallChain(frame, hArg);

        return frame.assignValue(iReturn, hArg.isService()
                ? xRTFunction.makeAsyncHandle(chain).bindTarget(hArg)
                : xRTFunction.makeHandle(chain, 0).bindTarget(hArg));
        }

    /**
     * Implementation for: {@code ReturnTypes invoke(Target target, ParamTypes args)}.
     */
    public int invokeInvoke(Frame frame, MethodHandle hMethod, ObjectHandle[] ahArg, int iReturn)
        {
        ObjectHandle   hTarget = ahArg[0];
        TupleHandle    hTuple  = (TupleHandle) ahArg[1];
        ObjectHandle[] ahPass  = hTuple.m_ahValue;            // TODO GG+CP do we need to check these?
        CallChain      chain   = hMethod.getCallChain(frame, hTarget);

        return chain.isNative()
                ? hTarget.getTemplate().invokeNativeT(frame, chain.getTop(), hTarget, ahPass, iReturn)
                : hTarget.getTemplate().invokeT(frame, chain, hTarget,
                        Utils.ensureSize(ahPass, chain.getMaxVars()), iReturn);
        }

    /**
     * Implementation for: {@code conditional String[] formalParamNames(Int i)}.
     */
    public int invokeFormalParamNames(Frame frame, MethodHandle hMethod, int[] aiReturn)
        {
        // TODO
        throw new UnsupportedOperationException("TODO");
        }

    /**
     * Implementation for: {@code conditional String[] formalReturnNames(Int i)}.
     */
    public int invokeFormalReturnNames(Frame frame, MethodHandle hMethod, int[] aiReturn)
        {
        // TODO
        throw new UnsupportedOperationException("TODO");
        }


    // ----- Object handle -------------------------------------------------------------------------

    public static MethodHandle makeHandle(TypeConstant typeTarget, MethodConstant idMethod)
        {
        ConstantPool pool = INSTANCE.pool();
        TypeConstant type = idMethod.getSignature().asMethodType(pool, typeTarget);

        return new MethodHandle(type, idMethod);
        }

    /**
     * Method handle.
     *
     * Similarly to the {@link xRTFunction.FunctionHandle}, all Method handles are based on a
     * "fully bound" type and carry the actual type as a part of their state,
     */
    public static class MethodHandle
            extends SignatureHandle
        {
        protected MethodHandle(TypeConstant type, MethodConstant idMethod)
            {
            super(INSTANCE.ensureClass(type), idMethod, (MethodStructure) idMethod.getComponent(), type);
            }

        public TypeConstant getTargetType()
            {
            return getType().resolveGenericType("Target");
            }

        public TypeInfo getTargetInfo()
            {
            return getTargetType().ensureTypeInfo();
            }

        public MethodInfo getMethodInfo()
            {
            return getTargetInfo().getMethodById(f_idMethod);
            }

        @Override
        public TypeConstant getParamType(int iArg)
            {
            return getMethodInfo().getIdentity().getSignature().getRawParams()[iArg];
            }

        @Override
        public TypeConstant getReturnType(int iArg)
            {
            return getMethodInfo().getIdentity().getSignature().getRawReturns()[iArg];
            }

        private CallChain getCallChain(Frame frame, ObjectHandle hTarget)
            {
            TypeComposition clazz    = hTarget.getComposition();
            MethodConstant  idMethod = getMethodId();
            MethodStructure method   = getMethod();
            if (method == null)
                {
                method = (MethodStructure) idMethod.getComponent();
                }

            CallChain chain;
            if (method != null && method.getAccess() == Constants.Access.PRIVATE)
                {
                chain = new CallChain(method);
                }
            else
                {
                SignatureConstant sig = idMethod.getSignature().
                        resolveGenericTypes(frame.poolContext(), frame.getGenericsResolver());

                chain = clazz.getMethodCallChain(sig);
                if (chain.getDepth() == 0)
                    {
                    return new CallChain.ExceptionChain(idMethod, hTarget.getType());
                    }
                }
            return chain;
            }

        @Override
        public String toString()
            {
            return "Method: " + getMethod();
            }
        }


    // ----- Template, Composition, and handle caching ---------------------------------------------

    /**
     * @return the ClassTemplate for an Array of Method
     */
    public static xArray ensureArrayTemplate()
        {
        xArray template = ARRAY_TEMPLATE;
        if (template == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeMethodArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeMethod());
            ARRAY_TEMPLATE = template = ((xArray) INSTANCE.f_templates.getTemplate(typeMethodArray));
            assert template != null;
            }
        return template;
        }

    /**
     * @return the ClassComposition for an Array of Method
     */
    public static ClassComposition ensureArrayComposition()
        {
        ClassComposition clz = ARRAY_CLZCOMP;
        if (clz == null)
            {
            ConstantPool pool = INSTANCE.pool();
            TypeConstant typeMethodArray = pool.ensureParameterizedTypeConstant(pool.typeArray(), pool.typeMethod());
            ARRAY_CLZCOMP = clz = INSTANCE.f_templates.resolveClass(typeMethodArray);
            assert clz != null;
            }
        return clz;
        }

    /**
     * @return the handle for an empty Array of Method
     */
    public static ArrayHandle ensureEmptyArray()
        {
        if (ARRAY_EMPTY == null)
            {
            ARRAY_EMPTY = ensureArrayTemplate().createArrayHandle(
                    ensureArrayComposition(), Utils.OBJECTS_NONE);
            }
        return ARRAY_EMPTY;
        }

    /**
     * @return the ClassComposition for an Array of Method
     */
    public static ClassComposition ensureArrayComposition(TypeConstant typeTarget)
        {
        assert typeTarget != null;

        ConstantPool pool            = INSTANCE.pool();
        TypeConstant typeMethodArray = pool.ensureParameterizedTypeConstant(pool.typeArray(),
            pool.ensureParameterizedTypeConstant(pool.typeMethod(), typeTarget));
        return INSTANCE.f_templates.resolveClass(typeMethodArray);
        }


    // ----- data members --------------------------------------------------------------------------

    private static xArray           ARRAY_TEMPLATE;
    private static ClassComposition ARRAY_CLZCOMP;
    private static ArrayHandle      ARRAY_EMPTY;
    }
