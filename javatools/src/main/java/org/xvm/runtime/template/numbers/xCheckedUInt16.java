package org.xvm.runtime.template.numbers;

import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;

/**
 * Native checked UInt16 support.
 */
public class xCheckedUInt16
        extends xCheckedUnsignedInt {
    public xCheckedUInt16(Container container, ClassStructure structure, boolean fBaseTemplate) {
        super(container, structure, 0L, 0xFFFFL, 16);
    }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate() {
        return f_container.nativeTemplate(xCheckedInt16.class);
    }
}
