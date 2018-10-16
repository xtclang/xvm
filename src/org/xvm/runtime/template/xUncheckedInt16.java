package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native unchecked Int16 support.
 */
public class xUncheckedInt16
        extends xUncheckedConstrainedInt
    {
    public static xUncheckedInt16 INSTANCE;

    public xUncheckedInt16(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, Short.MIN_VALUE, Short.MAX_VALUE, 16, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }
    }
