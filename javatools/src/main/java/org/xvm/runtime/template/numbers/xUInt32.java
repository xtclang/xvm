package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native UInt32 support.
 */
public class xUInt32
        extends xUnsignedConstrainedInt
    {
    public static xUInt32 INSTANCE;

    public xUInt32(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, 0, 2L * (long) Integer.MAX_VALUE + 1, 32, true);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void registerNativeTemplates()
        {
        // create unchecked template
        registerNativeTemplate(new xUncheckedUInt32(f_container, f_struct, true));
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xInt32.INSTANCE;
        }

    @Override
    protected xConstrainedInteger getUncheckedTemplate()
        {
        return xUncheckedUInt32.INSTANCE;
        }
    }