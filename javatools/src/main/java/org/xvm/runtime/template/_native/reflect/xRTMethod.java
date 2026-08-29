package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.DeferredCallHandle;
import org.xvm.runtime.TypeComposition;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xTuple.TupleHandle;

import org.xvm.util.Lazy;
import static org.xvm.util.Handy.copyOf;


/**
 * Native Method implementation.
 */
public class xRTMethod
        extends xRTSignature {

    public static xRTMethod getInstance(Frame frame) {
        return NativeTemplates.get(frame).method();
    }

    public static xRTMethod getInstance(Container container) {
        return NativeTemplates.get(container).method();
    }

    public xRTMethod(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void initNative() {
        markNativeProperty("access");

        markNativeMethod("formalParamNames" , null, null);
        markNativeMethod("formalReturnNames", null, null);
        markNativeMethod("bindTarget"       , null, null);
        markNativeMethod("invoke"           , null, null);

        super.initNative();
    }

    @Override
    public TypeComposition ensureClass(Container container, TypeConstant typeActual) {
        // see explanation at xRTFunction.ensureClass()
        ConstantPool pool = typeActual.getConstantPool();

        assert typeActual.isA(pool.typeMethod());

        TypeConstant typeTarget = typeActual.getParamType(0);
        TypeConstant typeP      = typeActual.getParamType(1);
        TypeConstant typeR      = typeActual.getParamType(2);
        TypeConstant typeMethod = pool.ensureParameterizedTypeConstant(
                                        pool.typeMethod(), typeTarget, typeP, typeR);
        if (typeActual.isAnnotated()) {
            typeMethod = typeMethod.adoptAnnotations(pool, typeActual);
        }

        return super.ensureClass(container, typeMethod);
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        if (constant instanceof MethodConstant idMethod) {
            IdentityConstant idTarget   = idMethod.getNamespace();
            TypeConstant     typeTarget = idTarget.getType();

            if (frame.isMethod()) {
                TypeConstant typeThis = frame.getThis().getType();
                if (typeThis.isNestMateOf(idTarget)) {
                    if (idTarget.equals(typeThis.getDefiningConstant())) {
                        typeTarget = typeThis;
                    } else {
                        typeTarget = ((ClassStructure) idTarget.getComponent()).
                                getFormalType().resolveGenerics(frame.poolContext(), typeThis);
                    }
                }
            }

            ObjectHandle hMethod = makeHandle(frame, typeTarget, idMethod);

            return Op.isDeferred(hMethod)
                    ? hMethod.proceed(frame, Utils.NEXT)
                    : frame.pushStack(hMethod);
        }

        return super.createConstHandle(frame, constant);
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        MethodHandle hMethod = (MethodHandle) hTarget;
        switch (sPropName) {
        case "access":
            return getPropertyAccess(frame, hMethod, iReturn);
        }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        switch (method.getName()) {
        case "bindTarget":
            return invokeBindTarget(frame, (MethodHandle) hTarget, hArg, iReturn);
        }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn) {
        switch (method.getName()) {
        case "invoke":
            return invokeInvoke(frame, (MethodHandle) hTarget, ahArg, iReturn);
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn) {
        MethodHandle hMethod = (MethodHandle) hTarget;
        switch (method.getName()) {
        case "formalParamNames":
            return invokeFormalParamNames(frame, hMethod, aiReturn);

        case "formalReturnNames":
            return invokeFormalReturnNames(frame, hMethod, aiReturn);
        }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
    }

    @Override
    protected int callEqualsImpl(Frame frame, TypeComposition clazz,
                                 ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        MethodHandle hMethod1 = (MethodHandle) hValue1;
        MethodHandle hMethod2 = (MethodHandle) hValue2;

        return frame.assignValue(iReturn,
            xBoolean.makeHandle(frame, hMethod1.getMethodId().equals(hMethod2.getMethodId())));
    }

    @Override
    protected int callCompareImpl(Frame frame, TypeComposition clazz,
                                  ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        MethodHandle hMethod1 = (MethodHandle) hValue1;
        MethodHandle hMethod2 = (MethodHandle) hValue2;

        return frame.assignValue(iReturn,
            xOrdered.makeHandle(frame, hMethod1.getMethodId().compareTo(hMethod2.getMethodId())));
    }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: access.get()
     */
    public int getPropertyAccess(Frame frame, MethodHandle hMethod, int iReturn) {
        Access access = hMethod.getMethodInfo().getAccess();
        return frame.assignDeferredValue(iReturn, xRTType.ensureAccessHandle(frame, access));
    }


    // ----- method implementations ----------------------------------------------------------------

    /**
     * Implementation for: {@code Function<ParamTypes, ReturnTypes> bindTarget(Target target)}.
     */
    public int invokeBindTarget(Frame frame, MethodHandle hMethod, ObjectHandle hTarget, int iReturn) {
        return hMethod.getCallChain(frame, hTarget).bindTarget(frame, hTarget, iReturn);
    }

    /**
     * Implementation for: {@code ReturnTypes invoke(Target target, ParamTypes args)}.
     */
    public int invokeInvoke(Frame frame, MethodHandle hMethod, ObjectHandle[] ahArg, int iReturn) {
        ObjectHandle hTarget = ahArg[0];
        TupleHandle  hTuple  = (TupleHandle) ahArg[1];
        // clone the tuple's storage: when the callee needs no extra registers,
        // Utils.ensureSize() hands the passed array through unchanged and it becomes the callee
        // frame's register file (f_ahVar), so any parameter reassignment inside the invoked
        // method would write into the caller's - possibly immutable, possibly const-heap-cached -
        // tuple (see the equivalent defensive copy in xRTFunction.invokeInvoke)
        ObjectHandle[] ahPass = copyOf(hTuple.m_ahValue);
        CallChain      chain  = hMethod.getCallChain(frame, hTarget);

        return chain.invokeT(frame, hTarget, ahPass, iReturn);
    }

    /**
     * Implementation for: {@code conditional String[] formalParamNames(Int i)}.
     */
    public int invokeFormalParamNames(Frame frame, MethodHandle hMethod, int[] aiReturn) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * Implementation for: {@code conditional String[] formalReturnNames(Int i)}.
     */
    public int invokeFormalReturnNames(Frame frame, MethodHandle hMethod, int[] aiReturn) {
        // TODO
        throw new UnsupportedOperationException("TODO");
    }


    // ----- Object handle -------------------------------------------------------------------------

    /**
     * Obtain a handle for the specified method.
     *
     * @param frame       the current frame
     * @param typeTarget  the type of the method target
     * @param idMethod    the method id
     *
     * @return the resulting {@link MethodHandle} or a {@link DeferredCallHandle}
     */
    public static ObjectHandle makeHandle(Frame frame, TypeConstant typeTarget, MethodConstant idMethod) {
        ConstantPool    pool      = frame.poolContext();
        Container       container = frame.f_context.f_container;
        TypeConstant    type      = idMethod.getSignature().asMethodType(pool, typeTarget);
        MethodStructure method    = idMethod.getComponent();

        if (method == null) {
            TypeInfo   infoTarget = typeTarget.ensureTypeInfo();
            MethodInfo infoMethod = infoTarget.getMethodById(idMethod, true);

            method = infoMethod == null
                ? null
                : infoMethod.getTopmostMethodStructure(infoTarget);
            if (method == null) {
                return new DeferredCallHandle(
                    xException.makeHandle(frame, "Invalid method: " + idMethod.getValueString()));
            }
        }

        Annotation[] aAnno = method.getAnnotations();

        if (aAnno != null && aAnno.length > 0) {
            type = pool.ensureAnnotatedTypeConstant(type, aAnno);

            TypeComposition clzMethod = NativeTemplates.get(container).method().ensureClass(container, type);
            MethodHandle    hStruct   = new MethodHandle(clzMethod.ensureAccess(Access.STRUCT),
                                            type, method, typeTarget);

            int iResult = hStruct.getTemplate().proceedConstruction(
                                frame, null, true, hStruct, Utils.OBJECTS_NONE, Op.A_STACK);
            return frame.popResultImmutable(iResult);
        }

        return new MethodHandle(NativeTemplates.get(container).method().ensureClass(container, type),
                type, method, typeTarget);
    }

    /**
     * Method handle.
     *
     * Similarly to the {@link xRTFunction.FunctionHandle}, all Method handles are based on a
     * "fully bound" type and carry the actual type as a part of their state,
     */
    public static class MethodHandle
            extends SignatureHandle {
        protected MethodHandle(TypeComposition clz, TypeConstant typeMethod, MethodStructure method,
                               TypeConstant typeTarget) {
            super(clz, method.getIdentityConstant(), method, typeMethod);

            m_fMutable   = clz.isStruct();
            f_typeTarget = typeTarget;

            // Preserve the old debug validation without calling getMethodInfo()
            // on a handle whose constructor has not completed yet.
            assert resolveMethodInfo(typeTarget, method) != null;
        }

        public MethodInfo getMethodInfo() {
            return f_typeTarget.ensureTypeInfo().getMethodById(f_idMethod, true);
        }

        @Override
        public TypeConstant getParamType(int iArg) {
            return getMethodInfo().getIdentity().getSignature().getRawParams().get(iArg);
        }

        @Override
        public TypeConstant getReturnType(int iArg) {
            return getMethodInfo().getIdentity().getSignature().getRawReturns().get(iArg);
        }

        private CallChain getCallChain(Frame frame, ObjectHandle hTarget) {
            TypeComposition clazz    = hTarget.getComposition();
            MethodConstant  idMethod = getMethodId();
            MethodStructure method   = getMethod();
            if (method == null) {
                method = idMethod.getComponent();
            }

            CallChain chain;
            if (method != null && method.getAccess() == Access.PRIVATE) {
                chain = new CallChain(method);
            } else {
                SignatureConstant sig = idMethod.getSignature().
                        resolveGenericTypes(frame.poolContext(), frame.getGenericsResolver(true));

                chain = clazz.getMethodCallChain(sig);
                if (chain.isEmpty()) {
                    return new CallChain.ExceptionChain(xException.makeHandle(frame,
                            "Missing method \"" + sig.getValueString() +
                            "\" on " + hTarget.getType().getValueString()));
                }
            }
            return chain;
        }

        @Override
        public String toString() {
            return "Method: " + getMethod();
        }

        private final TypeConstant f_typeTarget;
    }

    private static MethodInfo resolveMethodInfo(TypeConstant typeTarget, MethodStructure method) {
        return typeTarget.ensureTypeInfo().getMethodById(method.getIdentityConstant(), true);
    }


    // ----- Template, Composition, and handle caching ---------------------------------------------

    /**
     * @return the ArrayConstant for an empty Array of Method
     */
    public static ArrayConstant ensureEmptyArrayConstant(Container container) {
        xRTMethod template = NativeTemplates.get(container).method();
        return template.f_constEmptyArray.get(template);
    }

    /**
     * @return the handle for an empty Array of Method
     */
    public static ObjectHandle ensureEmptyArray(Container container) {
        ArrayConstant constArray = ensureEmptyArrayConstant(container);
        var heap = container.getConstHeap();
        ObjectHandle hArray = heap.getConstHandle(container, constArray);
        if (hArray == null) {
            TypeComposition clzArray = container.resolveClass(constArray.getType());
            hArray = xArray.createImmutableArray(clzArray, Utils.OBJECTS_NONE);
            heap.saveConstHandle(container, constArray, hArray);
        }
        return hArray;
    }

    /**
     * @return the TypeComposition for an Array of Method
     */
    public static TypeComposition ensureArrayComposition(Frame frame, TypeConstant typeTarget) {
        assert typeTarget != null;

        ConstantPool pool            = frame.poolContext();
        TypeConstant typeMethodArray = pool.ensureArrayType(
            pool.ensureParameterizedTypeConstant(pool.typeMethod(), typeTarget));
        return frame.container().resolveClass(typeMethodArray);
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * Empty Method[] constant derived from this template's constant pool.
     */
    private final Lazy.Bound<xRTMethod, ArrayConstant> f_constEmptyArray = Lazy.ofBound(owner -> {
        ConstantPool pool = owner.pool();
        return new ArrayConstant(pool, Constant.Format.Array, pool.ensureArrayType(pool.typeMethod()));
    });
}
