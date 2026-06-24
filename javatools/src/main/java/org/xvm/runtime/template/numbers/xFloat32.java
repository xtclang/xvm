package org.xvm.runtime.template.numbers;


import java.math.BigDecimal;
import java.math.MathContext;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;

import org.xvm.asm.constants.Float32Constant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;


/**
 * Native Float32 support.
 */
public class xFloat32
        extends BaseBinaryFP {
    public static xFloat32 INSTANCE;

    public xFloat32(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, 32);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        if (constant instanceof Float32Constant constFloat) {
            return frame.pushStack(makeHandle(constFloat.getValue()));
        }

        return super.createConstHandle(frame, constant);
    }

    @Override
    protected byte[] getBits(double d) {
        return xConstrainedInteger.toByteArray(
            Float.floatToRawIntBits((float) d) & 0xFFFFFFFFL, 4);
    }

    @Override
    protected double fromLong(long l) {
        return Float.intBitsToFloat((int) (l & 0xFFFFFFFFL));
    }

    @Override
    public FloatHandle makeHandle(double dValue) {
        // handles store doubles; round here to preserve Float32 identity
        return super.makeHandle((float) dValue);
    }

    @Override
    protected double toFloat64(double d) {
        if (!Double.isFinite(d)) {
            return d;
        }

        // match the JIT bridge's Float32.$toBigDecimal(); use the shortest float32 decimal image
        // so native conversions see the same rounded text value
        return toBigDecimal(d).doubleValue();
    }

    @Override
    protected BigDecimal toBigDecimal(double d) {
        return new BigDecimal(Float.toString((float) d), MathContext.DECIMAL32);
    }

    @Override
    protected String toString(double d) {
        return String.valueOf((float) d);
    }
}