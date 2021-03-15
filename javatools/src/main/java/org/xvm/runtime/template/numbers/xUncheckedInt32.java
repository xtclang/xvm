package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native unchecked Int32 support.
 */
public class xUncheckedInt32
        extends xUncheckedSignedInt
    {
    public static xUncheckedInt32 INSTANCE;

    public xUncheckedInt32(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, Integer.MIN_VALUE, Integer.MAX_VALUE, 32);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xUncheckedUInt32.INSTANCE;
        }
    }
