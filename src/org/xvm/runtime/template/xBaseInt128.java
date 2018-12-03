package org.xvm.runtime.template;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.util.PackedInteger;

/**
 * Abstract base for Int128 and UInt128.
 */
public abstract class xBaseInt128
        extends xConst
    {
    private boolean f_fSigned;

    public xBaseInt128(TemplateRegistry templates, ClassStructure structure, boolean fSigned)
        {
        super(templates, structure, false);

        f_fSigned = fSigned;
        }

    @Override
    public void initDeclared()
        {
        markNativeMethod("to", VOID, new String[]{"Int64"});
        markNativeMethod("to", VOID, new String[]{"Int32"});
        markNativeMethod("to", VOID, new String[]{"Int16"});
        markNativeMethod("to", VOID, new String[]{"Int8"});
        markNativeMethod("to", VOID, new String[]{"UInt64"});
        markNativeMethod("to", VOID, new String[]{"UInt32"});
        markNativeMethod("to", VOID, new String[]{"UInt16"});
        markNativeMethod("to", VOID, new String[]{"UInt8"});
        markNativeMethod("to", VOID, THIS);

        // @Op methods
        markNativeMethod("add", THIS, THIS);
        markNativeMethod("sub", THIS, THIS);
        markNativeMethod("mul", THIS, THIS);
        markNativeMethod("div", THIS, THIS);
        markNativeMethod("mod", THIS, THIS);
        }

    @Override
    public boolean isGenericHandle()
        {
        return false;
        }

    @Override
    public int createConstHandle(Frame frame, Constant constant)
        {
        if (constant instanceof IntConstant)
            {
            PackedInteger piValue = ((IntConstant) constant).getValue();

            frame.pushStack(makeLongLong(
                LongLong.fromBigInteger(piValue.getBigInteger())));
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
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "to":
                {
                TypeConstant typeRet = method.getReturn(0).getType();
                xConstrainedInteger template = xConstrainedInteger.getTemplateByType(typeRet);
                if (template != null)
                    {
                    return convertIntegerType(frame, template, hTarget, iReturn);
                    }
                break;
                }
            }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();
        LongLong llr = f_fSigned ? ll1.add(ll2) : ll1.addUnsigned(ll2);

        if (llr == LongLong.OVERFLOW)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeLongLong(llr));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();
        LongLong llr = f_fSigned ?  ll1.sub(ll2) : ll1.subUnassigned(ll2);

        if (llr == LongLong.OVERFLOW)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeLongLong(llr));
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();
        LongLong llr = f_fSigned ? ll1.mul(ll2) : ll1.mulUnsigned(ll2);

        if (llr == LongLong.OVERFLOW)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeLongLong(llr));
        }

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        LongLong ll = ((LongLongHandle) hTarget).getValue();
        LongLong llr = ll.prev(f_fSigned);

        if (llr == LongLong.OVERFLOW)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeLongLong(llr));
        }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        LongLong ll = ((LongLongHandle) hTarget).getValue();
        LongLong llr = ll.next(f_fSigned);

        if (llr == LongLong.OVERFLOW)
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeLongLong(llr));
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();

        if (ll2.equals(LongLong.ZERO))
            {
            return overflow(frame);
            }

        return frame.assignValue(iReturn, makeLongLong(
            f_fSigned ? ll1.div(ll2) : ll1.divUnsigned(ll2)));
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();

        if (ll2.equals(LongLong.ZERO))
            {
            return overflow(frame);
            }
        return frame.assignValue(iReturn, makeLongLong(
            f_fSigned ? ll1.mod(ll2) : ll1.modUnsigned(ll2)));
        }

    @Override
    public int invokeShl(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeLongLong(ll1.shl(ll2)));
        }

    @Override
    public int invokeShr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeLongLong(ll1.shr(ll2)));
        }

    @Override
    public int invokeShrAll(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeLongLong(ll1.ushr(ll2)));
        }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeLongLong(ll1.and(ll2)));
        }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeLongLong(ll1.or(ll2)));
        }

    @Override
    public int invokeXor(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeLongLong(ll1.xor(ll2)));
        }

    @Override
    public int invokeDivMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int[] aiReturn)
        {
        LongLong ll1 = ((LongLongHandle) hTarget).getValue();
        LongLong ll2 = ((LongLongHandle) hArg).getValue();

        return f_fSigned
            ? frame.assignValues(aiReturn,
                makeLongLong(ll1.div(ll2)), makeLongLong(ll1.mod(ll2)))
            : frame.assignValues(aiReturn,
                makeLongLong(ll1.divUnsigned(ll2)), makeLongLong(ll1.modUnsigned(ll2)));
        }

    @Override
    public int invokeCompl(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        LongLong ll = ((LongLongHandle) hTarget).getValue();

        return frame.assignValue(iReturn, makeLongLong(ll.complement()));
        }

    @Override
    public int buildHashCode(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        LongLong ll = ((LongLongHandle) hTarget).getValue();

        return frame.assignValue(iReturn, xInt64.makeHandle(ll.hashCode()));
        }

    // ----- comparison support -----

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        LongLongHandle h1 = (LongLongHandle) hValue1;
        LongLongHandle h2 = (LongLongHandle) hValue2;

        return frame.assignValue(iReturn, xBoolean.makeHandle(h1.getValue().equals(h2.getValue())));
        }

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

    // ----- Object methods -----

    protected int overflow(Frame frame)
        {
        return frame.raiseException(xException.makeHandle("Int128 overflow"));
        }

    @Override
    public int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        LongLong ll = ((LongLongHandle) hTarget).getValue();

        return frame.assignValue(iReturn, xString.makeHandle(
            f_fSigned
                ? ll.toBigInteger().toString()
                : ll.toUnsignedBigInteger().toString()));
        }

    /**
     * Converts an object of "this" integer type to the type represented by the template.
     *
     * @return one of the {@link Op#R_NEXT} or {@link Op#R_EXCEPTION} values
     */
    abstract protected int convertIntegerType(Frame frame, xConstrainedInteger template,
                                              ObjectHandle hTarget, int iReturn);

    public LongLongHandle makeLongLong(LongLong ll)
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
            return obj instanceof LongLongHandle &&
                m_llValue.equals(((LongLongHandle) obj).getValue());
            }

        @Override
        public String toString()
            {
            return super.toString() + m_llValue;
            }
        }
    }
