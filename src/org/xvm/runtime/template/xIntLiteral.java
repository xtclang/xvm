package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.ClassTemplate;
import org.xvm.runtime.TypeSet;


/**
 * TODO:
 */
public class xIntLiteral
        extends ClassTemplate
    {
    public xIntLiteral(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure);
        }

    @Override
    public void initDeclared()
        {
        }
    }
