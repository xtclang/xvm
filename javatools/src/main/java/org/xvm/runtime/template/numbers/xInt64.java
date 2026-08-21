package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.MethodStructure;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;
import org.xvm.runtime.NativeTemplates;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.JavaLong;


/**
 * Native Int64 support.
 */
public class xInt64
        extends xConstrainedInteger {
    public xInt64(Container container, ClassStructure structure) {
        super(container, structure, Long.MIN_VALUE, Long.MAX_VALUE, 64, false, false);
    }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate() {
        return getComplimentaryTemplate("numbers.UInt64", xUInt64.class);
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

    public static JavaLong makeHandle(Frame frame, long lValue) {
        return makeHandle(frame.container(), lValue);
    }

    public static JavaLong makeHandle(Container container, long lValue) {
        return NativeTemplates.get(container).int64().makeJavaLong(lValue);
    }

    public static JavaLong makeHandle(ClassTemplate template, long lValue) {
        return makeHandle(template.f_container, lValue);
    }

    public static JavaLong makeHandle(ObjectHandle owner, long lValue) {
        return makeHandle(owner.getComposition().getContainer(), lValue);
    }
}
