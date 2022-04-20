package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native Int32 support.
 */
public class xInt32
        extends xConstrainedInteger
    {
    public static xInt32 INSTANCE;

    public xInt32(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, Integer.MIN_VALUE, Integer.MAX_VALUE, 32, false, true);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void registerNativeTemplates()
        {
        // create unchecked template
        registerNativeTemplate(new xUncheckedInt32(f_container, f_struct, true));
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xUInt32.INSTANCE;
        }

    @Override
    protected xConstrainedInteger getUncheckedTemplate()
        {
        return xUncheckedInt32.INSTANCE;
        }
    }