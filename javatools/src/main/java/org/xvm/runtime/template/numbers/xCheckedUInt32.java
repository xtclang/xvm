package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native checked UInt32 support.
 */
public class xCheckedUInt32
        extends xCheckedUnsignedInt {
    public xCheckedUInt32(Container container, ClassStructure structure, boolean fBaseTemplate) {
        super(container, structure, 0L, 0xFFFF_FFFFL, 32);
    }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate() {
        return f_container.nativeTemplate(xCheckedInt32.class);
    }
}
