package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native checked Int16 support.
 */
public class xCheckedInt16
        extends xCheckedConstrainedInt {
    public xCheckedInt16(Container container, ClassStructure structure) {
        super(container, structure, Short.MIN_VALUE, Short.MAX_VALUE, 16, false);
    }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate() {
        return getComplimentaryTemplate("numbers.CheckedUInt16", xCheckedUInt16.class);
    }
}
