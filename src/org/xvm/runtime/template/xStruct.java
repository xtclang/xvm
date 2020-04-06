package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.TemplateRegistry;


/**
 * TODO:
 */
public class  xStruct
        extends ClassTemplate
    {
    public xStruct(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);
        }

    @Override
    public void initNative()
        {
        }
    }
