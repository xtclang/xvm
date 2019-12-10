package org.xvm.runtime.template.numbers;


import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xEnum.EnumHandle;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xString;


/**
 * Base class for native Float* support.
 */
abstract public class FPBase
        extends xConst
    {
    public static FPBase INSTANCE;

    public FPBase(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initDeclared()
        {
        if (METHOD_APPEND_TO == null)
            {
            ConstantPool   pool      = pool();
            ClassStructure clzString = xString.INSTANCE.f_struct;
            TypeConstant   typeArg   = pool.ensureClassTypeConstant(
                    pool.ensureEcstasyClassConstant("Appender"), null,
                    pool.typeChar());

            METHOD_APPEND_TO = clzString.findMethod("appendTo", 1, typeArg);
            }

        // @Op methods
        markNativeMethod("abs"        , VOID, THIS);
        markNativeMethod("add"        , THIS, THIS);
        markNativeMethod("sub"        , THIS, THIS);
        markNativeMethod("mul"        , THIS, THIS);
        markNativeMethod("div"        , THIS, THIS);
        markNativeMethod("mod"        , THIS, THIS);
        markNativeMethod("neg"        , VOID, THIS);

        // properties
        markNativeProperty("infinity");
        markNativeProperty("NaN");

        // operations
        markNativeMethod("pow"        , THIS, THIS);
        markNativeMethod("split"      , VOID, null);
        markNativeMethod("round"      , null, THIS);
        markNativeMethod("floor"      , VOID, THIS);
        markNativeMethod("ceil"       , VOID, THIS);
        markNativeMethod("exp"        , VOID, THIS);
        markNativeMethod("scaleByPow" , INT,  THIS);
        markNativeMethod("log"        , VOID, THIS);
        markNativeMethod("log2"       , VOID, THIS);
        markNativeMethod("log10"      , VOID, THIS);
        markNativeMethod("sqrt"       , VOID, THIS);
        markNativeMethod("cbrt"       , VOID, THIS);
        markNativeMethod("sin"        , VOID, THIS);
        markNativeMethod("tan"        , VOID, THIS);
        markNativeMethod("asin"       , VOID, THIS);
        markNativeMethod("acos"       , VOID, THIS);
        markNativeMethod("atan"       , VOID, THIS);
        markNativeMethod("atan2"      , THIS, THIS);
        markNativeMethod("asinh"      , VOID, THIS);
        markNativeMethod("sinh"       , VOID, THIS);
        markNativeMethod("cosh"       , VOID, THIS);
        markNativeMethod("tanh"       , VOID, THIS);
        markNativeMethod("asinh"      , VOID, THIS);
        markNativeMethod("acosh"      , VOID, THIS);
        markNativeMethod("atanh"      , VOID, THIS);
        markNativeMethod("deg2rad"    , VOID, THIS);
        markNativeMethod("rad2deg"    , VOID, THIS);
        markNativeMethod("nextUp"     , VOID, THIS);
        markNativeMethod("nextDown"   , VOID, THIS);

        // conversions
        markNativeMethod("toFloat64"  , VOID, FLOAT64);
        markNativeMethod("toVarInt"   , VOID, VAR_INT);
        markNativeMethod("toVarUInt"  , VOID, VAR_UINT);
        markNativeMethod("toVarFloat" , VOID, VAR_FLOAT);
        markNativeMethod("toVarDec"   , VOID, VAR_DEC);
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
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

                return frame.assignValue(iReturn, makeFloat(Math.pow(d1, d2)));
                }

            case "round":
                {
                double d = ((FloatHandle) hTarget).getValue();
                int    i = hArg == ObjectHandle.DEFAULT
                            ? 0
                            : ((EnumHandle) hArg).getOrdinal();
                double r = new BigDecimal(d).plus(Rounding.values()[i].getContext()).doubleValue();

                return frame.assignValue(iReturn, makeFloat(r));
                }

            case "scaleByPow":
                {
                double d = ((FloatHandle) hTarget).getValue();
                long   l = ((JavaLong) hArg).getValue();

                return frame.assignValue(iReturn, makeFloat(Math.pow(d, l)));
                }

            case "atan2":
                {
                double d1 = ((FloatHandle) hTarget).getValue();
                double d2 = ((FloatHandle) hArg).getValue();

                return frame.assignValue(iReturn, makeFloat(Math.atan2(d1, d2)));
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
                return frame.assignValue(iReturn, makeFloat(Math.abs(d)));

            case "toFloat64":
                return frame.assignValue(iReturn, makeFloat(d));

            case "toVarInt":
            case "toVarUInt":
            case "toVarFloat":
            case "toVarDec":
                throw new UnsupportedOperationException(); // TODO

            case "neg":
                // same as invokeNeg()
                return frame.assignValue(iReturn, makeFloat(-d));

            case "floor":
                return frame.assignValue(iReturn, makeFloat(Math.floor(d)));

            case "ceil":
                return frame.assignValue(iReturn, makeFloat(Math.ceil(d)));

            case "exp":
                return frame.assignValue(iReturn, makeFloat(Math.exp(d)));

            case "log":
                return frame.assignValue(iReturn, makeFloat(Math.log(d)));

            case "log2":
                return frame.assignValue(iReturn, makeFloat(Math.log10(d)/LOG10_2));

            case "log10":
                return frame.assignValue(iReturn, makeFloat(Math.log10(d)));

            case "sqrt":
                return frame.assignValue(iReturn, makeFloat(Math.sqrt(d)));

            case "cbrt":
                return frame.assignValue(iReturn, makeFloat(Math.cbrt(d)));

            case "sin":
                return frame.assignValue(iReturn, makeFloat(Math.sin(d)));

            case "tan":
                return frame.assignValue(iReturn, makeFloat(Math.tan(d)));

            case "asin":
                return frame.assignValue(iReturn, makeFloat(Math.asin(d)));

            case "acos":
                return frame.assignValue(iReturn, makeFloat(Math.acos(d)));

            case "atan":
                return frame.assignValue(iReturn, makeFloat(Math.atan(d)));

            case "sinh":
                return frame.assignValue(iReturn, makeFloat(Math.sinh(d)));

            case "cosh":
                return frame.assignValue(iReturn, makeFloat(Math.cosh(d)));

            case "tanh":
                return frame.assignValue(iReturn, makeFloat(Math.tanh(d)));

            case "asinh":
                return frame.assignValue(iReturn, makeFloat(Math.log(d+Math.sqrt(d*d+1.0))));

            case "acosh":
                return frame.assignValue(iReturn, makeFloat( Math.log(d+Math.sqrt(d*d-1.0))));

            case "atanh":
                return frame.assignValue(iReturn, makeFloat(0.5*Math.log((d+1.0)/(d-1.0))));

            case "deg2rad":
                return frame.assignValue(iReturn, makeFloat(Math.toRadians(d)));

            case "rad2deg":
                return frame.assignValue(iReturn, makeFloat(Math.toDegrees(d)));

            case "nextUp":
                return frame.assignValue(iReturn, makeFloat(Math.nextUp(d)));

            case "nextDown":
                return frame.assignValue(iReturn, makeFloat(Math.nextDown(d)));

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

        return frame.assignValue(iReturn, makeFloat(d1+d2));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        double d1 = ((FloatHandle) hTarget).getValue();
        double d2 = ((FloatHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeFloat(d1-d2));
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        double d1 = ((FloatHandle) hTarget).getValue();
        double d2 = ((FloatHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeFloat(d1*d2));
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

        return frame.assignValue(iReturn, makeFloat(d1/d2));
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

        return frame.assignValue(iReturn, makeFloat(d1%d2));
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        double d = ((FloatHandle) hTarget).getValue();

        return frame.assignValue(iReturn, makeFloat(-d));
        }


    // ----- FP operations -------------------------------------------------------------------------



    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return precision (_p_) is defined by IEEE 754
     */
    abstract protected int getPrecision();

    /**
     * Raise an overflow exception.
     *
     * @return {@link Op#R_EXCEPTION}
     */
    protected int overflow(Frame frame)
        {
        return frame.raiseException(xException.outOfBounds(frame, f_struct.getName() + " overflow"));
        }

    @Override
    protected int callEstimateLength(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        double d = ((FloatHandle) hTarget).getValue();

        return frame.assignValue(iReturn, xInt64.makeHandle(String.valueOf(d).length()));
        }

    @Override
    protected int callAppendTo(Frame frame, ObjectHandle hTarget, ObjectHandle hAppender, int iReturn)
        {
        double d = ((FloatHandle) hTarget).getValue();

        ObjectHandle[] ahArg = new ObjectHandle[METHOD_APPEND_TO.getMaxVars()];
        ahArg[0] = hAppender;

        return frame.call1(METHOD_APPEND_TO, xString.makeHandle(String.valueOf(d)), ahArg, iReturn);
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

        return frame.assignValue(iReturn, xString.makeHandle(String.valueOf(d)));
        }


    // ----- handle --------------------------------------------------------------------------------

    public FloatHandle makeFloat(double dValue)
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


    // ----- constants and fields ------------------------------------------------------------------

    /**
     * A copy of the enum in FPNumber.x
     */
    enum Rounding
        {
        TiesToEven    (RoundingMode.HALF_EVEN),
        TiesToAway    (RoundingMode.UP       ),
        TowardPositive(RoundingMode.CEILING  ),
        TowardZero    (RoundingMode.DOWN     ),
        TowardNegative(RoundingMode.FLOOR    );

        Rounding(RoundingMode mode)
            {
            m_ctx = new MathContext(1, mode);
            }
        public MathContext getContext()
            {
            return m_ctx;
            }
        private MathContext m_ctx;
        }

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

    /**
     * The log10(2) value.
     */
    public static final double LOG10_2    = Math.log10(2);


    public static String[] FLOAT64   = new String[]{"numbers.Float64"};
    public static String[] VAR_INT   = new String[]{"numbers.VarInt"};
    public static String[] VAR_UINT  = new String[]{"numbers.VarUInt"};
    public static String[] VAR_FLOAT = new String[]{"numbers.VarFloat"};
    public static String[] VAR_DEC   = new String[]{"numbers.VarDec"};

    private static MethodStructure METHOD_APPEND_TO;
    }
