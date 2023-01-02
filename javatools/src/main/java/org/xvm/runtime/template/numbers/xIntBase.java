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
        if (cBytes > 8)
            {
            long lHi = xConstrainedInteger.fromByteArray(ab, 8, Math.min(8, cBytes -8), false);
            return frame.assignValue(iReturn, makeLongLong(new LongLong(lLo, lHi)));
            }
        return frame.assignValue(iReturn, makeLong(lLo));
        }

    @Override
    protected int constructFromBits(Frame frame, byte[] ab, int cBits, int iReturn)
        {
        if (cBits > 128)
            {
            return frame.raiseException(
                xException.illegalArgument(frame, "Invalid bit count: " + cBits));
            }

        int cBytes;
        if (cBits % 8 == 0)
            {
            cBytes = cBits >>>3;
            }
        else
            {
            // TODO GG: clone and zero-out the tail
            cBytes = (cBits + 7) >>> 3;
            }
        return constructFomBytesImpl(frame, ab, cBytes, iReturn);
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "bits":
                {
                if (hTarget instanceof JavaLong hLong)
                    {
                    long l     = hLong.getValue();
                    int  cBits = 64;

                    return frame.assignValue(iReturn, xArray.makeBitArrayHandle(
                        xConstrainedInteger.toByteArray(l, cBits >>> 3),
                            cBits, xArray.Mutability.Constant));
                    }
                else
                    {
                    LongLongHandle hLong2 = (LongLongHandle) hTarget;
                    // TODO
                    throw new UnsupportedOperationException("bits");
                    }
                }

            case "leftmostBit":
                {
                if (hTarget instanceof JavaLong hLong)
                    {
                    long l = hLong.getValue();

                    return frame.assignValue(iReturn, xInt.makeHandle(Long.highestOneBit(l)));
                    }
                else
                    {
                    LongLong ll = ((LongLongHandle) hTarget).getValue();
                    long     lH = ll.getHighValue();

                    return frame.assignValue(iReturn, xInt.makeHandle(lH == 0
                        ? Long.highestOneBit(ll.getLowValue())
                        : Long.highestOneBit(lH) + 64));
                    }
                }

            case "rightmostBit":
                {
                if (hTarget instanceof JavaLong hLong)
                    {
                    long l = hLong.getValue();

                    return frame.assignValue(iReturn, xInt.makeHandle(Long.lowestOneBit(l)));
                    }
                else
                    {
                    LongLong ll   = ((LongLongHandle) hTarget).getValue();
                    long     lLow = ll.getLowValue();

                    return frame.assignValue(iReturn, xInt.makeHandle(lLow == 0
                        ? 64 + Long.lowestOneBit(ll.getHighValue())
                        : Long.lowestOneBit(lLow)));
                    }
                }

            case "leadingZeroCount":
                {
                if (hTarget instanceof JavaLong hLong)
                    {
                    long l = hLong.getValue();

                    return frame.assignValue(iReturn, xInt.makeHandle(Long.numberOfLeadingZeros(l)));
                    }
                else
                    {
                    LongLong ll = ((LongLongHandle) hTarget).getValue();
                    long     lH = ll.getHighValue();

                    return frame.assignValue(iReturn, xInt.makeHandle(lH == 0
                        ? 64 + Long.numberOfLeadingZeros(ll.getLowValue())
                        : Long.numberOfLeadingZeros(lH)));
                    }
                }

            case "trailingZeroCount":
                {
                if (hTarget instanceof JavaLong hLong)
                    {
                    long l = hLong.getValue();

                    return frame.assignValue(iReturn, xInt.makeHandle(Long.numberOfTrailingZeros(l)));
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
            case "abs":
                return invokeAbs(frame, hTarget, iReturn);

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

                if (template == getComplimentaryTemplate())
                    {
                    if (hTarget instanceof JavaLong hLong)
                        {
                        return frame.assignValue(iReturn,
                                ((xIntBase) template).makeLong(hLong.getValue()));
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
                    if (hTarget instanceof JavaLong hLong)
                        {
                        lValue = hLong.getValue();
                        }
                    else
                        {
                        LongLong ll = ((LongLongHandle) hTarget).getValue();
                        if (templateTo.f_fChecked && !ll.isSmall(f_fSigned))
                            {
                            return overflow(frame);
                            }
                        lValue = ll.getLowValue();
                        }
                    return templateTo.convertLong(frame, lValue, iReturn, true);
                    }

                if (template instanceof xUnconstrainedInteger templateTo)
                    {
                    PackedInteger piValue = hTarget instanceof JavaLong hLong
                            ? PackedInteger.valueOf(hLong.getValue())
                            : new PackedInteger(((LongLongHandle) hTarget).getValue().toBigInteger());
                    return frame.assignValue(iReturn, templateTo.makeInt(piValue));
                    }

                if (template instanceof BaseBinaryFP templateTo)
                    {
                    long lValue;
                    if (hTarget instanceof JavaLong hLong)
                        {
                        lValue = hLong.getValue();
                        }
                    else
                        {
                        LongLong ll = ((LongLongHandle) hTarget).getValue();
                        if (!ll.isSmall(f_fSigned))
                            {
                            return overflow(frame);
                            }
                        lValue = ll.getLowValue();
                        }

                    return templateTo.convertLong(frame, lValue, iReturn);
                    }

                if (template instanceof BaseInt128 templateTo)
                    {
                    if (hTarget instanceof JavaLong hLong)
                        {
                        long lValue = hLong.getValue();

                        if (lValue < 0 && f_fSigned && !templateTo.f_fSigned)
                            {
                            // cannot assign negative value to the unsigned type
                            return overflow(frame);
                            }

                        return templateTo.convertLong(frame, lValue, iReturn);
                        }
                    else
                        {
                        LongLong ll = ((LongLongHandle) hTarget).getValue();
                        if (ll.signum() < 0 && !templateTo.f_fSigned)
                            {
                            // cannot assign negative value to the unsigned type
                            return overflow(frame);
                            }

                        return frame.assignValue(iReturn, templateTo.makeLongLong(ll));
                        }
                    }

                if (template instanceof xChar)
                    {
                    long lValue;
                    if (hTarget instanceof JavaLong hLong)
                        {
                        lValue = hLong.getValue();
                        }
                    else
                        {
                        LongLong ll = ((LongLongHandle) hTarget).getValue();
                        if (!ll.isSmall(f_fSigned))
                            {
                            return overflow(frame);
                            }
                        lValue = ll.getLowValue();
                        }

                    if (lValue < 0 || lValue > 0x10_FFFF)
                        {
                        return overflow(frame);
                        }
                    return frame.assignValue(iReturn, xChar.makeHandle(lValue));
                    }

                break;
                }

            case "neg":
                return invokeNeg(frame, hTarget, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    /**
     * Native implementation of "Number abs()".
     */
    abstract protected int invokeAbs(Frame frame, ObjectHandle hTarget, int iReturn);

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
                    LongLong llr = new LongLong(l1).add(new LongLong(l2));
                    return frame.assignValue(iReturn, makeLongLong(llr));
                    }

                return frame.assignValue(iReturn, makeLong(lr));
                }
            else
                {
                LongLong ll2 = ((LongLongHandle) hTarget).getValue();
                LongLong llr = new LongLong(l1).add(ll2);
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
                LongLong llr = ll1.add(new LongLong(l2));
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
                    LongLong llr = new LongLong(l1).sub(new LongLong(l2));
                    return frame.assignValue(iReturn, makeLongLong(llr));
                    }

                return frame.assignValue(iReturn, makeLong(lr));
                }
            else
                {
                LongLong ll2 = ((LongLongHandle) hTarget).getValue();
                LongLong llr = new LongLong(l1).sub(ll2);
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
                LongLong llr = ll1.sub(new LongLong(l2));
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
    protected boolean checkMulOverflow(long l1, long l2, long lr)
        {
        long a1 = Math.abs(l1);
        long a2 = Math.abs(l2);

        // see Math.multiplyExact()
        return (a1 | a2) >>> 31 != 0 &&
            ((l2 != 0) && (lr / l2 != l1) || (l1 == Long.MIN_VALUE && l2 == -1));
        }

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
                    LongLong llr = new LongLong(l1).mul(new LongLong(l2));
                    return frame.assignValue(iReturn, makeLongLong(llr));
                    }

                return frame.assignValue(iReturn, makeLong(lr));
                }
            else
                {
                LongLong ll2 = ((LongLongHandle) hTarget).getValue();
                LongLong llr = new LongLong(l1).mul(ll2);
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
                LongLong llr = ll1.mul(new LongLong(l2));
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
                        : new LongLong(l1).modUnsigned(ll2);
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
                        : ll1.modUnsigned(new LongLong(l2));
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
    abstract public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn);

    @Override
    abstract public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn);


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
                LongLong ll2 = ((LongLongHandle) hValue1).getValue();
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
                LongLong ll2 = ((LongLongHandle) hValue1).getValue();
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
                LongLong ll1 = new LongLong(l1);
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
                LongLong ll2 = new LongLong(h2.getValue());
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

    public JavaLong makeLong(long lValue)
        {
        return new JavaLong(getCanonicalClass(), lValue);
        }

    public LongLongHandle makeLongLong(LongLong ll)
        {
        return new LongLongHandle(getCanonicalClass(), ll);
        }

    public ObjectHandle makeHandle(LongLong ll)
        {
        return ll.isSmall(f_fSigned) ? makeLong(ll.getLowValue()) : makeLongLong(ll);
        }

    protected final boolean f_fSigned;
    }