package org.xvm.runtime.template.numbers;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.Float8e5Constant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;

/**
 * Native Float8e5 ("E5M2") support.
 */
public class xFloat8e5
        extends BaseBinaryFP {
    public xFloat8e5(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, 8);
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        if (constant instanceof Float8e5Constant constFloat) {
            return frame.pushStack(makeHandle(constFloat.getValue()));
        }

        return super.createConstHandle(frame, constant);
    }

    @Override
    protected byte[] getBits(double d) {
        return xConstrainedInteger.toByteArray(
            Float8e5Constant.toBits((float) d) & 0xFFL, 1);
    }

    @Override
    protected double fromLong(long l) {
        return Float8e5Constant.toFloat((int) (l & 0xFF));
    }

    @Override
    protected FPParts splitParts(double d) {
        int n = Float8e5Constant.toBits((float) d);
        return new FPParts((n & 0x80) != 0, n & 0x03, (n & 0x7C) >>> 2);
    }

    @Override
    protected String toString(double d) {
        return String.valueOf((float) d);
    }
}
