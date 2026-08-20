package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component.Format;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.TypeComposition;

import org.xvm.runtime.template.text.xString;

/**
 * Native Boolean implementation.
 */
public class xBoolean
        extends xEnum {
    public static xBoolean getInstance(Frame frame) {
        return NativeTemplates.get(frame).booleanTemplate();
    }

    public static xBoolean getInstance(Container container) {
        return NativeTemplates.get(container).booleanTemplate();
    }

    public xBoolean(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure);
    }

    @Override
    public void initNative() {
        if (getStructure().getFormat() == Format.ENUM) {
            super.initNative();

            // Preserve the old eager native-enum cache warmup, but keep the handles owned by
            // this template's container instead of publishing them in mutable process globals.
            getEnumByOrdinal(0);
            getEnumByOrdinal(1);
        }
    }

    @Override
    protected EnumHandle makeEnumHandle(TypeComposition clz, int iOrdinal) {
        return new BooleanHandle(clz, iOrdinal != 0);
    }

    @Override
    public int invokeAnd(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        return frame.assignValue(iReturn,
                makeHandle(frame, ((BooleanHandle) hTarget).get() & ((BooleanHandle) hArg).get()));
    }

    @Override
    public int invokeOr(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        return frame.assignValue(iReturn,
                makeHandle(frame, ((BooleanHandle) hTarget).get() | ((BooleanHandle) hArg).get()));
    }

    @Override
    public int invokeXor(Frame frame, ObjectHandle hTarget, ObjectHandle hArg, int iReturn) {
        return frame.assignValue(iReturn,
                makeHandle(frame, ((BooleanHandle) hTarget).get() ^ ((BooleanHandle) hArg).get()));
    }

    @Override
    public int invokeNeg(Frame frame, ObjectHandle hTarget, int iReturn) {
        return frame.assignValue(iReturn, not((BooleanHandle) hTarget));
    }

    @Override
    public int invokeCompl(Frame frame, ObjectHandle hTarget, int iReturn) {
        return frame.assignValue(iReturn, not((BooleanHandle) hTarget));
    }

    @Override
    protected int buildStringValue(Frame frame, ObjectHandle hTarget, int iReturn) {
        return frame.assignValue(iReturn,
                xString.makeHandle(frame, ((BooleanHandle) hTarget).get() ? "True" : "False"));
    }

    public static BooleanHandle makeHandle(Frame frame, boolean f) {
        return makeHandle(frame.container(), f);
    }

    public static BooleanHandle makeHandle(Container container, boolean f) {
        return (BooleanHandle) getInstance(container).getEnumByOrdinal(f ? 1 : 0);
    }

    public static BooleanHandle trueHandle(Frame frame) {
        return makeHandle(frame, true);
    }

    public static BooleanHandle trueHandle(Container container) {
        return makeHandle(container, true);
    }

    public static BooleanHandle falseHandle(Frame frame) {
        return makeHandle(frame, false);
    }

    public static BooleanHandle falseHandle(Container container) {
        return makeHandle(container, false);
    }

    public static BooleanHandle not(BooleanHandle hValue) {
        return makeHandle(hValue.getComposition().getContainer(), !hValue.get());
    }

    public static boolean isTrue(ObjectHandle hValue) {
        return hValue instanceof BooleanHandle hBool && hBool.get();
    }

    public static boolean isFalse(ObjectHandle hValue) {
        return hValue instanceof BooleanHandle hBool && !hBool.get();
    }

    public static class BooleanHandle
                extends EnumHandle {
        BooleanHandle(TypeComposition clz, boolean f) {
            super(clz, f ? 1 : 0);
        }

        public boolean get() {
            return m_index != 0;
        }

        @Override
        public String toString() {
            return m_index == 0 ? "False" : "True";
        }
    }
}
