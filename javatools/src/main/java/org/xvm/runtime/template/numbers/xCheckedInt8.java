package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native checked Int8 support.
 */
public class xCheckedInt8
        extends xCheckedConstrainedInt {
    public xCheckedInt8(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, Byte.MIN_VALUE, Byte.MAX_VALUE, 8, false);
    }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate() {
        return getComplimentaryTemplate("numbers.CheckedUInt8", xCheckedUInt8.class);
    }
}
