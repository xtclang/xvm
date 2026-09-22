package org.xvm.runtime.template.numbers;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.BFloat16Constant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;

/**
 * Native BFloat16 support.
 */
public class xBFloat16
        extends BaseBinaryFP {
    public static xBFloat16 INSTANCE;

    public xBFloat16(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, 16);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        if (constant instanceof BFloat16Constant constFloat) {
            return frame.pushStack(makeHandle(constFloat.getValue()));
        }

        return super.createConstHandle(frame, constant);
    }

    @Override
    protected byte[] getBits(double d) {
        return xConstrainedInteger.toByteArray(
            BFloat16Constant.toHalf((float) d) & 0xFFFFL, 2);
    }

    @Override
    protected double fromLong(long l) {
        return BFloat16Constant.toFloat((int) (l & 0xFFFF));
    }

    @Override
    public FloatHandle makeHandle(double dValue) {
        // a handle carries a Java double regardless of its Ecstasy type, and BaseBinaryFP computes
        // in double, so a result would otherwise keep precision and range BFloat16 does not have.
        // Round it back into the format here, at the single point every result passes through.
        return super.makeHandle(BFloat16Constant.toFloat(BFloat16Constant.toHalf((float) dValue)));
    }

    @Override
    protected FPParts splitParts(double d) {
        int n = BFloat16Constant.toHalf((float) d);
        return new FPParts((n & 0x8000) != 0, n & 0x007F, (n & 0x7F80) >>> 7);
    }

    @Override
    protected String toString(double d) {
        return String.valueOf((float) d);
    }
}
