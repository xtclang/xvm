package org.xvm.runtime.template.numbers;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.Float8e4Constant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;

/**
 * Native Float8e4 ("E4M3") support.
 */
public class xFloat8e4
        extends BaseBinaryFP {
    public xFloat8e4(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, 8);
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        if (constant instanceof Float8e4Constant constFloat) {
            return frame.pushStack(makeHandle(constFloat.getValue()));
        }

        return super.createConstHandle(frame, constant);
    }

    @Override
    protected byte[] getBits(double d) {
        return xConstrainedInteger.toByteArray(
            Float8e4Constant.toBits((float) d) & 0xFFL, 1);
    }

    @Override
    protected double fromLong(long l) {
        return Float8e4Constant.toFloat((int) (l & 0xFF));
    }

    @Override
    public FloatHandle makeHandle(double dValue) {
        // a handle carries a Java double regardless of its Ecstasy type, and BaseBinaryFP computes
        // in double, so a result would otherwise keep precision and range this format does not
        // have. Every value of this type has to be representable in it, so round the result back
        // through the 8-bit encoding here, at the single point every result passes through.
        //
        // Going via float first does not double-round: float32 carries far more than the 2p+2 bits
        // that a single correctly-rounded step needs for a 4-bit significand.
        return super.makeHandle(Float8e4Constant.toFloat(Float8e4Constant.toBits((float) dValue)));
    }

    @Override
    protected FPParts splitParts(double d) {
        int n = Float8e4Constant.toBits((float) d);
        return new FPParts((n & 0x80) != 0, n & 0x07, (n & 0x78) >>> 3);
    }

    @Override
    protected String toString(double d) {
        return String.valueOf((float) d);
    }
}
