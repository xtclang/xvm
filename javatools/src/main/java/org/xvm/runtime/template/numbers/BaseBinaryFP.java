package org.xvm.runtime.template.numbers;


import java.math.BigDecimal;
import java.math.BigInteger;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.text.xString;

import org.xvm.type.Decimal;
import org.xvm.type.Decimal32;
import org.xvm.type.Decimal64;
import org.xvm.type.Decimal128;

import org.xvm.util.PackedInteger;


/**
 * Base class for native BinaryFPNumber (Float16, 32, 64) support.
 */
public abstract class BaseBinaryFP
        extends BaseFP {
    public BaseBinaryFP(Container container, ClassStructure structure, int cBits) {
        super(container, structure, cBits);
    }

    @Override
    public void initNative() {
        super.initNative();

        markNativeMethod("toInt64",   null, null);
        markNativeMethod("toDec32",   null, null);
        markNativeMethod("toDec64",   null, null);
        markNativeMethod("toDec128",  null, null);
        markNativeMethod("toFloat32", null, null);
        markNativeMethod("toFloat64", null, null);
        markNativeMethod("toIntN",    null, null);
        markNativeMethod("toUIntN",   null, null);
        markNativeMethod("toFloatN",  null, null);
        markNativeMethod("toDecN",    null, null);
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        double d = ((FloatHandle) hTarget).getValue();

        switch (sPropName) {
        case "bits":
            return frame.assignValue(iReturn,
                xArray.makeBitArrayHandle(getBits(d), f_cBits, Mutability.Constant));

        case "infinity":
            return frame.assignValue(iReturn, xBoolean.makeHandle(Double.isInfinite(d)));

        case "NaN":
            return frame.assignValue(iReturn, xBoolean.makeHandle(Double.isNaN(d)));
        }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        switch (method.getName()) {
        case "toIntN":
            return convertToIntN(frame, ((FloatHandle) hTarget).getValue(), hArg, iReturn);

        case "toUIntN":
            return convertToUIntN(frame, ((FloatHandle) hTarget).getValue(), hArg, iReturn);

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

        case "pow": {
            double d1 = ((FloatHandle) hTarget).getValue();
            double d2 = ((FloatHandle) hArg).getValue();

            return frame.assignValue(iReturn, makeHandle(Math.pow(d1, d2)));
        }

        case "round": {
            double d = ((FloatHandle) hTarget).getValue();
            int    i = hArg == ObjectHandle.DEFAULT
                        ? 0
                        : ((EnumHandle) hArg).getOrdinal();
            double r = new BigDecimal(d).setScale(0, Rounding.values()[i].getMode()).doubleValue();

            return frame.assignValue(iReturn, makeHandle(r));
        }

        case "scaleByPow": {
            double d = ((FloatHandle) hTarget).getValue();
            long   l = ((JavaLong) hArg).getValue();

            return frame.assignValue(iReturn, makeHandle(Math.pow(d, l)));
        }

        case "atan2": {
            double d1 = ((FloatHandle) hTarget).getValue();
            double d2 = ((FloatHandle) hArg).getValue();

            return frame.assignValue(iReturn, makeHandle(Math.atan2(d1, d2)));
        }
        }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn) {
        // hTarget could be null for a native function call
        double d = hTarget == null ? 0 : ((FloatHandle) hTarget).getValue();
        switch (method.getName()) {
        case "abs":
            return frame.assignValue(iReturn, makeHandle(Math.abs(d)));

        case "toInt64":
            return convertToInt64(frame, d, ahArg, iReturn);

        case "toDec32":
            return frame.assignValue(iReturn, xDec32.INSTANCE.makeHandle(toDec32(d)));

        case "toDec64":
            return frame.assignValue(iReturn, xDec64.INSTANCE.makeHandle(toDec64(d)));

        case "toDec128":
            return frame.assignValue(iReturn, xDec128.INSTANCE.makeHandle(toDec128(d)));

        case "toFloat32":
            return frame.assignValue(iReturn, xFloat32.INSTANCE.makeHandle(d));

        case "toFloat64":
            return frame.assignValue(iReturn, xFloat64.INSTANCE.makeHandle(toFloat64(d)));

        case "toIntN":
        case "toUIntN":
            return method.getName().equals("toIntN")
                    ? convertToIntN(frame, d, ObjectHandle.DEFAULT, iReturn)
                    : convertToUIntN(frame, d, ObjectHandle.DEFAULT, iReturn);

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
    public int invokeNativeNN(Frame frame, MethodStructure method, ObjectHandle hTarget, ObjectHandle[] ahArg, int[] aiReturn) {
        switch (method.getName()) {
        case "split": {
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
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        double d1 = ((FloatHandle) hTarget).getValue();
        double d2 = ((FloatHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeHandle(d1+d2));
    }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        double d1 = ((FloatHandle) hTarget).getValue();
        double d2 = ((FloatHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeHandle(d1-d2));
    }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        double d1 = ((FloatHandle) hTarget).getValue();
        double d2 = ((FloatHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeHandle(d1*d2));
    }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        double d1 = ((FloatHandle) hTarget).getValue();
        double d2 = ((FloatHandle) hArg).getValue();

        if (d2 == 0.0) {
            return overflow(frame);
        }

        return frame.assignValue(iReturn, makeHandle(d1/d2));
    }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        double d1 = ((FloatHandle) hTarget).getValue();
        double d2 = ((FloatHandle) hArg).getValue();

        if (d2 == 0.0) {
            return overflow(frame);
        }

        return frame.assignValue(iReturn, makeHandle(d1%d2));
    }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn) {
        double d = ((FloatHandle) hTarget).getValue();

        return frame.assignValue(iReturn, makeHandle(-d));
    }


    // ----- comparison support --------------------------------------------------------------------

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        FloatHandle h1 = (FloatHandle) hValue1;
        FloatHandle h2 = (FloatHandle) hValue2;

        return frame.assignValue(iReturn,
                xOrdered.makeHandle(Double.compare(h1.getValue(), h2.getValue())));
    }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2) {
        return ((FloatHandle) hValue1).getValue() == ((FloatHandle) hValue2).getValue();
    }

    @Override
    protected int buildHashCode(Frame frame, TypeComposition clazz, ObjectHandle hTarget, int iReturn) {
        double d = ((FloatHandle) hTarget).getValue();

        return frame.assignValue(iReturn, xInt64.makeHandle(Double.hashCode(d)));
    }

    @Override
    protected int callEstimateLength(Frame frame, ObjectHandle hTarget, int iReturn) {
        double d = ((FloatHandle) hTarget).getValue();

        return frame.assignValue(iReturn, xInt64.makeHandle(toString(d).length()));
    }

    @Override
    protected int callAppendTo(Frame frame, ObjectHandle hTarget, ObjectHandle hAppender, int iReturn) {
        double d = ((FloatHandle) hTarget).getValue();

        return xString.callAppendTo(frame, xString.makeHandle(toString(d)), hAppender, iReturn);
    }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn) {
        double d = ((FloatHandle) hTarget).getValue();

        return frame.assignValue(iReturn, xString.makeHandle(toString(d)));
    }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Convert a long value into a handle for the type represented by this template.
     *
     * @return one of the {@link Op#R_NEXT} or {@link Op#R_EXCEPTION} values
     */
    public int convertLong(Frame frame, long lValue, int iReturn) {
        return frame.assignValue(iReturn, makeHandle((double) lValue));
    }

    protected int convertToInt64(Frame frame, double d, ObjectHandle[] ahArg, int iReturn) {
        boolean fCheckBound = ahArg[0] == xBoolean.TRUE;
        if (!Double.isFinite(d)) {
            if (fCheckBound) {
                return overflow(frame);
            }

            Rounding rounding = Rounding.values()[ahArg[1] == ObjectHandle.DEFAULT
                ? 0
                : ((EnumHandle) ahArg[1]).getOrdinal()];
            long l = switch (rounding) {
                case TiesToEven     -> (long) Math.rint(d);
                case TiesToAway     -> (long) (d < 0 ? Math.floor(d) : Math.ceil(d));
                case TowardPositive -> (long) Math.ceil(d);
                case TowardZero     -> (long) (d < 0 ? Math.ceil(d) : Math.floor(d));
                case TowardNegative -> (long) Math.floor(d);
            };
            return frame.assignValue(iReturn, xInt64.INSTANCE.makeJavaLong(l));
        }

        BigInteger n = roundedInteger(d, ahArg[1]);
        return fCheckBound && n.bitLength() >= Long.SIZE
                ? overflow(frame)
                : frame.assignValue(iReturn, xInt64.INSTANCE.makeJavaLong(n.longValue()));
    }

    protected int convertToIntN(Frame frame, double d, ObjectHandle hRound, int iReturn) {
        if (!Double.isFinite(d)) {
            return overflow(frame);
        }

        return frame.assignValue(iReturn,
                xIntN.INSTANCE.makeInt(new PackedInteger(roundedInteger(d, hRound))));
    }

    protected int convertToUIntN(Frame frame, double d, ObjectHandle hRound, int iReturn) {
        if (!Double.isFinite(d)) {
            return overflow(frame);
        }

        BigInteger n = roundedInteger(d, hRound);
        if (n.signum() < 0) {
            return overflow(frame);
        }

        return frame.assignValue(iReturn, xUIntN.INSTANCE.makeInt(new PackedInteger(n)));
    }

    protected BigInteger roundedInteger(double d, ObjectHandle hRound) {
        int iMode = hRound == ObjectHandle.DEFAULT
                ? 3
                : ((EnumHandle) hRound).getOrdinal();
        return toBigDecimal(d).setScale(0, Rounding.values()[iMode].getMode()).toBigInteger();
    }

    protected BigDecimal toBigDecimal(double d) {
        return new BigDecimal(toString(d));
    }

    protected Decimal32 toDec32(double d) {
        if (!Double.isFinite(d)) {
            return Double.isNaN(d)
                    ? Decimal32.NaN
                    : d < 0 ? Decimal32.NEG_INFINITY : Decimal32.POS_INFINITY;
        }

        try {
            return new Decimal32(new BigDecimal(toString(d)));
        } catch (Decimal.RangeException e) {
            return (Decimal32) e.getDecimal();
        }
    }

    protected Decimal64 toDec64(double d) {
        if (!Double.isFinite(d)) {
            return Double.isNaN(d)
                    ? Decimal64.NaN
                    : d < 0 ? Decimal64.NEG_INFINITY : Decimal64.POS_INFINITY;
        }

        try {
            return new Decimal64(new BigDecimal(toString(d)));
        } catch (Decimal.RangeException e) {
            return (Decimal64) e.getDecimal();
        }
    }

    protected Decimal128 toDec128(double d) {
        if (!Double.isFinite(d)) {
            return Double.isNaN(d)
                    ? Decimal128.NaN
                    : d < 0 ? Decimal128.NEG_INFINITY : Decimal128.POS_INFINITY;
        }

        try {
            return new Decimal128(new BigDecimal(toString(d)));
        } catch (Decimal.RangeException e) {
            return (Decimal128) e.getDecimal();
        }
    }

    protected double toFloat64(double d) {
        return d;
    }

    /**
     * @return a bit array for the specified double value
     */
    protected abstract byte[] getBits(double d);

    /**
     * @return a double value for the specified long value
     */
    protected abstract double fromLong(long l);

    /**
     * Note: while we could simply say "Sting.valueOf(d)", it may produce a higher precision
     * (and less human readable) value.
     *
     * @return a String value of the specified double
     */
    protected abstract String toString(double d);


    // ----- handle --------------------------------------------------------------------------------

    @Override
    protected ObjectHandle makeHandle(byte[] aBytes, int cBytes) {
        return makeHandle(fromLong(xConstrainedInteger.fromByteArray(aBytes, cBytes, false)));
    }

    @Override
    public FloatHandle makeHandle(double dValue) {
        return new FloatHandle(getCanonicalClass(), dValue);
    }

    public static class FloatHandle
            extends ObjectHandle {
        protected FloatHandle(ClassComposition clz, double dValue) {
            super(clz);

            f_dValue = dValue;
        }

        public double getValue() {
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