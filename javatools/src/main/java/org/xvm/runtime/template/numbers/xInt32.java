package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native Int32 support.
 */
public class xInt32
        extends xConstrainedInteger {
    public xInt32(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, Integer.MIN_VALUE, Integer.MAX_VALUE, 32, false, false);
    }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate() {
        return nativeTemplates().get(xUInt32.class);
    }
}
