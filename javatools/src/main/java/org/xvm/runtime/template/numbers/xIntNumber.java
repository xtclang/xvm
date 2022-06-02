package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native IntNumber support.
 */
public class xIntNumber
        extends xNumber
    {
    public xIntNumber(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);
        }

    @Override
    public void initNative()
        {
        }
    }