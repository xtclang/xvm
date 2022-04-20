package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.Float64Constant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;


/**
 * Native Float64 support.
 */
public class xFloat64
        extends BaseBinaryFP
    {
    public static xFloat64 INSTANCE;

    public xFloat64(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, 64);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof Float64Constant constFloat)
            {
            return frame.pushStack(makeHandle(constFloat.getValue()));
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    protected byte[] getBits(double d)
        {
        return xConstrainedInteger.toByteArray(Double.doubleToRawLongBits(d), 8);
        }

    @Override
    protected double fromLong(long l)
        {
        return Double.longBitsToDouble(l);
        }

    @Override
    protected String toString(double d)
        {
        return String.valueOf(d);
        }
    }