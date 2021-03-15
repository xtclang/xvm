package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native unchecked Int64 support.
 */
public class xUncheckedInt64
        extends xUncheckedSignedInt
    {
    public static xUncheckedInt64 INSTANCE;

    public xUncheckedInt64(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, Long.MIN_VALUE, Long.MAX_VALUE, 64);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xUncheckedUInt64.INSTANCE;
        }
    }
