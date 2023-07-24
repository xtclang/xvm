package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native checked UInt16 support.
 */
public class xCheckedUInt16
        extends xCheckedUnsignedInt
    {
    public static xCheckedUInt16 INSTANCE;

    public xCheckedUInt16(Container container, ClassStructure structure, boolean fInstance)
        {
        super(container, structure, 0L, 0xFFFFL, 16);

        if (fInstance)
            {
            INSTANCE = this;
            }
        }

    @Override
    protected xConstrainedInteger getComplimentaryTemplate()
        {
        return xCheckedInt16.INSTANCE;
        }
    }