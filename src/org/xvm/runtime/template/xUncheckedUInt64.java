package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native unchecked UInt64 support.
 */
public class xUncheckedUInt64
        extends xUncheckedConstrainedInt
    {
    public static xUncheckedUInt64 INSTANCE;

    public xUncheckedUInt64(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, Long.MIN_VALUE, Long.MAX_VALUE, 64, true);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }
    }