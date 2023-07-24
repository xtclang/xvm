package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native checked UInt32 support.
 */
public class xCheckedUInt32
        extends xCheckedUnsignedInt
    {
    public static xCheckedUInt32 INSTANCE;

    public xCheckedUInt32(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, 0L, 0xFFFF_FFFFL, 32);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xCheckedInt32.INSTANCE;
        }
    }