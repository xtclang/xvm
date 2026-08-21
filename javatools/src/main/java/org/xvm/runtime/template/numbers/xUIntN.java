package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native UIntN support.
 */
public class xUIntN
        extends xUnconstrainedInteger {
    public xUIntN(Container container, ClassStructure structure) {
        super(container, structure, false);
    }
}
