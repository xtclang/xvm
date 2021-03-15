package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.TemplateRegistry;


/**
 * Native Int16 support.
 */
public class xInt16
        extends xConstrainedInteger
    {
    public static xInt16 INSTANCE;

    public xInt16(TemplateRegistry templates, ClassStructure structure, boolean fInstance)
        {
        super(templates, structure, Short.MIN_VALUE, Short.MAX_VALUE, 16, false, true);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void registerNativeTemplates()
        {
        // create unchecked template
        registerNativeTemplate(new xUncheckedInt16(f_templates, f_struct, true));
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xUInt16.INSTANCE;
        }

    @Override
    protected xConstrainedInteger getUncheckedTemplate()
        {
        return xUncheckedInt16.INSTANCE;
        }
    }