package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native UInt8 support.
 */
public class xUInt8
        extends xUnsignedConstrainedInt
    {
    public static xUInt8 INSTANCE;

    public xUInt8(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, 0, 255, 8, true);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }
    }
