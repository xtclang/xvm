package org.xvm.runtime.template.numbers;


import java.math.BigInteger;

import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.collections.xArray;

import org.xvm.runtime.template.numbers.xIntLiteral.IntNHandle;

import org.xvm.runtime.template.text.xString;

import org.xvm.util.PackedInteger;


/**
 * Base class for IntN/UIntN integer types.
 */
public abstract class xUnconstrainedInteger
        extends xIntNumber
    {
    protected xUnconstrainedInteger(Container container, ClassStructure structure, boolean fSigned)
        {
        super(container, structure, false);

        f_fSigned = fSigned;
        }

    @Override
    public void initNative()
        {
        super.initNative();

        markNativeProperty("leadingZeroCount");

// TODO markNativeMethod("rotateLeft"   , INT , THIS);
// TODO markNativeMethod("rotateRight"  , INT , THIS);
// TODO markNativeMethod("retainLSBits" , INT , THIS);
// TODO markNativeMethod("retainMSBits" , INT , THIS);
// TODO markNativeMethod("reverseBits"  , VOID, THIS);
// TODO markNativeMethod("reverseBytes" , VOID, THIS);
// TODO markNativeMethod("stepsTo"      , THIS, INT );

        // @Op methods
        markNativeMethod("add"          , THIS, THIS);
        markNativeMethod("sub"          , THIS, THIS);
        markNativeMethod("mul"          , THIS, THIS);
        markNativeMethod("div"          , THIS, THIS);
        markNativeMethod("mod"          , THIS, THIS);
        markNativeMethod("neg"          , VOID, THIS);
// TODO markNativeMethod("and"          , THIS, THIS);
// TODO markNativeMethod("or"           , THIS, THIS);
// TODO markNativeMethod("xor"          , THIS, THIS);
// TODO markNativeMethod("not"          , VOID, THIS);
// TODO markNativeMethod("shiftLeft"    , INT, THIS);
// TODO markNativeMethod("shiftRight"   , INT, THIS);
// TODO markNativeMethod("shiftAllRight", INT, THIS);

        invalidateTypeInfo();
        }

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
            return frame.pushStack(makeInt(constInt.getValue()));
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
        return frame.assignValue(iReturn, makeInt(pi));
        }

    @Override
    protected int constructFromBytes(Frame frame, byte[] ab, int cBytes, int iReturn)
        {
        return frame.raiseException("TODO ");
        }

    @Override
    protected int constructFromBits(Frame frame, byte[] ab, int cBits, int iReturn)
        {
        return frame.raiseException("TODO ");
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "bits":
                {
                PackedInteger pi       = ((IntNHandle) hTarget).m_piValue;
                int           cb       = f_fSigned ? pi.getSignedByteSize() : pi.getUnsignedByteSize();
                byte[]        ab       = pi.getBigInteger().toByteArray();
                int           cbActual = ab.length;
                if (cb < cbActual)
                    {
                    cb = cbActual;
                    }
                else if (cb > cbActual)
                    {
                    byte[] abOld = ab;
                    ab = new byte[cb];
                    if ((abOld[0] & 0x80) != 0)
                        {
                        Arrays.fill(ab, 0, cb - cbActual, (byte) 0xFF);
                        }
                    System.arraycopy(abOld, 0, ab, cb - cbActual, cbActual);
                    }
                return frame.assignValue(iReturn, xArray.makeBitArrayHandle(ab, cb*8, xArray.Mutability.Constant));
                }

            case "bitCount":
                {
                PackedInteger pi = ((IntNHandle) hTarget).m_piValue;
                int cBits = pi.isBig() ? pi.getBigInteger().bitCount() : Long.bitCount(pi.getLong());
                return frame.assignValue(iReturn, xInt64.makeHandle(cBits));
                }

            case "bitLength":
                {
                PackedInteger pi    = ((IntNHandle) hTarget).m_piValue;
                int           cBits = pi.getBigInteger().bitLength();
                return frame.assignValue(iReturn, xInt64.makeHandle(cBits));
                }

            case "leftmostBit":
                {
                PackedInteger pi = ((IntNHandle) hTarget).m_piValue;
                if (pi.isBig())
                    {
                    int cBits = pi.getBigInteger().bitLength();
                    pi = new PackedInteger(BigInteger.ONE.shiftLeft(cBits));
                    }
                else
                    {
                    pi = PackedInteger.valueOf(Long.highestOneBit(pi.getLong()));
                    }
                return frame.assignValue(iReturn, makeInt(pi));
                }

            case "rightmostBit":
                {
                PackedInteger pi = ((IntNHandle) hTarget).m_piValue;
                if (pi.isBig())
                    {
                    int nBit = pi.getBigInteger().getLowestSetBit();
                    pi = new PackedInteger(BigInteger.ONE.shiftLeft(nBit));
                    }
                else
                    {
                    pi = PackedInteger.valueOf(Long.lowestOneBit(pi.getLong()));
                    }
                return frame.assignValue(iReturn, makeInt(pi));
                }

            case "leadingZeroCount":
                return frame.assignValue(iReturn, xInt64.makeHandle(0));

            case "trailingZeroCount":
                {
                PackedInteger pi = ((IntNHandle) hTarget).m_piValue;
                long          c;
                if (pi.isBig())
                    {
                    c = pi.getBigInteger().getLowestSetBit();
                    }
                else
                    {
                    c = Long.numberOfTrailingZeros(pi.getLong());
                    }
                return frame.assignValue(iReturn, xInt64.makeHandle(c));
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

            case "toInt8":
            case "toInt16":
            case "toInt32":
            case "toInt64":
            case "toInt128":
            case "toUInt8":
            case "toUInt16":
            case "toUInt32":
            case "toUInt64":
            case "toUInt128":
                {
                TypeConstant  typeRet  = method.getReturn(0).getType();
                PackedInteger pi       = ((IntNHandle) hTarget).getValue();
                xIntNumber    template = (xIntNumber) f_container.getTemplate(typeRet);
                long          lValue;

                if (template == this)
                    {
                    return frame.assignValue(iReturn, hTarget);
                    }

                boolean fCheckBounds = hArg == xBoolean.TRUE;

                if (template instanceof xConstrainedInteger templateTo)
                    {
                    if (fCheckBounds)
                        {
                        if (!templateTo.f_fSigned && pi.isNegative())
                            {
                            return templateTo.overflow(frame);
                            }
                        int cBytes = templateTo.f_fSigned
                            ? pi.getSignedByteSize()
                            : pi.getUnsignedByteSize();
                        if (cBytes * 8 > templateTo.f_cNumBits)
                            {
                            return templateTo.overflow(frame);
                            }
                        lValue = pi.getLong();
                        }
                    else
                        {
                        lValue = pi.isBig() ? pi.getBigInteger().longValue() : pi.getLong();
                        }

                    return templateTo.convertLong(frame, lValue, iReturn, fCheckBounds);
                    }

                if (template instanceof BaseInt128 templateTo)
                    {
                    if (fCheckBounds)
                        {
                        if (!templateTo.f_fSigned && pi.isNegative())
                            {
                            return templateTo.overflow(frame);
                            }
                        int cBytes = templateTo.f_fSigned
                            ? pi.getSignedByteSize()
                            : pi.getUnsignedByteSize();
                        if (cBytes > 16)
                            {
                            return templateTo.overflow(frame);
                            }
                        }

                    return frame.assignValue(iReturn, templateTo.makeHandle(
                        pi.isBig()
                            ? LongLong.fromBigInteger(pi.getBigInteger())
                            : new LongLong(pi.getLong(), templateTo.f_fSigned)));
                    }

                throw new IllegalStateException("Unsupported type " + typeRet);
                }
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "abs":
                {
                PackedInteger pi = ((IntNHandle) hTarget).getValue();
                return frame.assignValue(iReturn,
                        pi.compareTo(PackedInteger.ZERO) >= 0 ? hTarget : makeInt(pi.negate()));
                }

            case "neg":
                return invokeNeg(frame, hTarget, iReturn);

            case "toInt8":
            case "toInt16":
            case "toInt32":
            case "toInt64":
            case "toInt128":
            case "toUInt8":
            case "toUInt16":
            case "toUInt32":
            case "toUInt64":
            case "toUInt128":
                // default argument: checkBounds = False;
                return invokeNative1(frame, method, hTarget, xBoolean.FALSE, iReturn);

            case "toIntN":
                {
                ObjectHandle hResult = hTarget;
                if (!f_fSigned)
                    {
                    TypeConstant          typeRet  = method.getReturn(0).getType();
                    xUnconstrainedInteger template = (xUnconstrainedInteger) f_container.getTemplate(typeRet);
                    PackedInteger         pi       = ((IntNHandle) hTarget).getValue();
                    hResult = template.makeInt(pi);
                    }
                return frame.assignValue(iReturn, hResult);
                }

            case "toUIntN":
                {
                ObjectHandle hResult = hTarget;
                if (f_fSigned)
                    {
                    TypeConstant          typeRet  = method.getReturn(0).getType();
                    xUnconstrainedInteger template = (xUnconstrainedInteger) f_container.getTemplate(typeRet);
                    PackedInteger         pi       = ((IntNHandle) hTarget).getValue();
                    if (pi.isNegative())
                        {
                        return template.overflow(frame);
                        }

                    hResult = template.makeInt(pi);
                    }
                return frame.assignValue(iReturn, hResult);
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();
        PackedInteger pir = pi1.add(pi2);

        return frame.assignValue(iReturn, makeInt(pir));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();
        PackedInteger pir = pi1.sub(pi2);

        return frame.assignValue(iReturn, makeInt(pir));
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();
        PackedInteger pir = pi1.mul(pi2);

        return frame.assignValue(iReturn, makeInt(pir));
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        PackedInteger pi = ((IntNHandle) hTarget).getValue();

        return frame.assignValue(iReturn, makeInt(pi.negate()));
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        PackedInteger pi = ((IntNHandle) hTarget).getValue();

        return frame.assignValue(iReturn, makeInt(pi.previous()));
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        PackedInteger pi = ((IntNHandle) hTarget).getValue();

        return frame.assignValue(iReturn, makeInt(pi.next()));
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeInt(pi1.div(pi2)));
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();

        PackedInteger piMod = pi1.mod(pi2);
        if (piMod.compareTo(PackedInteger.ZERO) < 0)
            {
            piMod = piMod.add((pi2.compareTo(PackedInteger.ZERO) < 0 ? pi2.negate() : pi2));
            }

        return frame.assignValue(iReturn, makeInt(piMod));
        }

    @Override
    public int invokeShl(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeInt(pi1.shl(pi2)));
        }

    @Override
    public int invokeShr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeInt(pi1.shr(pi2)));
        }

    @Override
    public int invokeShrAll(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeInt(pi1.ushr(pi2)));
        }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeInt(pi1.and(pi2)));
        }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeInt(pi1.or(pi2)));
        }

    @Override
    public int invokeXor(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeInt(pi1.xor(pi2)));
        }

    @Override
    public int invokeDivRem(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();

        PackedInteger[] aQuoRem = pi1.divrem(pi2);
        return frame.assignValues(aiReturn, makeInt(aQuoRem[0]), makeInt(aQuoRem[1]));
        }

    @Override
    public int invokeCompl(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        PackedInteger pi = ((IntNHandle) hTarget).getValue();

        return frame.assignValue(iReturn, makeInt(pi.complement()));
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        PackedInteger pi = ((IntNHandle) hTarget).getValue();

        return frame.assignValue(iReturn, xString.makeHandle(pi.toString()));
        }


    // ----- comparison support --------------------------------------------------------------------

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        IntNHandle h1 = (IntNHandle) hValue1;
        IntNHandle h2 = (IntNHandle) hValue2;

        return frame.assignValue(iReturn, xOrdered.makeHandle(h1.getValue().compareTo(h2.getValue())));
        }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        return ((IntNHandle) hValue1).getValue().equals(((IntNHandle) hValue2).getValue());
        }

    @Override
    public int buildHashCode(Frame frame, TypeComposition clazz, ObjectHandle hTarget, int iReturn)
        {
        PackedInteger pi = ((IntNHandle) hTarget).getValue();

        return frame.assignValue(iReturn, xInt64.makeHandle(pi.hashCode()));
        }


    // ----- Object methods ------------------------------------------------------------------------

    /**
     * NOTE: we are using the IntNHandle for objects of UnconstrainedInteger types.
     */
    protected IntNHandle makeInt(PackedInteger pi)
        {
        return new IntNHandle(getCanonicalClass(), pi, null);
        }


    // ----- fields --------------------------------------------------------------------------------

    protected final boolean f_fSigned;
    }