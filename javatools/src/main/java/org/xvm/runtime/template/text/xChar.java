package org.xvm.runtime.template.text;


import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import java.util.Arrays;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.CharConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.OperatorBinding;
import org.xvm.runtime.ObjectHandle.JavaLong;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.xBoolean;
import org.xvm.runtime.template.xConst;
import org.xvm.runtime.template.xException;
import org.xvm.runtime.template.xOrdered;

import org.xvm.runtime.template.collections.xArray.ArrayHandle;
import org.xvm.runtime.template.collections.xByteArray;

import org.xvm.runtime.template.numbers.xInt64;
import org.xvm.runtime.template.numbers.xUInt32;

import org.xvm.runtime.template.text.xString.StringHandle;

import org.xvm.util.Handy;
import org.xvm.util.FrozenCharArray;


/**
 * Native Char implementation.
 */
public class xChar
        extends xConst {
    public xChar(Container container, ClassStructure structure) {
        super(container, structure);
    }

    @Override
    public void initNative() {
        bindOp(OperatorBinding.Op.NEXT, JavaLong.class, this::opNext);
        bindOp(OperatorBinding.Op.PREV, JavaLong.class, this::opPrev);

        super.initNative();

        markNativeProperty("codepoint");

        invalidateTypeInfo();

        // No fInstance branch is needed here. Char has no derived native Java template; the
        // runtime-registered xChar is the canonical owner template, and this cache is built from
        // this template's canonical class without a recursive NativeTemplates lookup.
        ClassComposition clz = getCanonicalClass();
        Arrays.setAll(cache, i -> new JavaLong(clz, i));
    }

    @Override
    public boolean isGenericHandle() {
        return false;
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        if (constant instanceof CharConstant constChar) {
            return frame.pushStack(new JavaLong(getCanonicalClass(), constChar.getValue()));
        }

        return super.createConstHandle(frame, constant);
    }

    @Override
    public int construct(Frame frame, MethodStructure constructor, TypeComposition clazz,
                         ObjectHandle hParent, ObjectHandle[] ahVar, int iReturn) {
        // there are three constructors take a JavaLong parameter (Byte, UInt32 and Int)
        // and one takes Byte[]
        ObjectHandle hArg = ahVar[0];
        if (hArg instanceof JavaLong hCodepoint) {
            return constructHandle(frame, hCodepoint.getValue(), iReturn);
        }

        if (hArg instanceof StringHandle hText) {
            FrozenCharArray ach = hText.getValue();
            if (ach.size() != 1) {
                return frame.raiseException("illegal argument: String has length=" + ach.size());
            }

            return constructHandle(frame, ach.get(0), iReturn);
        }

        byte[] ab = xByteArray.getBytes((ArrayHandle) hArg);
        try {
            long lCodepoint =
                Handy.readUtf8Char(new DataInputStream(new ByteArrayInputStream(ab)));
            return constructHandle(frame, lCodepoint, iReturn);
        } catch (IOException e) {
            return frame.raiseException(xException.illegalUTF(frame, e.getMessage()));
        }
    }

    protected int constructHandle(Frame frame, long lCodepoint, int iReturn) {
        if (lCodepoint > 0x10FFFFL ||                       // unicode limit
            lCodepoint > 0xD7FFL && lCodepoint < 0xE000L) { // surrogate values are illegal
            return frame.raiseException("illegal code-point: " + lCodepoint);
        }

        return frame.assignValue(iReturn, makeHandle(lCodepoint));
    }

    @Override
    public int invokeNativeGet(Frame frame, String sPropName, ObjectHandle hTarget, int iReturn) {
        switch (sPropName) {
        case "codepoint":
            return frame.assignValue(iReturn,
                    frame.container().nativeTemplates().uint32().makeJavaLong(
                            ((JavaLong) hTarget).getValue()));
        }

        return super.invokeNativeGet(frame, sPropName, hTarget, iReturn);
    }




    private int opNext(Frame frame, JavaLong hTarget, int iReturn) {
        long l = hTarget.getValue();

        if (l == Character.MAX_VALUE) {
            return overflow(frame);
        }

        return frame.assignValue(iReturn, makeHandle(l + 1));
    }

    private int opPrev(Frame frame, JavaLong hTarget, int iReturn) {
        long l = hTarget.getValue();

        if (l == Character.MIN_VALUE) {
            return overflow(frame);
        }

        return frame.assignValue(iReturn, makeHandle(l - 1));
    }

    // ----- comparison support --------------------------------------------------------------------

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
        JavaLong hThis = (JavaLong) hTarget;

        return frame.assignValue(iReturn, xInt64.makeHandle(frame, hThis.getValue()));
    }


    // ----- helpers -------------------------------------------------------------------------------

    public JavaLong makeHandle(long chValue) {
        assert chValue >= 0 & chValue <= 0x10FFFF;
        if (chValue < 128) {
            return cache[(int) chValue];
        }
        return new JavaLong(getCanonicalClass(), chValue);
    }

    public static JavaLong makeHandle(Frame frame, long chValue) {
        return makeHandle(frame.container(), chValue);
    }

    public static JavaLong makeHandle(Container container, long chValue) {
        return NativeTemplates.get(container).charTemplate().makeHandle(chValue);
    }

    public static JavaLong makeHandle(ClassTemplate template, long chValue) {
        return makeHandle(template.f_container, chValue);
    }

    public static JavaLong makeHandle(ObjectHandle owner, long chValue) {
        return makeHandle(owner.getComposition().getContainer(), chValue);
    }

    private static final int CACHE_SIZE = 128;

    private final JavaLong[] cache = new JavaLong[CACHE_SIZE];
}
