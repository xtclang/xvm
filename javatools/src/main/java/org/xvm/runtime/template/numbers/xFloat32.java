package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.Float32Constant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;


/**
 * Native Float32 support.
 */
public class xFloat32
        extends BaseBinaryFP
    {
    public static xFloat32 INSTANCE;

    public xFloat32(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, 32);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof Float32Constant constFloat)
            {
            return frame.pushStack(makeHandle(constFloat.getValue()));
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    protected byte[] getBits(double d)
        {
        return xConstrainedInteger.toByteArray(
            Float.floatToRawIntBits((float) d) & 0xFFFFFFFFL, 4);
        }

    @Override
    protected double fromLong(long l)
        {
        return Float.intBitsToFloat((int) (l & 0xFFFFFFFFL));
        }

    @Override
    protected String toString(double d)
        {
        return String.valueOf((float) d);
        }
    }