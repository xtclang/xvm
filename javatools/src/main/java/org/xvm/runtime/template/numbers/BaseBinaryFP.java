package org.xvm.runtime.template.numbers;


import java.math.BigDecimal;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xBitArray;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.text.xString;


/**
 * Base class for native BinaryFPNumber (Float16, 32, 64) support.
 */
abstract public class BaseBinaryFP
        extends BaseFP
    {
    public BaseBinaryFP(TemplateRegistry templates, ClassStructure structure, int cBits)
        {
        super(templates, structure, cBits);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "infinity":
                {
                double d = ((FloatHandle) hTarget).getValue();

                return frame.assignValue(iReturn, xBoolean.makeHandle(Double.isInfinite(d)));
                }

            case "NaN":
                {
                double d = ((FloatHandle) hTarget).getValue();

                return frame.assignValue(iReturn, xBoolean.makeHandle(Double.isNaN(d)));
                }
            }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "add":
                return invokeAdd(frame, hTarget, hArg, iReturn);

            case "sub":
                return invokeSub(frame, hTarget, hArg, iReturn);

            case "mul":
                return invokeMul(frame, hTarget, hArg, iReturn);

            case "div":
                return invokeDiv(frame, hTarget, hArg, iReturn);

            case "mod":
                return invokeMod(frame, hTarget, hArg, iReturn);

            case "pow":
                {
                double d1 = ((FloatHandle) hTarget).getValue();
                double d2 = ((FloatHandle) hArg).getValue();

                return frame.assignValue(iReturn, makeHandle(Math.pow(d1, d2)));
                }

            case "round":
                {
                double d = ((FloatHandle) hTarget).getValue();
                int    i = hArg == ObjectHandle.DEFAULT
                            ? 0
                            : ((EnumHandle) hArg).getOrdinal();
                double r = new BigDecimal(d).setScale(0, Rounding.values()[i].getMode()).doubleValue();

                return frame.assignValue(iReturn, makeHandle(r));
                }

            case "scaleByPow":
                {
                double d = ((FloatHandle) hTarget).getValue();
                long   l = ((JavaLong) hArg).getValue();

                return frame.assignValue(iReturn, makeHandle(Math.pow(d, l)));
                }

            case "atan2":
                {
                double d1 = ((FloatHandle) hTarget).getValue();
                double d2 = ((FloatHandle) hArg).getValue();

                return frame.assignValue(iReturn, makeHandle(Math.atan2(d1, d2)));
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        double d = ((FloatHandle) hTarget).getValue();
        switch (method.getName())
            {
            case "abs":
                return frame.assignValue(iReturn, makeHandle(Math.abs(d)));

            case "toBitArray":
                {
                byte[] abValue = getBits(d);
                return frame.assignValue(iReturn,
                    xBitArray.makeHandle(abValue, f_cBits, xArray.Mutability.Constant));
                }

            case "toInt":
                // TODO: overflow check
                return Double.isInfinite(d)
                    ? overflow(frame)
                    : frame.assignValue(iReturn, xInt64.INSTANCE.makeJavaLong((long) d));

            case "toDec64":
                return Double.isInfinite(d)
                    ? overflow(frame)
                    : frame.assignValue(iReturn, xDec64.INSTANCE.makeHandle(d));

            case "toFloat64":
                return Double.isInfinite(d)
                    ? overflow(frame)
                    : frame.assignValue(iReturn, makeHandle(d));

            case "toIntN":
            case "toUIntN":
            case "toFloatN":
            case "toDecN":
                throw new UnsupportedOperationException(); // TODO

            case "neg":
                // same as invokeNeg()
                return frame.assignValue(iReturn, makeHandle(-d));

            case "floor":
                return frame.assignValue(iReturn, makeHandle(Math.floor(d)));

            case "ceil":
                return frame.assignValue(iReturn, makeHandle(Math.ceil(d)));

            case "exp":
                return frame.assignValue(iReturn, makeHandle(Math.exp(d)));

            case "log":
                return frame.assignValue(iReturn, makeHandle(Math.log(d)));

            case "log2":
                return frame.assignValue(iReturn, makeHandle(Math.log10(d)*LOG2_10));

            case "log10":
                return frame.assignValue(iReturn, makeHandle(Math.log10(d)));

            case "sqrt":
                return frame.assignValue(iReturn, makeHandle(Math.sqrt(d)));

            case "cbrt":
                return frame.assignValue(iReturn, makeHandle(Math.cbrt(d)));

            case "sin":
                return frame.assignValue(iReturn, makeHandle(Math.sin(d)));

            case "tan":
                return frame.assignValue(iReturn, makeHandle(Math.tan(d)));

            case "asin":
                return frame.assignValue(iReturn, makeHandle(Math.asin(d)));

            case "acos":
                return frame.assignValue(iReturn, makeHandle(Math.acos(d)));

            case "atan":
                return frame.assignValue(iReturn, makeHandle(Math.atan(d)));

            case "sinh":
                return frame.assignValue(iReturn, makeHandle(Math.sinh(d)));

            case "cosh":
                return frame.assignValue(iReturn, makeHandle(Math.cosh(d)));

            case "tanh":
                return frame.assignValue(iReturn, makeHandle(Math.tanh(d)));

            case "asinh":
                return frame.assignValue(iReturn, makeHandle(Math.log(d+Math.sqrt(d*d+1.0))));

            case "acosh":
                return frame.assignValue(iReturn, makeHandle( Math.log(d+Math.sqrt(d*d-1.0))));

            case "atanh":
                return frame.assignValue(iReturn, makeHandle(0.5*Math.log((d+1.0)/(d-1.0))));

            case "deg2rad":
                return frame.assignValue(iReturn, makeHandle(Math.toRadians(d)));

            case "rad2deg":
                return frame.assignValue(iReturn, makeHandle(Math.toDegrees(d)));

            case "nextUp":
                return frame.assignValue(iReturn, makeHandle(Math.nextUp(d)));

            case "nextDown":
                return frame.assignValue(iReturn, makeHandle(Math.nextDown(d)));

            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn)
        {
        switch (method.getName())
            {
            case "split":
                {
                double d = ((FloatHandle) hTarget).getValue();
                long   l = Double.doubleToRawLongBits(d);

                boolean fSign     = (l & SIGN_MASK) != 0;
                int     iExp      = Math.getExponent(d);
                long    lMantissa = l & MANTISSA_MASK;
                return frame.assignValues(aiReturn, xBoolean.makeHandle(fSign),
                        xInt64.makeHandle(lMantissa), xInt64.makeHandle(iExp));
                }
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        double d1 = ((FloatHandle) hTarget).getValue();
        double d2 = ((FloatHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeHandle(d1+d2));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        double d1 = ((FloatHandle) hTarget).getValue();
        double d2 = ((FloatHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeHandle(d1-d2));
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        double d1 = ((FloatHandle) hTarget).getValue();
        double d2 = ((FloatHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeHandle(d1*d2));
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        double d1 = ((FloatHandle) hTarget).getValue();
        double d2 = ((FloatHandle) hArg).getValue();

        if (d2 == 0.0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(d1/d2));
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        double d1 = ((FloatHandle) hTarget).getValue();
        double d2 = ((FloatHandle) hArg).getValue();

        if (d2 == 0.0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(d1%d2));
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        double d = ((FloatHandle) hTarget).getValue();

        return frame.assignValue(iReturn, makeHandle(-d));
        }


    // ----- comparison support --------------------------------------------------------------------

    @Override
    public int callEquals(Frame frame, ClassComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        FloatHandle h1 = (FloatHandle) hValue1;
        FloatHandle h2 = (FloatHandle) hValue2;

        return frame.assignValue(iReturn,
            xBoolean.makeHandle(h1.getValue() == h2.getValue()));
        }

    @Override
    public int callCompare(Frame frame, ClassComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        FloatHandle h1 = (FloatHandle) hValue1;
        FloatHandle h2 = (FloatHandle) hValue2;

        return frame.assignValue(iReturn,
            xOrdered.makeHandle(Double.compare(h1.getValue(), h2.getValue())));
        }

    /**
     * Convert a long value into a handle for the type represented by this template.
     *
     * @return one of the {@link Op#R_NEXT} or {@link Op#R_EXCEPTION} values
     */
    public int convertLong(Frame frame, long lValue, int iReturn)
        {
        return frame.assignValue(iReturn, makeHandle((double) lValue));
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return a bit array for the specified double value
     */
    abstract protected byte[] getBits(double d);

    /**
     * @return a double value for the specified long value
     */
    abstract protected double fromLong(long l);

    /**
     * Note: while we could simply say "Sting.valueOf(d)", it may produce a higher precision
     * (and less human readable) value.
     *
     * @return a String value of the specified double
     */
    abstract protected String toString(double d);

    @Override
    protected int callEstimateLength(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        double d = ((FloatHandle) hTarget).getValue();

        return frame.assignValue(iReturn, xInt64.makeHandle(toString(d).length()));
        }

    @Override
    protected int callAppendTo(Frame frame, ObjectHandle hTarget, ObjectHandle hAppender, int iReturn)
        {
        double d = ((FloatHandle) hTarget).getValue();

        return xString.callAppendTo(frame, xString.makeHandle(toString(d)), hAppender, iReturn);
        }

    @Override
    protected int buildHashCode(Frame frame, ClassComposition clazz, ObjectHandle hTarget, int iReturn)
        {
        double d = ((FloatHandle) hTarget).getValue();

        return frame.assignValue(iReturn, xInt64.makeHandle(Double.hashCode(d)));
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        double d = ((FloatHandle) hTarget).getValue();

        return frame.assignValue(iReturn, xString.makeHandle(toString(d)));
        }


    // ----- handle --------------------------------------------------------------------------------

    @Override
    protected ObjectHandle makeHandle(byte[] aBytes, int cBytes)
        {
        return makeHandle(fromLong(xConstrainedInteger.fromByteArray(aBytes, cBytes, false))); // TODO GG REVIEW CHANGE
        }

    public FloatHandle makeHandle(double dValue)
        {
        return new FloatHandle(getCanonicalClass(), dValue);
        }

    protected static class FloatHandle
            extends ObjectHandle
        {
        protected FloatHandle(ClassComposition clz, double dValue)
            {
            super(clz);

            f_dValue = dValue;
            }

        public double getValue()
            {
            return f_dValue;
            }

        private final double f_dValue;
        }


    // ----- constants -----------------------------------------------------------------------------

    /**
     * Bit mask for the sign bit of a double value.
     */
    public static final long SIGN_MASK     = 0x8000000000000000L;

    /**
     * Bit mask for the exponent of a double value.
     */
    public static final long  EXP_MASK     = 0x7FF0000000000000L;

    /**
     * Bit mask for the mantissa of a double value.
     */
    public static final long MANTISSA_MASK = 0x000FFFFFFFFFFFFFL;
    }
