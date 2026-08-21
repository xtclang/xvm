package org.xvm.runtime.template.text;


import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

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


/**
 * Native Char implementation.
 */
public class xChar
        extends xConst {
    public xChar(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure);

        // Temporary legacy role flag: true only for the canonical native Char template.
        // It owns the small-value cache below; replacing this boolean with an explicit
        // canonical-template cache is a follow-up cleanup.
        f_fInstance = fInstance;
    }

    @Override
    public void initNative() {
        super.initNative();

        markNativeProperty("codepoint");

        invalidateTypeInfo();

        if (f_fInstance) {
            ClassComposition clz = getCanonicalClass();
            for (int i = 0; i < cache.length; ++i) {
                cache[i] = new JavaLong(clz, i);
            }
        }
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
            char[] ach = hText.getValue();
            if (ach.length != 1) {
                return frame.raiseException("illegal argument: String has length=" + ach.length);
            }

            return constructHandle(frame, ach[0], iReturn);
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

    @Override
    public int invokePrev(Frame frame, ObjectHandle hTarget, int iReturn) {
        long l = ((JavaLong) hTarget).getValue();

        if (l == Character.MIN_VALUE) {
            return overflow(frame);
        }

        return frame.assignValue(iReturn, makeHandle(l - 1));
    }

    @Override
    public int invokeNext(Frame frame, ObjectHandle hTarget, int iReturn) {
        long l = ((JavaLong) hTarget).getValue();

        if (l == Character.MAX_VALUE) {
            return overflow(frame);
        }

        return frame.assignValue(iReturn, makeHandle(l + 1));
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

    /**
     * True only for the canonical native template; avoids a recursive NativeTemplates lookup while
     * prebuilding this owner template's cached ASCII handles.
     */
    private final boolean f_fInstance;

    private final JavaLong[] cache = new JavaLong[128];
}
