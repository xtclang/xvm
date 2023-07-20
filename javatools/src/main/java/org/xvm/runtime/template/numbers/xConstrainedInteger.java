package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.collections.xArray;
import org.xvm.runtime.template.collections.xArray.Mutability;

import org.xvm.runtime.template.text.xChar;

import org.xvm.util.PackedInteger;


/**
 * Abstract base class for constrained integers that fit into 64 bits
 * (Int8, UInt16, Int32, ...)
 */
public abstract class xConstrainedInteger
        extends xIntNumber
    {
    protected xConstrainedInteger(Container container, ClassStructure structure,
                                  long cMinValue, long cMaxValue,
                                  int cNumBits, boolean fUnsigned, boolean fChecked)
        {
        super(container, structure, false);

        f_cMinValue = cMinValue;
        f_cMaxValue = cMaxValue;
        f_cNumBits  = cNumBits;
        f_fChecked  = fChecked;
        f_fSigned   = !fUnsigned;

        f_cAddCheckShift = 64 - cNumBits;
        f_cMulCheckShift = fUnsigned ? (cNumBits / 2) : (cNumBits / 2 - 1);
        f_lValueMask     = -1L >>> (64 - cNumBits);
        }

    @Override
    public void initNative()
        {
        super.initNative();

        if (f_fSigned)
            {
            markNativeProperty("magnitude");
            }

        markNativeProperty("leadingZeroCount");

        markNativeMethod("rotateLeft"   , INT , THIS);
        markNativeMethod("rotateRight"  , INT , THIS);
        markNativeMethod("retainLSBits" , INT , THIS);
        markNativeMethod("retainMSBits" , INT , THIS);
        markNativeMethod("reverseBits"  , VOID, THIS);
        markNativeMethod("reverseBytes" , VOID, THIS);
        markNativeMethod("stepsTo"      , THIS, INT );

        // @Op methods
        markNativeMethod("add"          , THIS, THIS);
        markNativeMethod("sub"          , THIS, THIS);
        markNativeMethod("mul"          , THIS, THIS);
        markNativeMethod("div"          , THIS, THIS);
        markNativeMethod("mod"          , THIS, THIS);
        markNativeMethod("neg"          , VOID, THIS);
        markNativeMethod("and"          , THIS, THIS);
        markNativeMethod("or"           , THIS, THIS);
        markNativeMethod("xor"          , THIS, THIS);
        markNativeMethod("not"          , VOID, THIS);
        markNativeMethod("shiftAllRight", INT, THIS);

        invalidateTypeInfo();
        }

    /**
     * @return a complimentary template (signed for unsigned and vice versa)
     */
    abstract protected xConstrainedInteger getComplimentaryTemplate();

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof IntConstant constInt)
            {
            return frame.pushStack(new JavaLong(getCanonicalClass(), constInt.getValue().getLong()));
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    protected int constructFromString(Frame frame, String sText, int iReturn)
        {
        PackedInteger pi;
        try
            {
            pi = xIntLiteral.parsePackedInteger(sText);
            }
        catch (NumberFormatException e)
            {
            return frame.raiseException(
                xException.illegalArgument(frame, "Invalid number \"" + sText + "\""));
            }

        if (f_fChecked)
            {
            int cBytes = f_fSigned ? pi.getSignedByteSize() : pi.getUnsignedByteSize();
            if (cBytes * 8 > f_cNumBits)
                {
                return frame.raiseException(
                    xException.outOfBounds(frame, "Overflow: " + sText));
                }
            }

        return convertLong(frame, pi.getLong(), iReturn, f_fChecked);
        }

    @Override
    protected int constructFromBytes(Frame frame, byte[] ab, int cBytes, int iReturn)
        {
        return cBytes == f_cNumBits / 8
            ? convertLong(frame, fromByteArray(ab, cBytes, f_fSigned), iReturn, f_fChecked)
            : frame.raiseException(
                xException.illegalArgument(frame, "Invalid byte count: " + cBytes));
        }

    @Override
    protected int constructFromBits(Frame frame, byte[] ab, int cBits, int iReturn)
        {
        return cBits == f_cNumBits
            ? convertLong(frame, fromByteArray(ab, cBits >>> 3, f_fSigned), iReturn, f_fChecked)
            : frame.raiseException(
                xException.illegalArgument(frame, "Invalid bit count: " + cBits));
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "magnitude":
                {
                assert f_fSigned;
                long l = ((JavaLong) hTarget).getValue();
                hTarget = getComplimentaryTemplate().makeJavaLong(l < 0 ? -l : l);
                return frame.assignValue(iReturn, hTarget);
                }

            case "digitCount":
                {
                long l = ((JavaLong) hTarget).getValue();

                if (l < 0)
                    {
                    l = -l;
                    }

                int cDigits = 19;
                if (l >= 0)
                    {
                    long n = 10;
                    for (cDigits = 1; cDigits < 19; ++cDigits)
                        {
                        if (l < n)
                            {
                            break;
                            }
                        n *= 10;
                        }
                    }

                return frame.assignValue(iReturn, xInt.makeHandle(cDigits));
                }

            case "bits":
                {
                long l = ((JavaLong) hTarget).getValue();

                return frame.assignValue(iReturn, xArray.makeBitArrayHandle(
                    toByteArray(l, f_cNumBits >>> 3), f_cNumBits, Mutability.Constant));
                }

            case "bitCount":
                {
                long l = ((JavaLong) hTarget).getValue();
                return frame.assignValue(iReturn, xInt.makeHandle(Long.bitCount(l)));
                }

            case "bitLength":
                return frame.assignValue(iReturn, xInt.makeHandle(f_cNumBits));

            case "leftmostBit":
                {
                long l = ((JavaLong) hTarget).getValue();
                return frame.assignValue(iReturn, makeJavaLong(Long.highestOneBit(l)));
                }

            case "rightmostBit":
                {
                long l = ((JavaLong) hTarget).getValue();
                return frame.assignValue(iReturn, makeJavaLong((Long.lowestOneBit(l))));
                }

            case "leadingZeroCount":
                {
                long l = ((JavaLong) hTarget).getValue();
                return frame.assignValue(iReturn, xInt.makeHandle((Long.numberOfLeadingZeros(l))));
                }

            case "trailingZeroCount":
                {
                long l = ((JavaLong) hTarget).getValue();
                return frame.assignValue(iReturn, xInt.makeHandle((Long.numberOfTrailingZeros(l))));
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

            case "and":
                return invokeAnd(frame, hTarget, hArg, iReturn);

            case "or":
                return invokeOr(frame, hTarget, hArg, iReturn);

            case "xor":
                return invokeXor(frame, hTarget, hArg, iReturn);

            case "not":
                return invokeCompl(frame, hTarget, iReturn);

            case "shiftLeft":
                return invokeShl(frame, hTarget, hArg, iReturn);

            case "shiftRight":
                return invokeShr(frame, hTarget, hArg, iReturn);

            case "shiftAllRight":
                return invokeShrAll(frame, hTarget, hArg, iReturn);

            case "rotateLeft":
                return invokeRotateL(frame, hTarget, hArg, iReturn);

            case "rotateRight":
                return invokeRotateR(frame, hTarget, hArg, iReturn);

            case "stepsTo":
                // the return value must be an Int!
                return xInt64.INSTANCE.invokeSub(frame, hArg, hTarget, iReturn);
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "toInt":
            case "toInt8":
            case "toInt16":
            case "toInt32":
            case "toInt64":
            case "toInt128":
            case "toUInt":
            case "toUInt8":
            case "toUInt16":
            case "toUInt32":
            case "toUInt64":
            case "toUInt128":
            case "toFloat16":
            case "toFloat32":
            case "toFloat64":
            case "toIntN":
            case "toUIntN":
            case "toFloatN":
            case "toDecN":
            case "toChar":
                {
                TypeConstant  typeRet  = method.getReturn(0).getType();
                ClassTemplate template = f_container.getTemplate(typeRet);

                if (template == this)
                    {
                    return frame.assignValue(iReturn, hTarget);
                    }

                boolean fTruncate = ahArg.length > 0 && ahArg[0] == xBoolean.TRUE;
                boolean fChecked  = f_fChecked && !fTruncate;
                if (template instanceof xIntBase templateTo)
                    {
                    long l = ((JavaLong) hTarget).getValue();
                    if (!f_fSigned && l < 0)
                        {
                        // positive value that doesn't fit 64 bits
                        return frame.assignValue(iReturn,
                            templateTo.makeLongLong(new LongLong(l, 0L)));
                        }
                    else
                        {
                        return frame.assignValue(iReturn, templateTo.makeLong(l));
                        }
                    }

                if (template instanceof xConstrainedInteger templateTo)
                    {
                    long lValue = ((JavaLong) hTarget).getValue();

                    // there is one overflow case that needs to be handled here: UInt64 -> Int*
                    if (fChecked && lValue < 0 && this instanceof xUInt64)
                        {
                        return templateTo.overflow(frame);
                        }

                    return templateTo.convertLong(frame, lValue, iReturn, fChecked);
                    }

                if (template instanceof xUnconstrainedInteger templateTo)
                    {
                    PackedInteger piValue = PackedInteger.valueOf(((JavaLong) hTarget).getValue());
                    return frame.assignValue(iReturn, templateTo.makeInt(piValue));
                    }

                if (template instanceof BaseBinaryFP templateTo)
                    {
                    long lValue = ((JavaLong) hTarget).getValue();

                    return templateTo.convertLong(frame, lValue, iReturn);
                    }

                if (template instanceof BaseInt128 templateTo)
                    {
                    long lValue = ((JavaLong) hTarget).getValue();

                    if (fChecked && f_fSigned && lValue < 0 && !templateTo.f_fSigned)
                        {
                        // cannot assign negative value to the unsigned type
                        return overflow(frame);
                        }

                    return templateTo.convertLong(frame, lValue, iReturn);
                    }

                if (template instanceof xChar)
                    {
                    long l = ((JavaLong) hTarget).getValue();
                    if (l < 0 || l > 0x10_FFFF)
                        {
                        if (fChecked)
                            {
                            return overflow(frame);
                            }
                        l &= 0x0F_FFFF;
                        }
                    return frame.assignValue(iReturn, xChar.makeHandle(l));
                    }

                break;
                }

            case "neg":
                return invokeNeg(frame, hTarget, iReturn);

            case "reverseBits":
            case "reverseBytes":
                throw new UnsupportedOperationException("subclass implementation required for " + method.getName());

            case "truncate":
                {
                long lValue = ((JavaLong) hTarget ).getValue();
                long cBits  = ((JavaLong) ahArg[0]).getValue();
                if (cBits < 0 || cBits > f_cNumBits)
                    {
                    return frame.raiseException(xException.outOfBounds(frame, cBits, f_cNumBits));
                    }

                if (cBits == 0)
                    {
                    lValue = 0;
                    }
                else if (cBits != f_cNumBits)
                    {
                    lValue = lValue & (0xFFFFFFFFFFFFFFFFL >>> (64-cBits));
                    }

                return frame.assignValue(iReturn, makeJavaLong(lValue));
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 + l2;

        if (f_fChecked && (((l1 ^ lr) & (l2 ^ lr)) << f_cAddCheckShift) < 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 - l2;

        if (f_fChecked && (((l1 ^ l2) & (l1 ^ lr)) << f_cAddCheckShift) < 0)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();
        long lr = l1 * l2;

        if (f_fChecked)
            {
            long a1 = Math.abs(l1);
            long a2 = Math.abs(l2);
            if ((a1 | a2) >>> f_cMulCheckShift != 0)
                {
                // see Math.multiplyExact()
                if (((l2 != 0) && (lr / l2 != l1)) ||
                        (l1 == f_cMinValue && l2 == -1) ||
                        lr > f_cMaxValue || lr < f_cMinValue)
                    {
                    return overflow(frame);
                    }
                }
            }


        return frame.assignValue(iReturn, makeJavaLong(lr));
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (f_fChecked && (!f_fSigned || l == f_cMinValue))
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(-l));
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (f_fChecked && l == f_cMinValue)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(l - 1));
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        if (f_fChecked && l == f_cMaxValue)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(l + 1));
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        if (l2 == 0)
            {
            return overflow(frame);
            }
        return frame.assignValue(iReturn, makeJavaLong(l1 / l2));
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        if (l2 == 0)
            {
            return overflow(frame);
            }

        long lMod = l1 % l2;
        if (f_fSigned && lMod != 0 && (lMod < 0) != (l2 < 0))
            {
            lMod += l2;
            assert (lMod < 0) == (l2 < 0);
            }

        return frame.assignValue(iReturn, makeJavaLong(lMod));
        }

    @Override
    public int invokeShl(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        return frame.assignValue(iReturn, makeJavaLong(l1 << l2));
        }

    @Override
    public int invokeShr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        return frame.assignValue(iReturn, makeJavaLong(l1 >> l2));
        }

    @Override
    public int invokeShrAll(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        return frame.assignValue(iReturn, makeJavaLong(l1 >>> l2));
        }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        return frame.assignValue(iReturn, makeJavaLong(l1 & l2));
        }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        return frame.assignValue(iReturn, makeJavaLong(l1 | l2));
        }

    @Override
    public int invokeXor(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        return frame.assignValue(iReturn, makeJavaLong(l1 ^ l2));
        }

    @Override
    public int invokeDivRem(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn)
        {
        long l1 = ((JavaLong) hTarget).getValue();
        long l2 = ((JavaLong) hArg).getValue();

        return frame.assignValues(aiReturn, makeJavaLong(l1 / l2), makeJavaLong(l1 % l2));
        }

    @Override
    public int invokeCompl(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        return frame.assignValue(iReturn, makeJavaLong(~l));
        }


    // ----- comparison support --------------------------------------------------------------------

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return frame.assignValue(iReturn, xOrdered.makeHandle(f_fSigned
                ? Long.compare(h1.getValue(), h2.getValue())
                : Long.compareUnsigned(h1.getValue(), h2.getValue())));
        }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        return ((JavaLong) hValue1).getValue() == ((JavaLong) hValue2).getValue();
        }

    @Override
    public int buildHashCode(Frame frame, TypeComposition clazz, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        return frame.assignValue(iReturn, xInt64.makeHandle(l));
        }


    // ----- type specific -------------------------------------------------------------------------

    protected int invokeRotateL(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();
        int  c  = (int) (((JavaLong) hArg).getValue() % f_cNumBits);

        long lHead = l << c;
        long lTail = l >>> (f_cNumBits - c);

        return frame.assignValue(iReturn, makeJavaLong(lHead | lTail));
        }

    protected int invokeRotateR(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();
        int  c  = (int) (((JavaLong) hArg).getValue() % f_cNumBits);

        long lHead = l << (f_cNumBits - c);
        long lTail = l >>> c;

        return frame.assignValue(iReturn, makeJavaLong(lHead | lTail));
        }

    /**
     * Convert a PackedInteger value into a handle for the type represented by this template.
     *
     * Note: this method can throw an Overflow since the "source" is either IntLiteral or UInt64.
     *
     * @return one of the {@link Op#R_NEXT} or {@link Op#R_EXCEPTION} values
     */
    public int convertLong(Frame frame, PackedInteger piValue, boolean fChecked, int iReturn)
        {
        return piValue.isBig()
            ? overflow(frame)
            : convertLong(frame, piValue.getLong(), iReturn, fChecked);
        }

    /**
     * Convert a long value into a handle for the type represented by this template.
     *
     * @param fCheck  pass true to check the value's range
     *
     * @return one of the {@link Op#R_NEXT} or {@link Op#R_EXCEPTION} values
     */
    public int convertLong(Frame frame, long lValue, int iReturn, boolean fCheck)
        {
        if (fCheck && f_cNumBits != 64 && (lValue < f_cMinValue || lValue > f_cMaxValue))
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeJavaLong(lValue));
        }

    /**
     * Create a JavaLong handle for the type represented by this template.
     *
     * @param lValue  the underlying long value
     *
     * @return the corresponding handle
     */
    public JavaLong makeJavaLong(long lValue)
        {
        if (f_cNumBits < 64)
            {
            lValue &= f_lValueMask;
            if (lValue > f_cMaxValue)
                {
                lValue -= (f_lValueMask + 1);
                }
            }
        return new JavaLong(getCanonicalClass(), lValue);
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Produce an array of bytes for the specified long value.
     *
     * @param l       the long value
     * @param cBytes  the number of bytes to preserve
     *
     * @return the byte array
     */
    static public byte[] toByteArray(long l, int cBytes)
        {
        return switch (cBytes)
            {
            case 8 -> new byte[]
                {
                (byte) (l >> 56),
                (byte) (l >> 48),
                (byte) (l >> 40),
                (byte) (l >> 32),
                (byte) (l >> 24),
                (byte) (l >> 16),
                (byte) (l >> 8),
                (byte) l,
                };

            case 4 -> new byte[]
                {
                (byte) (l >> 24),
                (byte) (l >> 16),
                (byte) (l >> 8),
                (byte) l,
                };

            case 2 -> new byte[]
                {
                (byte) (l >> 8),
                (byte) l,
                };

            case 1 -> new byte[]
                {
                (byte) l,
                };

            default -> throw new IllegalStateException();
            };
        }

    /**
     * Copy the bytes of the specified long value into the specified array.
     *
     * @param l   the long value
     * @param ab  the byte array to copy into
     * @param of  the offset to start copying at
     */
    static public void copyAsBytes(long l, byte[] ab, int of)
        {
        for (int i = 0, cShift = 56; i < 8; i++, cShift-=8)
            {
            ab[of+i] = (byte) (l >> cShift);
            }
        }

    /**
     * Produce a long value from the specified byte array.
     *
     * @param aBytes   the byte array
     * @param cBytes   the number of bytes to use
     * @param fSigned  true if the value is a signed value
     *
     * @return the long value
     */
    static public long fromByteArray(byte[] aBytes, int cBytes, boolean fSigned)
        {
        return fromByteArray(aBytes, 0, cBytes, fSigned);
        }

    /**
     * Produce a long value from the specified byte array.
     *
     * @param aBytes   the byte array
     * @param of       the offset of the first byte to use
     * @param cBytes   the number of bytes to use
     * @param fSigned  true if the value is a signed value
     *
     * @return the long value
     */
    static public long fromByteArray(byte[] aBytes, int of, int cBytes, boolean fSigned)
        {
        long l = fSigned & aBytes[cBytes-1] < 0 ? -1 : 0;
        for (int i = of; i < cBytes; i++)
            {
            l = l << 8 | (aBytes[i] & 0xFF);
            }
        return l;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected final long f_cMinValue;
    protected final long f_cMaxValue;
    protected final int  f_cNumBits;
    protected final int  f_cAddCheckShift;
    protected final int  f_cMulCheckShift;
    protected final long f_lValueMask;

    protected final boolean f_fChecked;
    protected final boolean f_fSigned;
    }