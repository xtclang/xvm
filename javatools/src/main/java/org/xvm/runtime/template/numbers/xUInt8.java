package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.asm.Constant;

import org.xvm.asm.constants.ByteConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;


/**
 * Native UInt8 (Byte) support.
 */
public class xUInt8
        extends xUnsignedConstrainedInt {
    public xUInt8(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, 0, 255, 8, false);

        // Temporary legacy role flag: true only for the canonical native UInt8 template.
        // It owns the small-value cache below; replacing this boolean with an explicit
        // canonical-template cache is a follow-up cleanup.
        f_fInstance = fInstance;
    }

    @Override
    public void initNative() {
        super.initNative();

        if (f_fInstance) {
            ClassComposition clz = getCanonicalClass();
            for (int i = 0; i < cache.length; ++i) {
                cache[i] = new JavaLong(clz, i);
            }
        }
    }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate() {
        return getComplimentaryTemplate("numbers.Int8", xInt8.class);
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
        return makeHandle(lValue & 0xFFL);
    }

    public JavaLong makeHandle(long lValue) {
        assert lValue >= 0 & lValue <= 255;
        return cache[(int) lValue];
    }

    public static JavaLong makeHandle(Frame frame, long lValue) {
        return makeHandle(frame.container(), lValue);
    }

    public static JavaLong makeHandle(Container container, long lValue) {
        return NativeTemplates.get(container).uint8().makeHandle(lValue);
    }

    public static JavaLong makeHandle(ClassTemplate template, long lValue) {
        return makeHandle(template.f_container, lValue);
    }

    public static JavaLong makeHandle(ObjectHandle owner, long lValue) {
        return makeHandle(owner.getComposition().getContainer(), lValue);
    }

    /**
     * True only for the canonical native template; avoids a recursive NativeTemplates lookup while
     * prebuilding this owner template's cached byte handles.
     */
    private final boolean f_fInstance;

    private final JavaLong[] cache = new JavaLong[256];
}
