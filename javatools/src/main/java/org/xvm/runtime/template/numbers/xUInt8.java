package org.xvm.runtime.template.numbers;


import java.util.Arrays;

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
    public xUInt8(Container container, ClassStructure structure) {
        super(container, structure, 0, 255, 8, false);
    }

    @Override
    public void initNative() {
        super.initNative();

        // No fInstance branch is needed here. UInt8 has no derived native Java template; the
        // final owner-local byte cache remains eager and direct, matching the previous
        // per-container handle reuse without keeping a constructor role flag.
        ClassComposition clz = getCanonicalClass();
        Arrays.setAll(cache, i -> new JavaLong(clz, i));
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

    private static final int CACHE_SIZE = 256;

    private final JavaLong[] cache = new JavaLong[CACHE_SIZE];
}
