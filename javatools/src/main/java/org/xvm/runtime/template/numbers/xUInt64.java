package org.xvm.runtime.template.numbers;


import java.math.BigInteger;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.IntConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.util.PackedInteger;


/**
 * Native UInt64 support.
 */
public class xUInt64
        extends xUnsignedConstrainedInt
    {
    public static xUInt64 INSTANCE;

    public xUInt64(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, 0, -1, 64, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xInt64.INSTANCE;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof IntConstant constInt)
            {
            PackedInteger piValue = constInt.getValue();
            long          lValue;
            if (piValue.isBig())
                {
                // this must be a value outside the long range
                lValue = piValue.getBigInteger().longValue();
                }
            else
                {
                lValue = piValue.getLong();
                }
            return frame.pushStack(makeJavaLong(lValue));
            }

        return super.createConstHandle(frame, constant);
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

    @Override
    public int convertLong(Frame frame, PackedInteger piValue, boolean fChecked, int iReturn)
        {
        if (piValue.isBig())
            {
            // there is a range: 0x7FFF_FFFF_FFFF_FFFF .. 0xFFFF_FFFF_FFFF_FFFF
            // that fits "long", but represented by the PackedInteger as "big"
            BigInteger bi = piValue.getBigInteger();
            if (bi.signum() > 0 && bi.bitLength() <= 64 || !fChecked)
                {
                return frame.assignValue(iReturn, makeJavaLong(bi.longValue()));
                }
            else
                {
                return overflow(frame);
                }
            }
        else
            {
            return super.convertLong(frame, piValue, fChecked, iReturn);
            }
        }
    }