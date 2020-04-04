package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native UInt32 support.
 */
public class xUInt32
        extends xUnsignedConstrainedInt
    {
    public static xUInt32 INSTANCE;

    public xUInt32(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, 0, 2L * (long)Integer.MAX_VALUE + 1, 32, true);

        if (fInstance)
            {
            INSTANCE = this;

            // create unchecked template
            new xUncheckedUInt32(templates, structure, true);
            }
        }

    @Override
    public void initDeclared()
        {
        super.initDeclared();

        xUncheckedUInt32.INSTANCE.initDeclared();
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xInt32.INSTANCE;
        }

    @Override
    protected xUncheckedConstrainedInt getUncheckedTemplate()
        {
        return xUncheckedUInt32.INSTANCE;
        }
    }
