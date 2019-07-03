package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op;

import org.xvm.asm.constants.IntConstant;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TemplateRegistry;


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
    public void initDeclared()
        {
        ZERO = new JavaLong(getCanonicalClass(), 0);
        ONE  = new JavaLong(getCanonicalClass(), 1);

        markNativeMethod("to", VOID, new String[]{"Boolean"});
        markNativeMethod("to", VOID, new String[]{"UInt8"}); // Byte
        markNativeMethod("to", VOID, new String[]{"Int64"});
        markNativeMethod("to", VOID, new String[]{"UInt64"});

        markNativeMethod("and", THIS, THIS);
        markNativeMethod("or" , THIS, THIS);
        markNativeMethod("xor", THIS, THIS);
        markNativeMethod("not", VOID, THIS);
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
            frame.pushStack(makeHandle(((IntConstant) constant).getValue().getLong() != 0L));
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
            case "to":
                {
                TypeConstant  typeRet  = method.getReturn(0).getType();
                ClassTemplate template = f_templates.getTemplate(typeRet);
                boolean       fValue   = ((JavaLong) hTarget).getValue() != 0;

                if (template instanceof xConstrainedInteger)
                    {
                    return frame.assignValue(iReturn,
                        ((xConstrainedInteger) template).makeJavaLong(fValue ? 1L : 0L));
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
    public int callEquals(Frame frame, ClassComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn)
        {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return frame.assignValue(iReturn,
            xBoolean.makeHandle(h1.getValue() == h2.getValue()));
        }

    @Override
    public int callCompare(Frame frame, ClassComposition clazz,
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
    public int buildHashCode(Frame frame, ClassComposition clazz, ObjectHandle hTarget, int iReturn)
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
