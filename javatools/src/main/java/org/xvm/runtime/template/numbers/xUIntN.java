package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native UIntN support.
 */
public class xUIntN
        extends xUnconstrainedInteger
    {
    public static xUIntN INSTANCE;

    public xUIntN(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }
    }
