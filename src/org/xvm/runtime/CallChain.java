package org.xvm.runtime;


import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;
import org.xvm.asm.PropertyStructure;

import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodBody.Implementation;
import org.xvm.asm.constants.MethodConstant;


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
        if (aMethods == null)
            {
            throw new IllegalArgumentException("methods are missing");
            }

        f_aMethods = aMethods;
        }

    // Construct a CallChain for a lambda
    public CallChain(MethodConstant idLambda)
        {
        assert idLambda.isLambda();
        f_aMethods = new MethodBody[] {new MethodBody(idLambda)};
        }

    public int getDepth()
        {
        return f_aMethods.length;
        }

    public MethodStructure getMethod(int nDepth)
        {
        return f_aMethods[nDepth].getMethodStructure();
        }

    public MethodStructure getTop()
        {
        return f_aMethods[0].getMethodStructure();
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

    // natural chain invocation with zero args and one return value
    public int invoke(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        assert !isNative();

        ObjectHandle[] ahVar = new ObjectHandle[getTop().getMaxVars()];

        return hTarget.getTemplate().invoke1(frame, this, hTarget, ahVar, iReturn);
        }

    // natural chain invocation with one arg and one return value
    public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        assert !isNative();

        ObjectHandle[] ahVar = new ObjectHandle[getTop().getMaxVars()];
        ahVar[0] = hArg;

        return hTarget.getTemplate().invoke1(frame, this, hTarget, ahVar, iReturn);
        }

    // natural chain invocation with one arg and multiple return values
    public int invoke(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn)
        {
        assert !isNative();

        ObjectHandle[] ahVar = new ObjectHandle[getTop().getMaxVars()];
        ahVar[0] = hArg;

        return hTarget.getTemplate().invokeN(frame, this, hTarget, ahVar, aiReturn);
        }

    public int callSuper01(Frame frame, int iReturn)
        {
        ObjectHandle hThis = frame.getThis();
        int nDepth = frame.m_nDepth + 1;

        MethodBody bodySuper = f_aMethods[nDepth];
        switch (bodySuper.getImplementation())
            {
            case Field:
                return getField(frame, hThis, iReturn);

            case Native:
                return hThis.getTemplate().invokeNativeN(frame, bodySuper.getMethodStructure(),
                    hThis, Utils.OBJECTS_NONE, iReturn);

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

    protected int getField(Frame frame, ObjectHandle hThis, int iReturn)
        {
        return hThis.getTemplate().getFieldValue(frame, hThis, getProperty().getName(), iReturn);
        }

    public int callSuper10(Frame frame, ObjectHandle hArg)
        {
        ObjectHandle hThis = frame.getThis();
        int nDepth = frame.m_nDepth + 1;

        MethodBody bodySuper = f_aMethods[nDepth];
        switch (bodySuper.getImplementation())
            {
            case Field:
                return setField(frame, hThis, hArg);

            case Native:
                return hThis.getTemplate().invokeNative1(frame, bodySuper.getMethodStructure(),
                    hThis, hArg, Op.A_IGNORE);

            case Explicit:
                {
                MethodStructure methodSuper = bodySuper.getMethodStructure();
                ObjectHandle[] ahVar = new ObjectHandle[methodSuper.getMaxVars()];
                ahVar[1] = hArg;

                return frame.invoke1(this, nDepth, hThis, ahVar, Op.A_IGNORE);
                }

            default:
                throw new IllegalStateException();
            }
        }

    protected int setField(Frame frame, ObjectHandle hThis, ObjectHandle hArg)
        {
        return hThis.getTemplate().setFieldValue(frame, hThis, getProperty().getName(), hArg);
        }

    public int callSuperN1(Frame frame, ObjectHandle[] ahArg, int iReturn,
                           boolean fReturnTuple)
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

            case Explicit:
                {
                ObjectHandle[] ahVar = Utils.ensureSize(ahArg, methodSuper.getMaxVars());

                return fReturnTuple
                    ? frame.invoke1(this, nDepth, hThis, ahVar, iReturn)
                    : frame.invokeT(this, nDepth, hThis, ahVar, iReturn);
                }

            default:
                throw new IllegalStateException();
            }
        }

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

            case Explicit:
                return frame.invokeN(this, nDepth, hThis,
                    Utils.ensureSize(ahArg, methodSuper.getMaxVars()), aiReturn);

            default:
                throw new IllegalStateException();
            }
        }
    }
