package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.ByteConstant;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.text.xString;

import org.xvm.util.Lazy;


/**
 * Native Bit implementation.
 */
public class xBit
        extends xConst {
    public static xBit getInstance(Frame frame) {
        return NativeTemplates.get(frame).bit();
    }

    public static xBit getInstance(Container container) {
        return NativeTemplates.get(container).bit();
    }

    public xBit(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, false);
    }

    @Override
    public void initNative() {
        // Preserve the old eager Bit handle cache warmup, but keep the handles owned by this
        // template's container instead of publishing them in mutable process globals.
        zeroHandle();
        oneHandle();

        markNativeMethod("toBoolean", VOID, new String[]{"Boolean"});
        markNativeMethod("toUInt8"  , null, new String[]{"numbers.UInt8"}); // Byte
        markNativeMethod("toInt64"  , null, new String[]{"numbers.Int64"});
        markNativeMethod("toUInt64" , null, new String[]{"numbers.UInt64"});

        markNativeMethod("and", THIS, THIS);
        markNativeMethod("or" , THIS, THIS);
        markNativeMethod("xor", THIS, THIS);
        markNativeMethod("not", VOID, THIS);

        invalidateTypeInfo();
    }

    @Override
    public boolean isGenericHandle() {
        return false;
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        if (constant instanceof IntConstant constInt) {
            return frame.pushStack(makeHandle(frame, constInt.getValue().getLong() != 0L));
        }
        if (constant.getFormat() == Format.Bit) {
            return frame.pushStack(makeHandle(frame,
                    ((ByteConstant) constant).getValue().intValue() != 0));
        }
        return super.createConstHandle(frame, constant);
    }

    @Override
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn) {
        if (ahVar.length >= 1 && ahVar[0] instanceof xIntLiteral.IntNHandle hIntN) {
            try {
                long lBit = hIntN.getValue().getInt();
                if (lBit == 0 || lBit == 1) {
                    return frame.assignValue(iReturn, makeHandle(frame, lBit == 1));
                }
            } catch (IllegalStateException ignore) {}

            return frame.raiseException(xException.illegalArgument(frame,
                hIntN.getValue().toString()));
        }
        return frame.raiseException(xException.unsupported(frame));
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        switch (method.getName()) {
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
                             ObjectHandle[] ahArg, int iReturn) {
        switch (method.getName()) {
        case "toBoolean":
        case "toUInt8":
        case "toInt64":
        case "toUInt64": {
            TypeConstant  typeRet  = method.getReturn(0).getType();
            ClassTemplate template = f_container.getTemplate(typeRet);
            boolean       fValue   = ((JavaLong) hTarget).getValue() != 0;

            if (template instanceof xConstrainedInteger templateTo) {
                return frame.assignValue(iReturn, templateTo.makeJavaLong(fValue ? 1L : 0L));
            }

            if (template instanceof xBoolean) {
                return frame.assignValue(iReturn, xBoolean.makeHandle(frame, fValue));
            }
            break;
        }
        }
        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        return frame.assignValue(iReturn,
            makeHandle(frame,
                    ((JavaLong) hTarget).getValue() != 0 & ((JavaLong) hArg).getValue() != 0));
    }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        return frame.assignValue(iReturn,
            makeHandle(frame,
                    ((JavaLong) hTarget).getValue() != 0 | ((JavaLong) hArg).getValue() != 0));
    }

    @Override
    public int invokeXor(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        return frame.assignValue(iReturn,
            makeHandle(frame,
                    ((JavaLong) hTarget).getValue() != 0 ^ ((JavaLong) hArg).getValue() != 0));
    }

    @Override
    public int invokeCompl(Frame frame, ObjectHandle hTarget, int iReturn) {
        long l = ((JavaLong) hTarget).getValue();

        return frame.assignValue(iReturn, makeHandle(frame, l == 0));
    }

    @Override
    public int callEquals(Frame frame, TypeComposition clazz,
                          ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        return frame.assignValue(iReturn, xBoolean.makeHandle(frame, compareIdentity(hValue1, hValue2)));
    }

    @Override
    public int callCompare(Frame frame, TypeComposition clazz,
                           ObjectHandle hValue1, ObjectHandle hValue2, int iReturn) {
        JavaLong h1 = (JavaLong) hValue1;
        JavaLong h2 = (JavaLong) hValue2;

        return frame.assignValue(iReturn,
            xOrdered.makeHandle(frame, Long.compare(h1.getValue(), h2.getValue())));
    }

    @Override
    public boolean compareIdentity(ObjectHandle hValue1, ObjectHandle hValue2) {
        return ((JavaLong) hValue1).getValue() == ((JavaLong) hValue2).getValue();
    }

    @Override
    public int buildHashCode(Frame frame, TypeComposition clazz, ObjectHandle hTarget, int iReturn) {
        long l = ((JavaLong) hTarget).getValue();

        return frame.assignValue(iReturn, xInt64.makeHandle(frame, l != 0 ? 1L : 0L));
    }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn) {
        long l = ((JavaLong) hTarget).getValue();

        return frame.assignValue(iReturn, l == 0 ? xString.zero(frame) : xString.one(frame));
    }

    public JavaLong makeHandle(boolean f) {
        return f ? oneHandle() : zeroHandle();
    }

    public JavaLong zeroHandle() {
        return f_zero.get();
    }

    public JavaLong oneHandle() {
        return f_one.get();
    }

    public static JavaLong makeHandle(Frame frame, boolean f) {
        return makeHandle(frame.container(), f);
    }

    public static JavaLong makeHandle(Container container, boolean f) {
        return getInstance(container).makeHandle(f);
    }

    private final Lazy<JavaLong> f_zero = Lazy.of(() -> new JavaLong(getCanonicalClass(), 0));
    private final Lazy<JavaLong> f_one  = Lazy.of(() -> new JavaLong(getCanonicalClass(), 1));
}
