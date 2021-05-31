package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.Float32Constant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.TemplateRegistry;


/**
 * Native Float32 support.
 */
public class xFloat32
        extends BaseBinaryFP
    {
    public static xFloat32 INSTANCE;

    public xFloat32(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, 32);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof Float32Constant)
            {
            double dValue = ((Float32Constant) constant).getValue();
            return frame.pushStack(makeHandle(dValue));
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
