package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TypeSet;


/**
 * TODO:
 *
 * @author gg 2017.02.27
 */
public class xUInt64
        extends Const
    {
    public xUInt64(TypeSet types, ClassStructure structure, boolean fInstance)
        {
        super(types, structure, false);
        }

    @Override
    public void initDeclared()
        {
        }
    }
