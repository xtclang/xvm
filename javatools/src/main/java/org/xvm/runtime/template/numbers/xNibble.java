package org.xvm.runtime.template.numbers;

import org.xvm.asm.ClassStructure;

import org.xvm.asm.Constant;
import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.ByteConstant;

import org.xvm.runtime.ClassComposition;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;

import org.xvm.runtime.template.text.xChar;

/**
 * Native Nibble (Byte) support.
 */
public class xNibble
        extends xUnsignedConstrainedInt {
    public static xNibble INSTANCE;

    public xNibble(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, 0, 15, 4, false);

        if (fInstance) {
            INSTANCE = this;
        }
    }

    @Override
    public void initNative() {
        super.initNative();

        if (this == INSTANCE) {
            ClassComposition clz = getCanonicalClass();
            for (int i = 0; i < cache.length; ++i) {
                cache[i] = new JavaLong(clz, i);
            }
        }
    }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate() {
        return xNibble.INSTANCE;
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

    public static JavaLong makeHandle(long lValue) {
        assert lValue >= 0 & lValue <= 15;
        return INSTANCE.cache[(int) lValue];
    }

    @Override
    public int invokeNative1(Frame frame, MethodStructure method, ObjectHandle hTarget,
                             ObjectHandle hArg, int iReturn) {
        if (method.getName().equals("toChar")) {
            long lValue = ((JavaLong) hTarget).getValue();
            long cValue = lValue <= 9 ? '0' + lValue : 'A' + lValue - 0xA;
            return frame.assignValue(iReturn, xChar.makeHandle(cValue));
        }
        return super.invokeNative1(frame, method, hTarget, hArg, iReturn);
    }

    private final JavaLong[] cache = new JavaLong[16];
}
