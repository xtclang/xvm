package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;

import org.xvm.runtime.template.xConst;


/**
 * Native Number support.
 */
public class xNumber
        extends xConst
    {
    public xNumber(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, false);
        }

    @Override
    public void initNative()
        {
        }
    }