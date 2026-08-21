package org.xvm.runtime.template.numbers;


import java.util.Arrays;

import org.xvm.asm.ClassStructure;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.ByteConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.text.xChar;


/**
 * Native Nibble (Byte) support.
 */
public class xNibble
        extends xUnsignedConstrainedInt {
    public xNibble(Container container, ClassStructure structure) {
        super(container, structure, 0, 15, 4, false);
    }

    @Override
    public void initNative() {
        super.initNative();

        // No fInstance branch is needed here. Nibble has no derived native Java template; the
        // canonical owner still eagerly fills the same final small-value array, preserving cached
        // handle identity and avoiding a Lazy/volatile read on this hot path.
        ClassComposition clz = getCanonicalClass();
        Arrays.setAll(cache, i -> new JavaLong(clz, i));
    }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate() {
        return this;
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        if (constant instanceof ByteConstant constByte) {
            return frame.pushStack(makeHandle(constByte.getValue().longValue()));
        }

        return super.createConstHandle(frame, constant);
    }

    @Override
    public JavaLong makeJavaLong(long lValue) {
        return makeHandle(lValue & 0x0FL);
    }

    public JavaLong makeHandle(long lValue) {
        assert lValue >= 0 & lValue <= 15;
        return cache[(int) lValue];
    }

    public static JavaLong makeHandle(Frame frame, long lValue) {
        return makeHandle(frame.container(), lValue);
    }

    public static JavaLong makeHandle(Container container, long lValue) {
        return NativeTemplates.get(container).nibble().makeHandle(lValue);
    }

    public static JavaLong makeHandle(ClassTemplate template, long lValue) {
        return makeHandle(template.f_container, lValue);
    }

    public static JavaLong makeHandle(ObjectHandle owner, long lValue) {
        return makeHandle(owner.getComposition().getContainer(), lValue);
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        if (method.getName().equals("toChar")) {
            long lValue = ((JavaLong) hTarget).getValue();
            long cValue = lValue <= 9 ? '0' + lValue : 'A' + lValue - 0xA;
            return frame.assignValue(iReturn, xChar.makeHandle(frame, cValue));
        }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }

    private static final int CACHE_SIZE = 16;

    private final JavaLong[] cache = new JavaLong[CACHE_SIZE];
}
