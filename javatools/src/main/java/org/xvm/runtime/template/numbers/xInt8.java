package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.constants.ByteConstant;

import org.xvm.runtime.Container;
import org.xvm.runtime.Frame;


/**
 * Native Int8 support.
 */
public class xInt8
        extends xConstrainedInteger {
    public xInt8(Container container, ClassStructure structure) {
        super(container, structure, Byte.MIN_VALUE, Byte.MAX_VALUE, 8, false, false);
    }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate() {
        return getComplimentaryTemplate("numbers.UInt8", xUInt8.class);
    }

    @Override
    public int createConstHandle(Frame frame, Constant constant) {
        if (constant instanceof ByteConstant constByte) {
            return frame.pushStack(makeJavaLong(constByte.getValue().longValue()));
        }

        return super.createConstHandle(frame, constant);
    }
}
