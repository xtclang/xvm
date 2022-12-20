package org.xvm.runtime.template.numbers;


import java.math.BigInteger;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ErrorList;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Lexer;
import org.xvm.compiler.Source;
import org.xvm.compiler.Token;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.text.xString;
import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.util.PackedInteger;


/**
 * Native IntLiteral implementation.
 */
public class xIntLiteral
        extends xConst
    {
    public static xIntLiteral INSTANCE;

    public xIntLiteral(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void initNative()
        {
        markNativeMethod("construct", STRING, VOID);

        markNativeMethod("and",           THIS, THIS);
        markNativeMethod("or",            THIS, THIS);
        markNativeMethod("xor",           THIS, THIS);
        markNativeMethod("shiftLeft",     INT,  THIS);
        markNativeMethod("shiftRight",    INT,  THIS);
        markNativeMethod("shiftAllRight", INT,  THIS);
        markNativeMethod("add",           THIS, THIS);
        markNativeMethod("sub",           THIS, THIS);
        markNativeMethod("mul",           THIS, THIS);
        markNativeMethod("div",           THIS, THIS);
        markNativeMethod("mod",           THIS, THIS);
        markNativeMethod("not",           VOID, THIS);

        markNativeMethod("toString", VOID, STRING);

        markNativeMethod("toInt8"            , null, new String[]{"numbers.Int8"});
        markNativeMethod("toInt16"           , null, new String[]{"numbers.Int16"});
        markNativeMethod("toInt32"           , null, new String[]{"numbers.Int32"});
        markNativeMethod("toInt64"           , null, new String[]{"numbers.Int64"});
        markNativeMethod("toInt128"          , null, new String[]{"numbers.Int128"});

        markNativeMethod("toUInt8"           , null, new String[]{"numbers.UInt8"});
        markNativeMethod("toUInt16"          , null, new String[]{"numbers.UInt16"});
        markNativeMethod("toUInt32"          , null, new String[]{"numbers.UInt32"});
        markNativeMethod("toUInt64"          , null, new String[]{"numbers.UInt64"});
        markNativeMethod("toUInt128"         , null, new String[]{"numbers.UInt128"});

        markNativeMethod("toIntN"            , null, new String[]{"numbers.IntN"});
        markNativeMethod("toUIntN"           , null, new String[]{"numbers.UIntN"});
        markNativeMethod("toFloatN"          , null, new String[]{"numbers.FloatN"});
        markNativeMethod("toDecN"            , null, new String[]{"numbers.DecN"});

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
        LiteralConstant constVal    = (LiteralConstant) constant;
        StringHandle    hText       = (StringHandle) frame.getConstHandle(constVal.getStringConstant());
        IntNHandle      hIntLiteral = makeIntLiteral(constVal.getPackedInteger(), hText);

        return frame.pushStack(hIntLiteral);
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        StringHandle hText = (StringHandle) ahVar[0];
        String       sText = hText.getStringValue();

        PackedInteger pi;
        try
            {
            pi = new PackedInteger(Long.parseLong(sText));
            }
        catch (NumberFormatException e)
            {
            ErrorList errs   = new ErrorList(5);
            Lexer     lexer  = new Lexer(new Source(sText), errs);
            Token     tokLit = lexer.next();
            if (errs.hasSeriousErrors() || tokLit.getId() != Token.Id.LIT_INT)
                {
                return frame.raiseException(
                    xException.illegalArgument(frame, "Invalid number \"" + sText + "\""));
                }

            pi = (PackedInteger) tokLit.getValue();
            }

        return frame.assignValue(iReturn, makeIntLiteral(pi, hText));
        }

    @Override
    public int getFieldValue(Frame frame, ObjectHandle hTarget, PropertyConstant idProp, int iReturn)
        {
        switch (idProp.getName())
            {
            case "text":
                return frame.assignValue(iReturn, ((IntNHandle) hTarget).getText());
            }
        return frame.raiseException("not supported field: " + idProp.getName());
        }

    @Override
    public int invokeAdd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.add(pi2)));
        }

    @Override
    public int invokeSub(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.sub(pi2)));
        }

    @Override
    public int invokeMul(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.mul(pi2)));
        }

    @Override
    public int invokeDiv(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.div(pi2)));
        }

    @Override
    public int invokeMod(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.mod(pi2)));
        }

    @Override
    public int invokeShl(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi    = ((IntNHandle) hTarget).getValue();
        long          count = ((JavaLong) hArg).getValue();

        if (count > Integer.MAX_VALUE)
            {
            return overflow(frame);
            }
        return frame.assignValue(iReturn, makeIntLiteral(pi.shl((int) count)));
        }

    @Override
    public int invokeShr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi    = ((IntNHandle) hTarget).getValue();
        long          count = ((JavaLong) hArg).getValue();

        if (count > Integer.MAX_VALUE)
            {
            return overflow(frame);
            }
        return frame.assignValue(iReturn, makeIntLiteral(pi.shr((int) count)));
        }

    @Override
    public int invokeShrAll(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi    = ((IntNHandle) hTarget).getValue();
        long          count = ((JavaLong) hArg).getValue();

        if (count > Integer.MAX_VALUE)
            {
            return overflow(frame);
            }
        return frame.assignValue(iReturn, makeIntLiteral(pi.ushr((int) count)));
        }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.and(pi2)));
        }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.or(pi2)));
        }

    @Override
    public int invokeXor(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        PackedInteger pi1 = ((IntNHandle) hTarget).getValue();
        PackedInteger pi2 = ((IntNHandle) hArg).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi1.xor(pi2)));
        }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        PackedInteger pi = ((IntNHandle) hTarget).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi.negate()));
        }

    @Override
    public int invokeCompl(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        PackedInteger pi = ((IntNHandle) hTarget).getValue();

        return frame.assignValue(iReturn, makeIntLiteral(pi.complement()));
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method,
                             ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "and":
                return invokeAnd(frame, hTarget, hArg, iReturn);

            case "or":
                return invokeOr(frame, hTarget, hArg, iReturn);

            case "xor":
                return invokeXor(frame, hTarget, hArg, iReturn);

            case "shiftLeft":
                return invokeShl(frame, hTarget, hArg, iReturn);

            case "shiftRight":
                return invokeShl(frame, hTarget, hArg, iReturn);

            case "shiftAllRight":
                return invokeShrAll(frame, hTarget, hArg, iReturn);

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

            }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        IntNHandle hLiteral = (IntNHandle) hTarget;
        switch (method.getName())
            {
            case "not":
                return invokeCompl(frame, hTarget, iReturn);

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
            case "toFloatN":
            case "toDecN":
                TypeConstant  typeRet  = method.getReturn(0).getType();
                ClassTemplate template = f_container.getTemplate(typeRet);
                PackedInteger piValue  = hLiteral.getValue();

                // REVIEW GG for unchecked use case

                if (template instanceof xConstrainedInteger templateTo)
                    {
                    return templateTo.convertLong(frame, piValue, iReturn);
                    }
                if (template instanceof BaseInt128 templateTo)
                    {
                    BigInteger  biValue = piValue.getBigInteger();
                    LongLong    llValue = LongLong.fromBigInteger(biValue);

                    return templateTo.f_fSigned || llValue.signum() >= 0
                        ? frame.assignValue(iReturn, templateTo.makeLongLong(llValue))
                        : templateTo.overflow(frame);
                    }
                break;
            }
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    protected IntNHandle makeIntLiteral(PackedInteger piValue)
        {
        return new IntNHandle(getCanonicalClass(), piValue, null);
        }

    protected IntNHandle makeIntLiteral(PackedInteger piValue, StringHandle hText)
        {
        return new IntNHandle(getCanonicalClass(), piValue, hText);
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        IntNHandle hLiteral = (IntNHandle) hTarget;
        return frame.assignValue(iReturn, hLiteral.getText());
        }


    // ----- comparison support --------------------------------------------------------------------

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        IntNHandle h1 = (IntNHandle) hValue1;
        IntNHandle h2 = (IntNHandle) hValue2;

        return frame.assignValue(iReturn, xBoolean.makeHandle(h1.getValue().equals(h2.getValue())));
        }

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        IntNHandle h1 = (IntNHandle) hValue1;
        IntNHandle h2 = (IntNHandle) hValue2;

        return frame.assignValue(iReturn, xOrdered.makeHandle(h1.getValue().cmp(h2.getValue())));
        }

    /**
     * This handle type is used by IntN, UIntN as well as IntLiteral.
     */
    public static class IntNHandle
            extends ObjectHandle
        {
        public IntNHandle(TypeComposition clazz, PackedInteger piValue, StringHandle hText)
            {
            super(clazz);

            assert piValue != null;

            m_piValue = piValue;
            }

        public StringHandle getText()
            {
            StringHandle hText = m_hText;
            if (hText == null)
                {
                m_hText = hText = xString.makeHandle(m_piValue.toString());
                }
            return hText;
            }

        public PackedInteger getValue()
            {
            return m_piValue;
            }

        @Override
        public int hashCode() { return m_piValue.hashCode(); }

        @Override
        public boolean equals(Object obj)
            {
            return obj instanceof IntNHandle that && m_piValue.equals(that.m_piValue);
            }

        @Override
        public String toString()
            {
            return super.toString() + m_piValue.toString();
            }

        protected PackedInteger m_piValue;
        protected StringHandle  m_hText; // (optional) cached text handle
        }
    }