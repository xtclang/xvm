package org.xvm.runtime;


import org.xvm.asm.ErrorListener;
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

import org.xvm.util.FrozenArray;

import static java.util.Objects.requireNonNull;


/**
 * Represents a chain of invocation.
 */
public class CallChain {
    /**
     * Construct a CallChain over a chain of method bodies.
     *
     * @param aMethods  the chain; may be empty ({@link MethodBody#NO_BODIES_FROZEN}), never null
     *
     * <p>Null is rejected rather than absorbed. Quietly mapping it to the empty chain is what
     * erased the difference between "no such method" and "the method's chain is empty" - the
     * caller holds that distinction and must decide it where the answer still means something.</p>
     */
    public CallChain(FrozenArray<MethodBody> aMethods) {
        f_aMethods = requireNonNull(aMethods);
    }

    /**
     * Construct a CallChain for a lambda or a private method.
     */
    public CallChain(MethodStructure method) {
        f_aMethods = FrozenArray.adopt(new MethodBody[] {new MethodBody(method)});
    }

    // ----- chain access ---------------------------------------------------------------------------
    //
    // The chain stays a MethodBody[]: a depth-indexed super-call walk on the dispatch hot path is
    // exactly what an array is for, and these accessors are trivially inlined. What the raw array did
    // NOT give us is one place for the access rules - the head and the bounds check were open-coded
    // at 28 sites, and inconsistently (getTop() guarded the empty chain; getProperty() did not, so it
    // threw AIOOBE where the others returned null). Routing every access through two accessors fixes
    // that, and means the storage could later become a FrozenArray by changing two methods rather
    // than twenty-eight.

    /**
     * @return the first body in the chain, or null if the chain is empty
     *
     * <p>protected rather than private so the nested {@code FieldAccessChain} subclass inherits it;
     * a private member is not inherited, and the subclass is static so it has no enclosing instance
     * to fall back on.</p>
     */
    protected MethodBody head() {
        return f_aMethods.isEmpty() ? null : f_aMethods.get(0);
    }

    /**
     * @param nDepth  the chain depth
     *
     * @return the body at that depth, or null if the depth is outside the chain
     */
    protected MethodBody bodyAt(int nDepth) {
        return nDepth < 0 || nDepth >= f_aMethods.size() ? null : f_aMethods.get(nDepth);
    }

    /**
     * @return the chain depth
     */
    public int getDepth() {
        return f_aMethods.size();
    }

    /**
     * @return true iff the chain is empty
     */
    public boolean isEmpty() {
        return f_aMethods.isEmpty();
    }

    /**
     * @return true iff the top body of this chain is a delegating method for an atomic property
     */
    public boolean isAtomic() {
        // read the cached answer ONCE: testing the field and then returning it reads it twice, so a
        // concurrent publication between the two could hand back a null to unbox
        Boolean atomic = m_FAtomic;
        if (atomic != null) {
            return atomic;
        }

        MethodBody bodyHead = head();
        if (bodyHead != null && bodyHead.getImplementation() == Implementation.Delegating) {
            PropertyConstant  idDelegate   = bodyHead.getPropertyConstant();
            PropertyStructure propDelegate = idDelegate.getComponent();
            return m_FAtomic = propDelegate != null && propDelegate.isAtomic();
        }

        return m_FAtomic = Boolean.FALSE;
    }

    /**
     * @return the method at the specified depth
     */
    public MethodStructure getMethod(int nDepth) {
        MethodBody bodyAtDepth = bodyAt(nDepth);
        return bodyAtDepth != null
                ? bodyAtDepth.getMethodStructure()
                : null;
    }

    /**
     * @return the top method
     */
    public MethodStructure getTop() {
        MethodBody bodyHead = head();
        return bodyHead == null ? null : bodyHead.getMethodStructure();
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
        MethodBody bodyHead = head();
        return bodyHead == null || bodyHead.getImplementation() == Implementation.Native;
    }

    /**
     * @return true iff the chain represents a field
     */
    public boolean isField() {
        return isFieldChain(f_aMethods);
    }

    /**
     * @return true iff the chain represents an explicit implementation
     */
    public boolean isExplicit() {
        MethodBody bodyHead = head();
        return bodyHead != null && bodyHead.getImplementation() == Implementation.Explicit;
    }

    /**
     * @return the property this chain represents access to
     */
    public PropertyStructure getProperty() {
        // was an unguarded f_aMethods[0]: every other head access checks for the empty chain, so an
        // empty one AIOOBE'd here rather than reporting the same "no such thing" the others do
        MethodBody bodyHead = head();
        return bodyHead == null
                ? null
                : (PropertyStructure) bodyHead.getIdentity().getNamespace().getComponent();
    }

    /**
     * Chain invocation with zero args and one return value.
     */
    public int invoke(Frame frame, ObjectHandle hTarget, int iReturn) {
        if (isNative()) {
            return hTarget.invokeNativeN(frame, getTop(), Utils.OBJECTS_NONE, iReturn);
        }

        ObjectHandle[] ahVar = new ObjectHandle[getMaxVars()];

        return hTarget.getTemplate().invoke1(frame, this, hTarget, ahVar, iReturn);
    }

    /**
     * Chain invocation with one arg and one return value.
     */
    public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        if (isNative()) {
            return hTarget.invokeNative1(frame, getTop(), hArg, iReturn);
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
            return hTarget.invokeNativeNN(frame, getTop(), new ObjectHandle[]{hArg}, aiReturn);
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
            return ahArg.length == 1
                    ? hTarget.invokeNative1(frame, getTop(), ahArg[0], iReturn)
                    : hTarget.invokeNativeN(frame, getTop(), ahArg, iReturn);
        }

        ObjectHandle[] ahVar = Utils.ensureSize(ahArg, getMaxVars());

        return hTarget.getTemplate().invoke1(frame, this, hTarget, ahVar, iReturn);
    }

    /**
     * Chain invocation with multiple arg and multiple return values.
     */
    public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn) {
        if (isNative()) {
            return hTarget.invokeNativeNN(frame, getTop(), ahArg, aiReturn);
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
    public static CallChain createPropertyCallChain(FrozenArray<MethodBody> aMethods) {
        return aMethods.size() == 1 && aMethods.get(0).getImplementation() == Implementation.Field
                ? new FieldAccessChain(aMethods)
                : new CallChain(aMethods);
    }

    /**
     * How a {@code Delegating} super-invocation finishes once the delegate target is in hand. The
     * four {@code callSuper*} methods differ only here, in the arity they forward.
     */
    @FunctionalInterface
    private interface DelegateStep {
        int complete(Frame frame, ObjectHandle hTarget, SignatureConstant sig);
    }

    /**
     * The shape every {@code Delegating} super-invocation shares: read the delegate target out of
     * its property, then finish the call against it - immediately when the read completed, or from
     * a continuation when it went asynchronous.
     *
     * <p>Extracted because all four {@code callSuper*} methods carried this switch verbatim, so a
     * fix to one of them left the other three behind. The completing step allocates a lambda, which
     * the previous shape only did on the asynchronous branch; that is confined to delegating
     * properties, which are not the common super-call.</p>
     *
     * @param frame      the current frame
     * @param hThis      the target of the super-invocation
     * @param bodySuper  the delegating method body
     * @param step       how to finish once the delegate target has been read
     *
     * @return one of {@code Op.R_NEXT}, {@code Op.R_CALL} or {@code Op.R_EXCEPTION}
     */
    private int callDelegating(Frame frame, ObjectHandle hThis, MethodBody bodySuper,
                               DelegateStep step) {
        SignatureConstant sig    = bodySuper.getSignature();
        PropertyConstant  idProp = bodySuper.getPropertyConstant();

        return switch (hThis.getTemplate().getPropertyValue(frame, hThis, idProp, Op.A_STACK)) {
            case Op.R_NEXT -> step.complete(frame, frame.popStack(), sig);

            case Op.R_CALL -> {
                frame.m_frameNext.addContinuation(frameCaller ->
                        step.complete(frameCaller, frameCaller.popStack(), sig));
                yield Op.R_CALL;
            }

            case Op.R_EXCEPTION -> Op.R_EXCEPTION;

            default -> throw new IllegalStateException();
        };
    }

    /**
     * Super invocation with no arguments and a single return (A_IGNORE for void).
     */
    public int callSuper01(Frame frame, int iReturn) {
        int        nDepth    = frame.m_nChainDepth + 1;
        MethodBody bodySuper = bodyAt(nDepth);
        if (bodySuper == null) {
            return missingSuper(frame);
        }

        ObjectHandle hThis = frame.getThis();

        return switch (bodySuper.getImplementation()) {
            case Field -> hThis.getComposition().getFieldValue(frame,
                    hThis, bodySuper.getPropertyConstant(), iReturn);

            case Native -> {
                ClassTemplate   template  = hThis.getTemplate();
                MethodStructure method    = bodySuper.getMethodStructure();
                Component       container = method.getParent().getParent();

                yield container instanceof PropertyStructure
                        ? template.invokeNativeGet(frame, container.getName(), hThis, iReturn)
                        : template.invokeNativeN(frame, method, hThis, Utils.OBJECTS_NONE, iReturn);
            }

            case Default, Explicit -> {
                MethodStructure methodSuper = bodySuper.getMethodStructure();
                ObjectHandle[]  ahVar       = new ObjectHandle[methodSuper.getMaxVars()];
                yield frame.invoke1(this, nDepth, hThis, ahVar, iReturn);
            }

            case Delegating -> callDelegating(frame, hThis, bodySuper,
                    (frameDelegate, hTarget, sig) ->
                            completeDelegate(frameDelegate, hTarget, sig, iReturn));

            default -> throw new IllegalStateException();
        };
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
        int        nDepth    = frame.m_nChainDepth + 1;
        MethodBody bodySuper = bodyAt(nDepth);
        if (bodySuper == null) {
            return missingSuper(frame);
        }

        ObjectHandle hThis = frame.getThis();

        return switch (bodySuper.getImplementation()) {
            case Field -> hThis.getComposition().setFieldValue(frame,
                    hThis, bodySuper.getPropertyConstant(), hArg);

            case Native -> hThis.getTemplate().invokeNative1(frame,
                    bodySuper.getMethodStructure(), hThis, hArg, iReturn);

            case Default, Explicit -> {
                MethodStructure methodSuper = bodySuper.getMethodStructure();
                ObjectHandle[]  ahVar       =
                        new ObjectHandle[Math.max(methodSuper.getMaxVars(), 1)];
                ahVar[0] = hArg;
                yield frame.invoke1(this, nDepth, hThis, ahVar, iReturn);
            }

            case Delegating -> callDelegating(frame, hThis, bodySuper,
                    (frameDelegate, hTarget, sig) ->
                            completeDelegate(frameDelegate, hTarget, sig, hArg, iReturn));

            default -> throw new IllegalStateException();
        };
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
        int        nDepth    = frame.m_nChainDepth + 1;
        MethodBody bodySuper = bodyAt(nDepth);
        if (bodySuper == null) {
            return missingSuper(frame);
        }

        ObjectHandle    hThis       = frame.getThis();
        MethodStructure methodSuper = bodySuper.getMethodStructure();

        return switch (bodySuper.getImplementation()) {
            case Native -> {
                ClassTemplate template = hThis.getTemplate();
                yield fReturnTuple
                        ? template.invokeNativeT(frame, methodSuper, hThis, ahArg, iReturn)
                        : ahArg.length == 1
                                ? template.invokeNative1(frame, methodSuper, hThis, ahArg[0], iReturn)
                                : template.invokeNativeN(frame, methodSuper, hThis, ahArg, iReturn);
            }

            case Default, Explicit -> {
                ObjectHandle[] ahVar = Utils.ensureSize(ahArg, methodSuper.getMaxVars());
                yield fReturnTuple
                        ? frame.invokeT(this, nDepth, hThis, ahVar, iReturn)
                        : frame.invoke1(this, nDepth, hThis, ahVar, iReturn);
            }

            case Delegating -> callDelegating(frame, hThis, bodySuper,
                    (frameDelegate, hTarget, sig) -> completeDelegate(frameDelegate, hTarget, sig,
                            ahArg, iReturn, fReturnTuple));

            default -> throw new IllegalStateException();
        };
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
        int        nDepth    = frame.m_nChainDepth + 1;
        MethodBody bodySuper = bodyAt(nDepth);
        if (bodySuper == null) {
            return missingSuper(frame);
        }

        ObjectHandle    hThis       = frame.getThis();
        MethodStructure methodSuper = bodySuper.getMethodStructure();

        return switch (bodySuper.getImplementation()) {
            case Native ->
                    hThis.getTemplate().invokeNativeNN(frame, methodSuper, hThis, ahArg, aiReturn);

            case Default, Explicit -> frame.invokeN(this, nDepth, hThis,
                    Utils.ensureSize(ahArg, methodSuper.getMaxVars()), aiReturn);

            case Delegating -> callDelegating(frame, hThis, bodySuper,
                    (frameDelegate, hTarget, sig) ->
                            completeDelegate(frameDelegate, hTarget, sig, ahArg, aiReturn));

            default -> throw new IllegalStateException();
        };
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
        SignatureConstant sig = head().getSignature().removeAutoNarrowing();

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
        public FieldAccessChain(FrozenArray<MethodBody> aMethods) {
            super(aMethods);

            // Validate the constructor argument directly. Calling isField() here
            // would dispatch through this before construction has completed.
            assert CallChain.isFieldChain(aMethods);
        }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, int iReturn) {
            return hTarget.getTemplate().getFieldValue(frame, hTarget,
                head().getPropertyConstant(), iReturn);
        }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
            assert iReturn == Op.A_IGNORE;

            return hTarget.getTemplate().setFieldValue(frame, hTarget,
                head().getPropertyConstant(), hArg);
        }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn) {
            assert ahArg.length > 1 && iReturn == Op.A_IGNORE;

            return hTarget.getTemplate().setFieldValue(frame, hTarget,
                head().getPropertyConstant(), ahArg[0]);
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
            super(MethodBody.NO_BODIES_FROZEN);

            TypeComposition clzTarget  = hTarget.getComposition();
            TypeConstant    typeTarget = clzTarget.getType();

            f_idConstructor = idConstructor;
            f_clzTarget     = clzTarget;

            TypeInfo   infoTarget = typeTarget.ensureTypeInfo(ErrorListener.RUNTIME);
            MethodInfo infoCtor   = infoTarget.findVirtualConstructor(idConstructor.getSignature());
            if (infoCtor == null) {
                f_constructor = null;
                f_typeCtor    = null;
            } else {
                MethodStructure constructor = infoCtor.getTopmostMethodStructure(infoTarget);
                TypeConstant[]  atypeParam  = constructor.getIdentityConstant().getSignature().
                                                resolveGenericTypes(pool, typeTarget).getRawParams()
                                                .unsafeArray();
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
            super(MethodBody.NO_BODIES_FROZEN);

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
        return f_aMethods.isEmpty()
            ? "empty"
            : head().getIdentity().getSignature().getValueString() +
                (isNative()
                    ? "; native"
                    : "; depth=" + getDepth());
    }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * @return true iff the specified chain bodies represent a field
     */
    private static boolean isFieldChain(FrozenArray<MethodBody> aMethods) {
        return !aMethods.isEmpty() &&
               aMethods.get(0).getImplementation() == Implementation.Field;
    }

    /**
     * The chain of method bodies. Immutable: a CallChain is cached per composition and handed to
     * every caller that dispatches through it, so the bodies must not be writable through it.
     * Chains built from a TypeInfo REUSE that TypeInfo's frozen array rather than re-wrapping it.
     */
    protected final FrozenArray<MethodBody> f_aMethods;

    /**
     * Cached response for "isAtomic()" API.
     */
    private Boolean m_FAtomic;
}
