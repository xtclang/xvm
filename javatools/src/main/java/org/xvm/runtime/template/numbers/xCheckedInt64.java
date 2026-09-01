package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native checked Int64 support.
 */
public class xCheckedInt64
        extends xCheckedConstrainedInt {
    public xCheckedInt64(Container container, ClassStructure structure, boolean fBaseTemplate) {
        super(container, structure, Long.MIN_VALUE, Long.MAX_VALUE, 64, false);
    }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate() {
        return f_container.nativeTemplate(xCheckedUInt64.class);
    }
}
