package org.xvm.runtime.template.numbers;


import java.math.RoundingMode;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Op;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xException;


/**
 * Base class for native FPNumber (Float* and Dec*) support.
 */
abstract public class BaseFP
        extends xNumber
    {
    public BaseFP(Container container, ClassStructure structure, int cBits)
        {
        super(container, structure, false);

        f_cBits = cBits;
        }

    @Override
    public void initNative()
        {
        super.initNative();

        // properties
        markNativeProperty("infinity");
        markNativeProperty("NaN");

        // @Op methods
        markNativeMethod("abs"        , VOID, THIS);
        markNativeMethod("add"        , THIS, THIS);
        markNativeMethod("sub"        , THIS, THIS);
        markNativeMethod("mul"        , THIS, THIS);
        markNativeMethod("div"        , THIS, THIS);
        markNativeMethod("mod"        , THIS, THIS);
        markNativeMethod("neg"        , VOID, THIS);

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

        invalidateTypeInfo();
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    protected int constructFromString(Frame frame, String sText, int iReturn)
        {
        try
            {
            return frame.assignValue(Op.A_STACK, makeHandle(Double.valueOf(sText)));
            }
        catch (NumberFormatException e)
            {
            return frame.raiseException(
                xException.illegalArgument(frame, "Invalid number \"" + sText + "\""));
            }
        }

    @Override
    protected int constructFromBytes(Frame frame, byte[] ab, int cBytes, int iReturn)
        {
        return cBytes == f_cBits / 8
            ? frame.assignValue(iReturn, makeHandle(ab, cBytes))
            : frame.raiseException(
                xException.illegalArgument(frame, "Invalid byte count: " + cBytes));
        }

    @Override
    protected int constructFromBits(Frame frame, byte[] ab, int cBits, int iReturn)
        {
        return cBits == f_cBits
            ? frame.assignValue(iReturn, makeHandle(ab, cBits >>> 3))
            : frame.raiseException(
                xException.illegalArgument(frame, "Invalid bit count: " + cBits));
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return an ObjectHandle based on the specified byte array
     */
    abstract protected ObjectHandle makeHandle(byte[] aBytes, int cBytes);

    /**
     * @return an ObjectHandle based on the specified double value
     */
    abstract protected ObjectHandle makeHandle(double dValue);


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
            f_mode = mode;
            }

        public RoundingMode getMode()
            {
            return f_mode;
            }

        private final RoundingMode f_mode;
        }

    /**
     * The log2(10) value.
     */
    public static final double LOG2_10 = 1.0/Math.log10(2);

    /**
     * The number of bits for this Float type.
     */
    protected final int f_cBits;
    }