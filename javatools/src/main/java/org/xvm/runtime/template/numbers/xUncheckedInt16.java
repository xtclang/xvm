package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native unchecked Int16 support.
 */
public class xUncheckedInt16
        extends xUncheckedSignedInt
    {
    public static xUncheckedInt16 INSTANCE;

    public xUncheckedInt16(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, Short.MIN_VALUE, Short.MAX_VALUE, 16);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xUncheckedUInt16.INSTANCE;
        }
    }
