package org.xvm.runtime.template.numbers;


import java.math.RoundingMode;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xException;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xBitArray;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.text.xString.StringHandle;


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
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        SignatureConstant sig = constructor.getIdentityConstant().getSignature();
        if (sig.getParamCount() == 1)
            {
            TypeConstant typeArg = sig.getRawParams()[0];
            if (typeArg.equals(pool().typeString()))
                {
                // construct(String text)
                String sText = ((StringHandle) ahVar[0]).getStringValue();
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

            // the rest are arrays
            typeArg = typeArg.getParamType(0);

            if (typeArg.equals(pool().typeByte()))
                {
                // construct(Byte[] bytes)
                byte[] abVal  = xByteArray.getBytes((ArrayHandle) ahVar[0]);
                int    cBytes = abVal.length;

                return cBytes == f_cBits / 8
                    ? frame.assignValue(iReturn, makeHandle(abVal, cBytes))
                    : frame.raiseException(
                        xException.illegalArgument(frame, "Invalid byte count: " + cBytes));
                }

            if (typeArg.equals(pool().typeBit()))
                {
                // construct(Bit[] bytes)
                ArrayHandle hArray = (ArrayHandle) ahVar[0];
                byte[]      abBits = xBitArray.getBits(hArray);
                int         cBits  = (int) hArray.m_hDelegate.m_cSize;

                return cBits == f_cBits
                    ? frame.assignValue(iReturn, makeHandle(abBits, cBits >>> 3))
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