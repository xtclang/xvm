package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native unchecked UInt32 support.
 */
public class xUncheckedUInt32
        extends xUncheckedUnsignedInt
    {
    public static xUncheckedUInt32 INSTANCE;

    public xUncheckedUInt32(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, 0L, 0xFFFF_FFFFL, 32);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xUncheckedInt32.INSTANCE;
        }
    }