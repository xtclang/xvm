package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native UInt16 support.
 */
public class xUInt16
        extends xUnsignedConstrainedInt
    {
    public static xUInt16 INSTANCE;

    public xUInt16(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, 0, 2L * (long) Short.MAX_VALUE + 1, 16, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    public void registerNativeTemplates()
        {
        // create unchecked template
        registerNativeTemplate(new xUncheckedUInt16(f_container, f_struct, true));
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xInt16.INSTANCE;
        }
    }