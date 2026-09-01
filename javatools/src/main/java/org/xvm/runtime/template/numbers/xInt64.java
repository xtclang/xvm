package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;


/**
 * Native Int64 support.
 */
public class xInt64
        extends xConstrainedInteger {
    public xInt64(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, Long.MIN_VALUE, Long.MAX_VALUE, 64, false, false);
    }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate() {
        return f_container.nativeTemplate(xUInt64.class);
    }

    @Override
    public int invokeNativeN(Frame frame, MethodStructure method, ObjectHandle hTarget,
            ObjectHandle[] ahArg, int iReturn) {
        switch (method.getName()) {
        case "rotateLeft": {
            long lValue = ((JavaLong) hTarget).getValue();
            long cBits  = ((JavaLong) ahArg[0]).getValue();
            lValue = Long.rotateLeft(lValue, (int) (cBits & 0x3F));
            return frame.assignValue(iReturn, makeJavaLong(lValue));
        }

        case "rotateRight": {
            long lValue = ((JavaLong) hTarget).getValue();
            long cBits  = ((JavaLong) ahArg[0]).getValue();
            lValue = Long.rotateRight(lValue, (int) (cBits & 0x3F));
            return frame.assignValue(iReturn, makeJavaLong(lValue));
        }

        case "reverseBits": {
            long lValue = ((JavaLong) hTarget).getValue();
            return frame.assignValue(iReturn, makeJavaLong(Long.reverse(lValue)));
        }

        case "reverseBytes": {
            long lValue = ((JavaLong) hTarget).getValue();
            return frame.assignValue(iReturn, makeJavaLong(Long.reverseBytes(lValue)));
        }
        }

        return super.invokeNativeN(frame, method, hTarget, ahArg, iReturn);
    }

    /**
     * @return an Int64 handle owned by the specified container
     */
    public static JavaLong makeHandle(Container container, long lValue) {
        return container.nativeTemplate(xInt64.class).makeJavaLong(lValue);
    }

    /**
     * @return an Int64 handle owned by the container the specified frame runs in
     */
    public static JavaLong makeHandle(Frame frame, long lValue) {
        return makeHandle(frame.container(), lValue);
    }
}
