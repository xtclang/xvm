package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native UIntN support.
 */
public class xUIntN
        extends xUnconstrainedInteger
    {
    public static xUIntN INSTANCE;

    public xUIntN(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, true, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }
    }