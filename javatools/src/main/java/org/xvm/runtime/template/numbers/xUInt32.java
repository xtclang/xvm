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
        super(container, structure, 0, 2L * (long) Integer.MAX_VALUE + 1, 32, false);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xInt32.INSTANCE;
        }
    }