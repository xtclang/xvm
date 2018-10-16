package org.xvm.runtime.template;

import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native unchecked UInt32 support.
 */
public class xUncheckedUInt32
        extends xUncheckedConstrainedInt
    {
    public static xUncheckedUInt32 INSTANCE;

    public xUncheckedUInt32(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, 0, 2L * (long)(Integer.MAX_VALUE), 32, true);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }
    }