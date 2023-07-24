package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;


/**
 * Native checked UInt64 support.
 */
public class xCheckedUInt64
        extends xCheckedUnsignedInt
    {
    public static xCheckedUInt64 INSTANCE;

    public xCheckedUInt64(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, 0L, 0xFFFF_FFFF_FFFF_FFFFL, 64);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xCheckedInt64.INSTANCE;
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        if (l1 <= 0)
            {
            // the first factor is bigger or equal than 2^63, so the answer is either 0 or l1
            if (l2 == 0 || l1 == 0)
                {
                return frame.assignValue(iReturn, makeJavaLong(0));
                }
            if (l2 == 1)
                {
                return frame.assignValue(iReturn, hTarget);
                }
            return overflow(frame);
            }

        if (l2 <= 0)
            {
            // the first factor is bigger or equal than 2^63, so the answer is either 0 or l1
            if (l1 == 0 || l2 == 0)
                {
                return frame.assignValue(iReturn, makeJavaLong(0));
                }
            if (l1 == 1)
                {
                return frame.assignValue(iReturn, hArg);
                }
            return overflow(frame);
            }

        long lr = l1 * l2;
        if ((l1 | l2) >>> 31 != 0 && divUnsigned(lr, l2) != l1)
            {
            return overflow(frame);
            }
        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        if (l2 == 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(divUnsigned(l1, l2)));
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        if (l2 == 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(modUnsigned(l1, l2)));
        }
    }