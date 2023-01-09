package org.xvm.runtime.template.numbers;


import java.math.BigInteger;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;

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

import org.xvm.runtime.template.numbers.BaseInt128.LongLongHandle;

import org.xvm.runtime.template.text.xChar;

import org.xvm.util.PackedInteger;


/**
 * Native Int support.
 */
public abstract class xIntBase
        extends xIntNumber
    {
    public xIntBase(Container container, ClassStructure structure, boolean fSigned)
        {
        super(container, structure, false);

        f_fSigned = fSigned;
        }

    /**
     * @return a complimentary template (signed for unsigned and vice versa)
     */
    abstract protected xIntBase getComplimentaryTemplate();

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
            PackedInteger piValue = constInt.getValue();

            return frame.pushStack(piValue.isBig()
                    ? makeLongLong(LongLong.fromBigInteger(piValue.getBigInteger()))
                    : makeLong(piValue.getLong()));
            }

        return super.createConstHandle(frame, constant);
        }

    @Override
    protected int constructFromString(Frame frame, String sText, int iReturn)
        {
        BigInteger big;
        try
            {
            big = xIntLiteral.parseBigInteger(sText);
            }
        catch (NumberFormatException e)
            {
            return frame.raiseException(
                xException.illegalArgument(frame, "Invalid number \"" + sText + "\""));
            }

        int cBits = big.bitLength();
        if (cBits > 127)
            {
            return frame.raiseException(
                xException.outOfBounds(frame, "Overflow: " + sText));
            }

        long lLo = big.longValue();
        if (cBits < 64)
            {
            return frame.assignValue(iReturn, makeLong(lLo));
            }

        long lHi = big.shiftRight(64).longValue();
        return frame.assignValue(iReturn, makeLongLong(new LongLong(lLo, lHi)));
        }

    @Override
    protected int constructFromBytes(Frame frame, byte[] ab, int cBytes, int iReturn)
        {
        if (cBytes > 16)
            {
            return frame.raiseException(
                xException.illegalArgument(frame, "Invalid byte count: " + cBytes));
            }

        return constructFomBytesImpl(frame, ab, cBytes, iReturn);
        }

    private int constructFomBytesImpl(Frame frame, byte[] ab, int cBytes, int iReturn)
        {
        long lLo = xConstrainedInteger.fromByteArray(ab, 0, Math.min(8, cBytes), false);
        if (cBytes <= 8)
            {
            return frame.assignValue(iReturn, lLo < 0 && f_fSigned
                ? makeLongLong(new LongLong(lLo, 0L)) // positive value that doesn't fit 64 bits
                : makeLong(lLo));
            }

        long lHi = xConstrainedInteger.fromByteArray(ab, 8, Math.min(8, cBytes -8), f_fSigned);
        if (!f_fSigned && lHi < 0)
            {
            return frame.raiseException(
                xException.outOfBounds(frame, "UInt128 only allows 127 bits"));
            }
        return frame.assignValue(iReturn, makeLongLong(new LongLong(lLo, lHi)));
        }

    @Override
    protected int constructFromBits(Frame frame, byte[] ab, int cBits, int iReturn)
        {
        if (cBits > 128)
            {
            return frame.raiseException(
                xException.illegalArgument(frame, "Invalid bit count: " + cBits));
            }

        if (cBits % 8 == 0)
            {
            return constructFomBytesImpl(frame, ab, cBits >>> 3, iReturn);
            }

        int cBytes = (cBits + 7) >>> 3;
        int cShift = (cBytes << 3) - cBits;

        long lLo = xConstrainedInteger.fromByteArray(ab, 0, Math.min(8, cBytes), false);
        if (cBytes <= 8)
            {
            return frame.assignValue(iReturn, makeLong(lLo >>> cShift));
            }

        long lHi = xConstrainedInteger.fromByteArray(ab, 8, Math.min(8, cBytes -8), f_fSigned);
        if (!f_fSigned && lHi < 0 && cShift == 0)
            {
            return frame.raiseException(
                xException.outOfBounds(frame, "UInt128 only allows 127 bits"));
            }
        return frame.assignValue(iReturn, makeLongLong(new LongLong(lLo, lHi >>> cShift)));
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "bits":
                {
                if (hTarget instanceof JavaLong hL)
                    {
                    long l = hL.getValue();

                    return frame.assignValue(iReturn, xArray.makeBitArrayHandle(
                        xConstrainedInteger.toByteArray(l, 8), 64, xArray.Mutability.Constant));
                    }
                else
                    {
                    LongLong ll = ((LongLongHandle) hTarget).getValue();

                    return frame.assignValue(iReturn, xArray.makeBitArrayHandle(
                        BaseInt128.toByteArray(ll), 128, xArray.Mutability.Constant));
                    }
                }

            case "bitLength":
                return frame.assignValue(iReturn, xInt.makeHandle(128));

            case "bitCount":
                {
                if (hTarget instanceof JavaLong hL)
                    {
                    long l = hL.getValue();

                    return frame.assignValue(iReturn, xInt.makeHandle(Long.bitCount(l)));
                    }
                else
                    {
                    LongLong ll = ((LongLongHandle) hTarget).getValue();

                    return frame.assignValue(iReturn, xInt.makeHandle(
                        Long.bitCount(ll.getLowValue()) + Long.bitCount(ll.getHighValue())));
                    }
                }

            case "leftmostBit":
                {
                if (hTarget instanceof JavaLong hL)
                    {
                    long l = hL.getValue();
                    if (f_fSigned && l < 0)
                        {
                        return frame.raiseException(xException.outOfBounds(frame, "Negative value"));
                        }

                    return frame.assignValue(iReturn, xInt.makeHandle(Long.highestOneBit(l)));
                    }
                else
                    {
                    LongLong ll = ((LongLongHandle) hTarget).getValue();
                    long     lH = ll.getHighValue();

                    if (lH < 0)
                        {
                        return frame.raiseException(xException.outOfBounds(frame, "Negative value"));
                        }

                    return frame.assignValue(iReturn,
                            xInt.INSTANCE.makeHandle(new LongLong(0, Long.highestOneBit(lH))));
                    }
                }

            case "rightmostBit":
                {
                if (hTarget instanceof JavaLong hL)
                    {
                    long l = hL.getValue();

                    return frame.assignValue(iReturn, xInt.makeHandle(Long.lowestOneBit(l)));
                    }
                else
                    {
                    LongLong ll   = ((LongLongHandle) hTarget).getValue();
                    long     lLow = ll.getLowValue();

                    return frame.assignValue(iReturn, xInt.INSTANCE.makeHandle(lLow == 0
                            ? new LongLong(0, Long.lowestOneBit(ll.getHighValue()))
                            : new LongLong(Long.lowestOneBit(lLow), 0)));
                    }
                }

            case "trailingZeroCount":
                {
                if (hTarget instanceof JavaLong hL)
                    {
                    long l = hL.getValue();

                    return frame.assignValue(iReturn, l == 0
                            ? xInt.makeHandle(128)
                            : xInt.makeHandle(Long.numberOfTrailingZeros(l)));
                    }
                else
                    {
                    LongLong ll   = ((LongLongHandle) hTarget).getValue();
                    long     lLow = ll.getLowValue();

                    return frame.assignValue(iReturn, xInt.makeHandle(lLow == 0
                        ? 64 + Long.numberOfTrailingZeros(ll.getHighValue())
                        : Long.numberOfTrailingZeros(lLow)));
                    }
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

            case "neg":
                return invokeNeg(frame, hTarget, iReturn);

            case "and":
                return invokeAnd(frame, hTarget, hArg, iReturn);

            case "or":
                return invokeOr(frame, hTarget, hArg, iReturn);

            case "xor":
                return invokeXor(frame, hTarget, hArg, iReturn);

            case "shiftLeft":
                return invokeShl(frame, hTarget, hArg, iReturn);

            case "shiftRight":
                return invokeShr(frame, hTarget, hArg, iReturn);

            case "stepsTo":
                return invokeSub(frame, hArg, hTarget, iReturn);
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
            case "toUInt8":
            case "toUInt16":
            case "toUInt32":
            case "toUInt64":
            case "toUInt":
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
                if (template == getComplimentaryTemplate())
                    {
                    if (hTarget instanceof JavaLong hL)
                        {
                        return frame.assignValue(iReturn,
                                ((xIntBase) template).makeLong(hL.getValue()));
                        }
                    else
                        {
                        LongLong ll = ((LongLongHandle) hTarget).getValue();
                        return frame.assignValue(iReturn,
                                ((xIntBase) template).makeLongLong(ll));
                        }
                    }

                if (template instanceof xConstrainedInteger templateTo)
                    {
                    long lValue;
                    if (hTarget instanceof JavaLong hL)
                        {
                        lValue = hL.getValue();
                        }
                    else
                        {
                        LongLong ll = ((LongLongHandle) hTarget).getValue();
                        if (!fTruncate && !ll.isSmall(f_fSigned))
                            {
                            return overflow(frame);
                            }
                        lValue = ll.getLowValue();
                        }
                    return templateTo.convertLong(frame, lValue, iReturn, !fTruncate);
                    }

                if (template instanceof xUnconstrainedInteger templateTo)
                    {
                    PackedInteger piValue = hTarget instanceof JavaLong hL
                            ? PackedInteger.valueOf(hL.getValue())
                            : new PackedInteger(((LongLongHandle) hTarget).getValue().toBigInteger());
                    return frame.assignValue(iReturn, templateTo.makeInt(piValue));
                    }

                if (template instanceof BaseBinaryFP templateTo)
                    {
                    long lValue;
                    if (hTarget instanceof JavaLong hL)
                        {
                        lValue = hL.getValue();
                        }
                    else
                        {
                        LongLong ll = ((LongLongHandle) hTarget).getValue();
                        if (!fTruncate && !ll.isSmall(f_fSigned))
                            {
                            return overflow(frame);
                            }
                        lValue = ll.getLowValue();
                        }

                    return templateTo.convertLong(frame, lValue, iReturn);
                    }

                if (template instanceof BaseInt128 templateTo)
                    {
                    if (hTarget instanceof JavaLong hL)
                        {
                        long lValue = hL.getValue();

                        if (!fTruncate && lValue < 0 && f_fSigned && !templateTo.f_fSigned)
                            {
                            // cannot assign negative value to the unsigned type
                            return overflow(frame);
                            }

                        return templateTo.convertLong(frame, lValue, iReturn);
                        }
                    else
                        {
                        LongLong ll = ((LongLongHandle) hTarget).getValue();
                        if (!fTruncate && ll.signum() < 0 && !templateTo.f_fSigned)
                            {
                            // cannot assign negative value to the unsigned type
                            return overflow(frame);
                            }

                        return frame.assignValue(iReturn, templateTo.makeHandle(ll));
                        }
                    }

                if (template instanceof xChar)
                    {
                    long lValue;
                    if (hTarget instanceof JavaLong hL)
                        {
                        lValue = hL.getValue();
                        }
                    else
                        {
                        LongLong ll = ((LongLongHandle) hTarget).getValue();
                        if (!fTruncate && !ll.isSmall(f_fSigned))
                            {
                            return overflow(frame);
                            }
                        lValue = ll.getLowValue();
                        }

                    if (!fTruncate && lValue < 0 || lValue > 0x10_FFFF)
                        {
                        return overflow(frame);
                        }
                    return frame.assignValue(iReturn, xChar.makeHandle(lValue));
                    }

                break;
                }

            case "neg":
                return invokeNeg(frame, hTarget, iReturn);

            case "not":
                return invokeCompl(frame, hTarget, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    abstract public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn);

    /**
     * @return true iff the result of the long addition overflowed
     */
    abstract protected boolean checkAddOverflow(long l1, long l2, long lr);

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        if (hTarget instanceof JavaLong h1)
            {
            long l1 = h1.getValue();
            if (hArg instanceof JavaLong h2)
                {
                long l2 = h2.getValue();
                long lr = l1 + l2;
                if (checkAddOverflow(l1, l2, lr))
                    {
                    // long overflow
                    LongLong llr = new LongLong(l1, f_fSigned).add(
                                   new LongLong(l2, f_fSigned));
                    return frame.assignValue(iReturn, makeLongLong(llr));
                    }

                return frame.assignValue(iReturn, makeLong(lr));
                }
            else
                {
                LongLong ll2 = ((LongLongHandle) hTarget).getValue();
                LongLong llr = new LongLong(l1, f_fSigned).add(ll2);
                if (llr == LongLong.OVERFLOW)
                    {
                    return overflow(frame);
                    }
                return frame.assignValue(iReturn, makeHandle(llr));
                }
            }
        else
            {
            LongLong ll1 = ((LongLongHandle) hTarget).getValue();
            if (hArg instanceof JavaLong h2)
                {
                long     l2  = h2.getValue();
                LongLong llr = ll1.add(new LongLong(l2, f_fSigned));
                if (llr == LongLong.OVERFLOW)
                    {
                    return overflow(frame);
                    }
                return frame.assignValue(iReturn, makeHandle(llr));
                }
            else
                {
                LongLong ll2 = ((LongLongHandle) hTarget).getValue();
                LongLong llr = ll1.add(ll2);
                if (llr == LongLong.OVERFLOW)
                    {
                    return overflow(frame);
                    }
                return frame.assignValue(iReturn, makeHandle(llr));
                }
            }
        }

    /**
     * @return true iff the result of the long subtraction overflowed
     */
    abstract protected boolean checkSubOverflow(long l1, long l2, long lr);

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        if (hTarget instanceof JavaLong h1)
            {
            long l1 = h1.getValue();
            if (hArg instanceof JavaLong h2)
                {
                long l2 = h2.getValue();
                long lr = l1 - l2;

                if (checkSubOverflow(l1, l2, lr))
                    {
                    // long overflow
                    LongLong llr = new LongLong(l1, f_fSigned).sub(
                                   new LongLong(l2, f_fSigned));
                    return frame.assignValue(iReturn, makeLongLong(llr));
                    }

                return frame.assignValue(iReturn, makeLong(lr));
                }
            else
                {
                LongLong ll2 = ((LongLongHandle) hTarget).getValue();
                LongLong llr = new LongLong(l1, f_fSigned).sub(ll2);
                if (llr == LongLong.OVERFLOW)
                    {
                    return overflow(frame);
                    }
                return frame.assignValue(iReturn, makeHandle(llr));
                }
            }
        else
            {
            LongLong ll1 = ((LongLongHandle) hTarget).getValue();
            if (hArg instanceof JavaLong h2)
                {
                long     l2  = h2.getValue();
                LongLong llr = ll1.sub(new LongLong(l2, f_fSigned));
                if (llr == LongLong.OVERFLOW)
                    {
                    return overflow(frame);
                    }
                return frame.assignValue(iReturn, makeHandle(llr));
                }
            else
                {
                LongLong ll2 = ((LongLongHandle) hTarget).getValue();
                LongLong llr = ll1.sub(ll2);
                if (llr == LongLong.OVERFLOW)
                    {
                    return overflow(frame);
                    }
                return frame.assignValue(iReturn, makeHandle(llr));
                }
            }
        }

    /**
     * @return true iff the result of the long multiplication overflowed
     */
    abstract protected boolean checkMulOverflow(long l1, long l2, long lr);

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        if (hTarget instanceof JavaLong h1)
            {
            long l1 = h1.getValue();
            if (hArg instanceof JavaLong h2)
                {
                long l2 = h2.getValue();
                long lr = l1 * l2;
                if (checkMulOverflow(l1, l2, lr))
                    {
                    LongLong llr = new LongLong(l1, f_fSigned).mul(
                                   new LongLong(l2, f_fSigned));
                    return frame.assignValue(iReturn, makeLongLong(llr));
                    }

                return frame.assignValue(iReturn, makeLong(lr));
                }
            else
                {
                LongLong ll2 = ((LongLongHandle) hTarget).getValue();
                LongLong llr = new LongLong(l1, f_fSigned).mul(ll2);
                if (llr == LongLong.OVERFLOW)
                    {
                    return overflow(frame);
                    }
                return frame.assignValue(iReturn, makeHandle(llr));
                }
            }
        else
            {
            LongLong ll1 = ((LongLongHandle) hTarget).getValue();
            if (hArg instanceof JavaLong h2)
                {
                long     l2  = h2.getValue();
                LongLong llr = ll1.mul(new LongLong(l2, f_fSigned));
                if (llr == LongLong.OVERFLOW)
                    {
                    return overflow(frame);
                    }
                return frame.assignValue(iReturn, makeHandle(llr));
                }
            else
                {
                LongLong ll2 = ((LongLongHandle) hTarget).getValue();
                LongLong llr = ll1.mul(ll2);
                if (llr == LongLong.OVERFLOW)
                    {
                    return overflow(frame);
                    }
                return frame.assignValue(iReturn, makeHandle(llr));
                }
            }
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        if (hTarget instanceof JavaLong h1)
            {
            long l1 = h1.getValue();
            if (hArg instanceof JavaLong h2)
                {
                long l2 = h2.getValue();
                if (l2 == 0)
                    {
                    return overflow(frame);
                    }
                return frame.assignValue(iReturn, makeLong(l1 / l2));
                }
            else
                {
                return frame.assignValue(iReturn, makeLong(0));
                }
            }
        else
            {
            LongLong ll1 = ((LongLongHandle) hTarget).getValue();
            if (hArg instanceof JavaLong h2)
                {
                long l2 = h2.getValue();
                if (l2 == 0)
                    {
                    return overflow(frame);
                    }
                LongLong llr = f_fSigned
                        ? ll1.div(l2)
                        : ll1.divUnsigned(l2);
                return frame.assignValue(iReturn, makeHandle(llr));
                }
            else
                {
                LongLong ll2 = ((LongLongHandle) hTarget).getValue();
                if (ll2 == LongLong.ZERO)
                    {
                    return overflow(frame);
                    }
                LongLong llr = f_fSigned
                        ? ll1.div(ll2)
                        : ll1.divUnsigned(ll2);
                return frame.assignValue(iReturn, makeHandle(llr));
                }
            }
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        if (hTarget instanceof JavaLong h1)
            {
            long l1 = h1.getValue();
            if (hArg instanceof JavaLong h2)
                {
                long l2 = h2.getValue();
                if (l2 == 0)
                    {
                    return overflow(frame);
                    }
                long lR = l1 % l2;
                if (f_fSigned && lR != 0 && (lR < 0) != (l2 < 0))
                    {
                    lR += l2;
                    }
                return frame.assignValue(iReturn, makeLong(lR));
                }
            else
                {
                LongLong ll2 = ((LongLongHandle) hTarget).getValue();
                if (ll2 == LongLong.ZERO)
                    {
                    return overflow(frame);
                    }
                LongLong llr = f_fSigned
                        ? new LongLong(l1).mod(ll2)
                        : new LongLong(l1, false).modUnsigned(ll2);
                return frame.assignValue(iReturn, makeHandle(llr));
                }
            }
        else
            {
            LongLong ll1 = ((LongLongHandle) hTarget).getValue();
            if (hArg instanceof JavaLong h2)
                {
                long l2 = h2.getValue();
                if (l2 == 0)
                    {
                    return overflow(frame);
                    }
                LongLong llr = f_fSigned
                        ? ll1.mod(new LongLong(l2))
                        : ll1.modUnsigned(new LongLong(l2, false));
                return frame.assignValue(iReturn, makeHandle(llr));
                }
            else
                {
                LongLong ll2 = ((LongLongHandle) hTarget).getValue();
                if (ll2 == LongLong.ZERO)
                    {
                    return overflow(frame);
                    }
                LongLong llr = f_fSigned
                        ? ll1.mod(ll2)
                        : ll1.modUnsigned(ll2);
                return frame.assignValue(iReturn, makeHandle(llr));
                }
            }
        }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1, ll2;
        if (hTarget instanceof JavaLong h1)
            {
            long l1 = h1.getValue();
            if (hArg instanceof JavaLong h2)
                {
                return frame.assignValue(iReturn, makeLong(l1 & h2.getValue()));
                }

            ll1 = new LongLong(l1, f_fSigned);
            ll2 = ((LongLongHandle) hTarget).getValue();
            }
        else
            {
            LongLongHandle h1 = (LongLongHandle) hTarget;
            ll1 = h1.getValue();
            ll2 = hArg instanceof JavaLong h2
                    ? new LongLong(h2.getValue(), f_fSigned)
                    : h1.getValue();
            }
        return frame.assignValue(iReturn, makeHandle(ll1.and(ll2)));
        }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1, ll2;
        if (hTarget instanceof JavaLong h1)
            {
            long l1 = h1.getValue();
            if (hArg instanceof JavaLong h2)
                {
                return frame.assignValue(iReturn, makeLong(l1 | h2.getValue()));
                }

            ll1 = new LongLong(l1, f_fSigned);
            ll2 = ((LongLongHandle) hTarget).getValue();
            }
        else
            {
            LongLongHandle h1 = (LongLongHandle) hTarget;
            ll1 = h1.getValue();
            ll2 = hArg instanceof JavaLong h2
                    ? new LongLong(h2.getValue(), f_fSigned)
                    : h1.getValue();
            }
        return frame.assignValue(iReturn, makeHandle(ll1.or(ll2)));
        }

    @Override
    public int invokeXor(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1, ll2;
        if (hTarget instanceof JavaLong h1)
            {
            long l1 = h1.getValue();
            if (hArg instanceof JavaLong h2)
                {
                return frame.assignValue(iReturn, makeLong(l1 ^ h2.getValue()));
                }

            ll1 = new LongLong(l1, f_fSigned);
            ll2 = ((LongLongHandle) hTarget).getValue();
            }
        else
            {
            LongLongHandle h1 = (LongLongHandle) hTarget;
            ll1 = h1.getValue();
            ll2 = hArg instanceof JavaLong h2
                    ? new LongLong(h2.getValue(), f_fSigned)
                    : h1.getValue();
            }
        return frame.assignValue(iReturn, makeHandle(ll1.xor(ll2)));
        }

    @Override
    public int invokeCompl(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        if (hTarget instanceof JavaLong hL)
            {
            return frame.assignValue(iReturn, makeLong(~hL.getValue()));
            }
        else
            {
            LongLong ll = ((LongLongHandle) hTarget).getValue();
            return frame.assignValue(iReturn, makeLongLong(ll.complement()));
            }
        }

    @Override
    public int invokeShl(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        int c = (int) ((JavaLong) hArg).getValue();
        if (hTarget instanceof JavaLong hL)
            {
            return frame.assignValue(iReturn, makeLong(hL.getValue() << c));
            }
        else
            {
            LongLong ll = ((LongLongHandle) hTarget).getValue();
            return frame.assignValue(iReturn, makeLongLong(ll.shl(c)));
            }
        }

    @Override
    public int invokeShr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        int c = (int) ((JavaLong) hArg).getValue();
        if (hTarget instanceof JavaLong hL)
            {
            return frame.assignValue(iReturn, makeLong(hL.getValue() >> c));
            }
        else
            {
            LongLong ll = ((LongLongHandle) hTarget).getValue();
            return frame.assignValue(iReturn, makeLongLong(ll.shr(c)));
            }
        }

    @Override
    public int invokeShrAll(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        int c = (int) ((JavaLong) hArg).getValue();
        if (hTarget instanceof JavaLong hL)
            {
            return frame.assignValue(iReturn, makeLong(hL.getValue() >>> c));
            }
        else
            {
            LongLong ll = ((LongLongHandle) hTarget).getValue();
            return frame.assignValue(iReturn, makeLongLong(ll.ushr(c)));
            }
        }

    @Override
    abstract public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn);

    @Override
    abstract public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn);

    @Override
    public int buildHashCode(Frame frame, TypeComposition clazz, ObjectHandle hTarget, int iReturn)
        {
        if (hTarget instanceof JavaLong hL)
            {
            return frame.assignValue(iReturn, xInt64.makeHandle(hL.getValue()));
            }

        LongLong ll = ((LongLongHandle) hTarget).getValue();

        return frame.assignValue(iReturn, xInt64.makeHandle(ll.hashCode()));
        }


    // ----- comparison support --------------------------------------------------------------------

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        if (hValue1 instanceof JavaLong h1)
            {
            if (hValue2 instanceof JavaLong h2)
                {
                return frame.assignValue(iReturn,
                        xBoolean.makeHandle(h1.getValue() == h2.getValue()));
                }
            else
                {
                LongLong ll2 = ((LongLongHandle) hValue2).getValue();
                return frame.assignValue(iReturn, ll2.isSmall(f_fSigned)
                        ? xBoolean.makeHandle(h1.getValue() == ll2.getLowValue())
                        : xBoolean.FALSE);
                }
            }
        else
            {
            LongLong ll1 = ((LongLongHandle) hValue1).getValue();
            if (hValue2 instanceof JavaLong h2)
                {
                return frame.assignValue(iReturn, ll1.isSmall(f_fSigned)
                        ? xBoolean.makeHandle(ll1.getLowValue() == h2.getValue())
                        : xBoolean.FALSE);
                }
            else
                {
                LongLong ll2 = ((LongLongHandle) hValue2).getValue();
                return frame.assignValue(iReturn, xBoolean.makeHandle(ll1.equals(ll2)));
                }
            }
        }

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        if (hValue1 instanceof JavaLong h1)
            {
            long l1 = h1.getValue();
            if (hValue2 instanceof JavaLong h2)
                {
                return frame.assignValue(iReturn,
                        xOrdered.makeHandle(Long.compare(l1, h2.getValue())));
                }
            else
                {
                LongLong ll1 = new LongLong(l1, f_fSigned);
                LongLong ll2 = ((LongLongHandle) hValue2).getValue();
                return frame.assignValue(iReturn, xOrdered.makeHandle(
                    f_fSigned ? ll1.compare(ll2) : ll1.compareUnsigned(ll2)));
                }
            }
        else
            {
            LongLong ll1 = ((LongLongHandle) hValue1).getValue();
            if (hValue2 instanceof JavaLong h2)
                {
                LongLong ll2 = new LongLong(h2.getValue(), f_fSigned);
                return frame.assignValue(iReturn, xOrdered.makeHandle(
                    f_fSigned ? ll1.compare(ll2) : ll1.compareUnsigned(ll2)));
                }
            else
                {
                LongLong ll2 = ((LongLongHandle) hValue2).getValue();
                return frame.assignValue(iReturn, xOrdered.makeHandle(
                    f_fSigned ? ll1.compare(ll2) : ll1.compareUnsigned(ll2)));
                }
            }
        }

    /**
     * Create a handle for the specified LongLong value.
     */
    public ObjectHandle makeHandle(LongLong ll)
        {
        return ll.isSmall(f_fSigned) ? makeLong(ll.getLowValue()) : makeLongLong(ll);
        }

    protected JavaLong makeLong(long lValue)
        {
        return new JavaLong(getCanonicalClass(), lValue);
        }

    protected LongLongHandle makeLongLong(LongLong ll)
        {
        return new LongLongHandle(getCanonicalClass(), ll);
        }

    public final boolean f_fSigned;
    }