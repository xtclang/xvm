package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;


/**
 * Abstract super class for all unchecked constrained integers (Int8, UInt16, Int32, ...)
 */
public abstract class xCheckedConstrainedInt
        extends xConstrainedInteger
    {
    public xCheckedConstrainedInt(Container container, ClassStructure structure,
                                  long cMinValue, long cMaxValue, int cNumBits, boolean fUnsigned)
        {
        super(container, structure, cMinValue, cMaxValue, cNumBits, fUnsigned, true);
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 + l2;

        if ((((l1 ^ lr) & (l2 ^ lr)) << f_cAddCheckShift) < 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 - l2;

        if ((((l1 ^ l2) & (l1 ^ lr)) << f_cAddCheckShift) < 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 * l2;

        long a1 = Math.abs(l1);
        long a2 = Math.abs(l2);
        if ((a1 | a2) >>> f_cMulCheckShift != 0)
            {
            // see Math.multiplyExact()
            if (((l2 != 0) && (lr / l2 != l1)) ||
                    (l1 == f_cMinValue && l2 == -1) ||
                    lr > f_cMaxValue || lr < f_cMinValue)
                {
                return overflow(frame);
                }
            }

        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (!f_fSigned || l == f_cMinValue)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(-l));
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (l == f_cMinValue)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(l - 1));
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (l == f_cMaxValue)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(l + 1));
        }

    @Override
    public int convertLong(Frame frame, long lValue, int iReturn, boolean fCheck)
        {
        return frame.assignValue(iReturn, makeJavaLong(lValue));
        }
    }