package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native unchecked Int8 support.
 */
public class xUncheckedInt8
        extends xUncheckedConstrainedInt
    {
    public static xUncheckedInt8 INSTANCE;

    public xUncheckedInt8(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, Byte.MIN_VALUE, Byte.MAX_VALUE, 8, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xUncheckedUInt8.INSTANCE;
        }
    }
