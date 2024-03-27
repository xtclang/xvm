package org.xvm.runtime.template.numbers;


import java.math.BigInteger;

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

import org.xvm.runtime.template.text.xString;

import org.xvm.util.PackedInteger;


/**
 * Abstract base for Int128 and UInt128.
 */
public abstract class BaseInt128
        extends xIntNumber
    {
    public BaseInt128(Container container, ClassStructure structure, boolean fSigned)
        {
        super(container, structure, false);

        f_fSigned  = fSigned;
        f_fChecked = false;
        }

    @Override
    public void initNative()
        {
        super.initNative();

        markNativeProperty("leadingZeroCount");

        markNativeMethod("abs"          , null, null);

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
        markNativeMethod("shiftLeft"    , INT, THIS);
        markNativeMethod("shiftRight"   , INT, THIS);
        markNativeMethod("shiftAllRight", INT, THIS);

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
            PackedInteger piValue = constInt.getValue();

            return frame.pushStack(makeHandle(
                LongLong.fromBigInteger(piValue.getBigInteger())));
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
            return convertLong(frame, lLo, iReturn, f_fSigned);
            }

        long lHi = big.shiftRight(64).longValue();
        return frame.assignValue(iReturn, makeHandle(new LongLong(lLo, lHi)));
        }

    @Override
    protected int constructFromBytes(Frame frame, byte[] ab, int cBytes, int iReturn)
        {
        if (cBytes != 16)
            {
            return frame.raiseException(
                xException.illegalArgument(frame, "Invalid byte count: " + cBytes));
            }

        long lLo = xConstrainedInteger.fromByteArray(ab, 0, 8, false);
        long lHi = xConstrainedInteger.fromByteArray(ab, 8, 8, f_fSigned);
        return frame.assignValue(iReturn, makeHandle(new LongLong(lLo, lHi)));
        }

    @Override
    protected int constructFromBits(Frame frame, byte[] ab, int cBits, int iReturn)
        {
        if (cBits != 128)
            {
            return frame.raiseException(
                xException.illegalArgument(frame, "Invalid bit count: " + cBits));
            }

        long lLo = xConstrainedInteger.fromByteArray(ab, 0, 8, false);
        long lHi = xConstrainedInteger.fromByteArray(ab, 8, 8, f_fSigned);
        return frame.assignValue(iReturn, makeHandle(new LongLong(lLo, lHi)));
        }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn)
        {
        switch (sPropName)
            {
            case "bits":
                {
                LongLong ll = ((LongLongHandle) hTarget).getValue();
                return frame.assignValue(iReturn, xArray.makeBitArrayHandle(
                    toByteArray(ll), 128, Mutability.Constant));
                }

            case "bitCount":
                {
                LongLong ll = ((LongLongHandle) hTarget).getValue();
                return frame.assignValue(iReturn, xInt64.makeHandle(
                    Long.bitCount(ll.getLowValue()) + Long.bitCount(ll.getHighValue())));
                }

            case "bitLength":
                return frame.assignValue(iReturn, xInt64.makeHandle(128));

            case "leftmostBit":
                {
                LongLong ll = ((LongLongHandle) hTarget).getValue();
                long     lH = ll.getHighValue();

                return frame.assignValue(iReturn, xInt64.makeHandle(lH == 0
                    ? Long.highestOneBit(ll.getLowValue())
                    : Long.highestOneBit(lH) + 64));
                }

            case "rightmostBit":
                {
                LongLong ll   = ((LongLongHandle) hTarget).getValue();
                long     lLow = ll.getLowValue();

                return frame.assignValue(iReturn, xInt64.makeHandle(lLow == 0
                    ? 64 + Long.lowestOneBit(ll.getHighValue())
                    : Long.lowestOneBit(lLow)));
                }

            case "leadingZeroCount":
                {
                LongLong ll = ((LongLongHandle) hTarget).getValue();
                long     lH = ll.getHighValue();

                return frame.assignValue(iReturn, xInt64.makeHandle(lH == 0
                    ? 64 + Long.numberOfLeadingZeros(ll.getLowValue())
                    : Long.numberOfLeadingZeros(lH)));
                }

            case "trailingZeroCount":
                {
                LongLong ll   = ((LongLongHandle) hTarget).getValue();
                long     lLow = ll.getLowValue();

                return frame.assignValue(iReturn, xInt64.makeHandle(lLow == 0
                    ? 64 + Long.numberOfTrailingZeros(ll.getHighValue())
                    : Long.numberOfTrailingZeros(lLow)));
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

            case "not":
                return invokeCompl(frame, hTarget, iReturn);

            case "shiftLeft":
                return invokeShl(frame, hTarget, hArg, iReturn);

            case "shiftRight":
                return invokeShr(frame, hTarget, hArg, iReturn);

            case "shiftAllRight":
                return invokeShrAll(frame, hTarget, hArg, iReturn);

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
            case "toIntN":
            case "toUIntN":
                {
                TypeConstant  typeRet  = method.getReturn(0).getType();
                ClassTemplate template = f_container.getTemplate(typeRet);

                if (template == this)
                    {
                    return frame.assignValue(iReturn, hTarget);
                    }

                boolean  fCheckBounds = hArg == xBoolean.TRUE;
                LongLong llValue      = ((LongLongHandle) hTarget).getValue();

                if (template instanceof xConstrainedInteger templateTo)
                    {
                    return convertToConstrainedType(frame, templateTo, llValue, fCheckBounds, iReturn);
                    }

                if (template instanceof xUnconstrainedInteger templateTo)
                    {
                    PackedInteger piValue = new PackedInteger(f_fSigned
                            ? llValue.toBigInteger()
                            : llValue.toUnsignedBigInteger());
                    // there's one special case that produces an overflow exception, even though
                    // there is no "checkBounds" parameter on toUIntN()
                    return !templateTo.f_fSigned && piValue.isNegative()
                            ? overflow(frame)
                            : frame.assignValue(iReturn, templateTo.makeInt(piValue));
                    }

                if (template instanceof BaseInt128 templateTo)
                    {
                    if (fCheckBounds)
                        {
                        if (f_fSigned && llValue.signum() < 0 && !templateTo.f_fSigned)
                            {
                            // cannot assign negative value to the unsigned type
                            return overflow(frame);
                            }

                        if (!f_fSigned && llValue.getHighValue() < 0 && templateTo.f_fSigned)
                            {
                            // too large value for signed LongLong
                            return overflow(frame);
                            }
                        }
                    return frame.assignValue(iReturn, templateTo.makeHandle(llValue));
                    }
                break;
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
                return invokeAbs(frame, hTarget, iReturn);

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
            case "toIntN":
            case "toUIntN":
                // default argument: checkBounds = False;
                return invokeNative1(frame, method, hTarget, xBoolean.FALSE, iReturn);
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    protected int invokeAbs(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        if (!f_fSigned)
            {
            return frame.assignValue(iReturn, hTarget);
            }

        LongLong ll = ((LongLongHandle) hTarget).getValue();

        if (ll.signum() >= 0)
            {
            return frame.assignValue(iReturn, hTarget);
            }

        LongLong llr = ll.negate();
        if (f_fChecked && llr == LongLong.OVERFLOW)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(llr));
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        if (f_fChecked && !f_fSigned)
            {
            return overflow(frame);
            }

        LongLong ll = ((LongLongHandle) hTarget).getValue();
        LongLong llr = ll.negate();

        if (f_fChecked && llr == LongLong.OVERFLOW)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(llr));
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();
        LongLong llr = f_fSigned ? ll1.add(ll2) : ll1.addUnsigned(ll2);

        if (f_fChecked && llr == LongLong.OVERFLOW)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(llr));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();
        LongLong llr = f_fSigned ?  ll1.sub(ll2) : ll1.subUnassigned(ll2);

        if (f_fChecked && llr == LongLong.OVERFLOW)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(llr));
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();
        LongLong llr = f_fSigned ? ll1.mul(ll2) : ll1.mulUnsigned(ll2);

        if (f_fChecked && llr == LongLong.OVERFLOW)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(llr));
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        LongLong ll = ((LongLongHandle) hTarget).getValue();
        LongLong llr = ll.prev(f_fSigned);

        if (f_fChecked && llr == LongLong.OVERFLOW)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(llr));
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        LongLong ll = ((LongLongHandle) hTarget).getValue();
        LongLong llr = ll.next(f_fSigned);

        if (f_fChecked && llr == LongLong.OVERFLOW)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(llr));
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();

        LongLong llDiv = f_fSigned
            ? ll1.div(ll2)
            : ll1.divUnsigned(ll2);

        if (f_fChecked && llDiv == LongLong.OVERFLOW)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(llDiv));
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();

        LongLong llMod = f_fSigned
            ? ll1.mod(ll2)
            : ll1.modUnsigned(ll2);

        if (f_fChecked && llMod == LongLong.OVERFLOW)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeHandle(llMod));
        }

    @Override
    public int invokeDivRem(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();

        LongLong[] allQuoRem;
        if (f_fSigned)
            {
            allQuoRem = ll1.divrem(ll2);
            }
        else
            {
            allQuoRem = ll1.divremUnsigned(ll2);
            }

        LongLong llDiv = allQuoRem[0];
        LongLong llRem = allQuoRem[1];
        if (f_fChecked && llDiv == LongLong.OVERFLOW)
            {
            return overflow(frame);
            }
        return frame.assignValues(aiReturn, makeHandle(llDiv), makeHandle(llRem));
        }

    @Override
    public int invokeShl(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        int      c   = (int) ((JavaLong) hArg).getValue();

        return frame.assignValue(iReturn, makeHandle(ll1.shl(c)));
        }

    @Override
    public int invokeShr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        int      c   = (int) ((JavaLong) hArg).getValue();

        return frame.assignValue(iReturn, makeHandle(ll1.shr(c)));
        }

    @Override
    public int invokeShrAll(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        int      c   = (int) ((JavaLong) hArg).getValue();

        return frame.assignValue(iReturn, makeHandle(ll1.ushr(c)));
        }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeHandle(ll1.and(ll2)));
        }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeHandle(ll1.or(ll2)));
        }

    @Override
    public int invokeXor(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeHandle(ll1.xor(ll2)));
        }

    @Override
    public int invokeCompl(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        LongLong ll = ((LongLongHandle) hTarget).getValue();

        return frame.assignValue(iReturn, makeHandle(ll.complement()));
        }

    // ----- comparison support --------------------------------------------------------------------

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        LongLongHandle h1 = (LongLongHandle) hValue1;
        LongLongHandle h2 = (LongLongHandle) hValue2;

        return frame.assignValue(iReturn, xOrdered.makeHandle(
            f_fSigned
                ? h1.getValue().compare(h2.getValue())
                : h1.getValue().compareUnsigned(h2.getValue())));
        }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2)
        {
        return ((LongLongHandle) hValue1).getValue().equals(((LongLongHandle) hValue2).getValue());
        }

    @Override
    public int buildHashCode(Frame frame, TypeComposition clazz, ObjectHandle hTarget, int iReturn)
        {
        LongLong ll = ((LongLongHandle) hTarget).getValue();

        return frame.assignValue(iReturn, xInt64.makeHandle(ll.hashCode()));
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        LongLong ll = ((LongLongHandle) hTarget).getValue();

        return frame.assignValue(iReturn, xString.makeHandle(
            f_fSigned
                ? ll.toBigInteger().toString()
                : ll.toUnsignedBigInteger().toString()));
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Produce an array of bytes for the specified LongLong value.
     *
     * @param ll  the value
     *
     * @return the byte array
     */
    public static byte[] toByteArray(LongLong ll)
        {
        byte[] ab = new byte[16];
        xConstrainedInteger.copyAsBytes(ll.getHighValue(), ab, 0);
        xConstrainedInteger.copyAsBytes(ll.getLowValue(), ab, 8);
        return ab;
        }

    /**
     * Convert a long value into a handle for the type represented by this template.
     *
     * @return one of the {@link Op#R_NEXT} or {@link Op#R_EXCEPTION} values
     */
    protected int convertLong(Frame frame, long lValue, int iReturn, boolean fFromSigned)
        {
        return frame.assignValue(iReturn, makeHandle(new LongLong(lValue, fFromSigned)));
        }

    /**
     * Converts a LongLong value of "this" integer type to the type represented by the template.
     *
     * @return one of the {@link Op#R_NEXT} or {@link Op#R_EXCEPTION} values
     */
    protected abstract int convertToConstrainedType(Frame frame, xConstrainedInteger template,
                                                    LongLong llValue, boolean fCheckBounds, int iReturn);

    /**
     * Create a handle for the specified LongLong value.
     */
    public LongLongHandle makeHandle(LongLong ll)
        {
        return new LongLongHandle(getCanonicalClass(), ll);
        }

    public static class LongLongHandle
            extends ObjectHandle
        {
        protected LongLong m_llValue;

        public LongLongHandle(TypeComposition clazz, LongLong ll)
            {
            super(clazz);
            m_llValue = ll;
            }

        public LongLong getValue()
            {
            return m_llValue;
            }

        @Override
        public int hashCode()
            {
            return m_llValue.hashCode();
            }

        @Override
        public boolean equals(Object obj)
            {
            return obj instanceof LongLongHandle that && this.m_llValue.equals(that.m_llValue);
            }

        @Override
        public String toString()
            {
            return super.toString() + m_llValue;
            }
        }

    public final boolean f_fSigned;
    public final boolean f_fChecked; // for now, it's always false
    }