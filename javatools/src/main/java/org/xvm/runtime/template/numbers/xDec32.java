package org.xvm.runtime.template.numbers;


import java.math.BigDecimal;
import java.math.MathContext;

import java.util.Arrays;

import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;

import org.xvm.type.Decimal;
import org.xvm.type.Decimal32;


/**
 * Native Dec32 support.
 */
public class xDec32
        extends BaseDecFP
    {
    public static xDec32 INSTANCE;

    public xDec32(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, 32);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    // ----- helpers -------------------------------------------------------------------------------

    @Override
    protected Decimal32 fromDouble(double d)
        {
        try
            {
            if (Double.isFinite(d))
                {
                return new Decimal32(new BigDecimal(d, MathContext.DECIMAL32));
                }
            return Double.isInfinite(d)
                ? d < 0
                    ? Decimal32.NEG_INFINITY
                    : Decimal32.POS_INFINITY
                : Decimal32.NaN;
            }
        catch (Decimal.RangeException e)
            {
            return (Decimal32) e.getDecimal();
            }
        }

    @Override
    protected ObjectHandle makeHandle(byte[] abValue, int cBytes)
        {
        assert cBytes >= 4;
        if (cBytes > 4)
            {
            abValue = Arrays.copyOfRange(abValue, 0, 4);
            }
        return makeHandle(new Decimal32(abValue));
        }
    }