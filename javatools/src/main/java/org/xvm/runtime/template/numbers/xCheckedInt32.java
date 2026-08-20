package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native checked Int32 support.
 */
public class xCheckedInt32
        extends xCheckedConstrainedInt {
    public xCheckedInt32(Container container, ClassStructure structure, boolean fInstance) {
        super(container, structure, Integer.MIN_VALUE, Integer.MAX_VALUE, 32, false);
    }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate() {
        return getComplimentaryTemplate("numbers.CheckedUInt32", xCheckedUInt32.class);
    }
}
