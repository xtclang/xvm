package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native Int16 support.
 */
public class xInt16
        extends xConstrainedInteger
    {
    public static xInt16 INSTANCE;

    public xInt16(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, Short.MIN_VALUE, Short.MAX_VALUE, 16, false, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void registerNativeTemplates()
        {
        // create unchecked template
        registerNativeTemplate(new xUncheckedInt16(f_container, f_struct, true));
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xUInt16.INSTANCE;
        }
    }