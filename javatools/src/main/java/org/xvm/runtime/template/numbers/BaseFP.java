package org.xvm.runtime.template.numbers;


import java.math.MathContext;
import java.math.RoundingMode;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.Op;
import org.xvm.asm.constants.SignatureConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;

import org.xvm.runtime.template.collections.BitBasedArray.BitArrayHandle;
import org.xvm.runtime.template.collections.xByteArray.ByteArrayHandle;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xException;


/**
 * Base class for native FPNumber (Float* and Dec*) support.
 */
abstract public class BaseFP
        extends xConst
    {
    public BaseFP(TemplateRegistry templates, ClassStructure structure, int cBits)
        {
        super(templates, structure, false);

        f_cBits = cBits;
        }

    @Override
    public void initNative()
        {
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

        // conversions
        markNativeMethod("toBitArray" , VOID, null);
        markNativeMethod("toInt"      , VOID, INT);
        markNativeMethod("toDec64"    , VOID, DEC64);
        markNativeMethod("toFloat64"  , VOID, FLOAT64);
        markNativeMethod("toIntN"   , VOID, VAR_INT);
        markNativeMethod("toUIntN"  , VOID, VAR_UINT);
        markNativeMethod("toFloatN" , VOID, VAR_FLOAT);
        markNativeMethod("toDecN"   , VOID, VAR_DEC);
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, ClassComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        SignatureConstant sig = constructor.getIdentityConstant().getSignature();
        if (sig.getParamCount() == 1)
            {
            if (sig.getRawParams()[0].getParamType(0).equals(pool().typeByte()))
                {
                // construct(Byte[] bytes)
                ByteArrayHandle hBytes = (ByteArrayHandle) ahVar[0];
                byte[]          abVal  = hBytes.m_abValue;
                int             cBytes = hBytes.m_cSize;

                return cBytes == f_cBits / 8
                    ? frame.assignValue(iReturn, makeHandle(abVal, cBytes))
                    : frame.raiseException(
                        xException.illegalArgument(frame, "Invalid byte count: " + cBytes));
                }

            if (sig.getRawParams()[0].getParamType(0).equals(pool().typeBit()))
                {
                // construct(Bit[] bits)
                BitArrayHandle hBits = (BitArrayHandle) ahVar[0];
                byte[]         abVal = hBits.m_abValue;
                int            cBits = hBits.m_cSize;

                return cBits == f_cBits
                    ? frame.assignValue(iReturn, makeHandle(abVal, cBits >>> 3))
                    : frame.raiseException(
                        xException.illegalArgument(frame, "Invalid bit count: " + cBits));
                }
            }
        return frame.raiseException(xException.unsupportedOperation(frame));
        }


    // ----- handles -------------------------------------------------------------------------------

    /**
     * @return an ObjectHandle based on the specified byte array
     */
    abstract protected ObjectHandle makeHandle(byte[] aBytes, int cBytes);


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

        private RoundingMode f_mode;
        }

    /**
     * The log2(10) value.
     */
    public static final double LOG2_10 = 1.0/Math.log10(2);

    /**
     * Useful type signatures.
     */
    public static String[] DEC64     = new String[]{"numbers.Dec64"};
    public static String[] FLOAT64   = new String[]{"numbers.Float64"};
    public static String[] VAR_INT   = new String[]{"numbers.IntN"};
    public static String[] VAR_UINT  = new String[]{"numbers.UIntN"};
    public static String[] VAR_FLOAT = new String[]{"numbers.FloatN"};
    public static String[] VAR_DEC   = new String[]{"numbers.DecN"};

    /**
     * The number of bits for this Float type.
     */
    protected final int f_cBits;
    }
