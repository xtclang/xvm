package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.numbers.BaseInt128.LongLongHandle;


/**
 * Native UInt support.
 */
public class xUInt
        extends xIntBase
    {
    public static xUInt INSTANCE;

    public xUInt(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, /*signed*/ false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xIntBase getComplimentaryTemplate()
        {
        return xInt.INSTANCE;
        }

    @Override
    protected int invokeAbs(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.assignValue(iReturn, hTarget);
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        return frame.raiseException(xException.unsupportedOperation(frame));
        }

    @Override
    protected boolean checkAddOverflow(long l1, long l2, long lr)
        {
        return ((l1 & l2) | ((l1 | l2) & ~lr)) < 0;
        }

    @Override
    protected boolean checkSubOverflow(long l1, long l2, long lr)
        {
        return ((~l1 & l2) | ((~l1 | l2) & lr)) < 0;
        }

    @Override
    protected boolean checkMulOverflow(long l1, long l2, long lr)
        {
        // if one factor is bigger or equal than 2^63, the other must be either 0 or 1
        return l1 <= 0 && (l2 & ~1L) != 0
            || l2 <= 0 && (l1 & ~1L) != 0;
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        if (hTarget instanceof JavaLong h1)
            {
            long l = h1.getValue();
            return l == 0
                    ? overflow(frame)
                    : frame.assignValue(iReturn, makeLong(l-1));
            }
        else
            {
            LongLong ll = ((LongLongHandle) hTarget).getValue();
            LongLong lr = ll.prev(false);
            return lr == LongLong.OVERFLOW
                    ? overflow(frame)
                    : frame.assignValue(iReturn, makeHandle(lr));
            }
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        if (hTarget instanceof JavaLong h1)
            {
            long l = h1.getValue();
            return l == -1
                    ? frame.assignValue(iReturn, makeLongLong(MAX64_VALUE_NEXT))
                    : frame.assignValue(iReturn, makeLong(l-1));
            }
        else
            {
            LongLong ll = ((LongLongHandle) hTarget).getValue();
            LongLong lr = ll.next(true); // use "signed" next since UInt is limited to 127 bits
            return lr == LongLong.OVERFLOW
                    ? overflow(frame)
                    : frame.assignValue(iReturn, makeHandle(lr));
            }
        }

    public static final LongLong MAX64_VALUE_NEXT = new LongLong(0, 1);
    }