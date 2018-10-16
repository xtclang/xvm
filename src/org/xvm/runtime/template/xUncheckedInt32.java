package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;

/**
 * Native unchecked Int32 support.
 */
public class xUncheckedInt32 extends xUncheckedConstrainedInt
    {
    public static xUncheckedInt32 INSTANCE;

    public xUncheckedInt32(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, Integer.MIN_VALUE, Integer.MAX_VALUE, 32, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }
    }
