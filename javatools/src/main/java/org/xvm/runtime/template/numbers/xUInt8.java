package org.xvm.runtime.template.numbers;

import org.xvm.asm.ClassStructure;

import org.xvm.asm.Constant;

import org.xvm.asm.constants.ByteConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle.JavaLong;

/**
 * Native UInt8 (Byte) support.
 */
public class xUInt8
        extends xUnsignedConstrainedInt {
    public xUInt8(Container container, ClassStructure structure, boolean fBaseTemplate) {
        super(container, structure, 0, 255, 8, false);
    }

    @Override
    public void initNative() {
        super.initNative();

        if (isNativeInstance(xUInt8.class)) {
            ClassComposition clz = getCanonicalClass();
            for (int i = 0; i < cache.length; ++i) {
                cache[i] = new JavaLong(clz, i);
            }
        }
    }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate() {
        return f_container.nativeTemplate(xInt8.class);
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

    /**
     * @return a Byte handle owned by this template's container
     */
    public JavaLong makeHandle(long lValue) {
        assert lValue >= 0 & lValue <= 255;
        return cache[(int) lValue];
    }

    /**
     * @return a Byte handle owned by the specified container
     */
    public static JavaLong makeHandle(Container container, long lValue) {
        return container.nativeTemplate(xUInt8.class).makeHandle(lValue);
    }

    /**
     * @return a Byte handle owned by the container the specified frame runs in
     */
    public static JavaLong makeHandle(Frame frame, long lValue) {
        return makeHandle(frame.container(), lValue);
    }

    private final JavaLong[] cache = new JavaLong[256];
}
