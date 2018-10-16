package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native Int16 support.
 */
public class xInt16
        extends xConstrainedInteger
    {
    public static xInt16 INSTANCE;

    public xInt16(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, Short.MIN_VALUE, Short.MAX_VALUE, 16, false, true);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }
    }