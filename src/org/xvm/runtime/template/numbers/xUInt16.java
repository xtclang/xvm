package org.xvm.runtime.template.numbers;


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

            // create unchecked template
            new xUncheckedUInt16(templates, structure, true);
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xInt16.INSTANCE;
        }

    @Override
    protected xUncheckedConstrainedInt getUncheckedTemplate()
        {
        return xUncheckedUInt16.INSTANCE;
        }
    }
