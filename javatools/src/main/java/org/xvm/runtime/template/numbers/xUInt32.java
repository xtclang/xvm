package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native UInt32 support.
 */
public class xUInt32
        extends xUnsignedConstrainedInt {
    public xUInt32(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, 0, 2L * (long) Integer.MAX_VALUE + 1, 32, false);
    }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate() {
        return getComplimentaryTemplate("numbers.Int32", xInt32.class);
    }
}
