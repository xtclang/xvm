package org.xvm.runtime.template.numbers;


import java.math.BigDecimal;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.DecimalConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.collections.xArray.Mutability;
import org.xvm.runtime.template.collections.xBitArray;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xOrdered;
import org.xvm.runtime.template.xString;

import org.xvm.type.Decimal;


/**
 * Base class for native DecimalFPNumber (Dec*) support.
 */
abstract public class BaseDecFP
        extends BaseFP
    {
    public BaseDecFP(TemplateRegistry templates, ClassStructure structure, int cBits)
        {
        super(templates, structure, cBits);
        }

    @Override
    public void initDeclared()
        {
        super.initDeclared();

        getCanonicalType().invalidateTypeInfo();
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof DecimalConstant)
            {
            Decimal dec = ((DecimalConstant) constant).getValue();
            frame.pushStack(makeHandle(dec));
            return Op.R_NEXT;
            }

        return super.createConstHandle(frame, constant);
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
                BigDecimal big1 = ((DecimalHandle) hTarget).getValue().toBigDecimal();
                BigDecimal big2 = ((DecimalHandle) hArg).getValue().toBigDecimal();

                return frame.assignValue(iReturn, makeHandle(big1.pow(big2.intValue())));
                }

            case "round":
                {
                BigDecimal big = ((DecimalHandle) hTarget).getValue().toBigDecimal();
                int        i = hArg == ObjectHandle.DEFAULT
                            ? 0
                            : ((EnumHandle) hArg).getOrdinal();

                return frame.assignValue(iReturn,
                    makeHandle(big.setScale(0, Rounding.values()[i].getMode())));
                }

            case "scaleByPow":
                {
                BigDecimal big = ((DecimalHandle) hTarget).getValue().toBigDecimal();
                long       l   = ((JavaLong) hArg).getValue();

                return frame.assignValue(iReturn, makeHandle(big.pow((int) l)));
                }

            case "atan2":
                {
                BigDecimal big1 = ((DecimalHandle) hTarget).getValue().toBigDecimal();
                BigDecimal big2 = ((DecimalHandle) hArg).getValue().toBigDecimal();

                return frame.assignValue(iReturn,
                    makeHandle(Math.atan2(big1.doubleValue(), big2.doubleValue())));
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        Decimal    dec = ((DecimalHandle) hTarget).getValue();
        BigDecimal big = dec.toBigDecimal();
        switch (method.getName())
            {
            case "abs":
                return frame.assignValue(iReturn, makeHandle(big.abs()));

            case "toBitArray":
                {
                byte[] abValue = dec.toByteArray();
                return frame.assignValue(iReturn,
                    xBitArray.makeHandle(abValue, f_cBits, Mutability.Constant));
                }

            case "toFloat64":
                // TODO: overflow check
                return frame.assignValue(iReturn, xFloat64.INSTANCE.makeHandle(big.doubleValue()));

            case "toVarInt":
            case "toVarUInt":
            case "toVarFloat":
            case "toVarDec":
                throw new UnsupportedOperationException(); // TODO

            case "neg":
                // same as invokeNeg()
                return frame.assignValue(iReturn, makeHandle(big.negate()));

            case "floor":
                return frame.assignValue(iReturn,
                    makeHandle(big.setScale(0, Rounding.TowardNegative.getMode())));

            case "ceil":
                return frame.assignValue(iReturn,
                    makeHandle(big.setScale(0, Rounding.TowardPositive.getMode())));

            case "exp":
                return frame.assignValue(iReturn, makeHandle(Math.exp(big.doubleValue())));

            case "log":
                return frame.assignValue(iReturn, makeHandle(Math.log(big.doubleValue())));

            case "log2":
                return frame.assignValue(iReturn, makeHandle(Math.log10(big.doubleValue())*LOG2_10));

            case "log10":
                return frame.assignValue(iReturn, makeHandle(Math.log10(big.doubleValue())));

            case "sqrt":
                return frame.assignValue(iReturn, makeHandle(Math.sqrt(big.doubleValue())));

            case "cbrt":
                return frame.assignValue(iReturn, makeHandle(Math.cbrt(big.doubleValue())));

            case "sin":
                return frame.assignValue(iReturn, makeHandle(Math.sin(big.doubleValue())));

            case "tan":
                return frame.assignValue(iReturn, makeHandle(Math.tan(big.doubleValue())));

            case "asin":
                return frame.assignValue(iReturn, makeHandle(Math.asin(big.doubleValue())));

            case "acos":
                return frame.assignValue(iReturn, makeHandle(Math.acos(big.doubleValue())));

            case "atan":
                return frame.assignValue(iReturn, makeHandle(Math.atan(big.doubleValue())));

            case "sinh":
                return frame.assignValue(iReturn, makeHandle(Math.sinh(big.doubleValue())));

            case "cosh":
                return frame.assignValue(iReturn, makeHandle(Math.cosh(big.doubleValue())));

            case "tanh":
                return frame.assignValue(iReturn, makeHandle(Math.tanh(big.doubleValue())));

            case "asinh":
                {
                double d = big.doubleValue();
                return frame.assignValue(iReturn, makeHandle(Math.log(d+Math.sqrt(d*d+1.0))));
                }

            case "acosh":
                {
                double d = big.doubleValue();
                return frame.assignValue(iReturn, makeHandle( Math.log(d+Math.sqrt(d*d-1.0))));
                }

            case "atanh":
                {
                double d = big.doubleValue();
                return frame.assignValue(iReturn, makeHandle(0.5*Math.log((d+1.0)/(d-1.0))));
                }

            case "deg2rad":
                return frame.assignValue(iReturn, makeHandle(Math.toRadians(big.doubleValue())));

            case "rad2deg":
                return frame.assignValue(iReturn, makeHandle(Math.toDegrees(big.doubleValue())));

            case "nextUp":
                return frame.assignValue(iReturn, makeHandle(Math.nextUp(big.doubleValue())));

            case "nextDown":
                return frame.assignValue(iReturn, makeHandle(Math.nextDown(big.doubleValue())));

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
                Decimal dec = ((DecimalHandle) hTarget).getValue();

                // TODO:
                boolean fSign     = dec.isSigned();
                int     iExp      = 0;
                long    lMantissa = 0;
                return frame.assignValues(aiReturn, xBoolean.makeHandle(fSign),
                        xInt64.makeHandle(lMantissa), xInt64.makeHandle(iExp));
                }
            }

        return super.invokeNativeNN(frame, method, hTarget, ahArg, aiReturn);
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        BigDecimal big1 = ((DecimalHandle) hTarget).getValue().toBigDecimal();
        BigDecimal big2 = ((DecimalHandle) hArg).getValue().toBigDecimal();

        return frame.assignValue(iReturn, makeHandle(big1.add(big2)));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        BigDecimal big1 = ((DecimalHandle) hTarget).getValue().toBigDecimal();
        BigDecimal big2 = ((DecimalHandle) hArg).getValue().toBigDecimal();

        return frame.assignValue(iReturn, makeHandle(big1.subtract(big2)));
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        BigDecimal big1 = ((DecimalHandle) hTarget).getValue().toBigDecimal();
        BigDecimal big2 = ((DecimalHandle) hArg).getValue().toBigDecimal();

        return frame.assignValue(iReturn, makeHandle(big1.multiply(big2)));
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        BigDecimal big1 = ((DecimalHandle) hTarget).getValue().toBigDecimal();
        BigDecimal big2 = ((DecimalHandle) hArg).getValue().toBigDecimal();

        if (big2.signum() == 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(big1.divide(big2)));
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        BigDecimal big1 = ((DecimalHandle) hTarget).getValue().toBigDecimal();
        BigDecimal big2 = ((DecimalHandle) hArg).getValue().toBigDecimal();

        if (big2.signum() <= 0)
            {
            // TODO:  if (< 0) "modulus not positive"
            return overflow(frame);
            }

        BigDecimal bigR = big1.remainder(big2);
        return frame.assignValue(iReturn, makeHandle(bigR.signum() >= 0 ? bigR : bigR.add(big2)));
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        BigDecimal big = ((DecimalHandle) hTarget).getValue().toBigDecimal();

        return frame.assignValue(iReturn, makeHandle(big.negate()));
        }


    // ----- comparison support --------------------------------------------------------------------

    @Override
    public int callEquals(Frame frame, ClassComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        DecimalHandle h1 = (DecimalHandle) hValue1;
        DecimalHandle h2 = (DecimalHandle) hValue2;

        return frame.assignValue(iReturn,
            xBoolean.makeHandle(h1.getValue().equals(h2.getValue())));
        }

    @Override
    public int callCompare(Frame frame, ClassComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        DecimalHandle h1 = (DecimalHandle) hValue1;
        DecimalHandle h2 = (DecimalHandle) hValue2;

        return frame.assignValue(iReturn,
            xOrdered.makeHandle(h1.getValue().compareForObjectOrder(h2.getValue())));
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return a decimal value for the specified BigDecimal
     */
    abstract protected Decimal fromBigDecimal(BigDecimal big);

    /**
     * @return TODO
     */
    protected String toString(BigDecimal big)
        {
        return big.stripTrailingZeros().toString();
        }

    @Override
    protected int callEstimateLength(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        BigDecimal big = ((DecimalHandle) hTarget).getValue().toBigDecimal();

        return frame.assignValue(iReturn, xInt64.makeHandle(toString(big).length()));
        }

    @Override
    protected int callAppendTo(Frame frame, ObjectHandle hTarget, ObjectHandle hAppender, int iReturn)
        {
        BigDecimal big = ((DecimalHandle) hTarget).getValue().toBigDecimal();

        return xString.callAppendTo(frame, xString.makeHandle(toString(big)), hAppender, iReturn);
        }

    @Override
    protected int buildHashCode(Frame frame, ClassComposition clazz, ObjectHandle hTarget, int iReturn)
        {
        Decimal dec = ((DecimalHandle) hTarget).getValue();

        return frame.assignValue(iReturn, xInt64.makeHandle(dec.hashCode()));
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        BigDecimal big = ((DecimalHandle) hTarget).getValue().toBigDecimal();

        return frame.assignValue(iReturn, xString.makeHandle(toString(big)));
        }


    // ----- handle --------------------------------------------------------------------------------

    protected DecimalHandle makeHandle(double d)
        {
        return makeHandle(new BigDecimal(d));
        }

    public DecimalHandle makeHandle(BigDecimal bigValue)
        {
        return makeHandle(fromBigDecimal(bigValue));
        }

    public DecimalHandle makeHandle(Decimal decValue)
        {
        return new DecimalHandle(getCanonicalClass(), decValue);
        }

    protected static class DecimalHandle
            extends ObjectHandle
        {
        protected DecimalHandle(ClassComposition clz, Decimal decValue)
            {
            super(clz);

            f_decValue = decValue;
            }

        public Decimal getValue()
            {
            return f_decValue;
            }

        private final Decimal f_decValue;
        }
    }
