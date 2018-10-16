package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native UInt16 support.
 */
public class xUInt16
        extends xUnsignedConstrainedInt
    {
    public static xUInt16 INSTANCE;

    public xUInt16(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, 0, 2L * (long)Short.MAX_VALUE + 1, 16, true);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }
    }
