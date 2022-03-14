package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.ByteConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.text.xString;


/**
 * Native Bit implementation.
 */
public class xBit
        extends xConst
    {
    public xBit(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, false);
        }

    @Override
    public void initNative()
        {
        ZERO = new JavaLong(getCanonicalClass(), 0);
        ONE  = new JavaLong(getCanonicalClass(), 1);

        markNativeMethod("toBoolean", VOID, new String[]{"Boolean"});
        markNativeMethod("toUInt8"  , VOID, new String[]{"numbers.UInt8"}); // Byte
        markNativeMethod("toInt64"  , VOID, new String[]{"numbers.Int64"});
        markNativeMethod("toUInt64" , VOID, new String[]{"numbers.UInt64"});

        markNativeMethod("and", THIS, THIS);
        markNativeMethod("or" , THIS, THIS);
        markNativeMethod("xor", THIS, THIS);
        markNativeMethod("not", VOID, THIS);

        getCanonicalType().invalidateTypeInfo();
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
            return frame.pushStack(makeHandle(constInt.getValue().getLong() != 0L));
            }
        if (constant.getFormat() == Format.Bit)
            {
            return frame.pushStack(makeHandle(((ByteConstant) constant).getValue().intValue() != 0));
            }
        return super.createConstHandle(frame, constant);
        }

    @Override
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn)
        {
        if (ahVar.length >= 1 && ahVar[0] instanceof xIntLiteral.IntNHandle hIntN)
            {
            try
                {
                long lBit = hIntN.getValue().getInt();
                if (lBit == 0 || lBit == 1)
                    {
                    return frame.assignValue(iReturn, makeHandle(lBit == 1));
                    }
                }
            catch (IllegalStateException ignore) {}

            return frame.raiseException(xException.illegalArgument(frame,
                hIntN.getValue().toString()));
            }
        return frame.raiseException(xException.unsupportedOperation(frame));
        }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn)
        {
        switch (method.getName())
            {
            case "and":
                return invokeAnd(frame, hTarget, hArg, iReturn);

            case "or":
                return invokeOr(frame, hTarget, hArg, iReturn);

            case "xor":
                return invokeXor(frame, hTarget, hArg, iReturn);

            case "not":
                return invokeCompl(frame, hTarget, iReturn);
            }

        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
        }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle[] ahArg, int iReturn)
        {
        switch (method.getName())
            {
            case "toBoolean":
            case "toUInt8":
            case "toInt64":
            case "toUInt64":
                {
                TypeConstant  typeRet  = method.getReturn(0).getType();
                ClassTemplate template = f_templates.getTemplate(typeRet);
                boolean       fValue   = ((JavaLong) hTarget).getValue() != 0;

                if (template instanceof xConstrainedInteger templateTo)
                    {
                    return frame.assignValue(iReturn, templateTo.makeJavaLong(fValue ? 1L : 0L));
                    }

                if (template instanceof xBoolean)
                    {
                    return frame.assignValue(iReturn, xBoolean.makeHandle(fValue));
                    }
                }
            break;
            }
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
        }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return frame.assignValue(iReturn,
            xBoolean.makeHandle(h1.getValue() == h2.getValue()));
        }

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return frame.assignValue(iReturn,
            xOrdered.makeHandle(Long.compare(h1.getValue(), h2.getValue())));
        }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return frame.assignValue(iReturn,
            makeHandle(((JavaLong) hTarget).getValue() != 0 & ((JavaLong) hArg).getValue() != 0));
        }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return frame.assignValue(iReturn,
            makeHandle(((JavaLong) hTarget).getValue() != 0 | ((JavaLong) hArg).getValue() != 0));
        }

    @Override
    public int invokeXor(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn)
        {
        return frame.assignValue(iReturn,
            makeHandle(((JavaLong) hTarget).getValue() != 0 ^ ((JavaLong) hArg).getValue() != 0));
        }

    @Override
    public int invokeCompl(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        return frame.assignValue(iReturn, makeHandle(l == 0));
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

        return frame.assignValue(iReturn, xInt64.makeHandle(l != 0 ? 1L : 0L));
        }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn)
        {
        long l = ((JavaLong) hTarget).getValue();

        return frame.assignValue(iReturn, l == 0 ? xString.ZERO : xString.ONE);
        }

    public static JavaLong makeHandle(boolean f)
        {
        return f ? ONE : ZERO;
        }

    public static JavaLong ZERO;
    public static JavaLong ONE;
    }
