package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native Int32 support.
 */
public class xInt32
        extends xConstrainedInteger
    {
    public static xInt32 INSTANCE;

    public xInt32(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, Integer.MIN_VALUE, Integer.MAX_VALUE, 32, false, true);

        if (fInstance)
            {
            INSTANCE = this;

            // create unchecked template
            new xUncheckedInt32(templates, structure, true);
            }
        }

    @Override
    public void initDeclared()
        {
        super.initDeclared();

        xUncheckedInt32.INSTANCE.initDeclared();
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xUInt32.INSTANCE;
        }

    @Override
    protected xUncheckedConstrainedInt getUncheckedTemplate()
        {
        return xUncheckedInt32.INSTANCE;
        }
    }
