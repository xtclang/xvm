package org.xvm.runtime;


import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.template._native.reflect.xRTFunction;


/**
 * Represents a chain of invocation.
 */
public class CallChain
    {
    // an array of method bodies
    private final MethodBody[] f_aMethods;

    // Construct the CallChain
    public CallChain(MethodBody[] aMethods)
        {
        f_aMethods = aMethods == null
                ? MethodBody.NO_BODIES
                : aMethods;
        }

    // Construct a CallChain for a lambda or a private method
    public CallChain(MethodStructure method)
        {
        f_aMethods = new MethodBody[] {new MethodBody(method)};
        }

    public int getDepth()
        {
        return f_aMethods.length;
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

    public MethodStructure getMethod(int nDepth)
        {
        return nDepth < f_aMethods.length
                ? f_aMethods[nDepth].getMethodStructure()
                : null;
        }

    public MethodStructure getTop()
        {
        return f_aMethods.length == 0
                ? null
                : f_aMethods[0].getMethodStructure();
        }

    public int getMaxVars()
        {
        return f_aMethods.length == 0
                ? 0
                : f_aMethods[0].getMethodStructure().getMaxVars();
        }

    public MethodStructure getSuper(Frame frame)
        {
        return getMethod(frame.m_nDepth + 1);
        }

    public boolean isNative()
        {
        return f_aMethods.length == 0 ||
               f_aMethods[0].getImplementation() == Implementation.Native;
        }

    public boolean isField()
        {
        return f_aMethods.length > 0 &&
               f_aMethods[0].getImplementation() == Implementation.Field;
        }

    public boolean isExplicit()
        {
        return f_aMethods.length > 0 &&
               f_aMethods[0].getImplementation() == Implementation.Explicit;
        }

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

        ObjectHandle[] ahVar = new ObjectHandle[getTop().getMaxVars()];

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

        ObjectHandle[] ahVar = new ObjectHandle[getTop().getMaxVars()];
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

        ObjectHandle[] ahVar = new ObjectHandle[getTop().getMaxVars()];
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

        ObjectHandle[] ahVar = Utils.ensureSize(ahArg, getTop().getMaxVars());

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

        ObjectHandle[] ahVar = Utils.ensureSize(ahArg, getTop().getMaxVars());

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

        ObjectHandle[] ahVar = new ObjectHandle[getTop().getMaxVars()];
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

        ObjectHandle[] ahVar = Utils.ensureSize(ahArg, getTop().getMaxVars());

        return hTarget.getTemplate().invokeT(frame, this, hTarget, ahVar, iReturn);
        }

    /**
     * Target binding.
     */
    public int bindTarget(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, hTarget.isService() ?
                xRTFunction.makeAsyncHandle(this).bindTarget(hTarget) :
                xRTFunction.makeHandle(this, 0).bindTarget(hTarget));
        }

    /**
     * Super invocation with no arguments and a single return.
     */
    public int callSuper01(Frame frame, int iReturn)
        {
        ObjectHandle hThis = frame.getThis();
        int nDepth = frame.m_nDepth + 1;

        MethodBody bodySuper = f_aMethods[nDepth];
        switch (bodySuper.getImplementation())
            {
            case Field:
                return hThis.getComposition().getFieldValue(frame,
                    hThis, bodySuper.getPropertyConstant(), iReturn);

            case Native:
                return hThis.getTemplate().invokeNativeN(frame, bodySuper.getMethodStructure(),
                    hThis, Utils.OBJECTS_NONE, iReturn);

            case Default:
            case Explicit:
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
        ObjectHandle hThis = frame.getThis();
        int nDepth = frame.m_nDepth + 1;

        MethodBody bodySuper = f_aMethods[nDepth];
        switch (bodySuper.getImplementation())
            {
            case Field:
                return hThis.getComposition().setFieldValue(frame,
                    hThis, bodySuper.getPropertyConstant(), hArg);

            case Native:
                return hThis.getTemplate().invokeNative1(frame, bodySuper.getMethodStructure(),
                    hThis, hArg, Op.A_IGNORE);

            case Default:
            case Explicit:
                {
                MethodStructure methodSuper = bodySuper.getMethodStructure();
                ObjectHandle[] ahVar = new ObjectHandle[methodSuper.getMaxVars()];
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
        ObjectHandle hThis = frame.getThis();
        int nDepth = frame.m_nDepth + 1;

        MethodBody bodySuper = f_aMethods[nDepth];
        MethodStructure methodSuper = bodySuper.getMethodStructure();

        switch (bodySuper.getImplementation())
            {
            case Native:
                return fReturnTuple
                    ? hThis.getTemplate().invokeNativeN(frame, methodSuper, hThis, ahArg, iReturn)
                    : hThis.getTemplate().invokeNativeT(frame, methodSuper, hThis, ahArg, iReturn);

            case Default:
            case Explicit:
                {
                ObjectHandle[] ahVar = Utils.ensureSize(ahArg, methodSuper.getMaxVars());

                return fReturnTuple
                    ? frame.invokeT(this, nDepth, hThis, ahVar, iReturn)
                    : frame.invoke1(this, nDepth, hThis, ahVar, iReturn);
                }

            default:
                throw new IllegalStateException();
            }
        }

    /**
     * Super invocation with multiple arguments and multiple returns.
     */
    public int callSuperNN(Frame frame, ObjectHandle[] ahArg, int[] aiReturn)
        {
        ObjectHandle hThis = frame.getThis();
        int nDepth = frame.m_nDepth + 1;

        MethodBody bodySuper = f_aMethods[nDepth];
        MethodStructure methodSuper = bodySuper.getMethodStructure();

        switch (bodySuper.getImplementation())
            {
            case Native:
                return hThis.getTemplate().invokeNativeNN(frame, methodSuper, hThis, ahArg, aiReturn);

            case Default:
            case Explicit:
                return frame.invokeN(this, nDepth, hThis,
                    Utils.ensureSize(ahArg, methodSuper.getMaxVars()), aiReturn);

            default:
                throw new IllegalStateException();
            }
        }

    public static class ExceptionChain
            extends CallChain
        {
        public ExceptionChain(MethodConstant idMethod, TypeConstant typeTarget)
            {
            super(MethodBody.NO_BODIES);

            f_idMethod   = idMethod;
            f_typeTarget = typeTarget;
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
            return frame.raiseException("Missing method \"" + f_idMethod.getValueString() +
                        "\" on " + f_typeTarget.getValueString());
            }

        private final MethodConstant f_idMethod;
        private final TypeConstant   f_typeTarget;
        }


    // ----- debugging support ---------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "depth=" + getDepth();
        }


    // ----- data fields ---------------------------------------------------------------------------

    /**
     * Cached response for "isAtomic()" API.
     */
    private Boolean m_FAtomic;
    }
