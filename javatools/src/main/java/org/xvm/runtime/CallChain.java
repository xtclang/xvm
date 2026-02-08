package org.xvm.runtime;


import org.xvm.asm.Component;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xService.ServiceHandle;

import org.xvm.runtime.template._native.reflect.xRTFunction;
import org.xvm.runtime.template._native.reflect.xRTFunction.FunctionHandle;


/**
 * Represents a chain of invocation.
 */
public class CallChain {
    /**
     * Construct a CallChain for an array of method bodies.
     */
    public CallChain(MethodBody[] aMethods) {
        f_aMethods = aMethods == null
                ? MethodBody.NO_BODIES
                : aMethods;
    }

    /**
     * Construct a CallChain for a lambda or a private method.
     */
    public CallChain(MethodStructure method) {
        f_aMethods = new MethodBody[] {new MethodBody(method)};
    }

    /**
     * @return the chain depth
     */
    public int getDepth() {
        return f_aMethods.length;
    }

    /**
     * @return true iff the chain is empty
     */
    public boolean isEmpty() {
        return f_aMethods.length == 0;
    }

    /**
     * @return true iff the top body of this chain is a delegating method for an atomic property
     */
    public boolean isAtomic() {
        if (m_FAtomic != null) {
            return m_FAtomic.booleanValue();
        }

        if (f_aMethods.length > 0 && f_aMethods[0].getImplementation() == Implementation.Delegating) {
            PropertyConstant  idDelegate   = f_aMethods[0].getPropertyConstant();
            PropertyStructure propDelegate = (PropertyStructure) idDelegate.getComponent();
            return m_FAtomic = propDelegate != null && propDelegate.isAtomic();
        }

        return m_FAtomic = Boolean.FALSE;
    }

    /**
     * @return the method at the specified depth
     */
    public MethodStructure getMethod(int nDepth) {
        return nDepth < f_aMethods.length
                ? f_aMethods[nDepth].getMethodStructure()
                : null;
    }

    /**
     * @return the top method
     */
    public MethodStructure getTop() {
        return f_aMethods.length == 0
                ? null
                : f_aMethods[0].getMethodStructure();
    }

    /**
     * @return the max var count for the top method
     */
    public int getMaxVars() {
        MethodStructure method = getTop();
        return method == null
                ? 0
                : method.getMaxVars();
    }

    /**
     * @return the super method for the specified frame (on this chain)
     */
    public MethodStructure getSuper(Frame frame) {
        return getMethod(frame.m_nChainDepth + 1);
    }

    /**
     * @return true iff the chain is native
     */
    public boolean isNative() {
        return f_aMethods.length == 0 ||
               f_aMethods[0].getImplementation() == Implementation.Native;
    }

    /**
     * @return true iff the chain represents a field
     */
    public boolean isField() {
        return f_aMethods.length > 0 &&
               f_aMethods[0].getImplementation() == Implementation.Field;
    }

    /**
     * @return true iff the chain represents an explicit implementation
     */
    public boolean isExplicit() {
        return f_aMethods.length > 0 &&
               f_aMethods[0].getImplementation() == Implementation.Explicit;
    }

    /**
     * @return the property this chain represents access to
     */
    public PropertyStructure getProperty() {
        return (PropertyStructure) f_aMethods[0].getIdentity().getNamespace().getComponent();
    }

    /**
     * Chain invocation with zero args and one return value.
     */
    public int invoke(Frame frame, ObjectHandle hTarget, int iReturn) {
        if (isNative()) {
            return hTarget.getTemplate().
                invokeNativeN(frame, getTop(), hTarget, Utils.OBJECTS_NONE, iReturn);
        }

        ObjectHandle[] ahVar = new ObjectHandle[getMaxVars()];

        return hTarget.getTemplate().invoke1(frame, this, hTarget, ahVar, iReturn);
    }

    /**
     * Chain invocation with one arg and one return value.
     */
    public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        if (isNative()) {
            return hTarget.getTemplate().
                invokeNative1(frame, getTop(), hTarget, hArg, iReturn);
        }

        ObjectHandle[] ahVar = new ObjectHandle[Math.max(getMaxVars(), 1)];
        ahVar[0] = hArg;

        return hTarget.getTemplate().invoke1(frame, this, hTarget, ahVar, iReturn);
    }

    /**
     * Chain invocation with one arg and multiple return values.
     */
    public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn) {
        if (isNative()) {
            return hTarget.getTemplate().
                invokeNativeNN(frame, getTop(), hTarget, new ObjectHandle[]{hArg}, aiReturn);
        }

        ObjectHandle[] ahVar = new ObjectHandle[Math.max(getMaxVars(), 1)];
        ahVar[0] = hArg;

        return hTarget.getTemplate().invokeN(frame, this, hTarget, ahVar, aiReturn);
    }

    /**
     * Chain invocation with multiple arg and single return value.
     */
    public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn) {
        if (isNative()) {
            ClassTemplate template = hTarget.getTemplate();
            return ahArg.length == 1
                    ? template.invokeNative1(frame, getTop(), hTarget, ahArg[0], iReturn)
                    : template.invokeNativeN(frame, getTop(), hTarget, ahArg, iReturn);
        }

        ObjectHandle[] ahVar = Utils.ensureSize(ahArg, getMaxVars());

        return hTarget.getTemplate().invoke1(frame, this, hTarget, ahVar, iReturn);
    }

    /**
     * Chain invocation with multiple arg and multiple return values.
     */
    public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn) {
        if (isNative()) {
            return hTarget.getTemplate().
                invokeNativeNN(frame, getTop(), hTarget, ahArg, aiReturn);
        }

        ObjectHandle[] ahVar = Utils.ensureSize(ahArg, getMaxVars());

        return hTarget.getTemplate().invokeN(frame, this, hTarget, ahVar, aiReturn);
    }

    /**
     * Chain invocation with a single arg and single return tuple value.
     */
    public int invokeT(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        if (isNative()) {
            return hTarget.getTemplate().
                invokeNativeT(frame, getTop(), hTarget, new ObjectHandle[] {hArg}, iReturn);
        }

        ObjectHandle[] ahVar = new ObjectHandle[Math.max(getMaxVars(), 1)];
        ahVar[0] = hArg;

        return hTarget.getTemplate().invokeT(frame, this, hTarget, ahVar, iReturn);
    }

    /**
     * Chain invocation with multiple arg and single return tuple value.
     */
    public int invokeT(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn) {
        if (isNative()) {
            return hTarget.getTemplate().invokeNativeT(frame, getTop(), hTarget, ahArg, iReturn);
        }

        ObjectHandle[] ahVar = Utils.ensureSize(ahArg, getMaxVars());

        return hTarget.getTemplate().invokeT(frame, this, hTarget, ahVar, iReturn);
    }

    /**
     * Target binding.
     */
    public int bindTarget(Frame frame, ObjectHandle hTarget, int iReturn) {
        return frame.assignValue(iReturn, hTarget.isService() ?
                xRTFunction.makeAsyncHandle(frame, this).bindTarget(frame, hTarget) :
                xRTFunction.makeHandle(frame, this, 0).bindTarget(frame, hTarget));
    }

    /**
     * Create a CallChain representing a property access.
     */
    public static CallChain createPropertyCallChain(MethodBody[] aMethods) {
        return aMethods.length == 1 && aMethods[0].getImplementation() == Implementation.Field
                ? new FieldAccessChain(aMethods)
                : new CallChain(aMethods);
    }

    /**
     * Super invocation with no arguments and a single return (A_IGNORE for void).
     */
    public int callSuper01(Frame frame, int iReturn) {
        int nDepth = frame.m_nChainDepth + 1;
        if (nDepth >= f_aMethods.length) {
            return missingSuper(frame);
        }

        ObjectHandle hThis     = frame.getThis();
        MethodBody   bodySuper = f_aMethods[nDepth];

        switch (bodySuper.getImplementation()) {
        case Field:
            return hThis.getComposition().getFieldValue(frame,
                hThis, bodySuper.getPropertyConstant(), iReturn);

        case Native: {
            ClassTemplate   template  = hThis.getTemplate();
            MethodStructure method    = bodySuper.getMethodStructure();
            Component       container = method.getParent().getParent();

            return container instanceof PropertyStructure
                ? template.invokeNativeGet(frame, container.getName(), hThis, iReturn)
                : template.invokeNativeN(frame, method, hThis, Utils.OBJECTS_NONE, iReturn);
        }

        case Default, Explicit: {
            MethodStructure methodSuper = bodySuper.getMethodStructure();

            ObjectHandle[] ahVar = new ObjectHandle[methodSuper.getMaxVars()];
            return frame.invoke1(this, nDepth, hThis, ahVar, iReturn);
        }

        case Delegating: {
            SignatureConstant sig    = bodySuper.getSignature();
            PropertyConstant  idProp = bodySuper.getPropertyConstant();

            switch (hThis.getTemplate().getPropertyValue(frame, hThis, idProp, Op.A_STACK)) {
            case Op.R_NEXT:
                return completeDelegate(frame, frame.popStack(), sig, iReturn);

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    completeDelegate(frameCaller, frameCaller.popStack(), sig, iReturn));
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

        default:
            throw new IllegalStateException();
        }
    }

    private int completeDelegate(Frame frame, ObjectHandle hTarget, SignatureConstant sig, int iReturn) {
        CallChain chain = hTarget.getComposition().getMethodCallChain(sig);
        return chain.isEmpty()
                ? missingSuper(frame)
                : chain.invoke(frame, hTarget, iReturn);
    }

    /**
     * Super invocation with a single arguments and a single return (A_IGNORE for void).
     */
    public int callSuper11(Frame frame, ObjectHandle hArg, int iReturn) {
        int nDepth = frame.m_nChainDepth + 1;
        if (nDepth >= f_aMethods.length) {
            return missingSuper(frame);
        }

        ObjectHandle hThis     = frame.getThis();
        MethodBody   bodySuper = f_aMethods[nDepth];

        switch (bodySuper.getImplementation()) {
        case Field:
            return hThis.getComposition().setFieldValue(frame,
                hThis, bodySuper.getPropertyConstant(), hArg);

        case Native:
            return hThis.getTemplate().invokeNative1(frame, bodySuper.getMethodStructure(),
                hThis, hArg, iReturn);

        case Default, Explicit: {
            MethodStructure methodSuper = bodySuper.getMethodStructure();
            ObjectHandle[]  ahVar       = new ObjectHandle[Math.max(methodSuper.getMaxVars(), 1)];
            ahVar[0] = hArg;

            return frame.invoke1(this, nDepth, hThis, ahVar, iReturn);
        }

        case Delegating: {
            SignatureConstant sig    = bodySuper.getSignature();
            PropertyConstant  idProp = bodySuper.getPropertyConstant();

            switch (hThis.getTemplate().getPropertyValue(frame, hThis, idProp, Op.A_STACK)) {
            case Op.R_NEXT:
                return completeDelegate(frame, frame.popStack(), sig, hArg, iReturn);

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    completeDelegate(frameCaller, frameCaller.popStack(), sig, hArg, iReturn));
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

        default:
            throw new IllegalStateException();
        }
    }

    private int completeDelegate(Frame frame, ObjectHandle hTarget, SignatureConstant sig,
                                 ObjectHandle hArg, int iReturn) {
        CallChain chain = hTarget.getComposition().getMethodCallChain(sig);
        return chain.isEmpty()
                ? missingSuper(frame)
                : chain.invoke(frame, hTarget, hArg, iReturn);
    }

    /**
     * Super invocation with multiple arguments and a single return.
     */
    public int callSuperN1(Frame frame, ObjectHandle[] ahArg, int iReturn, boolean fReturnTuple) {
        int nDepth = frame.m_nChainDepth + 1;
        if (nDepth >= f_aMethods.length) {
            return missingSuper(frame);
        }

        ObjectHandle    hThis       = frame.getThis();
        MethodBody      bodySuper   = f_aMethods[nDepth];
        MethodStructure methodSuper = bodySuper.getMethodStructure();

        switch (bodySuper.getImplementation()) {
        case Native: {
            ClassTemplate template = hThis.getTemplate();
            return fReturnTuple
                ? template.invokeNativeT(frame, methodSuper, hThis, ahArg, iReturn)
                : ahArg.length == 1
                    ? template.invokeNative1(frame, methodSuper, hThis, ahArg[0], iReturn)
                    : template.invokeNativeN(frame, methodSuper, hThis, ahArg, iReturn);
        }

        case Default, Explicit: {
            ObjectHandle[] ahVar = Utils.ensureSize(ahArg, methodSuper.getMaxVars());
            return fReturnTuple
                    ? frame.invokeT(this, nDepth, hThis, ahVar, iReturn)
                    : frame.invoke1(this, nDepth, hThis, ahVar, iReturn);
        }

        case Delegating: {
            SignatureConstant sig    = bodySuper.getSignature();
            PropertyConstant  idProp = bodySuper.getPropertyConstant();

            switch (hThis.getTemplate().getPropertyValue(frame, hThis, idProp, Op.A_STACK)) {
            case Op.R_NEXT:
                return completeDelegate(frame, frame.popStack(),
                    sig, ahArg, iReturn, fReturnTuple);

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    completeDelegate(frameCaller, frameCaller.popStack(),
                        sig, ahArg, iReturn, fReturnTuple));
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

        default:
            throw new IllegalStateException();
        }
    }

    private int completeDelegate(Frame frame, ObjectHandle hTarget, SignatureConstant sig,
                                 ObjectHandle[] ahArg, int iReturn, boolean fReturnTuple) {
        CallChain chain = hTarget.getComposition().getMethodCallChain(sig);
        return chain.isEmpty()
                ? missingSuper(frame)
                : fReturnTuple
                    ? chain.invokeT(frame, hTarget, ahArg, iReturn)
                    : chain.invoke(frame, hTarget, ahArg, iReturn);
    }

    /**
     * Super invocation with multiple arguments and multiple returns.
     */
    public int callSuperNN(Frame frame, ObjectHandle[] ahArg, int[] aiReturn) {
        int nDepth = frame.m_nChainDepth + 1;
        if (nDepth >= f_aMethods.length) {
            return missingSuper(frame);
        }

        ObjectHandle    hThis       = frame.getThis();
        MethodBody      bodySuper   = f_aMethods[nDepth];
        MethodStructure methodSuper = bodySuper.getMethodStructure();

        switch (bodySuper.getImplementation()) {
        case Native:
            return hThis.getTemplate().invokeNativeNN(frame, methodSuper, hThis, ahArg, aiReturn);

        case Default, Explicit:
            return frame.invokeN(this, nDepth, hThis,
                    Utils.ensureSize(ahArg, methodSuper.getMaxVars()), aiReturn);

        case Delegating: {
            SignatureConstant sig    = bodySuper.getSignature();
            PropertyConstant  idProp = bodySuper.getPropertyConstant();

            switch (hThis.getTemplate().getPropertyValue(frame, hThis, idProp, Op.A_STACK)) {
            case Op.R_NEXT:
                return completeDelegate(frame, frame.popStack(), sig, ahArg, aiReturn);

            case Op.R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    completeDelegate(frameCaller, frameCaller.popStack(), sig, ahArg, aiReturn));
                return Op.R_CALL;

            case Op.R_EXCEPTION:
                return Op.R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

        default:
            throw new IllegalStateException();
        }
    }

    private int completeDelegate(Frame frame, ObjectHandle hTarget, SignatureConstant sig,
                                 ObjectHandle[] ahArg, int[] aiReturn) {
        CallChain chain = hTarget.getComposition().getMethodCallChain(sig);
        return chain.isEmpty()
                ? missingSuper(frame)
                : chain.invoke(frame, hTarget, ahArg, aiReturn);
    }

    /**
     * Raise a "missing super" exception.
     */
    private int missingSuper(Frame frame) {
        SignatureConstant sig = f_aMethods[0].getSignature().removeAutoNarrowing();

        return frame.raiseException(xException.makeHandle(frame,
            "Missing super() implementation for \"" + sig.getValueString() +
                "\" on \"" + frame.getThis().getType().removeAccess().getValueString() + '"'));
    }


    // ----- CallChain subclasses ------------------------------------------------------------------

    /**
     * A CallChain representing a field access.
     */
    public static class FieldAccessChain
            extends CallChain {
        public FieldAccessChain(MethodBody[] aMethods) {
            super(aMethods);

            assert f_aMethods.length > 0 &&
                   f_aMethods[0].getImplementation() == Implementation.Field;
        }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, int iReturn) {
            return hTarget.getTemplate().getFieldValue(frame, hTarget,
                f_aMethods[0].getPropertyConstant(), iReturn);
        }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
            assert iReturn == Op.A_IGNORE;

            return hTarget.getTemplate().setFieldValue(frame, hTarget,
                f_aMethods[0].getPropertyConstant(), hArg);
        }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn) {
            assert ahArg.length > 1 && iReturn == Op.A_IGNORE;

            return hTarget.getTemplate().setFieldValue(frame, hTarget,
                f_aMethods[0].getPropertyConstant(), ahArg[0]);
        }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn) {
            throw new IllegalStateException();
        }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn) {
            throw new IllegalStateException();
        }

        @Override
        public int invokeT(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
            throw new IllegalStateException();
        }

        @Override
        public int invokeT(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn) {
            throw new IllegalStateException();
        }

        @Override
        public int bindTarget(Frame frame, ObjectHandle hTarget, int iReturn) {
            throw new IllegalStateException();
        }
    }

    /**
     * A CallChain representing a virtual constructor.
     */
    public static class VirtualConstructorChain
            extends CallChain {
        public VirtualConstructorChain(ConstantPool pool, MethodConstant idConstructor,
                                       ObjectHandle hTarget) {
            super((MethodBody[]) null);

            TypeComposition clzTarget  = hTarget.getComposition();
            TypeConstant    typeTarget = clzTarget.getType();

            f_idConstructor = idConstructor;
            f_clzTarget     = clzTarget;

            TypeInfo   infoTarget = typeTarget.ensureTypeInfo();
            MethodInfo infoCtor   = infoTarget.findVirtualConstructor(idConstructor.getSignature());
            if (infoCtor == null) {
                f_constructor = null;
                f_typeCtor    = null;
            } else {
                MethodStructure constructor = infoCtor.getTopmostMethodStructure(infoTarget);
                TypeConstant[]  atypeParam  = constructor.getIdentityConstant().getSignature().
                                                resolveGenericTypes(pool, typeTarget).getRawParams();
                f_constructor = constructor;
                f_typeCtor    = pool.buildFunctionType(atypeParam, typeTarget);
            }
        }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, int iReturn) {
            throw new IllegalStateException();
        }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
            throw new IllegalStateException();
        }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn) {
            throw new IllegalStateException();
        }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn) {
            throw new IllegalStateException();
        }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn) {
            throw new IllegalStateException();
        }

        @Override
        public int invokeT(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
            throw new IllegalStateException();
        }

        @Override
        public int invokeT(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn) {
            throw new IllegalStateException();
        }

        @Override
        public int bindTarget(Frame frame, ObjectHandle hTarget, int iReturn) {
            if (f_constructor == null) {
                return frame.raiseException("Failed to find a virtual constructor " +
                        f_idConstructor.getValueString() + " at " +
                        f_clzTarget.getType().getValueString());
            }

            ObjectHandle hCtor = xRTFunction.makeConstructorHandle(
                    frame, f_constructor, f_typeCtor, f_clzTarget, f_constructor.getParamArray(), false);
            if (hTarget instanceof ServiceHandle hService) {
                if (Op.isDeferred(hCtor)) {
                    frame.m_frameNext.addContinuation(frameCaller ->
                        frameCaller.assignValue(iReturn,
                            xRTFunction.makeAsyncDelegatingHandle(hService,
                                (FunctionHandle) frameCaller.popStack())));
                    return Op.R_CALL;
                }
                return frame.assignValue(iReturn,
                        xRTFunction.makeAsyncDelegatingHandle(hService, (FunctionHandle) hCtor));
            } else {
                return frame.assignDeferredValue(iReturn, hCtor);
            }
        }

        private final MethodConstant  f_idConstructor;
        private final MethodStructure f_constructor;
        private final TypeConstant    f_typeCtor;
        private final TypeComposition f_clzTarget;
    }

    /**
     * A CallChain representing an exception.
     */
    public static class ExceptionChain
            extends CallChain {
        public ExceptionChain(ExceptionHandle hException) {
            super(MethodBody.NO_BODIES);

            f_hException = hException;
        }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, int iReturn) {
            return throwException(frame);
        }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
            return throwException(frame);
        }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn) {
            return throwException(frame);
        }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn) {
            return throwException(frame);
        }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn) {
            return throwException(frame);
        }

        @Override
        public int invokeT(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
            return throwException(frame);
        }

        @Override
        public int invokeT(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn) {
            return throwException(frame);
        }

        @Override
        public int bindTarget(Frame frame, ObjectHandle hTarget, int iReturn) {
            return throwException(frame);
        }

        private int throwException(Frame frame) {
            return frame.raiseException(f_hException);
        }

        private final ExceptionHandle f_hException;
    }


    // ----- debugging support ---------------------------------------------------------------------

    @Override
    public String toString() {
        return f_aMethods.length == 0
            ? "empty"
            : f_aMethods[0].getIdentity().getSignature().getValueString() +
                (isNative()
                    ? "; native"
                    : "; depth=" + getDepth());
    }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * An array of method bodies.
     */
    protected final MethodBody[] f_aMethods;

    /**
     * Cached response for "isAtomic()" API.
     */
    private Boolean m_FAtomic;
}