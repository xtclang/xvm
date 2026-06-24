package org.xvm.runtime.template.numbers;


import java.math.BigDecimal;
import java.math.MathContext;

import java.util.Arrays;

import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;

import org.xvm.type.Decimal;
import org.xvm.type.Decimal128;


/**
 * Native Dec128 support.
 */
public class xDec128
        extends BaseDecFP {
    public static xDec128 INSTANCE;

    public xDec128(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, 128);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    // ----- helpers -------------------------------------------------------------------------------

    @Override
    protected Decimal128 fromDouble(double d) {
        try {
            if (Double.isFinite(d)) {
                return new Decimal128(new BigDecimal(d, MathContext.DECIMAL128));
            }
            return Double.isInfinite(d)
                ? d < 0
                    ? Decimal128.NEG_INFINITY
                    : Decimal128.POS_INFINITY
                : Decimal128.NaN;
        } catch (Decimal.RangeException e) {
            return (Decimal128) e.getDecimal();
        }
    }

    @Override
    protected ObjectHandle makeHandle(byte[] abValue, int cBytes) {
        assert cBytes >= 16;
        if (cBytes > 16) {
            abValue = Arrays.copyOfRange(abValue, 0, 16);
        }
        return makeHandle(new Decimal128(abValue));
    }
}