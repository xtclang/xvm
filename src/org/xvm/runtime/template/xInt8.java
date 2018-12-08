package org.xvm.runtime.template;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native Int8 support.
 */
public class xInt8
        extends xConstrainedInteger
    {
    public static xInt8 INSTANCE;

    public xInt8(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, Byte.MIN_VALUE, Byte.MAX_VALUE, 8, false, true);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xUInt8.INSTANCE;
        }
    }