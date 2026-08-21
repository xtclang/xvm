package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native checked UInt8 support.
 */
public class xCheckedUInt8
        extends xCheckedUnsignedInt {
    public xCheckedUInt8(Container container, ClassStructure structure) {
        super(container, structure, 0L, 0xFFL, 8);
    }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate() {
        return getComplimentaryTemplate("numbers.CheckedInt8", xCheckedInt8.class);
    }
}
