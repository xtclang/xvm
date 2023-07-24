package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native checked UInt8 support.
 */
public class xCheckedUInt8
        extends xCheckedUnsignedInt
    {
    public static xCheckedUInt8 INSTANCE;

    public xCheckedUInt8(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, 0L, 0xFFL, 8);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xCheckedInt8.INSTANCE;
        }
    }