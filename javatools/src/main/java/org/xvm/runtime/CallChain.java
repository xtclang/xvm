package org.xvm.runtime;


import org.xvm.asm.Component;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.SignatureConstant;

import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template._native.reflect.xRTFunction;


/**
 * Represents a chain of invocation.
 */
public class CallChain
    {
    /**
     * Construct a CallChain for an array of method bodies.
     */
    public CallChain(MethodBody[] aMethods)
        {
        f_aMethods = aMethods == null
                ? MethodBody.NO_BODIES
                : aMethods;
        }

    /**
     * Construct a CallChain for a lambda or a private method.
     */
    public CallChain(MethodStructure method)
        {
        f_aMethods = new MethodBody[] {new MethodBody(method)};
        }

    /**
     * @return the chain depth
     */
    public int getDepth()
        {
        return f_aMethods.length;
        }

    /**
     * @return true iff the chain is empty
     */
    public boolean isEmpty()
        {
        return f_aMethods.length == 0;
        }

    /**
     * @return true iff the top body of this chain is a delegating method for an atomic property
     */
    public boolean isAtomic()
        {
        if (m_FAtomic != null)
            {
            return m_FAtomic.booleanValue();
            }

        if (f_aMethods != null && f_aMethods[0].getImplementation() == Implementation.Delegating)
            {
            PropertyConstant  idDelegate   = f_aMethods[0].getPropertyConstant();
            PropertyStructure propDelegate = (PropertyStructure) idDelegate.getComponent();
            return m_FAtomic = propDelegate != null && propDelegate.isAtomic();
            }

        return m_FAtomic = Boolean.FALSE;
        }

    /**
     * @return the method at the specified depth
     */
    public MethodStructure getMethod(int nDepth)
        {
        return nDepth < f_aMethods.length
                ? f_aMethods[nDepth].getMethodStructure()
                : null;
        }

    /**
     * @return the top method
     */
    public MethodStructure getTop()
        {
        return f_aMethods.length == 0
                ? null
                : f_aMethods[0].getMethodStructure();
        }

    /**
     * @return the max var count for the top method
     */
    public int getMaxVars()
        {
        MethodStructure method = getTop();
        return method == null
                ? 0
                : method.getMaxVars();
        }

    /**
     * @return the super method for the specified frame (on this chain)
     */
    public MethodStructure getSuper(Frame frame)
        {
        return getMethod(frame.m_nChainDepth + 1);
        }

    /**
     * @return true iff the chain is native
     */
    public boolean isNative()
        {
        return f_aMethods.length == 0 ||
               f_aMethods[0].getImplementation() == Implementation.Native;
        }

    /**
     * @return true iff the chain represents a field
     */
    public boolean isField()
        {
        return f_aMethods.length > 0 &&
               f_aMethods[0].getImplementation() == Implementation.Field;
        }

    /**
     * @return true iff the chain represents an explicit implementation
     */
    public boolean isExplicit()
        {
        return f_aMethods.length > 0 &&
               f_aMethods[0].getImplementation() == Implementation.Explicit;
        }

    /**
     * @return the property this chain represents access to
     */
    public PropertyStructure getProperty()
        {
        return (PropertyStructure) f_aMethods[0].getIdentity().getNamespace().getComponent();
        }

    /**
     * Chain invocation with zero args and one return value.
     */
    public int invoke(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        if (isNative())
            {
            return hTarget.getTemplate().
                invokeNativeN(frame, getTop(), hTarget, Utils.OBJECTS_NONE, iReturn);
            }

        ObjectHandle[] ahVar = new ObjectHandle[getMaxVars()];

        return hTarget.getTemplate().invoke1(frame, this, hTarget, ahVar, iReturn);
        }

    /**
     * Chain invocation with one arg and one return value.
     */
    public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        if (isNative())
            {
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
    public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn)
        {
        if (isNative())
            {
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
    public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        if (isNative())
            {
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
    public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        if (isNative())
            {
            return hTarget.getTemplate().
                invokeNativeNN(frame, getTop(), hTarget, ahArg, aiReturn);
            }

        ObjectHandle[] ahVar = Utils.ensureSize(ahArg, getMaxVars());

        return hTarget.getTemplate().invokeN(frame, this, hTarget, ahVar, aiReturn);
        }

    /**
     * Chain invocation with a single arg and single return tuple value.
     */
    public int invokeT(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        if (isNative())
            {
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
    public int invokeT(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
        {
        if (isNative())
            {
            return hTarget.getTemplate().invokeNativeT(frame, getTop(), hTarget, ahArg, iReturn);
            }

        ObjectHandle[] ahVar = Utils.ensureSize(ahArg, getMaxVars());

        return hTarget.getTemplate().invokeT(frame, this, hTarget, ahVar, iReturn);
        }

    /**
     * Target binding.
     */
    public int bindTarget(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, hTarget.isService() ?
                xRTFunction.makeAsyncHandle(frame, this).bindTarget(frame, hTarget) :
                xRTFunction.makeHandle(frame, this, 0).bindTarget(frame, hTarget));
        }

    /**
     * Create a CallChain representing a property access.
     */
    public static CallChain createPropertyCallChain(MethodBody[] aMethods)
        {
        return aMethods.length == 1 && aMethods[0].getImplementation() == Implementation.Field
                ? new FieldAccessChain(aMethods)
                : new CallChain(aMethods);
        }

    /**
     * Super invocation with no arguments and a single return.
     */
    public int callSuper01(Frame frame, int iReturn)
        {
        int nDepth = frame.m_nChainDepth + 1;
        if (nDepth >= f_aMethods.length)
            {
            return missingSuper(frame);
            }

        ObjectHandle hThis     = frame.getThis();
        MethodBody   bodySuper = f_aMethods[nDepth];

        switch (bodySuper.getImplementation())
            {
            case Field:
                return hThis.getComposition().getFieldValue(frame,
                    hThis, bodySuper.getPropertyConstant(), iReturn);

            case Native:
                {
                ClassTemplate   template  = hThis.getTemplate();
                MethodStructure method    = bodySuper.getMethodStructure();
                Component       container = method.getParent().getParent();
                return container instanceof PropertyStructure
                    ? template.invokeNativeGet(frame, container.getName(), hThis, iReturn)
                    : template.invokeNativeN(frame, method, hThis, Utils.OBJECTS_NONE, iReturn);
                }
            case Default, Explicit, Delegating:
                {
                MethodStructure methodSuper = bodySuper.getMethodStructure();
                ObjectHandle[] ahVar = new ObjectHandle[methodSuper.getMaxVars()];

                return frame.invoke1(this, nDepth, hThis, ahVar, iReturn);
                }

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Super invocation with a single arguments and no returns.
     */
    public int callSuper10(Frame frame, ObjectHandle hArg)
        {
        int nDepth = frame.m_nChainDepth + 1;
        if (nDepth >= f_aMethods.length)
            {
            return missingSuper(frame);
            }

        ObjectHandle hThis     = frame.getThis();
        MethodBody   bodySuper = f_aMethods[nDepth];

        switch (bodySuper.getImplementation())
            {
            case Field:
                return hThis.getComposition().setFieldValue(frame,
                    hThis, bodySuper.getPropertyConstant(), hArg);

            case Native:
                return hThis.getTemplate().invokeNative1(frame, bodySuper.getMethodStructure(),
                    hThis, hArg, Op.A_IGNORE);

            case Default, Explicit, Delegating:
                {
                MethodStructure methodSuper = bodySuper.getMethodStructure();
                ObjectHandle[]  ahVar       = new ObjectHandle[Math.max(methodSuper.getMaxVars(), 1)];
                ahVar[0] = hArg;

                return frame.invoke1(this, nDepth, hThis, ahVar, Op.A_IGNORE);
                }

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Super invocation with multiple arguments and a single return.
     */
    public int callSuperN1(Frame frame, ObjectHandle[] ahArg, int iReturn, boolean fReturnTuple)
        {
        int nDepth = frame.m_nChainDepth + 1;
        if (nDepth >= f_aMethods.length)
            {
            return missingSuper(frame);
            }

        ObjectHandle    hThis       = frame.getThis();
        MethodBody      bodySuper   = f_aMethods[nDepth];
        MethodStructure methodSuper = bodySuper.getMethodStructure();

        switch (bodySuper.getImplementation())
            {
            case Native ->
                {
                ClassTemplate template = hThis.getTemplate();
                return fReturnTuple
                    ? template.invokeNativeT(frame, methodSuper, hThis, ahArg, iReturn)
                    : ahArg.length == 1
                        ? template.invokeNative1(frame, methodSuper, hThis, ahArg[0], iReturn)
                        : template.invokeNativeN(frame, methodSuper, hThis, ahArg, iReturn);
                }

            case Default, Explicit, Delegating ->
                {
                ObjectHandle[] ahVar = Utils.ensureSize(ahArg, methodSuper.getMaxVars());
                return fReturnTuple
                        ? frame.invokeT(this, nDepth, hThis, ahVar, iReturn)
                        : frame.invoke1(this, nDepth, hThis, ahVar, iReturn);
                }

            default -> throw new IllegalStateException();
            }
        }

    /**
     * Super invocation with multiple arguments and multiple returns.
     */
    public int callSuperNN(Frame frame, ObjectHandle[] ahArg, int[] aiReturn)
        {
        int nDepth = frame.m_nChainDepth + 1;
        if (nDepth >= f_aMethods.length)
            {
            return missingSuper(frame);
            }

        ObjectHandle    hThis       = frame.getThis();
        MethodBody      bodySuper   = f_aMethods[nDepth];
        MethodStructure methodSuper = bodySuper.getMethodStructure();

        return switch (bodySuper.getImplementation())
            {
            case Native ->
                hThis.getTemplate().invokeNativeNN(frame, methodSuper, hThis, ahArg, aiReturn);

            case Default, Explicit, Delegating ->
                frame.invokeN(this, nDepth, hThis,
                        Utils.ensureSize(ahArg, methodSuper.getMaxVars()), aiReturn);

            default -> throw new IllegalStateException();
            };
        }

    /**
     * Raise a "missing super" exception.
     */
    private int missingSuper(Frame frame)
        {
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
            extends CallChain
        {
        public FieldAccessChain(MethodBody[] aMethods)
            {
            super(aMethods);

            assert isField();
            }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, int iReturn)
            {
            return hTarget.getTemplate().getFieldValue(frame, hTarget,
                f_aMethods[0].getPropertyConstant(), iReturn);
            }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
            {
            assert iReturn == Op.A_IGNORE;

            return hTarget.getTemplate().setFieldValue(frame, hTarget,
                f_aMethods[0].getPropertyConstant(), hArg);
            }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
            {
            assert ahArg.length > 1 && iReturn == Op.A_IGNORE;

            return hTarget.getTemplate().setFieldValue(frame, hTarget,
                f_aMethods[0].getPropertyConstant(), ahArg[0]);
            }
        }

    /**
     * A CallChain representing an exception.
     */
    public static class ExceptionChain
            extends CallChain
        {
        public ExceptionChain(ExceptionHandle hException)
            {
            super(MethodBody.NO_BODIES);

            f_hException = hException;
            }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, int iReturn)
            {
            return throwException(frame);
            }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
            {
            return throwException(frame);
            }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn)
            {
            return throwException(frame);
            }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
            {
            return throwException(frame);
            }

        @Override
        public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
            {
            return throwException(frame);
            }

        @Override
        public int invokeT(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
            {
            return throwException(frame);
            }

        @Override
        public int invokeT(Frame frame, ObjectHandle hTarget, ObjectHandle[] ahArg, int iReturn)
            {
            return throwException(frame);
            }

        @Override
        public int bindTarget(Frame frame, ObjectHandle hTarget, int iReturn)
            {
            return throwException(frame);
            }

        private int throwException(Frame frame)
            {
            return frame.raiseException(f_hException);
            }

        private final ExceptionHandle f_hException;
        }


    // ----- debugging support ---------------------------------------------------------------------

    @Override
    public String toString()
        {
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