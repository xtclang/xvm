package org.xvm.runtime.template.numbers;


import org.xvm.asm.ClassStructure;

import org.xvm.runtime.Container;


/**
 * Native unchecked UInt8 support.
 */
public class xUncheckedUInt8
        extends xUncheckedUnsignedInt
    {
    public static xUncheckedUInt8 INSTANCE;

    public xUncheckedUInt8(Container container, ClassStructure structure, boolean fInstance)
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
        return xUncheckedInt8.INSTANCE;
        }
    }