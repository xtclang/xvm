package org.xvm.runtime.template.numbers;


import java.math.BigDecimal;
import java.math.MathContext;

import java.util.Arrays;

import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.ObjectHandle;

import org.xvm.type.Decimal;
import org.xvm.type.Decimal64;


/**
 * Native Dec64 support.
 */
public class xDec64
        extends BaseDecFP
    {
    public static xDec64 INSTANCE;

    public xDec64(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, 64);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    // ----- helpers -------------------------------------------------------------------------------

    @Override
    protected Decimal64 fromDouble(double d)
        {
        try
            {
            if (Double.isFinite(d))
                {
                return new Decimal64(new BigDecimal(d, MathContext.DECIMAL64));
                }
            return Double.isInfinite(d)
                ? d < 0
                    ? Decimal64.NEG_INFINITY
                    : Decimal64.POS_INFINITY
                : Decimal64.NaN;
            }
        catch (Decimal.RangeException e)
            {
            return (Decimal64) e.getDecimal();
            }
        }

    @Override
    protected ObjectHandle makeHandle(byte[] abValue, int cBytes)
        {
        assert cBytes >= 8;
        if (cBytes > 8)
            {
            abValue = Arrays.copyOfRange(abValue, 0, 8);
            }
        return makeHandle(new Decimal64(abValue));
        }
    }