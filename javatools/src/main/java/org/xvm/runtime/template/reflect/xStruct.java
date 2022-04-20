package org.xvm.runtime.template.reflect;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.Container;


/**
 * TODO:
 */
public class  xStruct
        extends ClassTemplate
    {
    public xStruct(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure);
        }

    @Override
    public void initNative()
        {
        }
    }