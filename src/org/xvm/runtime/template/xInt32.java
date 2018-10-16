package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native Int32 support.
 */
public class xInt32
        extends xConstrainedInteger
    {
    public static xInt32 INSTANCE;

    public xInt32(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, Integer.MIN_VALUE, Integer.MAX_VALUE, 32, false, true);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }
    }
