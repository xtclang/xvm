package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native UInt32 support.
 */
public class xUInt32
        extends xUnsignedConstrainedInt
    {
    public static xUInt32 INSTANCE;

    public xUInt32(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, 0, 2L * (long)Integer.MAX_VALUE + 1, 32, true);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }
    }
