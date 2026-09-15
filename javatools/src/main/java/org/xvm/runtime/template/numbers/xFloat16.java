package org.xvm.runtime.template.numbers;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.Float16Constant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;

/**
 * Native Float16 support.
 */
public class xFloat16
        extends BaseBinaryFP {
    public static xFloat16 INSTANCE;

    public xFloat16(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, 16);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        if (constant instanceof Float16Constant constFloat) {
            return frame.pushStack(makeHandle(constFloat.getValue()));
        }

        return super.createConstHandle(frame, constant);
    }

    @Override
    protected byte[] getBits(double d) {
        return xConstrainedInteger.toByteArray(
            Float16Constant.toHalf((float) d) & 0xFFFFL, 2);
    }

    @Override
    protected double fromLong(long l) {
        return Float16Constant.toFloat((int) (l & 0xFFFF));
    }

    @Override
    protected FPParts splitParts(double d) {
        short n = Float.floatToFloat16((float) d);
        return new FPParts((n & 0x8000) != 0, n & 0x03FF, (n & 0x7C00) >>> 10);
    }

    @Override
    protected String toString(double d) {
        return String.valueOf((float) d);
    }
}
