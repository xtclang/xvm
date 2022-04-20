package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;


/**
 * TODO:
 */
public class xNumber
        extends ClassTemplate
    {
    public xNumber(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure);
        }

    @Override
    public void initNative()
        {
        }
    }