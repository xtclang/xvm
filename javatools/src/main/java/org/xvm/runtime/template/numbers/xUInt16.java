package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native UInt16 support.
 */
public class xUInt16
        extends xUnsignedConstrainedInt {
    public xUInt16(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, 0, 2L * (long) Short.MAX_VALUE + 1, 16, false);
    }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate() {
        return nativeTemplates().get(xInt16.class);
    }
}
