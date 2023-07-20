package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;


/**
 * Abstract super class for all unchecked constrained integers (Int8, UInt16, Int32, ...)
 */
public abstract class xUncheckedUnsignedInt
        extends xUnsignedConstrainedInt
    {
    public xUncheckedUnsignedInt(Container container, ClassStructure structure,
                                 long cMinValue, long cMaxValue, int cNumBits)
        {
        super(container, structure, cMinValue, cMaxValue, cNumBits, false);
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 + l2;

        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 - l2;

        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 * l2;

        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        return frame.assignValue(iReturn, makeJavaLong(-l));
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        return frame.assignValue(iReturn, makeJavaLong(l - 1));
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        return frame.assignValue(iReturn, makeJavaLong(l + 1));
        }

    @Override
    public int convertLong(Frame frame, long lValue, int iReturn, boolean fCheck)
        {
        return frame.assignValue(iReturn, makeJavaLong(lValue));
        }

    @Override
    public JavaLong makeJavaLong(long lValue)
        {
        if (f_cNumBits < 64)
            {
            lValue &= f_lValueMask;
            }
        return super.makeJavaLong(lValue);
        }
    }