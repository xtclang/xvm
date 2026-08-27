package org.xvm.runtime.template._native.reflect;


import org.xvm.asm.Annotation;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.GenericHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xBoolean.BooleanHandle;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xNullable;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.numbers.xInt64;

import org.xvm.runtime.template.text.xString;

import org.xvm.util.Lazy;


/**
 * Native (abstract level) Method and Function implementation.
 */
public class xRTSignature
        extends ClassTemplate {
    public xRTSignature(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void initNative() {
        markNativeProperty("name");
        markNativeProperty("params");
        markNativeProperty("returns");
        markNativeProperty("conditionalResult");
        markNativeProperty("futureResult");

        markNativeMethod("hasTemplate", null, null);

        invalidateTypeInfo();
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        SignatureHandle hFunc = (SignatureHandle) hTarget;

        switch (sPropName) {
        case "name":
            return getPropertyName(frame, hFunc, iReturn);

        case "params":
            return getPropertyParams(frame, hFunc, iReturn);

        case "returns":
            return getPropertyReturns(frame, hFunc, iReturn);

        case "conditionalResult":
            return getPropertyConditionalResult(frame, hFunc, iReturn);

        case "futureResult":
            return getPropertyFutureResult(frame, hFunc, iReturn);

        case "ParamTypes":
            return getPropertyParamTypes(frame, hFunc, iReturn);

        case "ReturnTypes":
            return getPropertyReturnTypes(frame, hFunc, iReturn);
        }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
    }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                              ObjectHandle[] ahArg, int[] aiReturn) {
        SignatureHandle hFunc = (SignatureHandle) hTarget;
        switch (method.getName()) {
        case "hasTemplate":
            return invokeHasTemplate(frame, hFunc, aiReturn);
        }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
    }


    // ----- property implementations --------------------------------------------------------------

    /**
     * Implements property: name.get()
     */
    protected int getPropertyName(Frame frame, SignatureHandle hFunc, int iReturn) {
        return frame.assignValue(iReturn, xString.makeHandle(frame, hFunc.getName()));
    }

    /**
     * Implements property: params.get()
     */
    protected int getPropertyParams(Frame frame, SignatureHandle hFunc, int iReturn) {
        return new RTArrayConstructor(frame.container(), hFunc, false, iReturn).doNext(frame);
    }

    /**
     * Implements property: params.get()
     */
    protected int getPropertyReturns(Frame frame, SignatureHandle hFunc, int iReturn) {
        return new RTArrayConstructor(frame.container(), hFunc, true, iReturn).doNext(frame);
    }

    /**
     * Implements property: conditionalResult.get()
     */
    protected int getPropertyConditionalResult(Frame frame, SignatureHandle hFunc, int iReturn) {
        MethodStructure structFunc = hFunc.getMethod();
        BooleanHandle   handle     = xBoolean.makeHandle(frame, structFunc.isConditionalReturn());
        return frame.assignValue(iReturn, handle);
    }

    /**
     * Implements property: futureResult.get()
     */
    protected int getPropertyFutureResult(Frame frame, SignatureHandle hFunc, int iReturn) {
        BooleanHandle handle = xBoolean.makeHandle(frame, hFunc.isAsync());
        return frame.assignValue(iReturn, handle);
    }

    /**
     * Implements formal property: ParamTypes
     */
    protected int getPropertyParamTypes(Frame frame, SignatureHandle hFunc, int iReturn) {
        int              cParams  = hFunc.getParamCount();
        ObjectHandle[]   ahType   = new ObjectHandle[cParams];
        TypeComposition clzArray = xRTType.ensureTypeArrayComposition(frame.f_context.f_container);
        for (int i = 0; i < cParams; i++) {
            ahType[i] = hFunc.getParamType(i).ensureTypeHandle(frame.f_context.f_container);
        }
        return frame.assignValue(iReturn, xArray.createImmutableArray(clzArray, ahType));
    }

    /**
     * Implements formal property: ReturnTypes
     */
    protected int getPropertyReturnTypes(Frame frame, SignatureHandle hFunc, int iReturn) {
        int              cReturns = hFunc.getReturnCount();
        ObjectHandle[]   ahType   = new ObjectHandle[cReturns];
        TypeComposition clzArray = xRTType.ensureTypeArrayComposition(frame.f_context.f_container);
        for (int i = 0; i < cReturns; i++) {
            ahType[i] = hFunc.getReturnType(i).ensureTypeHandle(frame.f_context.f_container);
        }
        return frame.assignValue(iReturn, xArray.createImmutableArray(clzArray, ahType));
    }


    // ----- method implementations --------------------------------------------------------------

    /**
     * Method implementation: `conditional MethodTemplate hasTemplate()`
     */
    public int invokeHasTemplate(Frame frame, SignatureHandle hFunc, int[] aiReturn) {
        MethodStructure method = hFunc.getMethod();
        return method == null
                ? frame.assignValue(aiReturn[0], xBoolean.falseHandle(frame))
                : frame.assignValues(aiReturn, xBoolean.trueHandle(frame),
                    xRTMethodTemplate.makeHandle(frame.container(), method));
    }


    // ----- Template and TypeComposition caching and helpers -------------------------------------

    /**
     * @return the TypeConstant for a Return
     */
    public static TypeConstant ensureReturnType(Container container) {
        xRTSignature template = getInstance(container);
        return template.f_typeReturn.get(template);
    }

    /**
     * @return the TypeConstant for an RTReturn
     */
    public static TypeConstant ensureRTReturnType(Container container) {
        xRTSignature template = getInstance(container);
        return template.f_typeRTReturn.get(template);
    }

    /**
     * @return the TypeConstant for a Parameter
     */
    public static TypeConstant ensureParamType(Container container) {
        xRTSignature template = getInstance(container);
        return template.f_typeParam.get(template);
    }

    /**
     * @return the TypeConstant for an RTParameter
     */
    public static TypeConstant ensureRTParamType(Container container) {
        xRTSignature template = getInstance(container);
        return template.f_typeRTParam.get(template);
    }

    /**
     * @return the ClassTemplate for an RTReturn
     */
    public static xConst ensureRTReturnTemplate(Container container) {
        xRTSignature template = getInstance(container);
        return template.f_templateRTReturn.get(template);
    }

    /**
     * @return the ClassTemplate for an RTParameter
     */
    public static xConst ensureRTParamTemplate(Container container) {
        xRTSignature template = getInstance(container);
        return template.f_templateRTParam.get(template);
    }

    /**
     * @return the TypeComposition for an RTReturn of the specified type
     */
    public static TypeComposition ensureRTReturn(Frame frame, TypeConstant typeValue) {
        assert typeValue != null;

        ConstantPool pool   = frame.poolContext();
        Container    container = frame.container();
        TypeConstant type   = pool.ensureParameterizedTypeConstant(
                ensureReturnType(container), typeValue);
        TypeConstant typeRT = pool.ensureParameterizedTypeConstant(
                ensureRTReturnType(container), typeValue);

        return ensureRTReturnTemplate(container).ensureClass(container, typeRT, type);
    }

    /**
     * @return the TypeComposition for a RTParameter of the specified type and annotations
     */
    public static TypeComposition ensureRTParameter(Frame frame, TypeConstant typeValue,
                                                    Annotation[] aAnno) {
        assert typeValue != null;

        ConstantPool pool   = frame.poolContext();
        Container    container = frame.container();
        TypeConstant type   = pool.ensureParameterizedTypeConstant(
                ensureParamType(container), typeValue);
        TypeConstant typeRT = pool.ensureParameterizedTypeConstant(
                ensureRTParamType(container), typeValue);

        if (aAnno.length > 0) {
            type   = pool.ensureAnnotatedTypeConstant(type, aAnno);
            typeRT = pool.ensureAnnotatedTypeConstant(typeRT, aAnno);
        }

        return ensureRTParamTemplate(container).ensureClass(container, typeRT, type);
    }

    /**
     * @return the TypeComposition for an Array of Return
     */
    public static TypeComposition ensureReturnArray(Container container) {
        xRTSignature template = getInstance(container);
        return template.f_clzReturnArray.get(template);
    }

    /**
     * @return the TypeComposition for an Array of Parameter
     */
    public static TypeComposition ensureParamArray(Container container) {
        xRTSignature template = getInstance(container);
        return template.f_clzParamArray.get(template);
    }

    /**
     * @return the caller-owned native Signature template
     */
    public static xRTSignature getInstance(Container container) {
        return NativeTemplates.get(container).signature();
    }


    // ----- Object handle -------------------------------------------------------------------------

    /**
     * Signature handle.
     */
    public abstract static class SignatureHandle
            extends GenericHandle {
        // ----- constructors -----------------------------------------------------------------

        protected SignatureHandle(TypeComposition clz, MethodConstant idMethod,
                                  MethodStructure method, TypeConstant type) {
            super(clz);

            f_type     = type;
            f_idMethod = idMethod;
            f_method   = method;
            f_chain    = null;
            f_nDepth   = 0;
        }

        protected SignatureHandle(TypeComposition clz, CallChain chain, int nDepth) {
            super(clz);

            f_idMethod = chain.getMethod(nDepth).getIdentityConstant();
            f_type     = f_idMethod.getSignature().asFunctionType();
            f_method   = null;
            f_chain    = chain;
            f_nDepth   = nDepth;
        }

        // ----- fields -----------------------------------------------------------------------

        @Override
        public TypeConstant getType() {
            return augmentType(f_type);
        }

        public MethodConstant getMethodId() {
            return f_idMethod;
        }

        public String getName() {
            MethodStructure method = getMethod();
            if (method != null) {
                return method.getName();
            }

            MethodConstant id = f_idMethod;
            if (id != null) {
                return id.getName();
            }

            return "native";
        }

        public MethodStructure getMethod() {
            return f_method == null
                    ? f_chain == null
                            ? null
                            : f_chain.getMethod(f_nDepth)
                    : f_method;
        }

        public int getParamCount() {
            MethodStructure method = getMethod();
            return method == null ? 0 : method.getParamCount();
        }

        public Parameter getParam(int iArg) {
            return getMethod().getParam(iArg);
        }

        public TypeConstant getParamType(int iArg) {
            TypeConstant typeFn = getType();
            return typeFn.getConstantPool().extractFunctionParams(typeFn)[iArg];
        }

        public TypeConstant[] getParamTypes() {
            TypeConstant typeFn = getType();
            return typeFn.getConstantPool().extractFunctionParams(typeFn);
        }

        public int getReturnCount() {
            MethodStructure method = getMethod();
            return method == null ? 0 : method.getReturnCount();
        }

        public Parameter getReturn(int iArg) {
            return getMethod().getReturn(iArg);
        }

        public TypeConstant getReturnType(int iArg) {
            TypeConstant typeFn = getType();
            return typeFn.getConstantPool().extractFunctionReturns(typeFn)[iArg];
        }

        public TypeConstant[] getReturnTypes() {
            TypeConstant typeFn = getType();
            return typeFn.getConstantPool().extractFunctionReturns(typeFn);
        }

        public int getVarCount() {
            MethodStructure method = getMethod();
            return method == null ? 0 : method.getMaxVars();
        }

        public boolean isAsync() {
            return false;
        }

        // ----- Object methods --------------------------------------------------------------------

        @Override
        public String toString() {
            return "Signature: " + getMethod();
        }

        // ----- fields -----------------------------------------------------------------------

        /**
         * The underlying function/method constant id.
         */
        protected final MethodConstant f_idMethod;

        /**
         * The underlying function/method. Might be null sometimes for methods.
         */
        protected final MethodStructure f_method;

        /**
         * The function's or method's type.
         */
        protected final TypeConstant f_type;

        /**
         * The method call chain (not null only if function is null).
         */
        protected final CallChain f_chain;
        protected final int       f_nDepth;
    }


    // ----- inner class: RTArrayConstructor -------------------------------------------------------

    /**
     * A continuation helper to create an array of natural RTReturn or RTParameter objects.
     */
    static class RTArrayConstructor
            implements Frame.Continuation {
        protected RTArrayConstructor(Container container, SignatureHandle hMethod,
                                     boolean fRetVals, int iReturn) {
            this.hMethod   = hMethod;
            this.fRetVals  = fRetVals;
            this.template  = fRetVals
                    ? ensureRTReturnTemplate(container)
                    : ensureRTParamTemplate(container);
            this.cElements = fRetVals ? hMethod.getReturnCount() : hMethod.getParamCount();
            this.ahElement = new ObjectHandle[cElements];
            this.construct = template.getStructure().findMethod("construct", fRetVals ? 2 : 5);
            this.ahParams  = new ObjectHandle[fRetVals ? 2 : 5];
            this.iReturn   = iReturn;

            index = -1;
        }

        @Override
        public int proceed(Frame frameCaller) {
            if (fDeferred) {
                ahParams[4] = frameCaller.popStack();
                fDeferred   = false;
            } else {
                ahElement[index] = frameCaller.popStack();
            }
            return doNext(frameCaller);
        }

        public int doNext(Frame frameCaller) {
            while (++index < cElements) {
                Parameter param = fRetVals ? hMethod.getReturn(index) : hMethod.getParam(index);
                String sName = param.getName();

                ahParams[0] = xInt64.makeHandle(frameCaller, index);
                ahParams[1] = sName == null
                        ? xNullable.makeHandle(frameCaller)
                        : xString.makeHandle(frameCaller, sName);
                if (!fRetVals) {
                    ahParams[2] = xBoolean.makeHandle(frameCaller, param.isTypeParameter());
                    if (param.hasDefaultValue()) {
                        ahParams[3] = xBoolean.trueHandle(frameCaller);
                        ObjectHandle hDefault = frameCaller.getConstHandle(param.getDefaultValue());
                        if (Op.isDeferred(hDefault)) {
                            index--;
                            fDeferred = true;
                            return hDefault.proceed(frameCaller, this);
                        }
                        ahParams[4] = hDefault;
                    } else {
                        ahParams[3] = xBoolean.falseHandle(frameCaller);
                        ahParams[4] = xNullable.makeHandle(frameCaller);
                    }
                }

                TypeConstant type = fRetVals
                        ? hMethod.getReturnType(index)
                        : hMethod.getParamType(index);
                TypeComposition clz   = fRetVals
                        ? ensureRTReturn(frameCaller, type)
                        : ensureRTParameter(frameCaller, type, param.getAnnotations());
                switch (template.construct(frameCaller, construct, clz, null, ahParams, Op.A_STACK)) {
                case Op.R_NEXT:
                    ahElement[index] = frameCaller.popStack();
                    break;

                case Op.R_CALL:
                    frameCaller.m_frameNext.addContinuation(this);
                    return Op.R_CALL;

                case Op.R_EXCEPTION:
                    return Op.R_EXCEPTION;

                default:
                    throw new IllegalStateException();
                }
            }

            ObjectHandle hArray = xArray.createImmutableArray(
                    fRetVals
                            ? ensureReturnArray(frameCaller.container())
                            : ensureParamArray(frameCaller.container()),
                    ahElement);
            return frameCaller.assignValue(iReturn, hArray);
        }

        private final SignatureHandle hMethod;
        private final int             cElements;
        private final boolean         fRetVals;
        private final ObjectHandle[]  ahElement;
        private final xConst          template;
        private final MethodStructure construct;
        private final ObjectHandle[]  ahParams;
        private final int             iReturn;
        private       int             index;
        private       boolean         fDeferred; // indicates a deferred computation of default value
    }


    // ----- data members --------------------------------------------------------------------------

    /**
     * Signature metadata belongs to a ConstantPool/Container pair. Keeping these as final lazy
     * fields preserves the old one-time caching behavior without sharing metadata across owners.
     */
    private final Lazy.Bound<xRTSignature, TypeConstant> f_typeReturn = Lazy.ofBound(owner ->
            owner.pool().ensureEcstasyTypeConstant("reflect.Return"));

    private final Lazy.Bound<xRTSignature, TypeConstant> f_typeParam = Lazy.ofBound(owner ->
            owner.pool().typeParameter());

    private final Lazy.Bound<xRTSignature, TypeConstant> f_typeRTReturn = Lazy.ofBound(owner ->
            owner.container().getClassStructure("_native.reflect.RTReturn")
                    .getIdentityConstant().getType());

    private final Lazy.Bound<xRTSignature, TypeConstant> f_typeRTParam = Lazy.ofBound(owner ->
            owner.container().getClassStructure("_native.reflect.RTParameter")
                    .getIdentityConstant().getType());

    private final Lazy.Bound<xRTSignature, xConst> f_templateRTReturn = Lazy.ofBound(owner ->
            owner.container().getTemplate(owner.f_typeRTReturn.get(owner), xConst.class));

    private final Lazy.Bound<xRTSignature, xConst> f_templateRTParam = Lazy.ofBound(owner ->
            owner.container().getTemplate(owner.f_typeRTParam.get(owner), xConst.class));

    private final Lazy.Bound<xRTSignature, TypeComposition> f_clzReturnArray = Lazy.ofBound(owner ->
            owner.container().resolveClass(owner.pool().ensureArrayType(owner.f_typeReturn.get(owner))));

    private final Lazy.Bound<xRTSignature, TypeComposition> f_clzParamArray = Lazy.ofBound(owner ->
            owner.container().resolveClass(owner.pool().ensureArrayType(owner.f_typeParam.get(owner))));
}
