package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native Int16 support.
 */
public class xInt16
        extends xConstrainedInteger {
    public xInt16(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, Short.MIN_VALUE, Short.MAX_VALUE, 16, false, false);
    }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate() {
        return f_container.nativeTemplate(xUInt16.class);
    }
}
