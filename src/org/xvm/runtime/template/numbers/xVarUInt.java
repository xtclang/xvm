package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native VarUInt16 support.
 */
public class xVarUInt
        extends xUnconstrainedInteger
    {
    public static xVarUInt INSTANCE;

    public xVarUInt(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }
    }
